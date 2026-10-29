/* 教师首页接口 */
import { request } from './request'
/**
 * 首页聚合数据
 * 一次拿全部，不要拆成 4 个请求：小程序并发有限，拆了首屏会一块块闪
 *
 * @param {Object} [opts]
 * @param {boolean} [opts.silent] 失败时不弹 toast。
 *   首页自己会画「加载失败 + 重试」，两个提示同时出现是重复的
 * @returns {Promise<Object>} { classInfo, stats, term, quote }
 */
export function fetchOverview(opts = {}) {
  return request({ url: '/teacher/home/overview', silent: opts.silent })
}
