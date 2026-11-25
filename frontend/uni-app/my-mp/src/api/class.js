/* 班级接口（v0.5：一个老师可以带多个班） */
import { request } from './request'

/**
 * 我的班级列表（首页选择器 + 通讯录共用）
 *
 * 每个班带：id / name / grade / termStartDate / inviteCode /
 *          studentCount（在读人数）/ containerId（班级群）/ containerName / groupMemberCount
 *
 * 按创建顺序返回，第一个就是"默认班级"。
 *
 * @param {Object} [options] 透传给 request，目前只用到 { silent: true }
 */
export function listClasses(options = {}) {
  return request({ url: '/teacher/class/list', ...options })
}

/**
 * 读某个班的设置信息
 *
 * @param {number} [classId] 不传 = 我的第一个班（后端兜底，前端应尽量传）
 */
export function getClassInfo(classId, options = {}) {
  return request({ url: '/teacher/class', data: classId ? { classId } : {}, ...options })
}

/**
 * 保存班级设置
 *
 * 只提交三个字段。邀请码和归档状态故意不在这个接口里 ——
 * 改邀请码会让已经发出去的码失效，那是另一个需要二次确认的操作。
 *
 * **传什么改什么**：没传的字段后端不动。
 *
 * @param {Object} data
 * @param {string} data.name           班级名称，必填，最多 32 字
 * @param {string} [data.grade]        年级，最多 16 字，可以留空；不传=不改
 * @param {string} [data.termStartDate] 开学日期 'yyyy-MM-dd'；不传=不改
 * @param {number} [classId]           改哪个班；不传 = 第一个班
 */
export function updateClassInfo(data, classId) {
  // classId 走 query 而不是 body：request.js 对 GET/PUT 的 data 处理不同，
  // PUT 的 data 是请求体，拼进 URL 最省事，也不用改公共封装
  const q = classId ? `?classId=${classId}` : ''
  return request({ url: '/teacher/class' + q, method: 'PUT', data })
}

/**
 * 新建班级
 *
 * 建班时后端会自动建好这个班的班级群（班主任 = 群主），
 * 所以不需要再单独调一次建群接口。
 *
 * @param {{name:string, grade?:string, termStartDate?:string}} data
 * @returns {Promise<{id:number}>}
 */
export function createClass(data) {
  return request({ url: '/teacher/class', method: 'POST', data })
}

/**
 * 给某个班补建班级群（幂等：已经有群就返回原来那个）
 *
 * 正常流程用不到 —— 建班时后端已经建好了。
 * 这个接口是给"历史班级"和"建群时失败"这两种情况兜底的，
 * 通讯录里看到「班级群还没创建」时点一下就会调它。
 *
 * @param {number} classId
 * @returns {Promise<{containerId:number}>}
 */
export function ensureClassGroup(classId) {
  return request({ url: `/teacher/class/${classId}/group`, method: 'POST' })
}

/**
 * 删除班级
 *
 * 后端是归档（status=0），不是物理删除：这个班的学生、成绩、座位、
 * 请假记录都还在库里，只是不再出现在任何列表里。所以删除后
 * 再建一个同名班级是允许的，不会撞上旧数据。
 *
 * @param {number} classId
 */
export function deleteClass(classId) {
  return request({ url: `/teacher/class/${classId}`, method: 'DELETE' })
}
