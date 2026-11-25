/* 学科班级容器接口
 *
 * 路径分两组：
 *   /teacher/container/**  教师端管理（列表/成员/邀请码/黑名单/精华）
 *   /container/**          加入链路（任何登录用户都能用，本期只有老师会用）
 *
 * 为什么加入链路不放在 /teacher 下：学生端上线后要复用同一批接口，
 * 放进 teacher 命名空间会让"学生能不能调"变成一个需要解释的问题。
 */
import { request, getToken } from './request'
import { getBaseUrl } from '@/utils/api-host'

// ---------------------------------------------------------------
// 容器
// ---------------------------------------------------------------

/**
 * 我的学科容器
 * @param {Object} params
 * @param {string} [params.tab]          created=我创建的 / managed=我管理的 / joined=我加入的
 * @param {string} [params.keyword]      匹配学科名或容器名
 * @param {number} [params.classId]      只看关联某个行政班的
 * @param {number} [params.inviteStatus] 1=有效 2=已过期 3=永久
 */
export function listContainers(params) {
  return request({ url: '/teacher/container/list', data: params })
}

/**
 * 创建群组。成功后返回 { id, inviteCode }
 * @param {Object} data
 * @param {string} data.subjectName        群组名称，必填（与 name 同值，字段名沿用旧契约）
 * @param {string} [data.name]             不传则用 subjectName；两者是同一个名字
 * @param {number} [data.classId]          关联行政班，可空
 * @param {number} [data.inviteExpireDays] 有效天数，不传=永久
 * @param {string} [data.inviteExpireAt]   指定截止时间 "yyyy-MM-dd HH:mm:ss"，优先级高于天数
 * @param {string} [data.joinPassword]     6-20 位，不传=无密码
 */
export function createContainer(data) {
  return request({ url: '/teacher/container', method: 'POST', data })
}

/** 容器详情（含 permissions，前端据此决定按钮显隐） */
export function getContainer(id) {
  return request({ url: `/teacher/container/${id}` })
}

/**
 * 修改容器信息。**全量提交**：classId 传 null 表示解除关联
 */
export function updateContainer(id, data) {
  return request({ url: `/teacher/container/${id}`, method: 'PUT', data })
}

/** 解散容器（软删）。confirmName 必须与容器名完全一致 */
export function dissolveContainer(id, confirmName) {
  return request({ url: `/teacher/container/${id}/dissolve`, method: 'POST', data: { confirmName } })
}

// ---------------------------------------------------------------
// 成员
// ---------------------------------------------------------------

/**
 * 成员列表
 * @param {number} [params.role]   1=群主 2=班委 3=成员
 * @param {string} [params.keyword]
 * @param {number} [params.status] 不传=只看在群；-1=全部
 */
export function listMembers(id, params) {
  return request({ url: `/teacher/container/${id}/members`, data: params })
}

/** 设为/取消班委。role: 2=班委 3=普通成员（不能传 1） */
export function setMemberRole(id, userId, role) {
  return request({ url: `/teacher/container/${id}/members/${userId}/role`, method: 'PUT', data: { role } })
}

/**
 * 禁言 / 解除禁言
 * @param {Object} data
 * @param {number} data.muteMinutes >0 禁言分钟数；-1 永久；0 解除
 * @param {string} [data.reason]    禁言时必填
 */
export function muteMember(id, userId, data) {
  return request({ url: `/teacher/container/${id}/members/${userId}/mute`, method: 'PUT', data })
}

/**
 * 移除成员
 *
 * **参数拼在 URL 上而不是 body 里**：DELETE 带 body 在各端行为不一致，
 * 一旦被吞掉 blacklist 会静默变成 false —— 老师以为拉黑了，人还能进来。
 *
 * @param {boolean} blacklist 是否同时拉黑（拉黑后不能再凭码加入）
 * @param {string}  [reason]
 */
export function removeMember(id, userId, blacklist, reason) {
  const qs = [`blacklist=${blacklist ? 'true' : 'false'}`]
  if (reason) qs.push(`reason=${encodeURIComponent(reason)}`)
  return request({
    url: `/teacher/container/${id}/members/${userId}?${qs.join('&')}`,
    method: 'DELETE'
  })
}

// ---------------------------------------------------------------
// 黑名单（只有群主）
// ---------------------------------------------------------------

export function listBlacklist(id) {
  return request({ url: `/teacher/container/${id}/blacklist` })
}

/** 解除拉黑。解除后对方需要自己重新扫码加入，不会自动回群 */
export function releaseBlacklist(id, userId) {
  return request({ url: `/teacher/container/${id}/blacklist/${userId}`, method: 'DELETE' })
}

// ---------------------------------------------------------------
// 精华消息
// ---------------------------------------------------------------

export function listHighlights(id) {
  return request({ url: `/teacher/container/${id}/highlights` })
}

/** 收录精华（群主 + 班委） */
export function addHighlight(id, content) {
  return request({ url: `/teacher/container/${id}/highlights`, method: 'POST', data: { content } })
}

