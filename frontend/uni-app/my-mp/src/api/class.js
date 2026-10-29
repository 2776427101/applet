/* 班级设置接口 */
import { request } from './request'

/**
 * 读班级信息
 *
 * 不带 id：老师带哪个班由后端从 token 里的 userId 反查。
 * 前端能传 id 就能看/改别的班，那是水平越权。
 *
 * @returns {Promise<{id:number, name:string, grade:string,
 *   termStartDate:string, inviteCode:string, studentCount:number}>}
 */
export function getClassInfo() {
  return request({ url: '/teacher/class' })
}

/**
 * 保存班级设置
 *
 * 只提交三个字段。邀请码和归档状态故意不在这个接口里 ——
 * 改邀请码会让已经发出去的码失效，那是另一个需要二次确认的操作。
 *
 * **传什么改什么**：没传的字段后端不动。所以名单导入页可以只传
 * `{ name }` 来一键改名，不用先把年级和开学日期读出来再写回去。
 *
 * @param {Object} data
 * @param {string} data.name           班级名称，必填，最多 32 字
 * @param {string} [data.grade]        年级，最多 16 字，可以留空；不传=不改
 * @param {string} [data.termStartDate] 开学日期 'yyyy-MM-dd'；不传=不改
 */
export function updateClassInfo(data) {
  return request({ url: '/teacher/class', method: 'PUT', data })
}
