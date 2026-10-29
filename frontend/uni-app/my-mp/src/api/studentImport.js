/* 学生名单导入接口 */

import { request } from './request'
import { upload, chooseExcel } from './upload'

/**
 * 上传名单并预览，不写库。
 *
 * @param {Object} pick { filePath } 小程序选的文件临时路径
 *                      { file, name } H5 选到的 File 对象
 * @returns {Promise<Object>} 见 api-contract.md §6.6
 */
export function previewImport(pick) {
  return upload('/teacher/student/import/preview', pick)
}

/**
 * 确认导入。
 *
 * @param {Object} data
 * @param {'skip'|'update'} data.mode 学号已存在时：跳过还是更新
 * @param {Array} data.rows preview 返回的 rows，原样回传
 * @returns {Promise<{created,updated,skipped,failed,failures}>}
 */
export function commitImport(data) {
  return request({ url: '/teacher/student/import/commit', method: 'POST', data })
}

/**
 * 选文件。
 *
 * 实现已经挪到 api/upload.js —— 成绩导入也要选文件，
 * 两端（H5 用 fetch、小程序用 uploadFile）的差异没必要维护两份。
 *
 * @returns {Promise<{filePath?:string, file?:File, name:string}|null>} 用户取消返回 null
 */
export function chooseFile() {
  return chooseExcel()
}
