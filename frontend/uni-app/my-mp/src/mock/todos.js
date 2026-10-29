/* 待办假数据：返回结构和接口契约 §5.2 完全一致，切真接口时页面一行都不用改 */

/**
 * 相对今天算一个时间字符串。
 * 不写死日期是有原因的：写死的话过几天全部变成「已逾期」，
 * 就看不出「今天截止 / 明天截止 / 还有 N 天」这几种状态长什么样了。
 *
 * @param {number} n  今天往后推几天，负数就是往前
 * @param {number} hh 几点，默认 18 点
 */
function dayOffset(n, hh = 18) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  const p = (x) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(hh)}:00:00`
}

/* 7 条：5 条未完成（覆盖逾期/今天/明天/还有N天/无截止），2 条已完成 */
const SEED = [
  { id: 1, title: '收齐本周家长回执',   done: 0, dueAt: dayOffset(0) },
  { id: 2, title: '整理月考成绩单',     done: 0, dueAt: dayOffset(-2) },
  { id: 3, title: '通知下周体检安排',   done: 0, dueAt: dayOffset(1) },
  { id: 4, title: '联系李思远家长',     done: 0, dueAt: dayOffset(5) },
  { id: 5, title: '统计冬季校服尺码',   done: 0, dueAt: null },
  { id: 6, title: '交本周班会记录',     done: 1, dueAt: dayOffset(-1) },
  { id: 7, title: '确认期中考试监考表', done: 1, dueAt: null }
]

/* 假后端也得有状态：增删改都落在这个数组上，页面才看得出效果。
   存内存不落 storage，重进小程序就恢复成初始 7 条 */
let rows = SEED.map((r) => ({ ...r }))
let seq = 100

/**
 * 排序规则照抄契约：done ASC, due_at IS NULL, due_at ASC, created_at DESC
 * 这里没有 created_at 字段，同一组里就按 id 倒序代替（新的在前）
 */
function sortRows(list) {
  return list.slice().sort((a, b) => {
    if (a.done !== b.done) return a.done - b.done
    if (!a.dueAt && !b.dueAt) return b.id - a.id
    if (!a.dueAt) return 1
    if (!b.dueAt) return -1
    if (a.dueAt !== b.dueAt) return a.dueAt < b.dueAt ? -1 : 1
    return b.id - a.id
  })
}

/**
 * 待办列表
 * @param {{done?: number}} [params] done 不传=全部，0=未完成，1=已完成
 */
export function mockTodoList(params = {}) {
  /* 坑：0 是有效值。如果写成 if (params.done)，done=0 会被当成「没传」，
     结果「只看未完成」变成「看全部」，页面上已完成的那几条就冒出来了 */
  const { done } = params
  const all = done === undefined || done === null || done === ''
    ? rows
    : rows.filter((r) => r.done === Number(done))

  const list = sortRows(all)
  return { total: list.length, list }
}

/**
 * 新增待办。抛异常 = 假后端报错，让页面里的 catch 分支也能被测到
 */
export function mockAddTodo(data = {}) {
  const title = String(data.title || '').trim()
  if (!title) throw new Error('待办内容不能为空')
  if (title.length > 128) throw new Error('待办内容不能超过 128 字')

  const row = { id: ++seq, title, done: 0, dueAt: data.dueAt || null }
  rows.unshift(row)
  return { id: row.id }
}

/** 完成 / 取消完成 */
export function mockSetDone(id, done) {
  const row = rows.find((r) => r.id === id)
  if (!row) throw new Error('待办不存在')
  row.done = done ? 1 : 0
  return null
}

/** 删除。待办是物理删除，删了就没了 */
export function mockDelTodo(id) {
  const before = rows.length
  rows = rows.filter((r) => r.id !== id)
  if (rows.length === before) throw new Error('待办不存在')
  return null
}
