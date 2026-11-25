/* 后端地址的「运行时」配置。
 *
 * 为什么需要这个文件：
 *   原来的地址是构建时烧进包里的（.env.development / .env.production）。
 *   后果是每换一次网络 —— 手机热点、宿舍路由、教室 Wi-Fi —— 电脑的局域网 IP
 *   就会被重新分配一次，包里的旧 IP 立刻失效，手机上报「连不上 http://x.x.x.x」。
 *   修它得改 .env、重新构建、重新点「预览」出二维码、手机重新扫，四步。
 *
 *   这里把地址存一份到 storage，改地址只要在手机上点两下，不用碰构建。
 *   storage 里有值就用它，没有才回落到构建时烧进来的默认值 ——
 *   所以已经能用的包行为不变，只是多了一条逃生通道。
 */

const KEY = 'apiBaseUrl'

/* 构建时烧进来的地址（来自 .env.*），是「没改过」时的默认值 */
const ENV_BASE_URL = import.meta.env.VITE_API_BASE_URL

export const DEFAULT_BASE_URL = (ENV_BASE_URL || 'http://localhost:8080/api').replace(/\/+$/, '')

/**
 * 当前实际使用的后端地址。
 * 注意：不要把它当常量缓存到模块顶层，用户改了地址之后必须能立刻读到新值。
 */
export function getBaseUrl() {
  const saved = uni.getStorageSync(KEY)
  return String(saved || DEFAULT_BASE_URL).replace(/\/+$/, '')
}

/**
 * 把用户随手输入的地址整理成规范形式。
 *
 * 允许输入的写法（都是人在手机上愿意打的）：
 *   192.168.43.27              → http://192.168.43.27:8080/api
 *   192.168.43.27:8080         → http://192.168.43.27:8080/api
 *   http://192.168.43.27:8080  → http://192.168.43.27:8080/api
 *   https://api.example.com    → 原样（域名不补端口，线上走 443）
 *
 * 为什么要自动补 :8080 和 /api：漏了任何一个，报错都是「连不上」，
 * 用户根本不知道是少写了一段。能替人补的就别让人自己发现。
 */
export function normalizeBaseUrl(input) {
  let v = String(input || '').trim()
  if (!v) return ''

  // 只给了 IP / 域名，没有协议
  if (!/^https?:\/\//i.test(v)) v = 'http://' + v

  /* 补端口只对「IP 地址」做。
     域名不补：线上是 https://api.xxx.com，补成 :8080 反而是错的。
     开发场景里要填的永远是局域网 IP，这条规则刚好覆盖。 */
  const m = v.match(/^(https?:\/\/)([^/:]+)(:\d+)?(\/.*)?$/i)
  if (m) {
    const [, scheme, host, port, rest] = m
    const isIp = /^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host === 'localhost'
    const needPort = isIp && !port
    v = scheme + host + (port || (needPort ? ':8080' : '')) + (rest || '')
  }

  v = v.replace(/\/+$/, '')
  // 补 /api：后端的 context-path 是 /api，漏了它所有接口都是 404
  if (!/\/api$/i.test(v)) v = v + '/api'

  return v
}

/**
 * 保存地址。传空串等于「恢复成构建时的默认值」。
 * @returns {string} 实际生效的地址
 */
export function setBaseUrl(input) {
  const v = normalizeBaseUrl(input)
  if (!v) {
    uni.removeStorageSync(KEY)
    return DEFAULT_BASE_URL
  }
  uni.setStorageSync(KEY, v)
  return v
}

/** 是否被手动改过（用来在界面上提示"当前不是默认地址"） */
export function isCustomBaseUrl() {
  return !!uni.getStorageSync(KEY)
}

/**
 * 弹一个输入框改地址。
 *
 * 用 showModal 的 editable 而不是单独做一个页面：这个操作一年用不了几次，
 * 为一个输入框加一页、加一条路由、加一次构建不值得。
 * editable 需要基础库 2.17.1+，本项目是 3.17.5；不支持时 res.content 是
 * undefined，会走到下面的兜底提示，不会静默失败。
 *
 * @param {(newUrl:string)=>void} [onDone] 改完后的回调（一般是重新拉数据）
 */
export function promptApiHost(onDone) {
  const cur = getBaseUrl()
  uni.showModal({
    title: '服务器地址',
    content: `当前：${cur}\n\n换网络后电脑 IP 会变，改这里就行，不用重新打包。`,
    editable: true,
    placeholderText: '如 192.168.1.5:8080',
    confirmText: '保存',
    success: (res) => {
      if (!res.confirm) return

      // 基础库太老 / 平台不支持 editable 时，这里拿不到输入内容
      if (res.content === undefined) {
        uni.showModal({
          title: '这个版本改不了',
          content: '当前微信版本不支持在这里输入。请升级微信，或让开发者改 .env 后重新打包。',
          showCancel: false
        })
        return
      }

      const raw = String(res.content || '').trim()
      // 没输入就当作「不改」，而不是把地址清空 —— 清空会直接变成连不上
      if (!raw) return

      const v = setBaseUrl(raw)
      uni.showToast({ title: `已改为 ${v.replace(/^https?:\/\//, '')}`, icon: 'none' })
      if (onDone) onDone(v)
    }
  })
}

/** 恢复成构建时的默认地址 */
export function resetBaseUrl() {
  uni.removeStorageSync(KEY)
  return DEFAULT_BASE_URL
}
