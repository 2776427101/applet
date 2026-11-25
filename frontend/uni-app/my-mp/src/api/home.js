/* 教师首页接口 */
import { request } from './request'
/**
 * 首页聚合数据
 * 一次拿全部，不要拆成 4 个请求：小程序并发有限，拆了首屏会一块块闪
 *
 * @param {Object} [opts]
 * @param {boolean} [opts.silent] 失败时不弹 toast。
 *   首页自己会画「加载失败 + 重试」，两个提示同时出现是重复的
 * @returns {Promise<Object>} { classInfo, stats, term }
 */
export function fetchOverview(opts = {}) {
  /* classId 决定看哪个班。多班级下必须传 —— 不传后端只会给第一个班的数据，
     老师在界面上切到第二个班，首页数字却不变 */
  const data = opts.classId ? { classId: opts.classId } : {}
  return request({ url: '/teacher/home/overview', data, silent: opts.silent })
}

/**
 * 首页通知：待审批请假、今天到期的待办、今天生日
 *
 * 没有"已读/未读"—— 通知是后端按业务状态现算的。假批完、待办做完，
 * 那条通知自己就消失了，不需要前端标记也不需要后端存表。
 *
 * **不传 classId = 我的所有班**（v0.44 改了语义）。
 * 原来不传等于"第一个班"，老师带两个班时得切到二班才看得见二班的请假，
 * 切回来一班那条又没了 —— 提醒变成了"当前标签页的通知"。
 * 现在每条通知带 `classId` / `className`，跨班聚合之后才知道是哪个班的。
 *
 * @param {Object} [opts]
 * @param {number} [opts.classId] 只想要某一个班时传，一般不用传
 * @param {number} [opts.limit]   最多几条，默认 20
 * @param {boolean} [opts.silent] 失败不弹 toast（首页/通知页自己会画错误态）
 * @returns {Promise<{total:number, list:Array}>}
 */
export function fetchNotices(opts = {}) {
  const data = {}
  if (opts.classId) data.classId = opts.classId
  if (opts.limit) data.limit = opts.limit
  return request({ url: '/teacher/home/notices', data, silent: opts.silent })
}
