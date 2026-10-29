<template>
  <view class="page">
    <!-- ========== 1. 还没选文件 ========== -->
    <block v-if="!preview && !result">
      <!-- 解析失败：这里的报错经常是一整段「为什么 + 怎么办」
           （「表里没找到能对上科目的列，本次考试的科目是信息技术…」），
           toast 只显示两行，后半截会被吃掉。所以固定画在页面上 -->
      <view v-if="errMsg" class="card bad">
        <text class="bad-title">这份表导不进来</text>
        <text class="bad-text">{{ errMsg }}</text>
        <text class="bad-tip">换个文件，或者按上面说的改一下表格再来。</text>
      </view>

      <view class="card intro">
        <text class="intro-title">从 Excel 导入成绩</text>
        <!-- 导到哪场考试必须一开始就写清楚。
             如果进来时选中的是别的考试（比如刚建的那场），
             不说的话老师会把分数导进错的地方 -->
        <view v-if="examName" class="intro-exam">
          <text class="intro-exam-label">导入到</text>
          <text class="intro-exam-name">{{ examName }}</text>
        </view>
        <view class="intro-list">
          <text class="intro-item">· 支持 .xlsx（Excel 2007 及以上）</text>
          <text class="intro-item">· 表头不用在第 1 行，程序会在前 10 行里自己找</text>
          <text class="intro-item">· 一行一个学生，一列一个科目。列名写成科目名（「语文」「数学成绩」都认）</text>
          <text class="intro-item">· 有「学号」列就按学号对，没有就按姓名对</text>
        </view>
      </view>

      <!-- 导入会覆盖分数，这一点在选文件之前就要说清楚，
           不能等到预览页才告诉他 -->
      <view class="card caution">
        <text class="caution-title">导入会覆盖已有分数</text>
        <text class="caution-text">
          表格里有分数的格子，会覆盖库里原来的分数；表格里空着的格子，
          等于「这一科缺考」。所以最好拿一张完整的成绩单来导。
        </text>
      </view>

      <view class="pick" hover-class="pick-pressed" @click="onChoose">
        <text class="pick-icon">＋</text>
        <text class="pick-text">选择 Excel 文件</text>
        <text class="pick-tip">{{ loading ? '正在解析…' : '从聊天记录里选，或从手机文件里选' }}</text>
      </view>
    </block>

    <!-- ========== 2. 解析中 ========== -->
    <view v-else-if="loading" class="card loading">
      <view class="spinner"></view>
      <text class="loading-text">正在解析成绩表…</text>
      <text class="loading-tip">47 行 × 3 科大概要 1 秒</text>
    </view>

    <!-- ========== 3. 预览 ========== -->
    <block v-else-if="preview && !result">
      <!-- 文件信息 -->
      <view class="card file">
        <view class="file-main">
          <text class="file-name">{{ fileName }}</text>
          <text class="file-meta">
            导入到「{{ preview.examName }}」· 来自 sheet「{{ preview.sheetName }}」·
            表头在第 {{ preview.headerRowNo }} 行 · {{ preview.dataRows }} 行数据
          </text>
        </view>
        <text class="file-change" @click="onChoose">重选</text>
      </view>

      <!-- 最危险的一种情况：把已有分数清成缺考。
           单独一张红卡，而且放在统计前面 —— 它是「要不要继续」的判断依据，
           不是一条可以划过去的补充说明 -->
      <view v-if="stats.clear" class="card danger">
        <text class="danger-title">{{ stats.clear }} 个已有分数会被清成缺考</text>
        <text class="danger-text">
          Excel 里空着的格子，导入后等于「这一科没考」。
          如果这些学生其实考了、只是你还没把分填进表里，现在导入就会把原来的分数抹掉，
          而且没有撤销。
        </text>
        <text class="danger-text">
          确认这些人确实缺考，再往下点。
        </text>
      </view>

      <!-- 覆盖：也需要知道，但没那么危险（有旧分，也有新分） -->
      <view v-else-if="stats.overwrite" class="card warn-card">
        <text class="warn-title">{{ stats.overwrite }} 个分数会被覆盖</text>
        <text class="warn-text">
          这些格子库里已经有分，导入后以这张表为准。改错了要重新导一遍正确的表。
        </text>
      </view>

      <!-- 统计 -->
      <view class="card">
        <text class="sec-title">{{ stats.importable }} 行可以导入</text>
        <view class="stats">
          <view class="stat">
            <text class="stat-num c-create">{{ stats.create }}</text>
            <text class="stat-label">新填</text>
          </view>
          <view class="stat">
            <text class="stat-num c-dup">{{ stats.overwrite }}</text>
            <text class="stat-label">覆盖</text>
          </view>
          <view class="stat">
            <text class="stat-num" :class="stats.clear ? 'c-err' : 'c-gray'">{{ stats.clear }}</text>
            <text class="stat-label">清成缺考</text>
          </view>
          <view class="stat">
            <text class="stat-num c-gray">{{ stats.error }}</text>
            <text class="stat-label">导不进来</text>
          </view>
        </view>
        <text class="hint">按「格」算：一共会写入 {{ stats.cells }} 个成绩。</text>
      </view>

      <!-- 列识别 -->
      <view class="card">
        <text class="sec-title">认出来的列</text>
        <view class="chips">
          <view class="chip chip-sub" v-for="c in subjectCols" :key="'s' + c.column">
            <text class="chip-h">{{ c.subject }}</text>
            <text class="chip-f">满分 {{ fmt(fullOf(c.subject)) }}</text>
          </view>
          <view class="chip" v-for="c in idCols" :key="'i' + c.column">
            <text class="chip-h">{{ c.header }}</text>
            <!-- 表头和字段名一样时只显示一遍，否则「学号 学号」看着像渲染错了 -->
            <text v-if="c.header !== c.fieldLabel" class="chip-f">{{ c.fieldLabel }}</text>
          </view>
        </view>

        <view v-if="preview.unmappedHeaders.length" class="unmapped">
          <text class="unmapped-label">没用上的列：</text>
          <text class="unmapped-text">{{ preview.unmappedHeaders.join('、') }}</text>
        </view>

        <!-- 缺的科目一定要说：不说的话老师会以为「导完了怎么英语还是空的」 -->
        <view v-if="preview.missingSubjects.length" class="miss">
          <text class="miss-text">
            这次不导「{{ preview.missingSubjects.join('、') }}」——
            表里没有这几列，它们已有的分数保持原样。
          </text>
        </view>
      </view>

      <!-- 导不进来的行 -->
      <view v-if="errorRows.length" class="card">
        <text class="sec-title">这 {{ errorRows.length }} 行导不进来</text>
        <view class="row-item" v-for="r in errorRows" :key="'e' + r.excelRowNo">
          <text class="row-no">第 {{ r.excelRowNo }} 行</text>
          <view class="row-body">
            <text class="row-name">{{ r.name || '（没名字）' }}</text>
            <text class="row-reason">{{ r.errors.join('；') }}</text>
          </view>
        </view>
        <text class="hint">在 Excel 里改好这几行，重新选择文件就行。其他行不受影响。</text>
      </view>

      <!-- 底部按钮 -->
      <view class="bottom-space"></view>
      <view class="bar">
        <view class="bar-btn" :class="{ off: !canCommit || loading }" @click="onCommit">
          {{ loading ? '正在写入…' : commitText }}
        </view>
      </view>
    </block>

    <!-- ========== 4. 结果 ========== -->
    <block v-else>
      <view class="card done">
        <view class="done-icon">✓</view>
        <text class="done-title">导入完成</text>
        <text class="done-main">写入 {{ result.saved }} 个成绩 · {{ result.students }} 人</text>
        <view class="done-nums">
          <text class="done-num">新填 {{ result.create }}</text>
          <text class="done-sep">·</text>
          <text class="done-num">覆盖 {{ result.overwrite }}</text>
          <text class="done-sep">·</text>
          <text class="done-num" :class="{ bad: result.clear }">清成缺考 {{ result.clear }}</text>
        </view>
      </view>

      <view class="card tip">
        <text class="tip-text">
          回到成绩页会自动刷新。如果发现导错了，在 Excel 里改好再导一次就行 ——
          同一格重复导入只会覆盖，不会多出一条。
        </text>
      </view>

      <view class="bottom-space"></view>
      <view class="bar">
        <view class="bar-btn" @click="backToScore">返回成绩页</view>
      </view>
    </block>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { previewScoreImport, commitScoreImport, chooseFile } from '@/api/scoreImport'
