/* 接龙接口。
 *
 * 全部挂在 /container/{id}/relay 下，和群聊消息同一套命名空间 ——
 * 学生端上线后要复用，放进 /teacher 会让「学生能不能调」变成要解释的问题。
 *
 * 「我是谁」永远从 token 取，这里没有 userId 参数，也不该加。
 */
import { request } from './request'

/**
 * 发起接龙
 * @param {number} containerId 群 id
 * @param {Object} data { title, description, entryFormat, formatMin, formatMax,
 *                        options, maxCount, deadline, allowSelfEdit }
 *   后端会同时往群里发一条 type=5 的卡片消息，前端不用再调发消息接口
 * @returns {Promise<Object>} 接龙详情
 */
export function createRelay(containerId, data) {
  return request({ url: `/container/${containerId}/relay`, method: 'POST', data })
}

/**
 * 接龙详情：状态 + 名单 + 代填候选 + 我的填写，一次拿全
 */
export function getRelay(containerId, relayId) {
  return request({ url: `/container/${containerId}/relay/${relayId}` })
}

/**
 * 自助接龙
 *
 * 重复提交不报错，返回已有记录 —— 报错会让用户以为没成功，继续点。
 * 满员 4101 / 已截止 4102 / 已结束 4103。
 *
 * @param {string} value 填写内容；格式=序号 时传空串
 */
export function joinRelay(containerId, relayId, value) {
  return request({
    url: `/container/${containerId}/relay/${relayId}/entry`,
    method: 'POST',
    data: { value: value || '' }
  })
}

/**
 * 代填补录（发起人 / 群主 / 班委）
 *
 * 整批一个事务：中途有一条重复或超员，全部回滚。
 * 部分成功比全失败更难解释 —— 老师没法知道到底哪几条进去了。
 *
 * @param {Array<{studentId:number, value?:string}>} entries
 */
export function batchRelay(containerId, relayId, entries) {
  return request({
    url: `/container/${containerId}/relay/${relayId}/entry/batch`,
    method: 'POST',
    data: { entries }
  })
}

/**
 * 修改一条记录。只能改自己填的；代填行可以由发起人改。
 */
export function updateRelayEntry(containerId, relayId, entryId, value) {
  return request({
    url: `/container/${containerId}/relay/${relayId}/entry/${entryId}`,
    method: 'PUT',
    data: { value: value || '' }
  })
}

/** 结束接龙。只有发起人（或群主/班委）能结束 */
export function closeRelay(containerId, relayId) {
  return request({
    url: `/container/${containerId}/relay/${relayId}/close`,
    method: 'POST'
  })
}
