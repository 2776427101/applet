/* 座位模块接口层
 *
 * 只有两个接口：读整图、写整图。
 * 没有"移动一个座位"的接口 —— 前端手里本来就有整张图，
 * 点一下换完位置，保存时把整张图发回去就行。
 */

import { request } from './request'

/** 读座位图：{ rows, cols, seats[], unseated[] }
 * @param {Object} [opts]
 * @param {boolean} [opts.silent] 失败时不弹 toast（页面自己会画错误块）
 */
export function getSeatChart(opts = {}) {
  return request({ url: '/teacher/seat/chart', silent: opts.silent })
}

/**
 * 保存座位图（整图覆盖）
 * @param {Object} data { rows, cols, seats: [{ row, col, studentId }] }
 * @returns {Promise<{saved:number}>} saved = 已排座人数
 */
export function saveSeatChart(data) {
  return request({ url: '/teacher/seat/chart', method: 'PUT', data })
}
