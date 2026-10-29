<template>
  <view class="page">
    <!-- ============ 考试列表都没拉下来 ============ -->
    <!-- 单独一个分支：这时候连「有没有考试」都不知道，
         不能显示「还没有考试」，那是在编造一个不存在的事实 -->
    <view v-if="examErr && !exams.length" class="card err-card">
      <load-error
        :message="examErr"
        hint="考试列表没拉下来，网络恢复后点重试"
        compact
        @retry="loadExams"
      />
    </view>

    <template v-else>
    <!-- ============ 顶部：考试选择器 + 新建入口 ============ -->
    <view class="head">
      <view class="head-main">
        <picker
          v-if="exams.length"
          mode="selector"
          :range="examNames"
          :value="examIndex"
          @change="onPickExam"
        >
          <view class="exam-pick">
            <view class="exam-pick-main">
              <text class="exam-name">{{ currentExam.name }}</text>
              <text v-if="showType" class="exam-type">{{ typeText(currentExam.type) }}</text>
            </view>
            <text class="exam-date">{{ currentExam.examDate }} ▾</text>
          </view>
        </picker>
        <view v-else class="exam-pick">
          <text class="exam-name exam-name-empty">还没有考试</text>
          <text class="exam-date">先建一次考试才能录成绩</text>
        </view>
      </view>

      <!-- 新建考试：一学期只建几次，但每次都要找得到。
           放在标题右边而不是底部按钮，是因为底部那个位置留给「录入成绩」，
           那是每天都要点的 -->
      <view class="head-new" hover-class="head-new-pressed" @click="onCreateExam">
        <text class="head-new-text">＋ 新建</text>
      </view>
    </view>

    <!-- ============ 考试详情加载失败 ============ -->
    <view v-if="detailErr" class="card err-card">
      <load-error
        :message="detailErr"
        hint="成绩和统计没拉下来，重试一次试试"
        compact
        @retry="loadDetail"
      />
    </view>

    <template v-else-if="currentExam">
      <!-- ============ 总览：平均总分 + 年级排名 ============ -->
      <view class="card overview">
        <view class="ov-top">
          <view class="ov-avg">
            <text class="ov-label">平均总分</text>
            <view class="ov-num-row">
              <text class="ov-num">{{ fmt(stats.totalAvg) }}</text>
              <text class="ov-full">/ {{ fmt(totalFull) }}</text>
            </view>
          </view>

          <!-- 年级排名：算不出来就显示「未统计」，绝不显示「第 null 名」 -->
          <view class="rank-pill" :class="{ 'rank-pill-none': stats.gradeRank == null }">
            <text class="rank-main">{{ rankText }}</text>
            <text class="rank-sub">{{ rankSub }}</text>
          </view>
        </view>

        <view class="ov-divider"></view>

        <view class="ov-row">
          <view class="ov-cell">
            <text class="ov-cell-num">{{ fmt(stats.totalMax) }}</text>
            <text class="ov-cell-label">最高</text>
          </view>
          <view class="ov-cell">
            <text class="ov-cell-num">{{ fmt(stats.totalMin) }}</text>
            <text class="ov-cell-label">最低</text>
          </view>
          <view class="ov-cell">
            <text class="ov-cell-num">{{ recordedCount }}</text>
            <text class="ov-cell-label">已录人数</text>
          </view>
        </view>
      </view>

      <!-- ============ 单科 ============ -->
      <view class="subjects">
        <view
          v-for="s in subjects"
          :key="s"
          class="sub-pill"
          :class="{ 'sub-pill-on': s === activeSubject }"
          @click="activeSubject = s"
        >
          <text class="sub-text" :class="{ 'sub-text-on': s === activeSubject }">{{ s }}</text>
        </view>
      </view>

      <view v-if="activeStat" class="card subject-card">
        <view class="sc-grid">
          <view class="sc-cell">
            <text class="sc-num">{{ fmt(activeStat.avg) }}</text>
            <text class="sc-label">平均</text>
          </view>
          <view class="sc-cell">
            <text class="sc-num">{{ fmt(activeStat.max) }}</text>
            <text class="sc-label">最高</text>
          </view>
          <view class="sc-cell">
            <text class="sc-num">{{ fmt(activeStat.min) }}</text>
            <text class="sc-label">最低</text>
          </view>
          <view class="sc-cell">
            <text class="sc-num">{{ passText(activeStat.passRate) }}</text>
            <text class="sc-label">及格率</text>
          </view>
        </view>

        <!-- 及格率进度条：一眼看出「及格的人多不多」 -->
        <view class="bar-track">
          <view class="bar-fill" :style="{ width: passWidth(activeStat.passRate) }"></view>
        </view>
        <text class="sc-tip">
          满分 {{ fmt(activeStat.fullScore) }} · 及格线 {{ fmt(passLine(activeStat.fullScore)) }}
        </text>
      </view>

      <!-- ============ 排名列表 ============ -->
      <view class="sec-head">
        <text class="sec-title">班级排名</text>
        <text class="sec-sub">{{ ranked.length }} 人</text>
      </view>

      <view v-if="loading && !ranked.length" class="card">
        <view v-for="i in 5" :key="i" class="sk-row">
          <view class="sk sk-idx"></view>
          <view class="sk sk-name"></view>
          <view class="sk sk-bar"></view>
        </view>
      </view>

      <view v-else-if="!ranked.length" class="card empty">
        <text class="empty-title">这次考试还没有成绩</text>
        <!-- 两种录法都提一句。科目多的时候导入比一格一格敲快得多，
             但不说的话老师根本不会想到还有这条路 -->
        <text class="empty-tip">下面可以「录入本次成绩」，也可以「导入 Excel」</text>
      </view>

      <view v-else class="card list">
        <view
          v-for="(r, i) in ranked"
          :key="r.studentId"
          class="row"
          :class="{ 'row-last': i === ranked.length - 1 }"
        >
          <text class="row-rank" :class="rankClass(r.rank)">{{ r.rank == null ? '—' : r.rank }}</text>
          <view class="row-mid">
            <view class="row-line">
              <text class="row-name">{{ r.studentName }}</text>
              <text class="row-score">{{ fmt(r.total) }}</text>
            </view>
            <view class="row-track">
              <view class="row-fill" :class="rankClass(r.rank)" :style="{ width: barWidth(r.total) }"></view>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部按钮占位，避免最后一行被遮住 -->
      <view class="bottom-holder"></view>
    </template>

    <!-- ============ 没有考试 ============ -->
    <view v-else class="card empty empty-page">
      <text class="empty-title">还没有考试</text>
      <text class="empty-tip">建一次考试，才有成绩可看</text>
      <view class="empty-btn" hover-class="empty-btn-pressed" @click="onCreateExam">
        新建考试
      </view>
    </view>
    </template>

    <!-- ============ 底部固定主操作 ============ -->
    <!-- 一场考试都没有时，主按钮改成「新建考试」。
         原来点了只会 toast「先建一次考试」，等于把一个死路做成按钮。
         考试列表都没拉下来时不显示 —— 这时候新建考试也大概率会失败 -->
    <view v-if="!(examErr && !exams.length)" class="bottom">
      <!-- 有考试时并排两个入口：录入和导入是同一件事的两种做法，
           都放在最常用的位置，不让「导入」藏在某个二级菜单里 -->
      <view v-if="currentExam" class="bottom-row">
        <view class="btn-ghost" hover-class="btn-ghost-pressed" @click="onImport">
          <text class="btn-ghost-text">导入 Excel</text>
        </view>
        <view class="btn" hover-class="btn-pressed" @click="onEntry">
          <text class="btn-text">录入本次成绩</text>
        </view>
      </view>
      <view v-else class="btn" hover-class="btn-pressed" @click="onEntry">
        <text class="btn-text">新建考试</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import { getExamList, getScoreList, getScoreStats } from '@/api/score'
