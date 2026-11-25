/* 抽签接口。
 *
 * 只有三个动作：发起、开奖、看结果。
 * 没有「参与」接口 —— 抽签抽的是名单，不需要群成员点任何东西，
 * 这也是它在「学生还没有账号」的现状下就能用的原因。
 */
import { request } from './request'

/**
 * 发起抽签
 * @param {number} containerId
 * @param {Object} data { kind, source, studentIds, drawCount, groupCount, dedupe, publicResult }
 *   kind 1=随机点名 2=随机分组；source 1=当前班学生 2=手动勾选
 *   后端会同时发一条 type=6 的卡片消息
 */
export function createLottery(containerId, data) {
  return request({ url: `/container/${containerId}/lottery`, method: 'POST', data })
}

/**
 * 开奖
 *
 * 只有发起人（或群主/班委）能点。两人同时点也不会各抽一批：
 * 后端行锁 + 版本号，后到的那次直接拿到同一个结果。
 */
export function drawLottery(containerId, lotteryId) {
  return request({
    url: `/container/${containerId}/lottery/${lotteryId}/draw`,
    method: 'POST'
  })
}

/**
 * 结果详情
 *
 * 结果设为「仅发起人可见」时，非发起人拿到的是空列表 ——
 * 判断在服务端，不是前端「不渲染」。
 */
export function getLottery(containerId, lotteryId) {
  return request({ url: `/container/${containerId}/lottery/${lotteryId}` })
}
