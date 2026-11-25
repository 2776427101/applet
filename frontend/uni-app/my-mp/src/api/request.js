/* 统一请求封装：所有接口都从这里出去，页面里面不要再直接调 uni.request */

import { getBaseUrl, promptApiHost } from '@/utils/api-host'

/* 后端地址不再是一个模块级常量，而是每次发请求时现取。
   原因见 utils/api-host.js 顶部：地址要能在运行时改，缓存成常量就改不动了。

   下面这个 getter 只是给老代码留的兼容出口，新代码请直接用 getBaseUrl()。 */
export function baseUrl() {
    return getBaseUrl()
}

/* token 在 storage 里的 key, 存和取都用同一个常量，避免手写字符串写错 */
const TOKEN_KEY = 'token'

/* 登录失效时跳回的页面 */
const ENTRY_PAGE = '/pages/index/index'

/* 上一次弹「连不上」的时间戳。多个请求同时失败时只弹一次，
   否则一个页面并发四五个请求就会叠出四五个一样的弹窗 */
let lastNetPromptAt = 0

export function getToken() {
    return uni.getStorageSync(TOKEN_KEY)
}

export function setToken(token) {
    uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken() {
    uni.removeStorageSync(TOKEN_KEY)
}

/**
 * 发一个请求
 * @param {Object}  options              请求参数
 * @param {string}  options.url          接口路径，不含 BASE_URL，如 '/auth/wx-login'
 * @param {string}  [options.method]     请求方法，默认 GET
 * @param {Object}  [options.data]       请求体（POST）或 query（GET）
 * @param {boolean} [options.auth]       是否需要带 token，默认 true
 * @param {boolean} [options.silent]     失败时不弹 toast，默认 false
 *                                       页面自己会画「加载失败 + 重试」时传 true，
 *                                       否则 toast 和页面上的提示会同时出现，重复两遍
 * @param {number}  [options.timeout]    超时毫秒数，默认 10000
 *                                       只有 AI 问答这种「服务端还要再去请求别人」的接口需要调大，
 *                                       普通查库接口超过 10 秒一定是出问题了，让它早点失败
 * @returns {Promise<any>}               直接返回后端 data 字段的内容
 */
export function request({ url, method = 'GET', data = {}, auth = true, silent = false, timeout = 10000 }) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    /* 只有需要鉴权的接口才注入 token，登录接口自己不用带 */
    if (auth) {
      const token = getToken()
      if (token) {
        header.Authorization = `Bearer ${token}`
      }
    }
    /* 失败提示统一走这一个函数，silent 时静默 —— 保证「要不要弹」只有一处判断 */
    const toast = (msg) => {
      if (!silent) uni.showToast({ title: msg, icon: 'none' })
    }
    /* 地址在这里现取：用户在手机上改过地址之后，下一次请求就该走新地址 */
    uni.request({
      url: getBaseUrl() + url,
      method,
      data,
      header,
      timeout,
      success: (res) => {
        const body = res.data
        /* 关键：后端出错时 HTTP 状态码可能是 400/401/500，
           但 body 里依然是 {code, message, data} 结构。
           所以先解 body 判断业务码，不要先看 statusCode */
        if (!body || typeof body.code === 'undefined') {
          const msg = `响应格式异常（HTTP ${res.statusCode}）`
          toast(msg)
          reject(new Error(msg))
          return
        }
        /* 业务码 0 才是成功（注意不是 200），直接把 data 抛出去，页面少解一层 */
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        /* 1002 = token 无效或过期：清掉本地的，跳回入口页重新登录 */
        if (body.code === 1002) {
          clearToken()
          uni.removeStorageSync('userInfo')
          /* 已经在入口页就不要再 reLaunch，否则会无限重载 */
          const pages = getCurrentPages()
          const current = pages[pages.length - 1]
          if (!current || current.route !== 'pages/index/index') {
            uni.reLaunch({ url: ENTRY_PAGE })
          }
          const msg = body.message || '登录已失效'
          toast(msg)
          reject(new Error(msg))
          return
        }
        /* 其他业务错误（1001/1003/2002/3001/5000...）：统一提示一次，
           页面 catch 到之后不用再提示，避免弹两次 */
        const msg = body.message || '请求失败'
        toast(msg)
        /* 把业务码挂在 Error 上：有些页面要按码做不同反应
           （比如 3005 被禁言要把输入框置灰，而不是只弹一句提示），
           只给 message 的话页面只能去匹配中文字符串，改文案就崩 */
        const err = new Error(msg)
        err.code = body.code
        reject(err)
      },
      fail: (err) => {
        /* 网络层就失败了：后端没启动、断网、IP 变了、域名不在白名单。
           注意这里必须 reject 一个 Error 而不是原始的 err 对象 ——
           原始对象只有 errMsg: "request:fail ..."，页面拿 e.message 是 undefined，
           想显示原因就只能显示「加载失败」四个字。

           **一定要把实际连的地址带出来。** 原来只写「网络异常，请检查后端是否启动」，
           但真机测试时后端明明是活的 —— 是包里的内网 IP 过期了
           （换网络后 DHCP 会重新分配）。提示里没有地址，就只能在两个地方瞎猜。
           把 host 打出来，一眼就能看出"它连的 172.x，可我电脑现在是 192.168.x"。 */
        const host = getBaseUrl().replace(/\/api\/?$/, '')
        const msg = `连不上 ${host}`

        /* 连不上是唯一「用户自己能修」的错误，所以这里不弹 toast，弹一个能改地址的框。
           换成 toast 的话，用户只能干看着，还得找人重新打包。

           为什么不管 silent：silent 的本意是「别和页面自己画的错误块重复」，
           但连不上时页面上那个「重试」按钮点一百次也点不出结果，
           这一条路不给，用户就彻底卡死了。

           10 秒内只弹一次：一个页面可能同时发好几个请求（首页就是），
           不拦的话会叠出四五个一模一样的弹窗。 */
        const now = Date.now()
        if (now - lastNetPromptAt > 10000) {
          lastNetPromptAt = now
          uni.showModal({
            title: '连不上服务器',
            content: `${host}\n\n换了网络之后电脑的 IP 会变，可以在这里改。`,
            confirmText: '改地址',
            cancelText: '知道了',
            success: (r) => {
              if (r.confirm) promptApiHost()
            }
          })
        }
        reject(new Error(msg))
      }
    })
  })
}
