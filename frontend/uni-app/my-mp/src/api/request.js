/* 统一请求封装：所有接口都从这里出去，页面里面不要再直接调 uni.request */

/* 后端地址。
   开发时不配环境变量，默认连本机后端；正式打包时必须传 VITE_API_BASE_URL，
   例如：https://api.example.com/api（末尾的 /api 不要漏）。

   为什么不直接写死线上域名：同一份代码要同时支持本机开发和正式发布，
   而线上域名只有部署时才知道。用环境变量可以避免「发版前改代码、发完又改回来」。

   导出它是因为 uni.uploadFile 不走下面的 request 封装（它传的是文件不是 JSON），
   但同样要拼这个地址 —— 两处共用一份，改端口时不会漏。 */
const ENV_BASE_URL = import.meta.env.VITE_API_BASE_URL
export const BASE_URL = (ENV_BASE_URL || 'http://localhost:8080/api').replace(/\/+$/, '')

/* token 在 storage 里的 key, 存和取都用同一个常量，避免手写字符串写错 */
const TOKEN_KEY = 'token'

/* 登录失效时跳回的页面 */
const ENTRY_PAGE = '/pages/index/index'

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
 * @returns {Promise<any>}               直接返回后端 data 字段的内容
 */
export function request({ url, method = 'GET', data = {}, auth = true, silent = false }) {
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
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      timeout: 10000,
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
        reject(new Error(msg))
      },
      fail: (err) => {
        /* 网络层就失败了：后端没启动、断网、域名不在白名单。
           注意这里必须 reject 一个 Error 而不是原始的 err 对象 ——
           原始对象只有 errMsg: "request:fail ..."，页面拿 e.message 是 undefined，
           想显示原因就只能显示「加载失败」四个字 */
        const msg = '网络异常，请检查后端是否启动'
        toast(msg)
        reject(new Error(msg))
      }
    })
  })
}
