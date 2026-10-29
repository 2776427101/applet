/* 成绩模块接口层
 *
 * 页面只认这里导出的函数，不关心数据是假的还是真的。
 * 后端通了之后把 USE_MOCK 改成 false，页面代码一行都不用动。
 */

import { request } from './request'
import {
  mockExamList,
  mockAddExam,
  mockScoreList,
  mockSaveScores,
  mockScoreStats
} from '@/mock/scores'

/**
 * 后端 5 个接口已实测通过（29 条冒烟测试 0 失败），所以直接走真接口。
 * 想退回假数据看样式，把这里改回 true 就行，页面代码一行都不用动。
 */
const USE_MOCK = false

/* ---------------- 考试 ---------------- */

/** 考试列表（按考试日期倒序）
 * @param {Object} [params] { page, size }
 * @param {Object} [opts] { silent } 失败时不弹 toast（页面自己会画错误块）
 */
export function getExamList(params = {}, opts = {}) {
  if (USE_MOCK) return mockExamList()
  return request({ url: '/teacher/exam/list', data: params, silent: opts.silent })
}

/** 新建考试 */
export function addExam(data) {
  if (USE_MOCK) return mockAddExam(data)
  return request({ url: '/teacher/exam', method: 'POST', data })
}

/* ---------------- 成绩 ---------------- */

/** 某次考试的成绩表（一个学生一行，含总分和班内排名）
 * @param {number} examId
 * @param {Object} [opts] { silent }
 */
export function getScoreList(examId, opts = {}) {
  if (USE_MOCK) return mockScoreList(examId)
  return request({ url: '/teacher/score/list', data: { examId }, silent: opts.silent })
}

/** 批量录入成绩 */
export function saveScores(data) {
  if (USE_MOCK) return mockSaveScores(data)
  return request({ url: '/teacher/score/batch', method: 'POST', data })
}

/** 成绩统计（单科 + 总分 + 年级排名）
 * @param {number} examId
 * @param {Object} [opts] { silent }
 */
export function getScoreStats(examId, opts = {}) {
  if (USE_MOCK) return mockScoreStats(examId)
  return request({ url: '/teacher/score/stats', data: { examId }, silent: opts.silent })
}
