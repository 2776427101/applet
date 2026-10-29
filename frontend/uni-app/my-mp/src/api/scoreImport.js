/* 成绩表导入接口 */

import { request } from './request'
import { upload, chooseExcel } from './upload'

/**
 * 上传成绩表并预览，不写库。
 *
 * examId 必须和文件一起作为表单字段传 —— 成绩要落到哪场考试
 * 是这次导入的核心参数，不能省。
 *
 * silent：解析失败的提示经常是一整段话（「表里没找到能对上科目的列，
 * 本次考试的科目是…」），toast 只显示两行，后半截「怎么办」会被吃掉。
 * 所以让页面自己画错误块。
 *
 * @param {number} examId 目标考试
 * @param {Object} pick { filePath } 或 { file, name }
 * @returns {Promise<Object>} 见 api-contract.md §7.5
 */
export function previewScoreImport(examId, pick) {
  return upload('/teacher/score/import/preview', pick, { examId }, { silent: true })
}

/**
 * 确认导入。
 *
 * @param {Object} data
 * @param {number} data.examId
 * @param {Array} data.rows [{ excelRowNo, studentId, scores }]
 * @returns {Promise<{saved,students,create,overwrite,clear}>}
 */
export function commitScoreImport(data) {
  return request({ url: '/teacher/score/import/commit', method: 'POST', data })
}

/** 选 Excel 文件，用户取消返回 null */
export function chooseFile() {
  return chooseExcel()
}
