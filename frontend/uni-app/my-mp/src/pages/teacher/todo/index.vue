<template>
  <view class="page">
    <!-- 顶部：还剩几件 + 逾期提醒 + 新建 -->
    <view class="bar">
      <view class="bar-main">
        <!-- 加载中 / 加载失败都显示破折号，不显示 0 ——
             「0 项待处理」会让老师以为自己记的待办全没了 -->
        <text class="bar-num">{{ loading || (err && !list.length) ? '—' : undone.length }}</text>
        <text class="bar-unit">项待处理</text>
        <text class="bar-over" v-if="overdueCount > 0">{{ overdueCount }} 项已逾期</text>
      </view>
      <view class="bar-add" hover-class="bar-add-pressed" @click="openSheet">
        <text class="bar-add-plus">＋</text>
        <text class="bar-add-text">新建</text>
      </view>
    </view>

    <!-- 未完成 -->
    <view class="card">
      <!-- 骨架屏：只在第一次加载时出现，切换回来不再闪 -->
      <block v-if="loading">
        <view class="row" v-for="n in 4" :key="'sk' + n">
          <view class="check check-ghost"></view>
          <view class="row-main">
            <view class="sk-line" :style="{ width: 62 - n * 6 + '%' }"></view>
          </view>
        </view>
      </block>

      <!-- 加载失败：和「没有待办」必须分开。
           原来失败时 list 是空的，会显示「没有待办了，今天可以早点下班」——
           老师会以为自己记的待办全没了 -->
      <load-error
        v-else-if="err && !list.length"
        :message="err"
        hint="待办还在服务器上，网络恢复后点重试就行"
        compact
        @retry="load"
      />

      <!-- 空状态 -->
      <view class="empty" v-else-if="!undone.length">
        <view class="empty-circle"><text class="empty-tick">✓</text></view>
        <text class="empty-title">没有待办了</text>
        <text class="empty-sub">今天可以早点下班</text>
      </view>

      <!-- 正常列表 -->
      <block v-else>
        <view
          class="row"
          v-for="t in undone"
          :key="t.id"
          hover-class="row-pressed"
          @longpress="onLongPress(t)"
        >
          <view class="check" @click.stop="onToggle(t)">
            <view class="check-mark" v-if="t.done"></view>
          </view>
          <view class="row-main" @click="onToggle(t)">
            <text class="row-title">{{ t.title }}</text>
            <view class="tags" v-if="t.due.text">
              <text class="tag" :class="'tag-' + t.due.tone">{{ t.due.text }}</text>
            </view>
          </view>
        </view>
      </block>
    </view>

    <!-- 已完成：默认折叠。勾掉的任务不该占视野，但得能找回来 -->
    <view class="card card-done" v-if="doneList.length">
      <view class="done-head" hover-class="row-pressed" @click="showDone = !showDone">
        <text class="done-title">已完成 {{ doneList.length }}</text>
        <text class="done-arrow" :class="{ 'done-arrow-open': showDone }">›</text>
      </view>

      <block v-if="showDone">
        <view
          class="row row-done"
          v-for="t in doneList"
          :key="t.id"
          hover-class="row-pressed"
          @longpress="onLongPress(t)"
        >
          <view class="check check-on" @click.stop="onToggle(t)">
            <view class="check-mark"></view>
          </view>
          <view class="row-main" @click="onToggle(t)">
            <text class="row-title">{{ t.title }}</text>
            <view class="tags" v-if="t.due.text">
              <text class="tag tag-done">{{ t.due.text }}</text>
            </view>
          </view>
        </view>
      </block>
    </view>

    <!-- 左滑要自己写手势，会和横向滚动打架，所以用长按删除 -->
    <text class="hint" v-if="undone.length || doneList.length">长按某一条可以删除</text>

    <view class="bottom-space"></view>

    <!-- 新建面板 -->
    <view class="mask" v-if="sheetVisible" @click="closeSheet"></view>
    <view class="editor" v-if="sheetVisible">
      <view class="editor-head">
        <text class="editor-title">新建待办</text>
        <text class="editor-close" @click="closeSheet">✕</text>
      </view>

      <textarea
        class="editor-input"
        v-model="draftTitle"
        :focus="focusInput"
        :maxlength="128"
        placeholder="要做什么？比如「收齐家长回执」"
        placeholder-style="color:#B4A99B;font-size:30rpx"
        :auto-height="true"
        :show-confirm-bar="false"
        :cursor-spacing="120"
      />

      <view class="editor-row">
        <text class="editor-label">截止日期</text>
        <view class="editor-date">
          <picker
            mode="date"
            :value="draftDate || todayStr"
            :start="todayStr"
            @change="onPickDate"
          >
            <text class="date-text" :class="{ 'date-empty': !draftDate }">
              {{ draftDate || '不设截止' }}
            </text>
          </picker>
          <text class="date-clear" v-if="draftDate" @click="draftDate = ''">清除</text>
        </view>
      </view>

      <view class="editor-foot">
        <text class="editor-tip">不设截止日期也能建，之后随时补</text>
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
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getTodoList, addTodo, setTodoDone, delTodo } from '@/api/todo'
import LoadError from '@/components/load-error/load-error.vue'

