/* 学生端共用的展示转换：请假类型 / 状态 / 时间 / 时长
 *
 * 老师端把这几段写在 pages/teacher/leave/index.vue 里。
 * 学生端有"列表 + 详情 + 通知"三个地方要用同一套文案，
 * 抄三份的话，改一次措辞要改三处，漏一处就会出现
 * 「列表写病假、详情写请假」这种自相矛盾。
 */

const PAD = (n) => String(n).padStart(2, '0')

/**
 * 后端给 'yyyy-MM-dd HH:mm:ss'。
 * ⚠️ iOS 不认带横杠的格式，new Date() 会得到 Invalid Date，页面上时间全变空。
 * 必须先换成斜杠再解析 —— 这个坑老师端踩过一次，这里直接按正确写法来。
 */
export function toDate(s) {
  if (!s) return null
  const d = new Date(String(s).replace(/-/g, '/'))
  return isNaN(d.getTime()) ? null : d
}

/** '11-18 08:00' */
export function fmtTime(s) {
  const d = toDate(s)
  if (!d) return '—'
  return `${PAD(d.getMonth() + 1)}-${PAD(d.getDate())} ${PAD(d.getHours())}:${PAD(d.getMinutes())}`
}

/** '11-18 周四' —— 详情页里显示提交时间用 */
export function fmtDay(s) {
  const d = toDate(s)
  if (!d) return '—'
  const w = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  return `${PAD(d.getMonth() + 1)}-${PAD(d.getDate())} ${w} ${PAD(d.getHours())}:${PAD(d.getMinutes())}`
}

/**
 * 请了多久：按"跨了几个日历日"算，不是按小时数除 24。
 * 11-18 08:00 → 11-20 18:00 是「3 天」，不是「2.4 天」。
 * 同一天就按小时说，比「1 天」准确。
 */
export function duration(startAt, endAt) {
  const a = toDate(startAt)
  const b = toDate(endAt)
  if (!a || !b) return ''
  const ms = b.getTime() - a.getTime()
  if (ms <= 0) return ''
  const d0 = new Date(a.getFullYear(), a.getMonth(), a.getDate()).getTime()
  const d1 = new Date(b.getFullYear(), b.getMonth(), b.getDate()).getTime()
  const days = Math.round((d1 - d0) / 86400000) + 1
  if (days === 1) return `${Math.round(ms / 3600000)} 小时`
  return `${days} 天`
}

const TYPE_LABEL = { 1: '病假', 2: '事假', 3: '其他' }

export function typeLabel(t) {
  return TYPE_LABEL[t] || '其他'
}

const STATUS_LABEL = { 0: '待审批', 1: '已通过', 2: '已驳回', 3: '已销假' }

export function statusLabel(s) {
  return STATUS_LABEL[s] || ''
}

/**
 * 状态语气：前端只负责选颜色，不负责判断"能不能操作"。
 * 能不能撤销由后端的 canCancel / canReport 说了算 —— 规则改了不用改前端。
 */
export function statusTone(s) {
  if (s === 0) return 'wait'
  if (s === 1) return 'pass'
  if (s === 2) return 'reject'
  return 'done'
}

/** 今天 'yyyy-MM-dd'，表单默认值用 */
export function today() {
  const d = new Date()
  return `${d.getFullYear()}-${PAD(d.getMonth() + 1)}-${PAD(d.getDate())}`
}
