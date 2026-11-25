/* =============================================================
 * 成绩假数据（后端接口写好之前用）
 *
 * 分数按学生 id 套公式生成，和后端 02_seed.sql 里是同一套算法，
 * 所以切到真接口后数字几乎一样，一眼能看出对不对得上。
 *
 * 接口好了之后：把 src/api/score.js 里的 USE_MOCK 改成 false，
 * 这个文件就可以整个删掉。
 * ============================================================= */

const SUBJECTS = ['语文', '数学', '英语']
const FULL_SCORE = { 语文: 150, 数学: 150, 英语: 150 }

const exams = [
  {
    id: 2,
    name: '期中考试',
    type: 3,
    examDate: '2026-10-20',
    subjects: SUBJECTS,
    scoreCount: 141
  },
  {
    id: 1,
    name: '第一次月考',
    type: 2,
    examDate: '2026-09-28',
    subjects: SUBJECTS,
    scoreCount: 141
  }
]

/* 47 个学生，和后端种子数据同名同号 */
const students = Array.from({ length: 47 }, (_, i) => {
  const n = i + 1
  const no = String(n).padStart(2, '0')
  return {
    id: n,
    name: `学生${no}`,
    studentNo: `2026${String(n).padStart(3, '0')}`
  }
})

const round1 = (v) => Math.round(v * 10) / 10

/* 录入页保存过的分数，key 是 "examId:studentId:subject" */
const overrides = {}

/* 两套系数，让两次考试的排名不一样 */
const FORMULA = {
  1: {
    语文: (id) => 92 + ((id * 37) % 50) + ((id * 7) % 10) / 10,
    数学: (id) => 88 + ((id * 53) % 56) + ((id * 3) % 10) / 10,
    英语: (id) => 95 + ((id * 29) % 48) + ((id * 9) % 10) / 10
  },
  2: {
    语文: (id) => 90 + ((id * 41) % 54) + ((id * 3) % 10) / 10,
    数学: (id) => 85 + ((id * 47) % 60) + ((id * 9) % 10) / 10,
    英语: (id) => 93 + ((id * 31) % 52) + ((id * 7) % 10) / 10
  }
}

/** 生成某个学生某次考试的各科分数；学生 7 期中数学缺考 */
function scoresOf(examId, studentId) {
  const f = FORMULA[examId]
  const out = {}
  for (const sub of SUBJECTS) {
    out[sub] = round1(f[sub](studentId))
  }
  if (examId === 2 && studentId === 7) {
    out.数学 = null // 缺考，不是 0 分
  }
  /* 录入页保存过的分数覆盖掉公式生成的，这样"保存后返回"能看到变化 */
  for (const sub of SUBJECTS) {
    const key = `${examId}:${studentId}:${sub}`
    if (key in overrides) out[sub] = overrides[key]
  }
  return out
}

/** 一个学生一行，含总分和班内排名（同分并列） */
function buildRows(examId) {
  const rows = students.map((s) => {
    const scores = scoresOf(examId, s.id)
    let total = null
    for (const sub of SUBJECTS) {
      const v = scores[sub]
      if (v != null) total = total == null ? v : total + v
    }
    return {
      studentId: s.id,
      studentName: s.name,
      studentNo: s.studentNo,
      scores,
      total: total == null ? null : round1(total),
      rank: null
    }
  })

  rows.sort((a, b) => {
    if (a.total == null && b.total == null) return 0
    if (a.total == null) return 1
    if (b.total == null) return -1
    return b.total - a.total
  })

  let prev = null
  let prevRank = 0
  rows.forEach((r, i) => {
    if (r.total == null) return
    if (prev != null && Math.abs(prev - r.total) < 1e-9) {
      r.rank = prevRank
    } else {
      r.rank = i + 1
      prevRank = i + 1
      prev = r.total
    }
  })
  return rows
}

const delay = (data, ms = 280) =>
  new Promise((resolve) => setTimeout(() => resolve(data), ms))

/* ---------------- 考试 ---------------- */

export function mockExamList() {
  return delay({ total: exams.length, page: 1, size: 20, list: exams.map((e) => ({ ...e })) })
}

export function mockAddExam(data = {}) {
  const id = Math.max(...exams.map((e) => e.id)) + 1
  exams.unshift({
    id,
    name: data.name,
    type: data.type || 2,
    examDate: data.examDate,
    subjects: data.subjects || [],
    scoreCount: 0
  })
  return delay({ id }, 120)
}

/* ---------------- 成绩表 ---------------- */

export function mockScoreList(examId) {
  const exam = exams.find((e) => e.id === Number(examId))
  if (!exam) throw new Error('考试不存在')
  return delay({
    exam: {
      id: exam.id,
      name: exam.name,
      type: exam.type,
      examDate: exam.examDate,
      subjects: exam.subjects,
      fullScore: FULL_SCORE
    },
    list: buildRows(exam.id)
  })
}

/* ---------------- 统计 ---------------- */

export function mockScoreStats(examId) {
  const id = Number(examId)
  const rows = buildRows(id)

  const subjects = SUBJECTS.map((sub) => {
    const vals = rows.map((r) => r.scores[sub]).filter((v) => v != null)
    if (!vals.length) {
      return { subject: sub, avg: null, max: null, min: null, passRate: null, fullScore: FULL_SCORE[sub] }
    }
    const sum = vals.reduce((a, b) => a + b, 0)
    const line = FULL_SCORE[sub] * 0.6
    const pass = vals.filter((v) => v >= line).length
    return {
      subject: sub,
      avg: round1(sum / vals.length),
      max: Math.max(...vals),
      min: Math.min(...vals),
      passRate: Math.round((pass / vals.length) * 100) / 100,
      fullScore: FULL_SCORE[sub]
    }
  })

  const totals = rows.map((r) => r.total).filter((v) => v != null)

  return delay({
    examId: id,
    // 期中考试年级里有两个班录了成绩 → 第 2 名
    // 第一次月考只有本班 → null（只有一个班时排第 1 是假象）
    gradeRank: id === 2 ? 2 : null,
    gradeClassCount: id === 2 ? 2 : 1,
    subjects,
    totalAvg: totals.length ? round1(totals.reduce((a, b) => a + b, 0) / totals.length) : null,
    totalMax: totals.length ? Math.max(...totals) : null,
    totalMin: totals.length ? Math.min(...totals) : null
  })
}

/* ---------------- 批量录入 ---------------- */

export function mockSaveScores(data = {}) {
  const examId = Number(data.examId)
  let saved = 0
  for (const row of data.rows || []) {
    for (const sub of Object.keys(row.scores || {})) {
      overrides[`${examId}:${row.studentId}:${sub}`] = row.scores[sub]
      saved++
    }
  }
  return delay({ saved }, 300)
}
