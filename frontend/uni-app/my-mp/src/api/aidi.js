/* 乐迪（AI 小助手）接口 */
import { request } from './request'

/**
 * 问乐迪一句话
 *
 * 后端不存对话，历史由前端带着走 —— 页面关掉就结束，
 * 所以这里必须把整段历史传上去，模型才知道「他」指的是谁。
 *
 * @param {Object}   params
 * @param {number}   [params.classId] 问哪个班，0 / 不传 = 后端用第一个班
 * @param {Array}    params.messages  [{ role: 'user' | 'assistant', content }]
 * @returns {Promise<Object>} { reply, toolCalls }
 */
export function askAidi({ classId, messages, silent = true }) {
  const data = { messages }
  /* classId 为 0 时不要传：后端 resolveClass 里只有 null 才是"用第一个班"，
     传 0 会去查 id=0 的班，直接 1004。这个坑学生页踩过一次了 */
  if (classId) {
    data.classId = classId
  }
  return request({
    url: '/teacher/aidi/chat',
    method: 'POST',
    data,
    silent,
    /* 后端要串 1~3 次 DeepSeek 请求，每轮都可能有几秒。
       默认 10 秒不够用，会出现"其实答出来了但前端已经放弃" */
    timeout: 60000
  })
}

/**
 * 执行老师确认过的动作
 *
 * 参数原样回传后端，后端会重新校验归属再落库 —— 前端不判断能不能做，
 * 也不改参数。这里唯一的职责是"老师说点了"。
 *
 * @param {Object} params
 * @param {number} [params.classId]
 * @param {Object} params.action  { type, params }，来自上一轮 reply.action
 * @returns {Promise<Object>} { reply } 一句执行结果
 */
export function confirmAidiAction({ classId, action }) {
  const data = { action }
  if (classId) {
    data.classId = classId
  }
  return request({
    url: '/teacher/aidi/act',
    method: 'POST',
    data,
    /* 写操作不经过模型，正常几百毫秒；给 20 秒是防数据库慢查询 */
    timeout: 20000
  })
}
