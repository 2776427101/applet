/* =============================================================
 * 请假假数据
 *
 * 结构和 api-contract.md §9.1 完全一致：
 *   id / studentId / studentName / type / startAt / endAt / reason / status / createdAt
 *
 * 接口做好之后：把 src/api/leave.js 里的 USE_MOCK 改成 false，
 * 这个文件就可以整个删掉（没有别的地方引用它）。
 * ============================================================= */

import { mockStudentList } from './students'

const pad = (n) => String(n).padStart(2, '0')

/**
 * 相对今天算一个时间字符串。
 * 写死日期的话过几天全部变成"上个月"，看不出"今天/明天"的显示效果。
 *
 * @param {number} offset 今天往后推几天，负数往前
 * @param {number} hh     几点
 * @param {number} mm     几分
 */
function at(offset, hh, mm = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(hh)}:${pad(mm)}:00`
}

/* 6 条，覆盖 4 种状态：
   2 条待审批（页面默认要看的）、2 条已通过、1 条已驳回、1 条已销假 */
const SEED = [
  {
    id: 1,
    studentId: 5,
    studentName: '刘子轩',
    type: 1,
    startAt: at(1, 8),
    endAt: at(3, 18),
    reason: '扁桃体发炎，医生建议在家休息三天',
    status: 0,
    createdAt: at(-1, 7, 12),
    auditRemark: null
  },
  {
    id: 2,
    studentId: 9,
    studentName: '吴俊熙',
    type: 2,
    startAt: at(-2, 8),
    endAt: at(-1, 18),
    reason: '家里有事，需回老家一趟',
    status: 0,
    createdAt: at(-2, 6, 40),
    auditRemark: null
  },
  {
    id: 3,
    studentId: 2,
    studentName: '李思远',
    type: 1,
    startAt: at(0, 8),
    endAt: at(0, 18),
    reason: '发烧就医',
    status: 1,
    createdAt: at(0, 7, 12),
    auditRemark: '家长已电话确认'
  },
  {
    id: 4,
    studentId: 12,
    studentName: '许静姝',
    type: 3,
    startAt: at(-6, 13),
    endAt: at(-6, 17, 30),
    reason: '参加市级数学竞赛',
    status: 2,
    createdAt: at(-7, 20, 10),
    auditRemark: '比赛通知没交上来，先驳回，补了再批'
  },
  {
    id: 5,
    studentId: 16,
    studentName: '黄若曦',
    type: 1,
    startAt: at(-10, 8),
    endAt: at(-8, 18),
    reason: '感冒发烧，居家观察两天',
    status: 3,
    createdAt: at(-11, 9, 0),
    auditRemark: '已返校，销假'
  },
  {
    id: 6,
    studentId: 20,
    studentName: '罗嘉树',
    type: 2,
    startAt: at(-4, 14),
    endAt: at(-4, 18),
    reason: '下午去医院复查牙齿',
    status: 1,
    createdAt: at(-5, 21, 30),
    auditRemark: null
  }
]

/* 假后端要有状态：审批会改 status，新增会多一条，页面才看得出效果。
   存内存不落 storage，重进小程序恢复成初始 6 条 */
let rows = SEED.map((r) => ({ ...r }))
let seq = 100

/** 排序照抄契约：待审批(status=0)优先，然后 created_at 倒序 */
function sortRows(list) {
  return list.slice().sort((a, b) => {
    const aWait = a.status === 0 ? 0 : 1
    const bWait = b.status === 0 ? 0 : 1
    if (aWait !== bWait) return aWait - bWait
    if (a.createdAt === b.createdAt) return b.id - a.id
    return a.createdAt < b.createdAt ? 1 : -1
  })
}

/**
 * 请假列表
 * @param {{status?:number, page?:number, size?:number}} [params]
 */
export function mockLeaveList(params = {}) {
  const { status, page, size } = params

  /* 注意：-1 和 undefined 都表示"全部"。
     不能写 if (status)，因为 0（待审批）是有效值 */
  const filtered = status === undefined || status === null || status === -1
    ? rows
    : rows.filter((r) => r.status === Number(status))

  const sorted = sortRows(filtered)

  /* 假后端也老老实实分页，这样前端的分页逻辑不会等接真接口才发现写错 */
  const p = !page || page < 1 ? 1 : page
  const s = !size || size < 1 ? 20 : size
  const start = (p - 1) * s

  return {
    total: sorted.length,
    page: p,
    size: s,
    list: sorted.slice(start, start + s)
  }
}

/** 审批：只能 0 → 1 或 0 → 2，状态机在这里也要拦一道 */
export function mockAuditLeave(id, data = {}) {
  const row = rows.find((r) => r.id === id)
  if (!row) throw new Error('请假记录不存在')

  const target = Number(data.status)
  if (target !== 1 && target !== 2) throw new Error('只能通过或驳回')
  if (row.status !== 0) throw new Error('该请假已处理过')

  row.status = target
  row.auditRemark = data.remark || null
  return null
}

/** 代学生请假：老师代录的一律已通过 */
export function mockAddLeave(data = {}) {
  if (!data.studentId) throw new Error('请选择学生')
  if (!data.startAt || !data.endAt) throw new Error('请填写请假起止时间')
  if (data.endAt <= data.startAt) throw new Error('结束时间必须晚于开始时间')

  /* 真接口只收 studentId，姓名是后端 JOIN 出来的。
     假后端没有数据库，只能自己翻一下学生名单 —— 这样页面就不用
     为了照顾假数据多传一个契约里没有的 studentName */
  const stu = mockStudentList({ status: -1 }).list.find((s) => s.id === data.studentId)

  const row = {
    id: ++seq,
    studentId: data.studentId,
    studentName: stu ? stu.name : `学生${data.studentId}`,
    type: Number(data.type) || 1,
    startAt: data.startAt,
    endAt: data.endAt,
    reason: data.reason || null,
    status: 1,
    createdAt: at(0, new Date().getHours(), new Date().getMinutes()),
    auditRemark: null
  }
  rows.unshift(row)
  return { id: row.id }
}