/* ---------- 日期处理 ---------- */

const pad = (n) => String(n).padStart(2, '0')

/**
 * 后端给的是 'yyyy-MM-dd HH:mm:ss'。
 * ⚠️ iOS 不认这种带横杠的格式，new Date('2026-10-25 18:00:00') 会得到 Invalid Date，
 * 页面上所有时间标签会变成 NaN。必须先换成斜杠再解析。
 */
function toDate(s) {
  if (!s) return null
  const d = new Date(String(s).replace(/-/g, '/'))
  return isNaN(d.getTime()) ? null : d
}

/* 只取「哪一天」，把时分秒抹掉 */
function dayStamp(d) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
}

/**
 * 把 dueAt 翻译成一句人话。
 * 为什么只比日期不比时刻：今天 18:00 的待办，现在 20:00 也不该算「逾期」，
 * 它还是「今天的事」。班主任关心的是哪一天，不是哪一分钟。
 */
function dueInfo(t) {
  const due = toDate(t.dueAt)
  if (!due) return { text: '', tone: 'none' }

  /* 用 round 不用 floor：夏令时那两天两个时间戳的差不是整 86400000 */
  const diff = Math.round((dayStamp(due) - dayStamp(new Date())) / 86400000)
  const md = `${pad(due.getMonth() + 1)}-${pad(due.getDate())}`

  if (t.done) return { text: `${md} 截止`, tone: 'done' }
  if (diff < 0) return { text: `已逾期 ${-diff} 天`, tone: 'over' }
  if (diff === 0) return { text: '今天截止', tone: 'soon' }
  if (diff === 1) return { text: '明天截止', tone: 'soon' }
  return { text: `还有 ${diff} 天`, tone: 'normal' }
}

/* ---------- 列表 ---------- */

const list = ref([])
const loading = ref(true)
const showDone = ref(false)
const err = ref('') /* 加载失败的原因。空串 = 没失败 */

/* 后端已经按契约排好序了，这里只负责按 done 拆成两堆，不再排一次 */
const decorate = (t) => ({ ...t, due: dueInfo(t) })
const undone = computed(() => list.value.filter((t) => t.done === 0).map(decorate))
const doneList = computed(() => list.value.filter((t) => t.done === 1).map(decorate))
const overdueCount = computed(() => undone.value.filter((t) => t.due.tone === 'over').length)

async function load() {
  /* 重试前清掉上次的错误，否则这次成功了错误块也不会消失 */
  err.value = ''
  try {
    /* 一次把全部拿回来，前端自己分堆。
       这样点「完成」之后不用再请求一次，数据也一定是一致的。
       silent：页面自己会画「加载失败 + 重试」 */
    const res = await getTodoList({}, { silent: true })
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

/* 用 onShow 不用 onLoad：从别处返回这个页面时，数字要是最新的 */
onShow(load)

/* ---------- 勾选 ---------- */

async function onToggle(t) {
  const next = t.done ? 0 : 1
  const target = list.value.find((x) => x.id === t.id)
  if (!target) return

  /* 先改本地：勾选必须立刻有反馈。等接口回来再改会卡 250ms，手感很差 */
  target.done = next
  try {
    await setTodoDone(t.id, next)
  } catch (e) {
    /* 接口失败要回滚。不然界面显示已完成，数据库里还是未完成，
       下次进来又冒出来，用户会以为程序在骗他 */
    target.done = t.done
  }
}

/* ---------- 删除 ---------- */

function onLongPress(t) {
  uni.showModal({
    title: '删除待办',
    content: `「${t.title}」删掉就找不回来了，确定吗？`,
    confirmText: '删除',
    confirmColor: '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await delTodo(t.id)
        list.value = list.value.filter((x) => x.id !== t.id)
      } catch (e) {
        /* 已经 toast 过 */
      }
    }
  })
}

/* ---------- 新建 ---------- */

const sheetVisible = ref(false)
const draftTitle = ref('')
const draftDate = ref('')
const saving = ref(false)
const focusInput = ref(false)

const todayStr = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

function openSheet() {
  draftTitle.value = ''
  draftDate.value = ''
  sheetVisible.value = true
  /* 面板是 v-if 出来的，DOM 还没生成就设 focus 会失效，等一帧 */
  setTimeout(() => { focusInput.value = true }, 120)
}

function closeSheet() {
  sheetVisible.value = false
  focusInput.value = false
}

function onPickDate(e) {
  draftDate.value = e.detail.value
}