import { getExamList } from '@/api/score'

/* 要导到哪场考试。从成绩页带过来，全程不变 */
const examId = ref(null)

/* 考试名。只为了在页面上写清「导入到哪」，避免导错考试 */
const examName = ref('')

const fileName = ref('')
const preview = ref(null)
const result = ref(null)
const loading = ref(false)

/* 解析失败的原因。空串 = 没失败 */
const errMsg = ref('')

/* 上次选的文件。留着是为了「重选」以外还能直接重解析，
   也方便以后加「改完表再导一次」的入口 */
let lastPick = null

/* ---------------- 计算 ---------------- */

const stats = computed(() =>
  preview.value && preview.value.stats
    ? preview.value.stats
    : { rows: 0, importable: 0, error: 0, cells: 0, create: 0, overwrite: 0, clear: 0 }
)

/* 科目列：后端认成科目的那些 */
const subjectCols = computed(() =>
  preview.value ? preview.value.columns.filter((c) => c.subject) : []
)

/* 身份列：姓名 / 学号 / 班级。成绩导入里它们不参与「科目」这件事 */
const idCols = computed(() =>
  preview.value ? preview.value.columns.filter((c) => !c.subject && c.field) : []
)

const errorRows = computed(() =>
  preview.value ? preview.value.rows.filter((r) => r.action === 'error') : []
)

