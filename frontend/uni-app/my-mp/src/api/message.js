/* 师生私信（老师侧）
 *
 * 学生 id 是路径参数，不是请求体里的字段：路径一眼能看出"发给谁"，
 * 排查日志时也不用去翻 body。
 */
import { request } from './request'

/**
 * 我的私信会话列表（通讯录顶部那一区）
 * @returns {Promise<Array>} [{ studentId, name, studentNo, lastContent, lastTime, lastSenderRole, unread }]
 */
export function fetchThreads() {
  return request({ url: '/teacher/message' })
}

/**
 * 拉和某个学生的对话
 * @param {number} studentId
 * @returns {Promise<Object>} { title, subtitle, messages, unread }
 */
export function fetchThread(studentId) {
  return request({ url: `/teacher/message/${studentId}` })
}

/**
 * 发一条
 * @param {number} studentId
 * @param {string} content 最多 500 字
 * @returns {Promise<Object>} 刚发出去的那条
 */
export function sendToStudent(studentId, content) {
  return request({
    url: `/teacher/message/${studentId}`,
    method: 'POST',
    data: { content }
  })
}

/** 标为已读（打开对话时调，失败也无所谓，不阻塞界面） */
export function markThreadRead(studentId) {
  return request({
    url: `/teacher/message/${studentId}/read`,
    method: 'POST',
    silent: true
  })
}
