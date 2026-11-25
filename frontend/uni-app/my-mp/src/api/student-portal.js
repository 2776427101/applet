/* 学生端接口
 *
 * 和老师端共用同一个 token 存储位（storage key 都是 token）：
 * 一台设备同时只应该有一个身份，切换身份就重新登录。
 * 但会额外存一个 userRole，入口页据此判断"上次是老师还是学生"。
 */
import { request, setToken, clearToken } from './request'

/**
 * 学生登录：学号 + 姓名
 *
 * <p>三种结果：
 * <ul>
 *   <li>正常进班 → `{ token, expiresIn, student }`</li>
 *   <li>学号在多个班 → `{ needClass:true, classes:[...] }`，<b>没有 token</b>，
 *       由页面弹选择器，选完带上 classId 再调一次</li>
 *   <li><b>花名册里没有这个人</b> → `{ token, student, needJoin:true }`，
 *       token 是"未入班"的，只能调入班接口，页面要直接跳入班页</li>
 * </ul>
 *
 * <p>学生没有微信账号可绑（t_student 只是一行花名册），所以不走 wx-login。
 *
 * @param {{studentNo:string, name:string, classId?:number}} data
 * @returns {Promise<Object>}
 */
export async function studentLogin({ studentNo, name, classId }) {
  const body = { studentNo: studentNo.trim() }
  if (name && name.trim()) {
    body.name = name.trim()
  }
  if (classId) {
    body.classId = classId
  }
  const data = await request({
    url: '/auth/student-login',
    method: 'POST',
    data: body,
    auth: false /* 登录接口自己不需要 token */
  })

  /* 需要选班：这时候 data.token 是 null，千万不能存 ——
     存了 token=null，下次冷启动会以为"已登录"然后每个接口都 401 */
  if (data && data.needClass) {
    return data
  }

  setToken(data.token)
  uni.setStorageSync('studentInfo', data.student)
  uni.setStorageSync('userRole', 2)
  return data
}

/** 我是谁 + 未读数 */
export function fetchStudentMe() {
  return request({ url: '/student/me' })
}

/** 和班主任的对话 */
export function fetchStudentThread() {
  return request({ url: '/student/message' })
}

/** 回复班主任 */
export function sendStudentMessage(content) {
  return request({ url: '/student/message', method: 'POST', data: { content } })
}

/** 标为已读 */
export function markStudentRead() {
  return request({ url: '/student/message/read', method: 'POST' })
}

/** 退出学生身份，回入口页 */
export function studentLogout() {
  clearToken()
  uni.removeStorageSync('studentInfo')
  uni.removeStorageSync('userRole')
  uni.reLaunch({ url: '/pages/index/index' })
}

/**
 * 凭邀请码加入（切换到）另一个班。
 *
 * 成功后 token 换成新班的，`studentInfo` 也一起换 —— 这一步必须原子地做完：
 * 只换 token 不换 studentInfo 的话，首页还会显示旧班的班名和学号，
 * 而接口返回的已经是新班的数据，看上去像"数据错乱"。
 *
 * @param {string} inviteCode 6 位班级邀请码
 * @returns {Promise<{token:string, student:Object, alreadyIn:boolean}>}
 */
export async function joinClass(inviteCode) {
  const data = await request({
    url: '/student/join',
    method: 'POST',
    data: { inviteCode: String(inviteCode || '').trim() }
  })

  /* 已经在这个班：后端也会返回一张可用的 token，这里照样存。
     不存的话，页面要区分"有没有 token"两种状态，而漏掉一条就是拿着旧 token
     去调新班的接口 —— 401 会以"登录失败"的形式出现，指错方向 */
  setToken(data.token)
  uni.setStorageSync('studentInfo', data.student)
  uni.setStorageSync('userRole', 2)
  return data
}
