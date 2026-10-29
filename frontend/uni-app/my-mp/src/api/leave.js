/* 请假模块接口 */
import { request } from './request'
import { mockLeaveList, mockAuditLeave, mockAddLeave } from '@/mock/leaves'

/**
 * 后端 3 个接口已经跑通（2026-10-26 实测 43 条 + 8 条越权全过），
 * 改成 false 走真接口。
 *
 * 如果页面弹「网络异常」，说明后端没启动 —— 改回 true 就能退回假数据。
 */
const USE_MOCK = false

/** 包一层，让假数据也有网络延迟和失败，页面里的 catch 分支才测得到 */
function mockCall(fn, ...args) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      try {
        resolve(fn(...args))
      } catch (e) {
        uni.showToast({ title: e.message || '请求失败', icon: 'none' })
        reject(e)
      }
    }, 250)
  })
}

/**
 * 请假列表
 * @param {Object} [params]
 * @param {number} [params.status] -1=全部 0=待审批 1=已通过 2=已驳回 3=已销假
 * @param {number} [params.page]   页码，默认 1
 * @param {number} [params.size]   每页条数，默认 20，最大 100
 * @param {Object} [opts]
 * @param {boolean} [opts.silent] 失败时不弹 toast（页面自己会画错误块）
 * @returns {Promise<{total:number, page:number, size:number, list:Array}>}
 */
export function getLeaveList(params = {}, opts = {}) {
  if (USE_MOCK) return mockCall(mockLeaveList, params)
  return request({ url: '/teacher/leave/list', data: params, silent: opts.silent })
}

/**
 * 审批请假
 * 注意是 PUT 不是 PATCH：小程序不支持 PATCH（契约 §0 方法白名单）
 *
 * @param {number} id
 * @param {{status:number, remark?:string}} data status 只能 1(通过) 或 2(驳回)
 */
export function auditLeave(id, data) {
  if (USE_MOCK) return mockCall(mockAuditLeave, id, data)
  return request({ url: `/teacher/leave/${id}/audit`, method: 'PUT', data })
}

/**
 * 代学生请假（老师代录的一律"已通过"）
 * @param {{studentId:number, type:number, startAt:string, endAt:string, reason?:string}} data
 * @returns {Promise<{id:number}>}
 */
export function addLeave(data) {
  if (USE_MOCK) return mockCall(mockAddLeave, data)
  return request({ url: '/teacher/leave', method: 'POST', data })
}
