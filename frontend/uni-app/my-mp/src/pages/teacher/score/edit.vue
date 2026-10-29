<template>
  <view class="page">
    <!-- ============ 顶部说明 ============ -->
    <!-- 加载失败时整块隐藏：exam.name / subjects 都是空的，
         留着会显示「录入成绩 ·  · 0 科 · 0 人」，像一份真的空考试 -->
    <view v-if="!err" class="head">
      <view class="head-row">
        <view class="head-main">
          <text class="exam-name">{{ exam.name || '录入成绩' }}</text>
          <text class="exam-meta">
            {{ exam.examDate }} · {{ subjects.length }} 科 · {{ rows.length }} 人
          </text>
        </view>
        <!-- 47 人 × 3 科 = 141 格，一格一格敲太慢。
             这里给一条直接的出路，而不是让人退回去找导入入口 -->
        <view class="head-import" hover-class="head-import-pressed" @click="goImport">
          <text class="head-import-text">导入 Excel</text>
        </view>
      </view>
      <view class="hint">
        <text class="hint-dot">·</text>
        <text class="hint-text">留空 = 缺考，不计入总分；整行都没填的学生不会提交</text>
      </view>
    </view>

    <!-- ============ 表头（滚动时吸顶） ============ -->
    <view v-if="!err" class="thead">
      <view class="col-name">
        <text class="th-name">姓名</text>
      </view>
      <view v-for="s in subjects" :key="s" class="col-sub">
        <text class="th-sub">{{ s }}</text>
        <text class="th-full">{{ fmt(fullOf(s)) }}</text>
      </view>
    </view>

    <!-- ============ 学生行 ============ -->
    <view v-if="loading" class="card">
      <view v-for="i in 6" :key="i" class="sk-row">
        <view class="sk sk-name"></view>
        <view class="sk sk-cell"></view>
        <view class="sk sk-cell"></view>
        <view class="sk sk-cell"></view>
      </view>
    </view>

    <!-- 加载失败：和「这个班还没有学生」必须分开。
         原来失败时 rows 是空的，会显示「这个班还没有学生，先去学生页把人加进来」——
         老师去学生页一看 47 个人都在，只会更懵 -->
    <view v-else-if="err" class="card">
      <load-error
        :message="err"
        hint="学生名单和已录的分数都没拉下来，重试一次试试"
        compact
        @retry="load"
      />
    </view>

    <view v-else-if="!rows.length" class="card empty">
      <text class="empty-title">这个班还没有学生</text>
      <text class="empty-tip">先去「学生」页把人加进来</text>
    </view>

    <view v-else class="tbody">
      <view
        v-for="(r, i) in rows"
        :key="r.studentId"
        class="tr"
        :class="{ 'tr-touched': r.touched }"
      >
        <view class="col-name">
          <text class="idx">{{ i + 1 }}</text>
          <text class="name">{{ r.studentName }}</text>
        </view>
        <view v-for="s in subjects" :key="s" class="col-sub">
          <input
            class="cell"
            :class="{ 'cell-bad': !!errorOf(r, s) }"
            type="digit"
            :value="r.values[s]"
            placeholder="—"
            placeholder-style="color:#CFC5B6"
            :cursor-spacing="24"
            @input="onInput(r, s, $event)"
          />
        </view>
      </view>
    </view>

    <!-- 底部按钮占位 -->
    <view class="bottom-holder"></view>

    <!-- ============ 底部保存栏 ============ -->
    <view class="bottom">
      <view class="bottom-info">
        <text class="info-main" :class="{ 'info-bad': errorCount > 0 }">{{ infoText }}</text>
        <text class="info-sub">{{ subText }}</text>
      </view>
      <view
        class="btn"
        :class="{ 'btn-off': !canSave }"
        hover-class="btn-pressed"
        @click="onSave"
      >
        <text class="btn-text">{{ saving ? '保存中…' : '保存' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getScoreList, saveScores } from '@/api/score'
import LoadError from '@/components/load-error/load-error.vue'

/* ---------------- 状态 ---------------- */
const examId = ref(null)
const exam = ref({})
const subjects = ref([])
const fullScore = ref({})
const rows = ref([])
const loading = ref(true)
const saving = ref(false)
const err = ref('') /* 加载失败的原因。空串 = 没失败 */

/* ---------------- 工具 ---------------- */

function fmt(v) {
  if (v == null) return '—'
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(1)
}

function fullOf(sub) {
  return Number(fullScore.value[sub] || 100)
}

/** 一格填错的原因；填对或没填返回空字符串 */
function errorOf(r, s) {
  const raw = String(r.values[s] ?? '').trim()
  if (raw === '') return ''
  /* 用 Number 而不是 parseInt：parseInt('12abc') 会得到 12，把错误吃掉。
     Number('12.') 是 12，所以用户打到一半的「12.」不会被判错 */
  const n = Number(raw)
  if (Number.isNaN(n)) return '不是数字'
  if (n < 0) return '不能为负'
  if (n > fullOf(s)) return `超过满分 ${fmt(fullOf(s))}`
  return ''
}

/* ---------------- 计算 ---------------- */

const cellTotal = computed(() => rows.value.length * subjects.value.length)

const filledCount = computed(() => {
  let n = 0
  for (const r of rows.value) {
    for (const s of subjects.value) {
      if (String(r.values[s] ?? '').trim() !== '') n++
    }
  }
  return n
})

const errorCount = computed(() => {
  let n = 0
  for (const r of rows.value) {
    for (const s of subjects.value) {
      if (errorOf(r, s)) n++
    }
  }
  return n
})

/** 真正要提交的行：被改过的才提交（没碰过的学生不写库） */
const payload = computed(() => {
  const list = []
  for (const r of rows.value) {
    if (!r.touched) continue
    const scores = {}
    for (const s of subjects.value) {
      const raw = String(r.values[s] ?? '').trim()
      /* 空 = null = 缺考。注意不能给 0，0 是"考了但 0 分" */
      scores[s] = raw === '' ? null : Number(raw)
    }
    list.push({ studentId: r.studentId, scores })
  }
  return list
})

const canSave = computed(() => !saving.value && errorCount.value === 0 && payload.value.length > 0)

const infoText = computed(() => {
  if (errorCount.value > 0) return `${errorCount.value} 处填错`
  return `已填 ${filledCount.value} / ${cellTotal.value}`
})

const subText = computed(() => {
  if (errorCount.value > 0) return '红框的格子改一下才能保存'
  if (!payload.value.length) return '还没改动任何一格'
  return `将保存 ${payload.value.length} 个学生`
})

/* ---------------- 数据 ---------------- */

async function load() {
  if (!examId.value) {
    loading.value = false
    return
  }
  loading.value = true
  /* 重试前清掉上次的错误，否则这次成功了错误块也不会消失 */
  err.value = ''
  try {
    /* silent：页面自己会画「加载失败 + 重试」 */
    const res = await getScoreList(examId.value, { silent: true })
    exam.value = res.exam || {}
    subjects.value = res.exam?.subjects || []
    fullScore.value = res.exam?.fullScore || {}

    /* 把接口返回的分数转成字符串塞进输入框。
       数字类型不能直接绑 input，'128.0' 和 128 来回转会把光标顶到末尾 */
    rows.value = (res.list || []).map((r) => {
      const values = {}
      for (const s of subjects.value) {
        const v = r.scores ? r.scores[s] : null
        values[s] = v == null ? '' : String(v)
      }
      return {
        studentId: r.studentId,
        studentName: r.studentName,
        studentNo: r.studentNo,
        values,
        touched: false
      }
    })
  } catch (e) {
    /* 记错误，不清空 rows：清空之后「这个班还没有学生」和「加载失败」
       在界面上就完全一样了。模板里错误块的判断在空状态之前，会先命中 */
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* ---------------- 交互 ---------------- */

function onInput(r, s, e) {
  r.values[s] = e.detail.value
  /* 标记改过：哪怕整行清空也要提交，否则清空的动作存不进数据库 */
  r.touched = true
}

/**
 * 跳到成绩导入页。
 *
 * <p>用 redirectTo 不用 navigateTo：导入是整张表覆盖，导完再回到这个
 * 手工录入页没有意义，留着只会让返回栈里多一层。
 * redirectTo 之后，导入页点返回直接回成绩页。
 *
 * <p>如果有没保存的改动，`enableAlertBeforeUnload` 会先拦一次 ——
 * 这正是我们要的，不能让一次误触把填了一半的分数丢掉。
 */
function goImport() {
  if (!examId.value) return
  uni.redirectTo({ url: `/pages/teacher/score/import?examId=${examId.value}` })
}

async function onSave() {
  if (!canSave.value) {
    if (errorCount.value > 0) {
      uni.showToast({ title: '有格子填错了', icon: 'none' })
    } else if (!payload.value.length) {
      uni.showToast({ title: '还没填任何成绩', icon: 'none' })
    }
    return
  }
  saving.value = true
  try {
    const res = await saveScores({ examId: examId.value, rows: payload.value })
    disableLeaveAlert()
    uni.showToast({ title: `已保存 ${res.saved} 条`, icon: 'success' })
    /* 返回上一页，成绩页的 onShow 会自动重新拉数据 */
    setTimeout(() => uni.navigateBack(), 700)
  } catch (e) {
    /* request.js 已经 toast 过，这里不用再提示 */
  } finally {
    saving.value = false
  }
}

/* ---------------- 离开拦截 ---------------- */

/* 47 个学生的成绩填了一半被返回键吃掉，是很崩溃的事。
   wx.enableAlertBeforeUnload 是小程序原生能力（基础库 2.12+），
   H5 上没有 wx 对象，所以要判断一下 */
const hasUnsaved = computed(() => payload.value.length > 0)

function enableLeaveAlert() {
  if (typeof wx !== 'undefined' && wx.enableAlertBeforeUnload) {
    wx.enableAlertBeforeUnload({ message: '成绩还没保存，确定离开吗？' })
  }
}

function disableLeaveAlert() {
  if (typeof wx !== 'undefined' && wx.disableAlertBeforeUnload) {
    wx.disableAlertBeforeUnload()
  }
}

watch(hasUnsaved, (v) => {
  if (v) enableLeaveAlert()
  else disableLeaveAlert()
})

/* ---------------- 生命周期 ---------------- */

onLoad((q) => {
  examId.value = q && q.examId ? Number(q.examId) : null
  load()
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 16rpx 20rpx 0;
  box-sizing: border-box;
}

/* ---------- 顶部 ---------- */
.head {
  padding: 4rpx 6rpx 18rpx;
}

.head-row {
  display: flex;
  align-items: flex-start;
}

/* 考试名可能很长，必须给 min-width: 0 才会正常省略，
   否则会把右边的「导入 Excel」挤出去 */
.head-main {
  flex: 1;
  min-width: 0;
}

.head-import {
  flex-shrink: 0;
  margin-left: 16rpx;
  margin-top: 6rpx;
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  border: 2rpx solid #E8CFC9;
}

.head-import-pressed {
  background: #FDF2F0;
}

.head-import-text {
  font-size: 22rpx;
  color: #C5342B;
  font-weight: 500;
}

.exam-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #2B2622;
}

.exam-meta {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #8A8178;
}

.hint {
  display: flex;
  align-items: flex-start;
  margin-top: 14rpx;
  padding: 12rpx 16rpx;
  background: rgba(154, 122, 30, 0.08);
  border-radius: 10rpx;
}

.hint-dot {
  font-size: 24rpx;
  color: #9A7A1E;
  margin-right: 6rpx;
}

.hint-text {
  flex: 1;
  font-size: 22rpx;
  color: #9A7A1E;
  line-height: 1.5;
}

/* ---------- 表头 ---------- */
.thead {
  display: flex;
  /* 顶部对齐：让「姓名」和「语文/数学/英语」在同一行，
     下面那行「150」只有科目列才有，姓名列留空 */
  align-items: flex-start;
  /* 吸顶：滚到哪儿表头都在，不然 47 行往下滑就不知道哪列是哪科了 */
  position: sticky;
  top: 0;
  z-index: 10;
  background: #F7EFE4;
  padding: 10rpx 20rpx 12rpx;
  border-bottom: 1rpx solid #E7DCCC;
}

.col-name {
  width: 160rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.col-sub {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.th-name {
  font-size: 24rpx;
  color: #9A9186;
}

.th-sub {
  font-size: 24rpx;
  font-weight: 600;
  color: #2B2622;
}

.th-full {
  margin-top: 2rpx;
  font-size: 18rpx;
  color: #B4A99B;
}

/* ---------- 学生行 ---------- */
.tbody {
  background: #FFFFFF;
  border-radius: 16rpx;
  margin: 12rpx 0 0;
  overflow: hidden;
}

.tr {
  display: flex;
  align-items: center;
  padding: 12rpx 20rpx;
  border-bottom: 1rpx solid #F5F0E8;
}

.tr:last-child {
  border-bottom: none;
}

/* 改过的行左侧留一条竖线，一眼看出哪些改过 */
.tr-touched {
  box-shadow: inset 4rpx 0 0 #C5342B;
}

.idx {
  width: 40rpx;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 22rpx;
  color: #B4A99B;
}

.name {
  flex: 1;
  font-size: 26rpx;
  color: #2B2622;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cell {
  width: 100%;
  height: 64rpx;
  margin: 0 6rpx;
  padding: 0 6rpx;
  box-sizing: border-box;
  background: #FAF6EF;
  border: 1rpx solid #F0E9DE;
  border-radius: 8rpx;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 26rpx;
  color: #2B2622;
  text-align: center;
}

/* 填错的格子：红框 + 浅红底，不用弹窗打断输入 */
.cell-bad {
  background: rgba(197, 52, 43, 0.06);
  border-color: #C5342B;
  color: #C5342B;
}

/* ---------- 骨架屏 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 20rpx;
}

.sk-row {
  display: flex;
  align-items: center;
  padding: 16rpx 0;
}

.sk {
  background: #F2EDE5;
  border-radius: 8rpx;
  height: 40rpx;
}

.sk-name {
  width: 130rpx;
  margin-right: 20rpx;
}

.sk-cell {
  flex: 1;
  margin: 0 6rpx;
}

/* ---------- 空状态 ---------- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0;
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

/* ---------- 底部保存栏 ---------- */
.bottom-holder {
  height: 170rpx;
}

.bottom {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--window-bottom, 0);
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(247, 239, 228, 0.96);
  border-top: 1rpx solid #EDE3D5;
}

.bottom-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.info-main {
  font-size: 26rpx;
  font-weight: 600;
  color: #2B2622;
}

.info-bad {
  color: #C5342B;
}

.info-sub {
  margin-top: 4rpx;
  font-size: 20rpx;
  color: #9A9186;
}

.btn {
  width: 220rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #C5342B;
  border-radius: 14rpx;
  box-shadow: 0 6rpx 16rpx rgba(197, 52, 43, 0.22);
}

/* 不能保存时变灰，但还能点——点了会告诉用户为什么不能存 */
.btn-off {
  background: #D8CEC0;
  box-shadow: none;
}

.btn-pressed {
  background: #A82A22;
}

.btn-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #FFFFFF;
  letter-spacing: 2rpx;
}
</style>
