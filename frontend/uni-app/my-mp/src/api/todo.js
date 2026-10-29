/* 待办模块接口：全部走后端真接口 */

import { request } from './request'

/**
 * 待办列表
 * @param {Object} [params]
 * @param {number} [params.done] 0=未完成 1=已完成，不传=全部
 * @param {Object} [opts]
 * @param {boolean} [opts.silent] 失败时不弹 toast（页面自己会画错误块）
 * @returns {Promise<{total:number, list:Array}>}
 */
export function getTodoList(params = {}, opts = {}) {
  return request({ url: '/teacher/todo/list', data: params, silent: opts.silent })
}

/**
 * 新增待办
 * @param {{title:string, dueAt?:string}} data
 * @returns {Promise<{id:number}>}
 */
export function addTodo(data) {
  return request({ url: '/teacher/todo', method: 'POST', data })
}

/**
 * 完成 / 取消完成
 * 注意是 PUT 不是 PATCH：小程序不支持 PATCH（契约 §0 方法白名单）
 *
 * @param {number} id
 * @param {number} done 1=完成 0=取消
 */
export function setTodoDone(id, done) {
  return request({ url: `/teacher/todo/${id}/done`, method: 'PUT', data: { done } })
}

/**
 * 删除待办（物理删除，删了找不回来）
 * @param {number} id
 */
export function delTodo(id) {
  return request({ url: `/teacher/todo/${id}`, method: 'DELETE' })
}
