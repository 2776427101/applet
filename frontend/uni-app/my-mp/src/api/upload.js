/*
 * 文件上传（multipart/form-data）。
 *
 * 为什么不能复用 request()：request 发的是 application/json，
 * 而文件必须走 multipart。两者在两端（H5 / 小程序）的实现又完全不同：
 *
 *   - H5：uni.uploadFile 传 File 对象时请求能发出去、后端也返回 200，
 *         但 success 回调不触发，页面永远停在「正在解析」。所以改用 fetch。
 *   - 小程序：拿不到 File 对象，只有临时路径，只能用 uni.uploadFile，
 *         而且它的 res.data 是字符串，必须自己 JSON.parse。
 *
 * 这段代码原来写在 studentImport.js 里。成绩导入也要传文件，
 * 复制一份的代价是以后修 H5 的坑要记得改两个地方，所以抽出来。
 */

import { BASE_URL, getToken, clearToken } from './request'

/**
 * 上传一个文件。
 *
 * @param {string} url 相对路径，如 '/teacher/student/import/preview'
 * @param {Object} pick { filePath } 小程序选的文件临时路径
 *                     { file, name } H5 选到的 File 对象
 * @param {Object} [fields] 额外的普通表单字段，如 { examId: 2 }
 * @param {Object} [opts]
 * @param {boolean} [opts.silent] 失败时不弹 toast，由页面自己画错误块。
 *        解析类接口的报错经常是一整段话（「没找到姓名列…请确认表格里有一行表头」），
 *        toast 只显示两行，后半截会被吃掉，而那里恰恰是「怎么办」
 * @returns {Promise<any>} 后端 data 字段
 */
export function upload(url, pick, fields, opts) {
  const silent = !!(opts && opts.silent)
  return new Promise((resolve, reject) => {
    const token = getToken()
    const header = {}
    if (token) {
      header.Authorization = `Bearer ${token}`
    }

    /* 响应处理两端共用：业务码 0 才算成功、1002 跳登录、其他 toast 一次 */
    const handle = (body, statusCode) => {
      if (!body || typeof body.code === 'undefined') {
        reject(new Error(`响应格式异常（HTTP ${statusCode}）`))
        return
      }
      if (body.code === 0) {
        resolve(body.data)
        return
      }
      if (body.code === 1002) {
        clearToken()
        uni.removeStorageSync('userInfo')
        uni.reLaunch({ url: '/pages/index/index' })
        reject(new Error(body.message || '登录已失效'))
        return
      }
      if (!silent) {
        uni.showToast({ title: body.message || '上传失败', icon: 'none' })
      }
      reject(new Error(body.message || '上传失败'))
    }

    const onFail = (err) => {
      const msg = '网络异常，请检查后端是否启动'
      if (!silent) {
        uni.showToast({ title: msg, icon: 'none' })
      }
      /* 必须包成 Error：直接 reject(err) 的话页面里 e.message 是 undefined，
         「加载失败」会变成一句没有原因的提示 */
      reject(err instanceof Error ? err : new Error(msg))
    }

    /* 额外字段要转成字符串：FormData 和 uploadFile 的 formData 都只接受字符串，
       传数字在某些端会被静默丢掉 */
    const extra = {}
    if (fields) {
      for (const k in fields) {
        if (fields[k] !== undefined && fields[k] !== null) {
          extra[k] = String(fields[k])
        }
      }
    }

    // #ifdef H5
    /* H5：直接 fetch + FormData。pick.file 就是 chooseFile 拿到的 File 对象 */
    const fd = new FormData()
    fd.append('file', pick.file, pick.name)
    for (const k in extra) {
      fd.append(k, extra[k])
    }
    fetch(BASE_URL + url, { method: 'POST', headers: header, body: fd })
      .then((r) => r.text().then((t) => ({ status: r.status, text: t })))
      .then(({ status, text }) => {
        let body
        try {
          body = JSON.parse(text)
        } catch (e) {
          reject(new Error(`响应格式异常（HTTP ${status}）`))
          return
        }
        handle(body, status)
      })
      .catch(onFail)
    // #endif

    // #ifndef H5
    /* 小程序：只有临时路径，必须用 uni.uploadFile */
    uni.uploadFile({
      url: BASE_URL + url,
      /* 后端 @RequestParam("file") 认的就是这个名字，
         写错会报 400「Required part 'file' is not present」 */
      name: 'file',
      filePath: pick.filePath,
      formData: extra,
      header,
      timeout: 30000,
      success: (res) => {
        /* uploadFile 的 res.data 是字符串，不是对象 —— 必须自己 parse。
           这一点和 uni.request 不一样，很容易踩 */
        let body
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          reject(new Error(`响应格式异常（HTTP ${res.statusCode}）`))
          return
        }
        handle(body, res.statusCode)
      },
      fail: onFail
    })
    // #endif
  })
}

/**
 * 选文件。两端 API 不一样，所以要用条件编译：
 *   - 小程序：wx.chooseMessageFile 只能从聊天记录里选（微信的限制，没有别的入口）
 *   - H5：uni.chooseFile 走浏览器文件选择框
 *
 * @returns {Promise<{filePath?:string, file?:File, name:string}|null>} 用户取消返回 null
 */
export function chooseExcel() {
  return new Promise((resolve) => {
    // #ifdef H5
    uni.chooseFile({
      count: 1,
      /* 只给 .xlsx 会让某些系统的文件框把文件全灰掉，所以放宽到 Excel 全类，
         真正的格式判断在后端 —— 用户把 .xls 改名成 .xlsx 也要能给出准确提示 */
      extension: ['.xlsx', '.xls'],
      success: (res) => {
        const f = res.tempFiles && res.tempFiles[0]
        if (!f) {
          resolve(null)
          return
        }
        resolve({ file: f, name: f.name || '表格.xlsx' })
      },
      fail: () => resolve(null)
    })
    // #endif

    // #ifndef H5
    uni.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['xlsx', 'xls'],
      success: (res) => {
        const f = res.tempFiles && res.tempFiles[0]
        if (!f) {
          resolve(null)
          return
        }
        resolve({ filePath: f.path, name: f.name || '表格.xlsx' })
      },
      fail: () => resolve(null)
    })
    // #endif
  })
}