import LoadError from '@/components/load-error/load-error.vue'

/* ---------------- 状态 ---------------- */
const exams = ref([])
const currentExamId = ref(null)
const examDetail = ref(null)
const rows = ref([])
const stats = ref({})
const activeSubject = ref('')
const loading = ref(false)

/* 两个错误分开存：
   examErr   = 连「有哪些考试」都不知道 → 整页换成错误块
   detailErr = 考试列表有了，但这场考试的成绩/统计没拉下来 → 只换内容区 */
const examErr = ref('')
const detailErr = ref('')

/* 新建考试页建完之后会 emit 这个事件，带上新考试的 id。
   不这么做的话，回到本页会「保持上次选中的考试」——
   老师刚建完考试，看到的还是上一场，会以为没建成 */
onLoad(() => {
  uni.$on('exam:created', (id) => {
    if (id) currentExamId.value = Number(id)
  })
})

/* ---------------- 计算 ---------------- */

const examNames = computed(() => exams.value.map((e) => `${e.name} · ${e.examDate}`))

const examIndex = computed(() => {
  const i = exams.value.findIndex((e) => e.id === currentExamId.value)
  return i < 0 ? 0 : i
})

const currentExam = computed(() => exams.value[examIndex.value] || null)

/* 考试名里已经带「期中」「月考」了，再挂一个同类标签就是重复。
   只有名字里看不出来类型时才显示 */
