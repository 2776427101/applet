/* 学生端业务数据接口（首页 / 请假 / 成绩 / 座位 / 通知 / 资料）
 *
 * 和 student-portal.js 的分工：
 *   student-portal.js → 身份（登录、我是谁、和班主任的私信）
 *   student-data.js   → 业务（请假、成绩、座位、通知、资料）
 *
 * 这里所有接口都**不传 studentId / classId** —— 后端从 token 里取。
 * 前端多传一个 id，就多一个能改成别人 id 的参数。
 */
import { request, getToken } from './request'
import { getBaseUrl } from '@/utils/api-host'

/** 首页聚合：班级、四个数字、周次、最近成绩、我的座位 */
export function fetchStudentHome() {
  return request({ url: '/student/home' })
}

/**
 * 我的请假记录（按提交时间倒序）
 * @returns {Promise<Array>} [{ id, type, startAt, endAt, reason, status, auditRemark, canCancel, canReport }]
 */
export function fetchMyLeaves() {
  /* silent：页面自己会画「加载失败 + 重试」，再弹 toast 就是同一句话说两遍 */
  return request({ url: '/student/leave', silent: true })
}

/** 单条请假详情 */
export function fetchLeaveDetail(id) {
  return request({ url: `/student/leave/${id}`, silent: true })
}

/**
 * 提交请假
 * @param {{type:number, startAt:string, endAt:string, reason?:string}} data
 *        startAt / endAt 必须是 'yyyy-MM-dd HH:mm:ss'
 * @returns {Promise<{id:number}>}
 */
export function applyLeave(data) {
  return request({ url: '/student/leave', method: 'POST', data })
}

/** 撤销申请（只有待审批能撤） */
export function cancelLeave(id) {
  return request({ url: `/student/leave/${id}/cancel`, method: 'POST' })
}

/** 销假（只有已通过能销） */
export function reportLeave(id) {
  return request({ url: `/student/leave/${id}/report`, method: 'POST' })
}

/**
 * 我的成绩
 * @returns {Promise<{list:Array, summary:Object}>}
 */
export function fetchMyScore() {
  return request({ url: '/student/score', silent: true })
}

/** 我的座位 */
export function fetchMySeat() {
  return request({ url: '/student/seat', silent: true })
}

/**
 * 我的通知：请假审批结果 + 班主任未读私信
 * @param {number} [limit]
 */
export function fetchMyNotices(limit = 50) {
  return request({ url: '/student/notice', data: { limit }, silent: true })
}

/** 我的资料 */
export function fetchMyProfile() {
  return request({ url: '/student/profile', silent: true })
}

/**
 * 问乐迪（学生版）
 * @param {Array<{role:string, content:string}>} messages 对话历史，最后一条是 user
 * @returns {Promise<{reply:string, toolCalls:number}>}
 */
export function askStudentAidi(messages) {
  return request({
    url: '/student/aidi/chat',
    method: 'POST',
    data: { messages },
    /* 服务端还要再去请求 DeepSeek，默认 10 秒不够 —— 实测短答案 1~3 秒，
       给 60 秒是留给"模型连着查了几次数据"或者上游偶尔抽风的情况。
       注意这里放宽的只是「等多久算超时」，不改变实际等待时间 */
    timeout: 60000
  })
}

/**
 * 改联系方式
 * @param {{phone?:string, wechat?:string, dormitory?:string}} data
 *        **不传的字段表示不改**，传空串表示清空
 */
export function updateMyProfile(data) {
  return request({ url: '/student/profile', method: 'PUT', data })
}

/* ---------------- 群聊 ----------------
 * 学生端的群就是老师端建的那些群（t_subject_container），一份消息、一个邀请码。
 * v0.39 曾经是另建的一张班级群表，结果老师看不到学生在说什么 —— 已经废弃。
 */

/**
 * 我能看到的群：本班的（自动）+ 用邀请码加入的
 * @returns {Promise<Array<{id,name,subjectName,memberCount,lastContent,lastTime,classGroup}>>}
 */
export function fetchMyGroups() {
  return request({ url: '/student/group/list', silent: true })
}

/** 单个群的信息：群名、人数 */
export function fetchGroupInfo(id) {
  return request({ url: `/student/group/${id}`, silent: true })
}

/**
 * 拉消息
 * @param {number} id        群 id（t_subject_container.id）
 * @param {number} [afterId] 只拿比它新的（轮询用）。不传 = 最近 size 条
 * @param {number} [size]    默认 30，上限 50
 */
export function fetchGroupMessages(id, afterId, size) {
  const data = {}
  if (afterId) data.afterId = afterId
  if (size) data.size = size
  return request({ url: `/student/group/${id}/messages`, data, silent: true })
}

/**
 * 发一条群消息
 *
 * 两种用法：
 *   sendGroupMessage(id, '明天带练习册')                       // 文本
 *   sendGroupMessage(id, { type: 2, mediaUrl: '/files/...' })  // 图片/视频/文件
 *
 * 媒体消息的正文是空串（和老师端一致）：气泡靠 type 渲染，
 * 存一段"[图片]"进 content 只会在搜索和预览里多出一堆假文字。
 */
export function sendGroupMessage(id, payload) {
  const data = typeof payload === 'string' ? { content: payload } : (payload || {})
  return request({ url: `/student/group/${id}/messages`, method: 'POST', data })
}

/**
 * 传附件（图片 / 视频 / 文件）
 *
 * 和老师端 uploadToContainer 同一个落盘逻辑，只是路径在 /student 下 ——
 * 学生不在成员表里，老师那条接口的 requireMember 会把他挡在外面。
 *
 * @param {number} id       群 id
 * @param {string} filePath 临时文件路径（chooseMedia / chooseMessageFile 给的）
 * @param {string} kind     image / video / file
 */
export function uploadToGroup(id, filePath, kind = 'image') {
  return new Promise((resolve, reject) => {
    // 不在这里弹 toast：调用方正在 showLoading，先弹会被随后的 hideLoading 一起关掉
    uni.uploadFile({
      url: `${getBaseUrl()}/student/group/${id}/upload?kind=${kind}`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      success: (res) => {
        // uploadFile 返回字符串，要自己 parse；失败时后端可能是 500 页面而不是 JSON
        let body = null
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          body = null
        }
        if (!body || typeof body.code === 'undefined') {
          reject(new Error(`上传失败（HTTP ${res.statusCode}）`))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        const err = new Error(body.message || '上传失败')
        err.code = body.code
        reject(err)
      },
      fail: () => reject(new Error('上传失败，请检查网络'))
    })
  })
}

/** 退出用邀请码加入的群（本班的群退不了，后端会返回 1001） */
export function leaveGroup(id) {
  return request({ url: `/student/group/${id}/leave`, method: 'POST' })
}

/**
 * 用邀请码加入别的群（跨班旁听、高数群这种）
 * @param {string} inviteCode 老师端「群 → 邀请码」里那一串
 * @returns {Promise<{group:Object, alreadyIn:boolean}>}
 */
export function joinGroupByCode(inviteCode) {
  return request({
    url: '/student/group/join',
    method: 'POST',
    data: { inviteCode: String(inviteCode || '').trim() }
  })
}