async function onSave() {
  if (saving.value) return

  const title = draftTitle.value.trim()
  if (!title) {
    uni.showToast({ title: '写点什么吧', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await addTodo({
      title,
      /* picker 只给到「日」，补成当天最后一刻，语义就是「这一天结束前做完」 */
      dueAt: draftDate.value ? `${draftDate.value} 23:59:59` : null
    })
    closeSheet()
    /* 重新拉一次：新加的那条该排在哪儿由后端说了算，前端别自己猜 */
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
  padding: 24rpx;
  box-sizing: border-box;
}

/* ---------- 顶部统计 ---------- */
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6rpx 8rpx 22rpx;
}

.bar-main {
  display: flex;
  align-items: baseline;
}

.bar-num {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 56rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1;
}

.bar-unit {
  font-size: 26rpx;
  color: #9A9186;
  margin-left: 10rpx;
}

.bar-over {
  font-size: 24rpx;
  color: #C5342B;
  margin-left: 16rpx;
  background: #FDEDEB;
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
}

.bar-add {
  display: flex;
  align-items: center;
  background: #C5342B;
  border-radius: 999rpx;
  padding: 12rpx 26rpx 12rpx 20rpx;
  box-shadow: 0 8rpx 20rpx rgba(197, 52, 43, 0.24);
}

.bar-add-pressed {
  opacity: 0.85;
  transform: scale(0.97);
}

.bar-add-plus {
  font-size: 30rpx;
  color: #FFFFFF;
  line-height: 1;
}

.bar-add-text {
  font-size: 26rpx;
  color: #FFFFFF;
  margin-left: 6rpx;
}

/* ---------- 列表卡片 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 32rpx;
  padding: 8rpx 0;
  box-shadow: 0 10rpx 30rpx rgba(160, 120, 80, 0.06);
  overflow: hidden;
}

.card-done {
  margin-top: 20rpx;
}

.row {
  display: flex;
  align-items: flex-start;
  padding: 26rpx 26rpx;
}

/* 除第一行外都加一条分隔线，用 border-top 而不是 margin，避免撑出双倍间距 */
.row + .row {
  border-top: 1rpx solid #F4EDE3;
}

.row-pressed {
  background: #FDFAF6;
}

.row-main {
  flex: 1;
  margin-left: 22rpx;
  overflow: hidden;
}

.row-title {
  font-size: 30rpx;
  color: #2B2622;
  line-height: 1.5;
  word-break: break-all;
}

/* 已完成：划线 + 降透明度，一眼能看出它不再需要处理 */
.row-done .row-title {
  color: #B4A99B;
  text-decoration: line-through;
}

.tags {
  display: flex;
  margin-top: 10rpx;
}

.tag {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  line-height: 1.5;
}

.tag-over {
  color: #C5342B;
  background: #FDEDEB;
}

.tag-soon {
  color: #9A7A1E;
  background: #FBF3DC;
}

.tag-normal {
  color: #7C8A79;
  background: #EFF4EE;
}

.tag-done {
  color: #B4A99B;
  background: #F4EEE6;
}

/* ---------- 勾选框 ---------- */
.check {
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  border: 2rpx solid #D8CFC2;
  box-sizing: border-box;
  flex-shrink: 0;
  margin-top: 6rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-on {
  background: #C5342B;
  border-color: #C5342B;
}

/* 对号：用左边框+下边框拼出一个 L，再逆时针转 45°，正好是 ✓ */
.check-mark {
  width: 14rpx;
  height: 8rpx;
  border-left: 3rpx solid #FFFFFF;
  border-bottom: 3rpx solid #FFFFFF;
  transform: rotate(-45deg);
  margin-top: -3rpx;
}

.check-ghost {
  background: #F4EEE6;
  border-color: #F4EEE6;
}

/* ---------- 骨架屏 ---------- */
.sk-line {
  height: 30rpx;
  border-radius: 8rpx;
  background: #F4EEE6;
  margin-top: 6rpx;
}

/* ---------- 空状态 ---------- */
.empty {
  padding: 80rpx 0 90rpx;
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

/* ---------- 已完成折叠区 ---------- */
.done-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 26rpx 26rpx;
}

.done-title {
  font-size: 26rpx;
  color: #9A9186;
}

.done-arrow {
  font-size: 32rpx;
  color: #C9BFB1;
  line-height: 1;
  transition: transform 0.2s;
}

/* 箭头默认朝右，展开后转 90° 朝下 */
.done-arrow-open {
  transform: rotate(90deg);
}

/* ---------- 底部提示 ---------- */
.hint {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: #C0B6A8;
  margin-top: 26rpx;
}

.bottom-space {
  height: 60rpx;
}

/* ---------- 新建面板 ---------- */
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
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
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
  margin-bottom: 22rpx;
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

.editor-input {
  width: 100%;
  min-height: 96rpx;
  font-size: 30rpx;
  color: #2B2622;
  line-height: 1.6;
  background: #FAF5EF;
  border-radius: 20rpx;
  padding: 22rpx 24rpx;
  box-sizing: border-box;
}

.editor-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding: 0 6rpx;
}

.editor-label {
  font-size: 26rpx;
  color: #9A9186;
}

.editor-date {
  display: flex;
  align-items: center;
}

.date-text {
  font-size: 26rpx;
  color: #2B2622;
  padding: 10rpx 0 10rpx 20rpx;
}

.date-empty {
  color: #B4A99B;
}

.date-clear {
  font-size: 24rpx;
  color: #C5342B;
  margin-left: 20rpx;
  padding: 10rpx 0;
}

.editor-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 34rpx;
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