const showType = computed(() => {
  const e = currentExam.value
  if (!e) return false
  return !String(e.name).includes(typeText(e.type))
})

const subjects = computed(() => examDetail.value?.subjects || [])

const totalFull = computed(() => {
  const fs = examDetail.value?.fullScore || {}
  return subjects.value.reduce((sum, s) => sum + (Number(fs[s]) || 0), 0)
})

const ranked = computed(() => rows.value)

const recordedCount = computed(() => rows.value.filter((r) => r.total != null).length)

const activeStat = computed(() => {
  const list = stats.value.subjects || []
  return list.find((s) => s.subject === activeSubject.value) || null
})

const rankText = computed(() => {
  const d = stats.value
  if (d.gradeRank == null) return '未统计'
  return `年级第 ${d.gradeRank}`
})

const rankSub = computed(() => {
  const d = stats.value
  if (d.gradeRank == null) {
    /* 只有一个班时不排名：排第 1 是假象，老师会误以为「我们年级第一」 */
    return d.gradeClassCount ? `仅 ${d.gradeClassCount} 个班有成绩` : '暂无对比'
  }
  return `共 ${d.gradeClassCount} 个班`
})

/* ---------------- 工具 ---------------- */

/* 分数可能是 350 也可能是 117.4，整数不显示 .0 */
function fmt(v) {
  if (v == null || v === '') return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return '—'
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function passText(rate) {
  if (rate == null) return '—'
  return `${Math.round(Number(rate) * 100)}%`
}

function passWidth(rate) {
  if (rate == null) return '0%'
  return `${Math.min(100, Math.max(0, Number(rate) * 100))}%`
}

function passLine(full) {
  return Number(full || 100) * 0.6
}

/* 条形图按「本班最高分」为 100% 缩放，差生之间的差距才看得出来 */
function barWidth(total) {
  if (total == null) return '0%'
  const max = rows.value[0]?.total || 0
  if (!max) return '0%'
  return `${Math.max(4, (Number(total) / max) * 100)}%`
}

function rankClass(rank) {
  if (rank === 1) return 'r1'
  if (rank === 2) return 'r2'
  if (rank === 3) return 'r3'
  return 'rn'
}

function typeText(t) {
  return { 1: '周测', 2: '月考', 3: '期中', 4: '期末' }[t] || '考试'
}

/* ---------------- 数据 ---------------- */

async function loadExams() {
  /* 重试前清掉上次的错误，否则这次成功了错误块也不会消失 */
  examErr.value = ''
  try {
    /* silent：页面自己会画「加载失败 + 重试」，再弹 toast 就是同一句话说两遍 */
    const res = await getExamList({ page: 1, size: 20 }, { silent: true })
    exams.value = res.list || []
  } catch (e) {
    /* 手里没有旧列表时才记错误。有旧数据就继续显示，比整块盖掉更有用 */
    if (!exams.value.length) {
      examErr.value = (e && e.message) || '加载失败'
    }
    return
  }

  if (!exams.value.length) {
    currentExamId.value = null
    examDetail.value = null
    rows.value = []
    stats.value = {}
    return
  }
  /* 保持上次选中的考试；考试被删了才回到第一个 */
  if (!exams.value.some((e) => e.id === currentExamId.value)) {
    currentExamId.value = exams.value[0].id
  }
  await loadDetail()
}

async function loadDetail() {
  if (!currentExamId.value) return
  loading.value = true
  detailErr.value = ''
  try {
    /* 两个接口互不依赖，并行发，比串行快一半 */
    const [sl, st] = await Promise.all([
      getScoreList(currentExamId.value, { silent: true }),
      getScoreStats(currentExamId.value, { silent: true })
    ])
    examDetail.value = sl.exam
    rows.value = sl.list || []
    stats.value = st || {}

    const subs = sl.exam?.subjects || []
    if (!subs.includes(activeSubject.value)) {
      activeSubject.value = subs[0] || ''
    }
  } catch (e) {
    /* 这里不清空 rows / stats：清空之后「这次考试还没录成绩」
       和「加载失败」在界面上就完全一样了。保留旧数据，靠 detailErr 盖错误块 */
    detailErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* ---------------- 交互 ---------------- */

function onPickExam(e) {
  const item = exams.value[Number(e.detail.value)]
  if (!item || item.id === currentExamId.value) return
  currentExamId.value = item.id
  /* 换考试时先清空，否则会有一瞬间显示上一场考试的成绩 */
  rows.value = []
  stats.value = {}
  examDetail.value = null
  loadDetail()
}

function onEntry() {
  /* 没有考试时，主按钮就是「新建考试」。
     原来这里只 toast「先建一次考试」，等于把一条死路做成了一个按钮 */
  if (!currentExam.value) {
    onCreateExam()
    return
  }
  /* 录入页是普通页面不是 tab，用 navigateTo；
     保存后它会 navigateBack，本页的 onShow 会自动重新拉数据 */
  uni.navigateTo({ url: `/pages/teacher/score/edit?examId=${currentExam.value.id}` })
}

function onCreateExam() {
  uni.navigateTo({ url: '/pages/teacher/score/exam-edit' })
}

/* 从 Excel 导入整张成绩表。
   和「录入本次成绩」并列放在底部：47 人 × 3 科 = 141 格，
   一格一格敲是很实际的成本，导入不该是隐藏功能 */
function onImport() {
  if (!currentExam.value) return
  uni.navigateTo({ url: `/pages/teacher/score/import?examId=${currentExam.value.id}` })
}

/* tab 页用 onShow 不用 onLoad：从别的 tab 切回来要能刷新 */
onShow(() => {
  loadExams()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 0;
  box-sizing: border-box;
}

/* ---------- 顶部考试选择器 ---------- */
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

/* 考试名可能很长（「2028届软通1班第一次月考」），
   必须给 min-width: 0 才会在 flex 里正常省略，否则会把「＋ 新建」挤出去 */
.head-main {
  flex: 1;
  min-width: 0;
}

.head-new {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  box-shadow: 0 4rpx 12rpx rgba(160, 120, 80, 0.08);
}

.head-new-pressed {
  background: #FAF5EF;
}

.head-new-text {
  font-size: 24rpx;
  color: #C5342B;
  font-weight: 500;
}

/* 错误块自带内边距，卡片不要再加一层，否则上下会空得离谱 */
.err-card {
  padding: 0;
}

.exam-pick {
  display: flex;
  flex-direction: column;
  padding: 4rpx 0;
}

.exam-pick-main {
  display: flex;
  align-items: center;
}

.exam-name {
  font-size: 40rpx;
  font-weight: 600;
  color: #2B2622;
  letter-spacing: 1rpx;
}

.exam-name-empty {
  color: #B4A99B;
  font-weight: 500;
}

.exam-type {
  margin-left: 14rpx;
  padding: 2rpx 12rpx;
  font-size: 20rpx;
  color: #C5342B;
  background: rgba(197, 52, 43, 0.08);
  border-radius: 6rpx;
}

.exam-date {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #8A8178;
}

/* ---------- 卡片 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 20rpx;
  padding: 28rpx 26rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(160, 120, 80, 0.06);
}

/* ---------- 总览 ---------- */
.ov-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.ov-label {
  font-size: 24rpx;
  color: #8A8178;
}

.ov-num-row {
  display: flex;
  align-items: baseline;
  margin-top: 6rpx;
}

.ov-num {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 72rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1.05;
}

.ov-full {
  margin-left: 8rpx;
  font-size: 24rpx;
  color: #B4A99B;
}

/* 年级排名胶囊 */
.rank-pill {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  padding: 12rpx 20rpx;
  background: rgba(197, 52, 43, 0.07);
  border-radius: 16rpx;
}

.rank-main {
  font-size: 28rpx;
  font-weight: 600;
  color: #C5342B;
}

.rank-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #A8837E;
}

/* 算不出来的时候变灰，不要用红色假装有排名 */
.rank-pill-none {
  background: #F2EDE5;
}

.rank-pill-none .rank-main {
  color: #9A9186;
  font-weight: 500;
}

.rank-pill-none .rank-sub {
  color: #B4A99B;
}

.ov-divider {
  height: 1rpx;
  background: #F0E9DE;
  margin: 24rpx 0 20rpx;
}

.ov-row {
  display: flex;
}

.ov-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ov-cell-num {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 34rpx;
  color: #2B2622;
}

.ov-cell-label {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9A9186;
}

/* ---------- 科目胶囊 ---------- */
.subjects {
  display: flex;
  margin-bottom: 16rpx;
}

.sub-pill {
  padding: 12rpx 30rpx;
  margin-right: 14rpx;
  background: #EFE5D6;
  border-radius: 999rpx;
}

.sub-pill-on {
  background: #C5342B;
}

.sub-text {
  font-size: 26rpx;
  color: #8A8178;
}

.sub-text-on {
  color: #FFFFFF;
  font-weight: 600;
}

/* ---------- 单科卡片 ---------- */
.subject-card {
  padding-bottom: 24rpx;
}

.sc-grid {
  display: flex;
}

.sc-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sc-num {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 36rpx;
  color: #2B2622;
}

.sc-label {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.bar-track {
  height: 10rpx;
  margin: 24rpx 0 12rpx;
  background: #F0E9DE;
  border-radius: 999rpx;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: #2E7D5B;
  border-radius: 999rpx;
}

.sc-tip {
  font-size: 22rpx;
  color: #B4A99B;
}

/* ---------- 小标题 ---------- */
.sec-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 6rpx 14rpx;
}

.sec-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #2B2622;
}

.sec-sub {
  font-size: 22rpx;
  color: #9A9186;
}

/* ---------- 排名列表 ---------- */
.list {
  padding: 6rpx 26rpx;
}

.row {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #F5F0E8;
}

.row-last {
  border-bottom: none;
}

.row-rank {
  width: 52rpx;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 30rpx;
  color: #9A9186;
  text-align: center;
}

/* 前三名用中国红，其余灰 */
.r1 {
  color: #C5342B;
  font-weight: 700;
}

.r2 {
  color: #C5342B;
}

.r3 {
  color: #C5342B;
}

.rn {
  color: #9A9186;
}

.row-mid {
  flex: 1;
  margin-left: 14rpx;
  min-width: 0;
}

.row-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.row-name {
  font-size: 28rpx;
  color: #2B2622;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.row-score {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 28rpx;
  color: #2B2622;
}

.row-track {
  height: 8rpx;
  margin-top: 10rpx;
  background: #F5F0E8;
  border-radius: 999rpx;
  overflow: hidden;
}

.row-fill {
  height: 100%;
  border-radius: 999rpx;
}

.row-fill.r1 {
  background: #C5342B;
}

.row-fill.r2 {
  background: #C86B55;
}

.row-fill.r3 {
  background: #CE8E6E;
}

.row-fill.rn {
  background: #DCCFC0;
}

/* ---------- 骨架屏 ---------- */
.sk-row {
  display: flex;
  align-items: center;
  padding: 18rpx 0;
}

.sk {
  background: #F2EDE5;
  border-radius: 8rpx;
  height: 26rpx;
}

.sk-idx {
  width: 40rpx;
  margin-right: 16rpx;
}

.sk-name {
  width: 140rpx;
  margin-right: 20rpx;
}

.sk-bar {
  flex: 1;
}

/* ---------- 空状态 ---------- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 70rpx 0;
}

.empty-page {
  margin-top: 120rpx;
}

.empty-title {
  font-size: 28rpx;
  color: #2B2622;
}

.empty-tip {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #B4A99B;
}

.empty-btn {
  margin-top: 28rpx;
  padding: 16rpx 52rpx;
  border-radius: 999rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 26rpx;
  font-weight: 500;
}

.empty-btn-pressed {
  background: #A82A22;
}

/* ---------- 底部固定按钮 ---------- */
.bottom-holder {
  height: 150rpx;
}

.bottom {
  position: fixed;
  left: 0;
  right: 0;
  /* 小程序里 tabBar 是原生的、不在页面里，所以是 0；
     H5 里 tabBar 是页面的一部分，--window-bottom 等于它的高度。
     写死 0 的话 H5 预览会压住 tabBar */
  bottom: var(--window-bottom, 0);
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(247, 239, 228, 0.94);
}

/* 两个按钮并排：次要的「导入」固定宽度，主操作吃掉剩下的空间。
   反过来会让「导入」比「录入」还显眼，那是错的 —— 录一个两个学生
   还是手工快 */
.bottom-row {
  display: flex;
  align-items: stretch;
}

.btn {
  flex: 1;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #C5342B;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 18rpx rgba(197, 52, 43, 0.24);
}

/* 白底红字红边。不用灰色：灰色像「不可用」，而它其实很可用 */
.btn-ghost {
  width: 220rpx;
  flex-shrink: 0;
  height: 92rpx;
  margin-right: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #FFFFFF;
  border: 2rpx solid #E8CFC9;
  border-radius: 16rpx;
  box-sizing: border-box;
}

.btn-ghost-pressed {
  background: #FDF2F0;
}

.btn-ghost-text {
  font-size: 27rpx;
  font-weight: 500;
  color: #C5342B;
}

.btn-pressed {
  background: #A82A22;
}

.btn-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #FFFFFF;
  letter-spacing: 2rpx;
}
</style>
