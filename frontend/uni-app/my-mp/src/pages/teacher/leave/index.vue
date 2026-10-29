<template>
  <view class="page">
    <!-- 顶部：待审批 / 已处理 切换 + 代请假 -->
    <view class="head">
      <view class="seg">
        <view
          class="seg-item"
          :class="{ 'seg-on': tab === 'pending' }"
          @click="tab = 'pending'"
        >
          <text class="seg-text" :class="{ 'seg-text-on': tab === 'pending' }">待审批</text>
          <text class="seg-badge" v-if="pending.length">{{ pending.length }}</text>
        </view>
        <view
          class="seg-item"
          :class="{ 'seg-on': tab === 'done' }"
          @click="tab = 'done'"
        >
          <text class="seg-text" :class="{ 'seg-text-on': tab === 'done' }">已处理</text>
        </view>
      </view>

      <view class="add" hover-class="add-pressed" @click="openSheet">
        <text class="add-plus">＋</text>
        <text class="add-text">代请假</text>
      </view>
    </view>

    <!-- 骨架屏 -->
    <block v-if="loading">
      <view class="card" v-for="n in 3" :key="'sk' + n">
        <view class="sk-line" :style="{ width: 40 + n * 5 + '%' }"></view>
        <view class="sk-line sk-sm" style="width: 62%"></view>
      </view>
    </block>

    <!-- 加载失败：和「没有待审批」必须分开。
         原来失败时 list 是空的，会显示「都批完了，休息一下」——
         老师会以为请假都处理完了，其实是一条都没拉到 -->
    <view v-else-if="err && !list.length" class="card">
      <load-error
        :message="err"
        hint="请假记录还在服务器上，网络恢复后点重试就行"
        compact
        @retry="load"
      />
    </view>

    <!-- 空状态 -->
    <view class="empty" v-else-if="!shown.length">
      <view class="empty-circle">
        <text class="empty-tick">{{ tab === 'pending' ? '✓' : '·' }}</text>
      </view>
      <text class="empty-title">{{ tab === 'pending' ? '没有待审批的请假' : '还没有处理记录' }}</text>
      <text class="empty-sub">{{ tab === 'pending' ? '都批完了，休息一下' : '审批过的会出现在这里' }}</text>
    </view>

    <!-- 卡片列表 -->
    <block v-else>
      <view
        class="card"
        :class="{ 'card-faded': item.status !== 0 }"
        v-for="item in shown"
        :key="item.id"
      >
        <view class="card-top">
          <view class="card-left">
            <text class="name">{{ item.studentName || '未知学生' }}</text>
            <text class="type" :class="'type-' + item.type">{{ typeLabel(item.type) }}</text>
          </view>
          <text class="status" :class="'st-' + item.status">{{ statusLabel(item.status) }}</text>
        </view>

        <view class="time">
          <text class="time-text">{{ fmt(item.startAt) }} → {{ fmt(item.endAt) }}</text>
          <text class="dur" v-if="dur(item)">{{ dur(item) }}</text>
        </view>

        <view class="reason" v-if="item.reason">
          <text class="reason-text">{{ item.reason }}</text>
        </view>

        <view class="audit-remark" v-if="item.auditRemark">
          <text class="audit-label">审批备注</text>
          <text class="audit-text">{{ item.auditRemark }}</text>
        </view>

        <!-- 审批按钮只出现在待审批卡片上，平铺在底部，一步点到 -->
        <view class="actions" v-if="item.status === 0">
          <view class="btn btn-reject" hover-class="btn-pressed" @click="onAudit(item, 2)">
            <text class="btn-reject-text">驳回</text>
          </view>
          <view class="btn btn-pass" hover-class="btn-pressed" @click="onAudit(item, 1)">
            <text class="btn-pass-text">通过</text>
          </view>
        </view>
      </view>
    </block>

    <view class="bottom-space"></view>

    <!-- 代请假面板 -->
    <view class="mask" v-if="sheetVisible" @click="closeSheet"></view>
    <view class="editor" v-if="sheetVisible">
      <view class="editor-head">
        <text class="editor-title">代学生请假</text>
        <text class="editor-close" @click="closeSheet">✕</text>
      </view>

      <view class="field">
        <text class="label">学生</text>
        <view class="dt">
          <picker
            v-if="studentNames.length"
            mode="selector"
            :range="studentNames"
            :value="studentIndex"
            @change="onPickStudent"
          >
            <text class="value">{{ studentTip }} ▾</text>
          </picker>
          <text v-else class="value value-empty">{{ studentTip }}</text>
          <text
            v-if="!studentNames.length && !studentLoading"
            class="retry"
            @click="loadStudents"
          >重新加载</text>
        </view>
      </view>

      <view class="field">
        <text class="label">类型</text>
        <view class="types">
          <view
            class="type-chip"
            :class="{ 'type-chip-on': form.type === t.v }"
            v-for="t in TYPES"
            :key="t.v"
            @click="form.type = t.v"
          >
            <text class="type-chip-text" :class="{ 'type-chip-text-on': form.type === t.v }">
              {{ t.label }}
            </text>
          </view>
        </view>
      </view>

      <view class="field">
        <text class="label">开始</text>
        <view class="dt">
          <picker mode="date" :value="form.startDate" @change="(e) => (form.startDate = e.detail.value)">
            <text class="value">{{ form.startDate }} ▾</text>
          </picker>
          <picker mode="time" :value="form.startTime" @change="(e) => (form.startTime = e.detail.value)">
            <text class="value">{{ form.startTime }} ▾</text>
          </picker>
        </view>
      </view>

      <view class="field">
        <text class="label">结束</text>
        <view class="dt">
          <picker mode="date" :value="form.endDate" @change="(e) => (form.endDate = e.detail.value)">
            <text class="value">{{ form.endDate }} ▾</text>
          </picker>
          <picker mode="time" :value="form.endTime" @change="(e) => (form.endTime = e.detail.value)">
            <text class="value">{{ form.endTime }} ▾</text>
          </picker>
        </view>
      </view>

      <textarea
        class="editor-input"
        v-model="form.reason"
        :maxlength="255"
        placeholder="请假原因（可不填）"
        placeholder-style="color:#B4A99B;font-size:28rpx"
        :auto-height="true"
        :show-confirm-bar="false"
        :cursor-spacing="140"
      />

      <view class="editor-foot">
        <text class="editor-tip">代录的请假默认「已通过」</text>
        <view
          class="btn-save"
          :class="{ 'btn-save-off': saving }"
          hover-class="btn-save-pressed"
          @click="onSave"
        >
          <text class="btn-save-text">{{ saving ? '保存中…' : '保存' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getLeaveList, auditLeave, addLeave } from '@/api/leave'
import LoadError from '@/components/load-error/load-error.vue'
import { getStudentList } from '@/api/student'

const pad = (n) => String(n).padStart(2, '0')

const TYPES = [
  { v: 1, label: '病假' },
  { v: 2, label: '事假' },
  { v: 3, label: '其他' }
]

const TYPE_LABEL = { 1: '病假', 2: '事假', 3: '其他' }
const STATUS_LABEL = { 0: '待审批', 1: '已通过', 2: '已驳回', 3: '已销假' }

const typeLabel = (t) => TYPE_LABEL[t] || '其他'
const statusLabel = (s) => STATUS_LABEL[s] || ''

/* ---------- 时间 ---------- */

/**
 * 后端给 'yyyy-MM-dd HH:mm:ss'。
 * ⚠️ iOS 不认带横杠的格式，new Date() 会得到 Invalid Date，
 * 页面上时间全变空 —— 必须先换成斜杠再解析
 */
function toDate(s) {
  if (!s) return null
  const d = new Date(String(s).replace(/-/g, '/'))
  return isNaN(d.getTime()) ? null : d
}

/** '10-27 08:00' */
function fmt(s) {
  const d = toDate(s)
  if (!d) return '—'
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 请了多久：按"跨了几个日历日"算，不是按小时数除 24 */
function dur(item) {
  const a = toDate(item.startAt)
  const b = toDate(item.endAt)
  if (!a || !b) return ''
  const ms = b.getTime() - a.getTime()
  if (ms <= 0) return ''

  /* 10-27 08:00 → 10-29 18:00，老师心里想的是「请了三天」。
     如果直接 ms/86400000 会算出 2.4 天，那个 .4 没人看得懂。
     按日历日数：29 - 27 + 1 = 3 天 */
  const d0 = new Date(a.getFullYear(), a.getMonth(), a.getDate()).getTime()
  const d1 = new Date(b.getFullYear(), b.getMonth(), b.getDate()).getTime()
  const days = Math.round((d1 - d0) / 86400000) + 1

  /* 同一天就别说「1 天」了，「请了 4 小时」更准 */
  if (days === 1) return `${Math.round(ms / 3600000)} 小时`
  return `${days} 天`
}

/* ---------- 列表 ---------- */

const list = ref([])
const loading = ref(true)
const tab = ref('pending')
const err = ref('') /* 加载失败的原因。空串 = 没失败 */

/* 后端已经按"待审批优先 + 创建时间倒序"排好，这里只负责分堆 */
const pending = computed(() => list.value.filter((i) => i.status === 0))
const handled = computed(() => list.value.filter((i) => i.status !== 0))
const shown = computed(() => (tab.value === 'pending' ? pending.value : handled.value))

async function load() {
  /* 重试前清掉上次的错误，否则这次成功了错误块也不会消失 */
  err.value = ''
  try {
    /* 一次拉全部，前端自己分堆。
       size 给 100：一个班一学期的请假记录不会有这么多，
       真要超了再补"加载更多"，别为还没发生的问题写代码。
       silent：页面自己会画「加载失败 + 重试」 */
    const res = await getLeaveList({ size: 100 }, { silent: true })
    list.value = res.list || []
  } catch (e) {
    /* 手里没有旧数据时才记错误 —— 有数据就继续显示，别整块盖掉 */
    if (!list.value.length) {
      err.value = (e && e.message) || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

/* 用 onShow 不用 onLoad：从别的 tab 切回来时，待审批数要是最新的 */
onShow(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

/* ---------- 审批 ---------- */

/**
 * editable: true 让 showModal 带一个输入框，正好用来填审批备注，
 * 不用为"写句话"再单独做一个弹层。需要微信基础库 2.17.1+
 */
function onAudit(item, status) {
  const isPass = status === 1
  uni.showModal({
    title: isPass ? '通过这条请假？' : '驳回这条请假？',
    editable: true,
    placeholderText: '审批备注（可不填）',
    confirmText: isPass ? '通过' : '驳回',
    confirmColor: isPass ? '#2E7D5B' : '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      const remark = (res.content || '').trim()
      try {
        await auditLeave(item.id, { status, remark: remark || null })
        uni.showToast({ title: isPass ? '已通过' : '已驳回', icon: 'none' })
        /* 重新拉一次，让顺序和状态都跟后端一致 */
        await load()
      } catch (e) {
        /* 已经 toast 过（比如「该请假已处理过」） */
      }
    }
  })
}

/* ---------- 代请假 ---------- */

const sheetVisible = ref(false)
const saving = ref(false)

const students = ref([])
const studentIndex = ref(0)
const studentLoading = ref(false)

const studentNames = computed(() =>
  students.value.map((s) => (s.studentNo ? `${s.name} · ${s.studentNo}` : s.name))
)

/* 三种状态分开说清楚：加载中 / 加载完但是空的 / 有数据。
   都写成「加载中…」会让用户一直等一个永远不会来的名单 */
const studentTip = computed(() => {
  if (studentNames.value.length) return studentNames.value[studentIndex.value]
  return studentLoading.value ? '学生名单加载中…' : '没有可用的学生'
})

const today = () => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const form = reactive({
  type: 1,
  startDate: today(),
  startTime: '08:00',
  endDate: today(),
  endTime: '18:00',
  reason: ''
})

async function openSheet() {
  /* 每次打开都重置，避免上一次填了一半的内容留在里面 */
  form.type = 1
  form.startDate = today()
  form.startTime = '08:00'
  form.endDate = today()
  form.endTime = '18:00'
  form.reason = ''
  studentIndex.value = 0
  sheetVisible.value = true

  /* 名单只拉一次，之后开关面板都用缓存 */
  if (!students.value.length) loadStudents()
}

async function loadStudents() {
  if (studentLoading.value) return
  studentLoading.value = true
  try {
    /* 只取在读学生：已离班的不该再登记请假 */
    const res = await getStudentList({ status: 1 })
    students.value = res.list || []
  } catch (e) {
    /* request.js 已经 toast 过，这里只把 loading 关掉，
       让文案从「加载中」变成「没有可用的学生」，旁边出现「重新加载」 */
  } finally {
    studentLoading.value = false
  }
}

function closeSheet() {
  sheetVisible.value = false
}

function onPickStudent(e) {
  studentIndex.value = Number(e.detail.value)
}

async function onSave() {
  if (saving.value) return

  const stu = students.value[studentIndex.value]
  if (!stu) {
    uni.showToast({ title: '请选择学生', icon: 'none' })
    return
  }

  /* 拼成契约要求的 'yyyy-MM-dd HH:mm:ss'。
     这种格式可以直接用字符串比大小，不用先转 Date */
  const startAt = `${form.startDate} ${form.startTime}:00`
  const endAt = `${form.endDate} ${form.endTime}:00`
  if (endAt <= startAt) {
    uni.showToast({ title: '结束时间要晚于开始时间', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await addLeave({
      studentId: stu.id,
      type: form.type,
      startAt,
      endAt,
      reason: form.reason.trim() || null
    })
    closeSheet()
    /* 代录的一定是「已通过」，所以跳到已处理，不然刚存完看不到东西 */
    tab.value = 'done'
    await load()
  } catch (e) {
    /* 已经 toast 过 */
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
}

/* ---------- 顶部 ---------- */
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.seg {
  display: flex;
  background: #EFE5D6;
  border-radius: 999rpx;
  padding: 6rpx;
}

.seg-item {
  display: flex;
  align-items: center;
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
}

/* 选中态：白底 + 轻阴影，在米色背景上浮起来 */
.seg-on {
  background: #FFFFFF;
  box-shadow: 0 4rpx 12rpx rgba(160, 120, 80, 0.1);
}

.seg-text {
  font-size: 26rpx;
  color: #8A8178;
}

.seg-text-on {
  color: #2B2622;
  font-weight: 600;
}

/* 待审批数量：红色小胶囊，和首页「今日请假」对得上 */
.seg-badge {
  font-size: 20rpx;
  color: #FFFFFF;
  background: #C5342B;
  border-radius: 999rpx;
  min-width: 28rpx;
  height: 28rpx;
  line-height: 28rpx;
  text-align: center;
  margin-left: 8rpx;
  padding: 0 6rpx;
  box-sizing: border-box;
}

.add {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border: 2rpx solid #E7DBC9;
  border-radius: 999rpx;
  padding: 12rpx 24rpx 12rpx 18rpx;
}

.add-pressed {
  background: #FAF5EF;
  transform: scale(0.97);
}

.add-plus {
  font-size: 28rpx;
  color: #C5342B;
  line-height: 1;
}

.add-text {
  font-size: 26rpx;
  color: #2B2622;
  margin-left: 4rpx;
}

/* ---------- 卡片 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 28rpx 26rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

/* 已处理：降透明度，不抢待审批的注意力 */
.card-faded {
  opacity: 0.72;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-left {
  display: flex;
  align-items: center;
  flex: 1;
  overflow: hidden;
}

.name {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
  margin-right: 14rpx;
}

.type {
  font-size: 20rpx;
  padding: 3rpx 12rpx;
  border-radius: 6rpx;
  flex-shrink: 0;
}

.type-1 {
  color: #B8433C;
  background: #FCEDEB;
}

.type-2 {
  color: #9A7A1E;
  background: #FBF3DC;
}

.type-3 {
  color: #6B7B86;
  background: #EEF2F5;
}

.status {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}

.st-0 {
  color: #C5342B;
  background: #FDEDEB;
}

.st-1 {
  color: #2E7D5B;
  background: #EDF6EE;
}

.st-2 {
  color: #8A8178;
  background: #F4EEE6;
}

.st-3 {
  color: #2F6EA8;
  background: #EAF1F8;
}

.time {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.time-text {
  font-size: 24rpx;
  color: #6E655C;
  font-variant-numeric: tabular-nums;
}

.dur {
  font-size: 20rpx;
  color: #9A9186;
  background: #F7F2EA;
  border-radius: 6rpx;
  padding: 2rpx 10rpx;
  margin-left: 12rpx;
}

.reason {
  margin-top: 16rpx;
  background: #FAF6F0;
  border-radius: 16rpx;
  padding: 18rpx 20rpx;
}

.reason-text {
  font-size: 26rpx;
  color: #4A423B;
  line-height: 1.6;
  word-break: break-all;
}

.audit-remark {
  margin-top: 14rpx;
  padding-left: 18rpx;
  border-left: 4rpx solid #E7DBC9;
}

.audit-label {
  display: block;
  font-size: 20rpx;
  color: #B4A99B;
  margin-bottom: 4rpx;
}

.audit-text {
  font-size: 24rpx;
  color: #8A8178;
  line-height: 1.5;
  word-break: break-all;
}

/* ---------- 审批按钮 ---------- */
.actions {
  display: flex;
  margin-top: 24rpx;
}

.btn {
  flex: 1;
  height: 76rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-pressed {
  opacity: 0.82;
}

.btn-reject {
  border: 2rpx solid #E7DBC9;
  margin-right: 16rpx;
}

.btn-reject-text {
  font-size: 28rpx;
  color: #8A8178;
}

.btn-pass {
  background: #C5342B;
  box-shadow: 0 6rpx 16rpx rgba(197, 52, 43, 0.2);
}

.btn-pass-text {
  font-size: 28rpx;
  color: #FFFFFF;
  font-weight: 500;
}

/* ---------- 骨架屏 ---------- */
.sk-line {
  height: 30rpx;
  border-radius: 8rpx;
  background: #F4EEE6;
}

.sk-sm {
  height: 24rpx;
  margin-top: 18rpx;
}

/* ---------- 空状态 ---------- */
.empty {
  padding: 140rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-circle {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #EFF4EE;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-tick {
  font-size: 44rpx;
  color: #7C8A79;
  line-height: 1;
}

.empty-title {
  font-size: 30rpx;
  color: #2B2622;
  margin-top: 24rpx;
}

.empty-sub {
  font-size: 24rpx;
  color: #B4A99B;
  margin-top: 10rpx;
}

.bottom-space {
  height: 40rpx;
}

/* ---------- 代请假面板 ---------- */
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(43, 38, 34, 0.42);
  z-index: 10;
}

.editor {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 11;
  background: #FFFFFF;
  border-radius: 36rpx 36rpx 0 0;
  padding: 32rpx 32rpx calc(40rpx + env(safe-area-inset-bottom));
  animation: slideUp 0.22s ease-out;
}

@keyframes slideUp {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.editor-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.editor-close {
  font-size: 30rpx;
  color: #B4A99B;
  padding: 8rpx 8rpx 8rpx 20rpx;
}

.field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #F4EDE3;
}

.label {
  font-size: 26rpx;
  color: #9A9186;
  flex-shrink: 0;
}

.value {
  font-size: 28rpx;
  color: #2B2622;
  padding-left: 20rpx;
}

.value-empty {
  color: #B4A99B;
}

.retry {
  font-size: 24rpx;
  color: #C5342B;
  margin-left: 16rpx;
  padding: 8rpx 0;
}

.dt {
  display: flex;
  align-items: center;
}

.types {
  display: flex;
}

.type-chip {
  border: 2rpx solid #E7DBC9;
  border-radius: 999rpx;
  padding: 8rpx 24rpx;
  margin-left: 12rpx;
}

.type-chip-on {
  background: #C5342B;
  border-color: #C5342B;
}

.type-chip-text {
  font-size: 24rpx;
  color: #8A8178;
}

.type-chip-text-on {
  color: #FFFFFF;
}

.editor-input {
  width: 100%;
  min-height: 88rpx;
  font-size: 28rpx;
  color: #2B2622;
  line-height: 1.6;
  background: #FAF5EF;
  border-radius: 18rpx;
  padding: 20rpx 22rpx;
  box-sizing: border-box;
  margin-top: 24rpx;
}

.editor-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 28rpx;
}

.editor-tip {
  font-size: 22rpx;
  color: #C0B6A8;
  flex: 1;
}

.btn-save {
  background: #C5342B;
  border-radius: 999rpx;
  padding: 18rpx 56rpx;
  box-shadow: 0 8rpx 20rpx rgba(197, 52, 43, 0.22);
}

.btn-save-pressed {
  opacity: 0.86;
}

.btn-save-off {
  background: #D9A8A4;
  box-shadow: none;
}

.btn-save-text {
  font-size: 28rpx;
  color: #FFFFFF;
  font-weight: 500;
}
</style>