/** 取消精华。群主可取消任意一条，班委只能取消自己收录的 */
export function cancelHighlight(id, highlightId) {
  return request({ url: `/teacher/container/${id}/highlights/${highlightId}`, method: 'DELETE' })
}

// ---------------------------------------------------------------
// 邀请码 / 加入密码
// ---------------------------------------------------------------

/** 邀请码信息（群主 + 班委） */
export function getInvite(id) {
  return request({ url: `/teacher/container/${id}/invite` })
}

/**
 * 改有效期。**不换码**，所以过期后改到未来，同一个码就能继续用
 * @param {Object} data { days } 或 { expireAt } 或 {}（永久）
 */
export function updateInviteExpire(id, data) {
  return request({ url: `/teacher/container/${id}/invite/expire`, method: 'PUT', data })
}

/** 重新生成邀请码。旧码立即失效，已加入的成员不受影响 */
export function regenerateInvite(id) {
  return request({ url: `/teacher/container/${id}/invite/regenerate`, method: 'POST' })
}

/** 设置/修改/关闭加入密码。password 传 null = 关闭 */
export function setJoinPassword(id, password) {
  return request({ url: `/teacher/container/${id}/join-password`, method: 'PUT', data: { password } })
}

/** 操作日志（只有群主） */
export function listLogs(id, params) {
  return request({ url: `/teacher/container/${id}/logs`, data: params })
}

// ---------------------------------------------------------------
// 加入链路
// ---------------------------------------------------------------

/** 加入前预览。过期/黑名单不抛错，返回字段里带 expired / blacklisted */
export function previewByCode(code) {
  return request({ url: '/container/preview', data: { code } })
}

/** 用邀请码加入 */
export function joinByCode(code, password) {
  return request({ url: '/container/join', method: 'POST', data: { code, password } })
}

/** 主动退出（群主不能退出） */
export function leaveContainer(id) {
  return request({ url: `/container/${id}/leave`, method: 'POST' })
}

// ---------------------------------------------------------------
// 聊天
// ---------------------------------------------------------------

/**
 * 拉消息。三种用法：
 *   不传参数      → 最近 size 条（首次进页面）
 *   afterId       → 比它新的（轮询增量）
 *   beforeId      → 比它旧的（上滑看历史）
 * 返回一律按 id 升序。
 * @param {Object} params { afterId, beforeId, size }
 */
export function listMessages(id, params) {
  return request({ url: `/container/${id}/messages`, data: params })
}

/**
 * 发消息。payload 传字符串等于发文本；传对象可以发图片/视频/文件
 *
 * @param {string|Object} payload
 *   文本：'明天带练习册'
 *   图片：{ type: 2, mediaUrl: '/files/...' }
 *   视频：{ type: 3, mediaUrl: '/files/...', duration: 12 }
 *   文件：{ type: 4, mediaUrl: '/files/...', fileName: '讲义.pdf', fileSize: 102400 }
 *
 * 被禁言返回 3005（request.js 会 toast，页面据此把输入框置灰）
 */
export function sendMessage(id, payload) {
  const body = typeof payload === 'string' ? { content: payload } : payload
  return request({ url: `/container/${id}/messages`, method: 'POST', data: body })
}

/**
 * 上传附件。**不走 request 封装**：uni.uploadFile 传的是文件流不是 JSON，
 * 但地址和 token 必须和 request 用同一份（BASE_URL / getToken），否则改端口时两边不同步。
 *
 * 上传和发消息是两步：先传拿到 url，再带着 url 发一条 type=2/3/4 的消息。
 *
 * @param {string} kind image / video / file，决定后端用哪套扩展名白名单和大小上限
 * @returns {Promise<{url:string, name:string, size:number}>}
 */
export function uploadToContainer(id, filePath, kind = 'image') {
  return new Promise((resolve, reject) => {
    // 这里不弹 toast：调用方正在 showLoading，先弹提示会被随后的 hideLoading 一起关掉。
    // 统一由调用方在 hideLoading 之后提示
    uni.uploadFile({
      url: `${getBaseUrl()}/container/${id}/upload?kind=${kind}`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      success: (res) => {
        // uploadFile 返回的是字符串，要自己 JSON.parse；解析失败说明后端抛了非 JSON（比如 500 页面）
        let body = null
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          body = null
        }
        if (!body || typeof body.code === 'undefined') {
          reject(new Error(`上传失败（HTTP ${res.statusCode}）`))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        const err = new Error(body.message || '上传失败')
        err.code = body.code
        reject(err)
      },
      fail: () => {
        reject(new Error('上传失败，请检查网络'))
      }
    })
  })
}

/**
 * 把 /files/xxx 拼成能直接给 image/video 用的完整地址。
 *
 * 后端只存相对路径（换域名不用改库），域名在这里补上。
 * 地址是 .../api，附件在根路径下，所以要把结尾的 /api 去掉。
 */
export function fileUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//.test(path)) return path
  return getBaseUrl().replace(/\/api\/?$/, '') + path
}