/* 有东西可写才让点。0 格还点得动的话，点下去只会收到「没有可写入的成绩」 */
const canCommit = computed(() => stats.value.cells > 0)

const commitText = computed(() => {
  if (!stats.value.cells) return '没有可导入的成绩'
  return `确认导入 ${stats.value.cells} 格成绩`
})

/* ---------------- 工具 ---------------- */

function fmt(v) {
  if (v == null) return '—'
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function fullOf(subject) {
  const fs = preview.value && preview.value.fullScore ? preview.value.fullScore : {}
  return fs[subject] == null ? 100 : fs[subject]
}

/* ---------------- 数据 ---------------- */

async function onChoose() {
  const pick = await chooseFile()
  if (!pick) return
  lastPick = pick
  fileName.value = pick.name
  await doPreview(pick)
}

async function doPreview(pick) {
  result.value = null
  errMsg.value = ''
  loading.value = true
  try {
    preview.value = await previewScoreImport(examId.value, pick)
  } catch (e) {
    /* 回到初始态，并把原因固定画在页面上。
       只靠 toast 的话，老师一低头看 Excel 就错过了 */
    preview.value = null
    errMsg.value = (e && e.message) || '解析失败，请确认这是一份 .xlsx 成绩表'
  } finally {
    loading.value = false
  }
}

async function onCommit() {
  if (!canCommit.value || loading.value) return

  /* 只提交能导的行。错误行传上去后端也会拒，
     但提前过滤能让结果里的数字名副其实 */
  const rows = preview.value.rows
    .filter((r) => r.action !== 'error')
    .map((r) => ({
      excelRowNo: r.excelRowNo,
      studentId: r.studentId,
      /* scores 原样回传：里面有 null 的科目表示缺考，
         没有 key 的科目表示表里没这一列、不要动。
         这个区别是后端「不清空没导的科目」的全部依据 */
      scores: r.scores
    }))

  if (!rows.length) {
    uni.showToast({ title: '没有可导入的行', icon: 'none' })
    return
  }

  loading.value = true
  try {
    result.value = await commitScoreImport({ examId: examId.value, rows })
  } catch (e) {
    /* 失败就留在预览页，老师可以直接再点一次 */
  } finally {
    loading.value = false
  }
}

function backToScore() {
  /* 成绩页是 tabBar 页，正常情况下 navigateBack 能回去；
     直接进来的话（比如扫码）没有上一页，退回 switchTab */
  uni.navigateBack({
    fail: () => uni.switchTab({ url: '/pages/teacher/score/index' })
  })
}

onLoad((q) => {
  examId.value = q && q.examId ? Number(q.examId) : null
  if (!examId.value) {
    /* 没有 examId 就不知道往哪场考试导，直接说清楚，
       而不是让老师选完文件才收到一句「缺少 examId」 */
    errMsg.value = '缺少考试信息，请从成绩页的「导入 Excel」进来'
    return
  }
  loadExamName()
})

/* 只是为了让页面能写出「导入到「期中考试」」。
   失败就留空，不影响导入本身 —— 考试名显示不出来不该挡住正事 */
async function loadExamName() {
  try {
    const res = await getExamList({ page: 1, size: 20 }, { silent: true })
    const hit = (res.list || []).find((e) => e.id === examId.value)
    examName.value = hit ? `${hit.name} · ${hit.examDate}` : ''
  } catch (e) {
    examName.value = ''
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 0;
  box-sizing: border-box;
}

/* ---------- 通用卡片 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 28rpx 26rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.sec-title {
  font-size: 27rpx;
  font-weight: 500;
  color: #2B2622;
  display: block;
  margin-bottom: 18rpx;
}

.hint {
  font-size: 21rpx;
  color: #9A9186;
  line-height: 1.6;
  display: block;
  margin-top: 14rpx;
}

/* ---------- 初始态 ---------- */
.intro-title {
  font-size: 30rpx;
  font-weight: 500;
  color: #2B2622;
  display: block;
  margin-bottom: 18rpx;
}

/* 「导入到 期中考试 · 2026-10-20」 */
.intro-exam {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 18rpx;
  padding: 12rpx 16rpx;
  background: #FDF6E9;
  border-radius: 14rpx;
}

.intro-exam-label {
  font-size: 21rpx;
  color: #9A8158;
  margin-right: 12rpx;
}

.intro-exam-name {
  font-size: 23rpx;
  color: #2B2622;
  font-weight: 500;
  flex: 1;
  min-width: 0;
}

/* ---------- 解析失败 ---------- */
.bad {
  border: 2rpx solid #F0C9C4;
  background: #FEF7F6;
  box-shadow: none;
}

.bad-title {
  display: block;
  font-size: 27rpx;
  font-weight: 500;
  color: #C5342B;
  margin-bottom: 12rpx;
}

/* 报错原文经常很长，行高放宽一点才读得下去 */
.bad-text {
  display: block;
  font-size: 22rpx;
  color: #7A6F63;
  line-height: 1.75;
}

.bad-tip {
  display: block;
  margin-top: 12rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

.intro-list {
  display: flex;
  flex-direction: column;
}

.intro-item {
  font-size: 23rpx;
  color: #7A6F63;
  line-height: 1.9;
}

/* 选文件之前就把「会覆盖」说清楚。
   用浅黄而不是浅红：这时候还没造成任何后果，红字会吓人 */
.caution {
  background: #FDF6E9;
  box-shadow: none;
}

.caution-title {
  display: block;
  font-size: 25rpx;
  font-weight: 500;
  color: #8A6A28;
  margin-bottom: 10rpx;
}

.caution-text {
  display: block;
  font-size: 22rpx;
  color: #9A8158;
  line-height: 1.7;
}

.pick {
  margin-top: 28rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 60rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 2rpx dashed #E3D5C2;
}

.pick-pressed {
  background: #FDF9F4;
}

.pick-icon {
  font-size: 60rpx;
  color: #C5342B;
  font-weight: 200;
  line-height: 1;
}

.pick-text {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
  margin-top: 18rpx;
}

.pick-tip {
  font-size: 21rpx;
  color: #9A9186;
  margin-top: 10rpx;
}

/* ---------- 解析中 ---------- */
.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 90rpx 0;
}

.spinner {
  width: 52rpx;
  height: 52rpx;
  border: 5rpx solid #F0E4D4;
  border-top-color: #C5342B;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 26rpx;
  color: #2B2622;
  margin-top: 24rpx;
}

.loading-tip {
  font-size: 21rpx;
  color: #9A9186;
  margin-top: 8rpx;
}

/* ---------- 文件信息 ---------- */
.file {
  display: flex;
  align-items: center;
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 27rpx;
  font-weight: 500;
  color: #2B2622;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  font-size: 21rpx;
  color: #9A9186;
  margin-top: 6rpx;
  display: block;
  line-height: 1.6;
}

.file-change {
  font-size: 23rpx;
  color: #C5342B;
  margin-left: 18rpx;
  flex-shrink: 0;
}

/* ---------- 危险提示 ---------- */
/* 这一张是全页最需要被看见的，所以用实色描边 + 浅红底，
   和下面的统计卡明显区分开 */
.danger {
  border: 2rpx solid #E8A9A2;
  background: #FEF7F6;
}

.danger-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #C5342B;
  margin-bottom: 12rpx;
}

.danger-text {
  display: block;
  font-size: 22rpx;
  color: #7A6F63;
  line-height: 1.7;
  margin-bottom: 8rpx;
}

.warn-card {
  border: 2rpx solid #F0DCA8;
  background: #FDF9EC;
  box-shadow: none;
}

.warn-title {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: #B07B22;
  margin-bottom: 10rpx;
}

.warn-text {
  display: block;
  font-size: 22rpx;
  color: #9A8158;
  line-height: 1.7;
}

/* ---------- 统计 ---------- */
.stats {
  display: flex;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-num {
  font-size: 40rpx;
  font-weight: 600;
  line-height: 1.2;
}

.c-create {
  color: #2F7A4F;
}

.c-dup {
  color: #B07B22;
}

.c-err {
  color: #C5342B;
}

.c-gray {
  color: #9A9186;
}

.stat-label {
  font-size: 20rpx;
  color: #9A9186;
  margin-top: 6rpx;
}

/* ---------- 列识别 ---------- */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.chip {
  display: flex;
  align-items: center;
  background: #F1F1F4;
  border-radius: 12rpx;
  padding: 8rpx 14rpx;
}

/* 科目列用绿色，和身份列区分开：
   老师最关心的是「这几列被当成科目了吗」 */
.chip-sub {
  background: #EEF5EE;
}

.chip-h {
  font-size: 21rpx;
  color: #2B2622;
}

.chip-sub .chip-h {
  color: #2F7A4F;
  font-weight: 500;
}

.chip-f {
  font-size: 19rpx;
  color: #8A8178;
  margin-left: 8rpx;
  padding-left: 8rpx;
  border-left: 1rpx solid #DDD6CC;
}

.chip-sub .chip-f {
  color: #5B8A6E;
  border-left-color: #CFE2D2;
}

.unmapped {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
}

.unmapped-label {
  font-size: 21rpx;
  color: #9A9186;
}

.unmapped-text {
  font-size: 21rpx;
  color: #B4A99B;
  flex: 1;
}

.miss {
  margin-top: 18rpx;
  padding: 14rpx 18rpx;
  background: #FDF6E9;
  border-radius: 14rpx;
}

.miss-text {
  font-size: 21rpx;
  color: #8A6A28;
  line-height: 1.6;
}

/* ---------- 行明细 ---------- */
.row-item {
  display: flex;
  padding: 16rpx 0;
  border-top: 1rpx solid #F5EDE2;
}

.row-no {
  font-size: 21rpx;
  color: #B4A99B;
  width: 120rpx;
  flex-shrink: 0;
  padding-top: 2rpx;
}

.row-body {
  flex: 1;
  min-width: 0;
}

.row-name {
  font-size: 24rpx;
  color: #2B2622;
  display: block;
}

.row-reason {
  font-size: 21rpx;
  color: #C5342B;
  line-height: 1.5;
  margin-top: 4rpx;
  display: block;
}

/* ---------- 底部按钮 ---------- */
.bottom-space {
  height: 150rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--window-bottom, 0px);
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(247, 239, 228, 0.96);
  box-sizing: border-box;
}

.bar-btn {
  height: 92rpx;
  border-radius: 26rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 29rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 24rpx rgba(197, 52, 43, 0.25);
}

.bar-btn.off {
  background: #D9CEC0;
  box-shadow: none;
}

/* ---------- 结果 ---------- */
.done {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56rpx 26rpx;
}

.done-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #EEF5EE;
  color: #2F7A4F;
  font-size: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.done-title {
  font-size: 30rpx;
  font-weight: 500;
  color: #2B2622;
  margin-top: 22rpx;
}

.done-main {
  font-size: 24rpx;
  color: #7A6F63;
  margin-top: 12rpx;
}

.done-nums {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.done-num {
  font-size: 23rpx;
  color: #7A6F63;
}

.done-num.bad {
  color: #C5342B;
}

.done-sep {
  font-size: 22rpx;
  color: #D3C7B8;
  margin: 0 12rpx;
}

.tip {
  background: #FDF6E9;
  box-shadow: none;
}

.tip-text {
  font-size: 22rpx;
  color: #9A8158;
  line-height: 1.7;
}
</style>
