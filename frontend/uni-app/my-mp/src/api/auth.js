/* 登录相关接口 */
import { request, setToken, clearToken } from './request'
/* ------------------------------------------------------------------
   开发模式默认用固定 code，配合后端 WX_MOCK=true 登录测试账号。
   正式构建（npm run build:*）自动走真实 wx.login()，不需要发版前手工改开关。

   如果开发时也要联调真实微信登录，设置 VITE_USE_MOCK_LOGIN=false。
   注意：后端 mock 开着时不能传真实 code —— 每次 code 都不同，
   会被当成不同 openid，绑好的班级就对不上了。
   ------------------------------------------------------------------ */
const USE_MOCK_LOGIN = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_LOGIN !== 'false'
const MOCK_CODE = 'test_openid_001'
/* 拿登录用的 code */
function getLoginCode() {
  if (USE_MOCK_LOGIN) {
    return Promise.resolve(MOCK_CODE)
  }
  /* 上线后走这里：调微信拿一次性 code（5 分钟有效，只能用一次） */
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (res) => resolve(res.code),
      fail: reject
    })
  })
}
/**
 * 登录：code 换 token，成功后自动存起来
 * @returns {Promise<Object>} { token, expiresIn, needBindClass, user }
 */
export async function login() {
  const code = await getLoginCode()

  const data = await request({
    url: '/auth/wx-login',
    method: 'POST',
    data: { code },
    auth: false /* 登录接口自己不需要 token */
  })
  setToken(data.token)
  /* 顺便把用户信息存一份，页面可以先渲染名字，不用等接口 */
  uni.setStorageSync('userInfo', data.user)
  return data
}
/**
 * 查当前登录用户，冷启动时用来校验 token 还有效没
 * @returns {Promise<Object>} { id, name, role, avatar, classId, className, phone, unit }
 */
export function fetchMe() {
  return request({ url: '/auth/me' })
}

/**
 * 改自己的资料（姓名、手机号、单位）。
 *
 * 注意路径是 /auth/me，不带 id —— 后端只认 token 里的那个人，
 * 传 id 过去也改不了别人。这一点很重要：能改谁的资料不能由前端说了算。
 *
 * @param {{name:string, phone?:string, unit?:string}} data
 * @returns {Promise<Object>} 改完之后的用户信息，直接拿去刷新页面
 */
export function updateProfile(data) {
  return request({ url: '/auth/me', method: 'PUT', data })
}
/* 退出登录：清掉本地凭证，回入口页 */
export function logout() {
  clearToken()
  uni.removeStorageSync('userInfo')
  uni.reLaunch({ url: '/pages/index/index' })
}