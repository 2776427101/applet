<template>
  <view class="page">
    <!-- ============ 顶部 ============ -->
    <!-- 不再写一遍「座位」大标题：原生导航栏已经有标题了，重复两次只是浪费一整行 -->
    <view class="head">
      <text class="head-info">{{ err ? '座位表没加载出来' : `${rows} 排 × ${cols} 列 · 已排 ${seatedCount} / ${students.length} 人` }}</text>
      <view v-if="dirty" class="dirty-dot">未保存</view>
    </view>

    <!-- 讲台：没有这条，老师分不清第一排在上面还是下面 -->
    <view class="podium">
      <text class="podium-text">讲 台</text>
    </view>

    <!-- ============ 座位网格 ============ -->
    <!-- 首次加载先给一句话，别让老师盯着一张 6×8 的空格子以为数据没了 -->
    <view v-if="loading" class="loading-box">
      <text class="loading-text">正在加载座位…</text>
    </view>

    <!-- 加载失败：这一页比别的页更要紧 —— 绝不能显示一张空座位表。
         老师会以为「座位被清了」，在上面重新排一遍再点保存，
         就把服务器上真实的座位表覆盖成空表了。这是会丢数据的 -->
    <view v-else-if="err" class="err-box">
      <load-error
        :message="err"
        hint="为了不把现有座位覆盖掉，加载失败时这里不显示空表，也不能保存"
        compact
        @retry="load"
      />
    </view>

    <view v-else class="grid" :style="gridStyle">
      <view
        v-for="cell in cells"
        :key="cell.key"
        class="seat"
        :class="seatClass(cell)"
        :style="cellStyle"
        @click="onTapSeat(cell)"
      >
        <text class="seat-name">{{ cell.stu ? cell.stu.studentName : '' }}</text>
      </view>
    </view>

    <!-- 图例 -->
    <view v-if="!loading && !err" class="legend">
      <view class="lg"><view class="lg-dot male"></view><text class="lg-text">男生</text></view>
      <view class="lg"><view class="lg-dot female"></view><text class="lg-text">女生</text></view>
      <view class="lg"><view class="lg-dot empty"></view><text class="lg-text">空位</text></view>
      <view class="lg"><view class="lg-dot picked"></view><text class="lg-text">已选中</text></view>
    </view>

    <!-- ============ 未排座 ============ -->
    <view v-if="unseated.length" class="unseated">
      <view class="un-head">
        <text class="un-title">还有 {{ unseated.length }} 人未排座</text>
        <text class="un-tip">点学生，再点空位</text>
      </view>
      <scroll-view class="chips" scroll-x :show-scrollbar="false">
        <view class="chips-inner">
          <view
            v-for="s in unseated"
            :key="s.studentId"
            class="chip"
            :class="{ 'chip-on': isPickedStudent(s.studentId) }"
            @click="onTapStudent(s)"
          >
            <text class="chip-text">{{ s.studentName }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 全排完时的提示，避免这块直接消失显得页面空 -->
    <view v-else-if="students.length" class="unseated un-all">
      <text class="un-title">全部 {{ students.length }} 人都排好了</text>
      <text class="un-tip">点座位选中，再点另一个座位就能换位</text>
    </view>

    <!-- ============ 选中操作条 ============ -->
    <view v-if="sel" class="actbar">
      <text class="actbar-text">{{ selText }}</text>
      <view class="actbar-btns">
        <view v-if="sel.type === 'seat'" class="act-btn" @click="removeSeat">
          <text class="act-btn-text">移出座位</text>
        </view>
        <view class="act-btn ghost" @click="clearSel">
          <text class="act-btn-text ghost">取消</text>
        </view>
      </view>
    </view>

    <!-- ============ 底部工具 ============ -->
    <!-- 加载失败时整条工具都不给：随机排座会按 0 个人排，
         保存会把空表写回去。宁可让老师点重试，也不能给一条会毁数据的路 -->
    <view v-if="!err" class="tools">
      <view class="tool" @click="openSize">
        <text class="tool-text">调整行列</text>
      </view>
      <view class="tool" @click="onShuffle">
        <text class="tool-text">随机排座</text>
      </view>
      <view class="tool primary" :class="{ off: !dirty || saving }" @click="onSave">
        <text class="tool-text primary">{{ saving ? '保存中…' : '保存' }}</text>
      </view>
    </view>

    <!-- ============ 调整行列面板 ============ -->
    <view v-if="sizeVisible" class="mask" @click="sizeVisible = false">
      <view class="sheet" @click.stop>
        <text class="sheet-title">调整行列</text>
        <text class="sheet-tip">缩小后超出范围的座位会回到「未排座」，不会丢人</text>

        <view class="size-row">
          <text class="size-label">排数</text>
          <view class="stepper">
            <view class="step" @click="stepSize('rows', -1)"><text class="step-text">−</text></view>
            <text class="step-val">{{ draftRows }}</text>
            <view class="step" @click="stepSize('rows', 1)"><text class="step-text">+</text></view>
          </view>
        </view>

        <view class="size-row">
          <text class="size-label">列数</text>
          <view class="stepper">
            <view class="step" @click="stepSize('cols', -1)"><text class="step-text">−</text></view>
            <text class="step-val">{{ draftCols }}</text>
            <view class="step" @click="stepSize('cols', 1)"><text class="step-text">+</text></view>
          </view>
        </view>

        <view class="sheet-btns">
          <view class="sheet-btn ghost" @click="sizeVisible = false"><text class="sheet-btn-text ghost">取消</text></view>
          <view class="sheet-btn" @click="applySize"><text class="sheet-btn-text">确定</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getSeatChart, saveSeatChart } from '@/api/seat'
import LoadError from '@/components/load-error/load-error.vue'

/* 行列上下限，和后端 MAX_SIZE 保持一致 */
const MIN_SIZE = 1
const MAX_SIZE = 20

const rows = ref(6)
const cols = ref(8)

/**
 * 座位网格，1-based 二维数组：grid[排][列]。
 * 0 号位空着不用 —— 后端契约里 row/col 就是从 1 开始的，
 * 前后端用同一套下标，省得每次转换时 ±1 写出 off-by-one。
 */
const grid = ref(makeGrid(6, 8))

/** 全班学生（在座 + 未排座），用来算「未排座」和「随机排座」 */
const students = ref([])

const loading = ref(true)
const saving = ref(false)
const dirty = ref(false)
const err = ref('') /* 加载失败的原因。空串 = 没失败 */

/** 当前选中：{ type:'seat', row, col } 或 { type:'student', studentId } */
const sel = ref(null)

function makeGrid(r, c) {
  const g = new Array(r + 1)
  for (let i = 1; i <= r; i++) {
    g[i] = new Array(c + 1).fill(null)
  }
  return g
}

/* ---------- 读 ---------- */

async function load() {
  /* 重试前清掉上次的错误，否则这次成功了错误块也不会消失 */
  err.value = ''
  try {
    /* silent：页面自己会画「加载失败 + 重试」 */
    const res = await getSeatChart({ silent: true })
    rows.value = res.rows || 6
    cols.value = res.cols || 8

    const g = makeGrid(rows.value, cols.value)
    const map = new Map()

    /* seats 是 rows × cols 个格子，空位也在里面，直接按 row/col 放进去 */
    for (const s of res.seats || []) {
      if (s.studentId == null) continue
      const stu = { studentId: s.studentId, studentName: s.studentName, gender: s.gender }
      g[s.row][s.col] = stu
      map.set(stu.studentId, stu)
    }
    for (const s of res.unseated || []) {
      if (!map.has(s.studentId)) {
        map.set(s.studentId, { studentId: s.studentId, studentName: s.studentName, gender: s.gender })
      }
    }

    grid.value = g
    students.value = [...map.values()]
    sel.value = null
    dirty.value = false
  } catch (e) {
    /* 记下错误，模板会用它把网格和底部工具整块换成错误块。
       不这么做的话，默认的 6×8 空网格会顶上来，看起来像「座位全被清了」 */
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* 有未保存改动时不重新拉，否则切个 tab 回来辛苦排的就没了 */
onShow(() => {
  if (!dirty.value) load()
})

/* ---------- 派生数据 ---------- */

/** 扁平化成 v-for 用的列表 */
const cells = computed(() => {
  const list = []
  for (let r = 1; r <= rows.value; r++) {
    for (let c = 1; c <= cols.value; c++) {
      list.push({ key: `${r}-${c}`, row: r, col: c, stu: grid.value[r][c] })
    }
  }
  return list
})

const seatedCount = computed(() => cells.value.filter((x) => x.stu).length)

/** 未排座 = 全班学生 - 当前坐在格子里的 */
const unseated = computed(() => {
  const seated = new Set()
  for (let r = 1; r <= rows.value; r++) {
    for (let c = 1; c <= cols.value; c++) {
      const s = grid.value[r][c]
      if (s) seated.add(s.studentId)
    }
  }
  return students.value.filter((s) => !seated.has(s.studentId))
})

const selText = computed(() => {
  if (!sel.value) return ''
  if (sel.value.type === 'student') {
    const s = students.value.find((x) => x.studentId === sel.value.studentId)
    return `已选中 ${s ? s.studentName : ''}，点一个空位放下`
  }
  const s = grid.value[sel.value.row][sel.value.col]
  return `已选中 ${s ? s.studentName : ''}（第 ${sel.value.row} 排第 ${sel.value.col} 列）`
})

/* ---------- 样式 ---------- */

/**
 * 格子尺寸按列数算。
 * 写死宽高的话，改成 5 列会空一大片、改成 12 列名字会挤成一团。
 * 750rpx 是小程序设计稿的标准宽度，这里按它推算每格占多少。
 */
const cellStyle = computed(() => {
  const gap = 6
  const padding = 24
  const w = (750 - padding * 2 - gap * (cols.value - 1)) / cols.value
  const h = Math.round(w * 1.18)
  const fs = Math.max(15, Math.min(24, Math.round(w * 0.26)))
  return {
    height: `${h}rpx`,
    fontSize: `${fs}rpx`
  }
})

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${cols.value}, 1fr)`,
  gap: '6rpx',
  padding: '0 24rpx'
}))

function seatClass(cell) {
  return {
    male: !!cell.stu && cell.stu.gender === 1,
    female: !!cell.stu && cell.stu.gender === 2,
    unknown: !!cell.stu && cell.stu.gender !== 1 && cell.stu.gender !== 2,
    empty: !cell.stu,
    /* 选了一个座位时，别的座位都是潜在落点，给它一点提示 */
    target: !cell.stu && !!sel.value,
    picked: isPickedSeat(cell)
  }
}

function isPickedSeat(cell) {
  return !!sel.value && sel.value.type === 'seat' && sel.value.row === cell.row && sel.value.col === cell.col
}

function isPickedStudent(id) {
  return !!sel.value && sel.value.type === 'student' && sel.value.studentId === id
}

/* ---------- 交互：点座位 ---------- */

function onTapSeat(cell) {
  /* 情况一：什么都没选 */
  if (!sel.value) {
    if (!cell.stu) {
      uni.showToast({ title: '先点一个学生', icon: 'none' })
      return
    }
    sel.value = { type: 'seat', row: cell.row, col: cell.col }
    return
  }

  /* 情况二：已经选中一个座位 */
  if (sel.value.type === 'seat') {
    /* 点自己 = 取消 */
    if (sel.value.row === cell.row && sel.value.col === cell.col) {
      sel.value = null
      return
    }
    /* 交换。目标空着就是移动，目标有人就是两人换位 ——
       两种情况用同一行代码，不需要 if 分开写 */
    const from = grid.value[sel.value.row][sel.value.col]
    grid.value[sel.value.row][sel.value.col] = cell.stu
    grid.value[cell.row][cell.col] = from
    dirty.value = true
    sel.value = null
    return
  }

  /* 情况三：选中了一个未排座的学生，点哪里就放哪里 */
  const stu = students.value.find((x) => x.studentId === sel.value.studentId)
  if (!stu) {
    sel.value = null
    return
  }
  /* 目标位置上原来的人不用管：他不在格子里了，未排座列表会自动把他收回去 */
  grid.value[cell.row][cell.col] = stu
  dirty.value = true
  sel.value = null
}

function onTapStudent(s) {
  if (isPickedStudent(s.studentId)) {
    sel.value = null
    return
  }
  sel.value = { type: 'student', studentId: s.studentId }
}

function clearSel() {
  sel.value = null
}

function removeSeat() {
  if (!sel.value || sel.value.type !== 'seat') return
  grid.value[sel.value.row][sel.value.col] = null
  dirty.value = true
  sel.value = null
}

/* ---------- 交互：随机排座 ---------- */

function onShuffle() {
  if (!students.value.length) {
    uni.showToast({ title: '班里还没有学生', icon: 'none' })
    return
  }
  uni.showModal({
    title: '随机排座',
    content: `会把全部 ${students.value.length} 人打乱重排，覆盖当前座位。`,
    confirmText: '开始',
    success: (res) => {
      if (res.confirm) doShuffle()
    }
  })
}

function doShuffle() {
  /* Fisher-Yates。不用 sort(() => Math.random() - 0.5)：
     那个算法的分布不均匀，有的排列出现概率是别人的好几倍 */
  const arr = [...students.value]
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const t = arr[i]
    arr[i] = arr[j]
    arr[j] = t
  }

  const g = makeGrid(rows.value, cols.value)
  let k = 0
  for (let r = 1; r <= rows.value && k < arr.length; r++) {
    for (let c = 1; c <= cols.value && k < arr.length; c++) {
      g[r][c] = arr[k++]
    }
  }
  grid.value = g
  dirty.value = true
  sel.value = null
  /* 座位比人多时不用提示；人比座位多时得说一声，否则老师以为漏了 */
  const rest = arr.length - k
  if (rest > 0) {
    uni.showToast({ title: `座位不够，还有 ${rest} 人没排上`, icon: 'none' })
  }
}

/* ---------- 交互：调整行列 ---------- */

const sizeVisible = ref(false)
const draftRows = ref(6)
const draftCols = ref(8)

function openSize() {
  draftRows.value = rows.value
  draftCols.value = cols.value
  sizeVisible.value = true
}

function stepSize(key, delta) {
  const target = key === 'rows' ? draftRows : draftCols
  const next = target.value + delta
  if (next < MIN_SIZE || next > MAX_SIZE) return
  target.value = next
}

function applySize() {
  const r = draftRows.value
  const c = draftCols.value

  /* 保留还在范围内的座位，超出的自然消失 ——
     他们的学生没被删，会回到「未排座」列表里 */
  const g = makeGrid(r, c)
  const keepR = Math.min(r, rows.value)
  const keepC = Math.min(c, cols.value)
  for (let i = 1; i <= keepR; i++) {
    for (let j = 1; j <= keepC; j++) {
      g[i][j] = grid.value[i][j]
    }
  }

  grid.value = g
  rows.value = r
  cols.value = c
  dirty.value = true
  sel.value = null
  sizeVisible.value = false
}

/* ---------- 交互：保存 ---------- */

function buildPayload() {
  const seats = []
  for (let r = 1; r <= rows.value; r++) {
    for (let c = 1; c <= cols.value; c++) {
      const s = grid.value[r][c]
      if (s) seats.push({ row: r, col: c, studentId: s.studentId })
    }
  }
  return { rows: rows.value, cols: cols.value, seats }
}

async function onSave() {
  if (!dirty.value || saving.value) return
  saving.value = true
  try {
    const res = await saveSeatChart(buildPayload())
    dirty.value = false
    uni.showToast({ title: `已保存 ${res.saved} 人`, icon: 'none' })
    /* 存完重新拉一次：以库里的为准，避免本地和服务端悄悄不一致 */
    await load()
  } catch (e) {
    /* request.js 已经 toast 过；dirty 保持 true，用户可以再点一次保存 */
  } finally {
    saving.value = false
  }
}

/* ---------- 离开提醒 ---------- */

/**
 * 有未保存改动时拦一下返回。
 * tabBar 页面不一定触发，所以页面顶部还有个「未保存」角标兜底 ——
 * 提醒是锦上添花，不能当成唯一保障。
 */
watch(dirty, (v) => {
  if (typeof wx === 'undefined') return
  try {
    if (v && wx.enableAlertBeforeUnload) {
      wx.enableAlertBeforeUnload({ message: '座位还没保存，确定离开吗？' })
    } else if (!v && wx.disableAlertBeforeUnload) {
      wx.disableAlertBeforeUnload()
    }
  } catch (e) {
    /* 部分基础库在 tabBar 页不支持，忽略即可 */
  }
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding-bottom: 200rpx;
  box-sizing: border-box;
}

/* ---------- 顶部 ---------- */
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx 14rpx;
}

.head-info {
  font-size: 24rpx;
  color: #7A6F63;
}

.dirty-dot {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 20rpx;
}

/* ---------- 讲台 ---------- */
.podium {
  margin: 0 24rpx 16rpx;
  height: 56rpx;
  border-radius: 16rpx;
  border: 1rpx dashed #D9CFC2;
  background: #FFFDFA;
  display: flex;
  align-items: center;
  justify-content: center;
}

.podium-text {
  font-size: 22rpx;
  color: #B4A99B;
  letter-spacing: 8rpx;
}

/* ---------- 网格 ---------- */
.loading-box {
  padding: 120rpx 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 加载失败：套一张白卡，和整页的卡片风格一致 */
.err-box {
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.loading-text {
  font-size: 24rpx;
  color: #B4A99B;
}

.grid {
  display: grid;
}

.seat {
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  box-sizing: border-box;
  transition: background 0.15s;
}

.seat.male {
  background: #EAF1F8;
  color: #2F6EA8;
}

.seat.female {
  background: #F9EAE8;
  color: #B0554E;
}

.seat.unknown {
  background: #F2F0EC;
  color: #6F675E;
}

.seat.empty {
  background: transparent;
  border: 1rpx dashed #DED4C7;
}

/* 有人被选中时，空位变成落点提示 */
.seat.target {
  border-color: #E9B7B2;
  background: #FEF8F7;
}

.seat.picked {
  outline: 3rpx solid #C5342B;
  outline-offset: -3rpx;
}

.seat-name {
  width: 100%;
  text-align: center;
  line-height: 1.15;
  letter-spacing: -0.5rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding: 0 2rpx;
}

/* ---------- 图例 ---------- */
.legend {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 18rpx 28rpx 0;
}

.lg {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.lg-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 5rpx;
}

.lg-dot.male { background: #EAF1F8; border: 1rpx solid #D6E3F0; }
.lg-dot.female { background: #F9EAE8; border: 1rpx solid #F0D9D5; }
.lg-dot.empty { background: transparent; border: 1rpx dashed #DED4C7; }
.lg-dot.picked { background: #fff; border: 2rpx solid #C5342B; }

.lg-text {
  font-size: 20rpx;
  color: #9A9186;
}

/* ---------- 未排座 ---------- */
.unseated {
  margin: 22rpx 24rpx 0;
  background: #FFFFFF;
  border-radius: 20rpx;
  padding: 18rpx 0 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(160, 120, 80, 0.05);
}

.un-all {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  padding: 18rpx 0;
}

.un-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 20rpx 12rpx;
}

.un-title {
  font-size: 24rpx;
  font-weight: 500;
  color: #2B2622;
}

.un-tip {
  font-size: 20rpx;
  color: #B4A99B;
}

.chips {
  width: 100%;
  white-space: nowrap;
}

.chips-inner {
  display: inline-flex;
  gap: 12rpx;
  padding: 0 20rpx;
}

.chip {
  flex-shrink: 0;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #F6F1EA;
}

.chip-on {
  background: #FDEDEB;
  outline: 2rpx solid #C5342B;
  outline-offset: -2rpx;
}

.chip-text {
  font-size: 22rpx;
  color: #5F5E5A;
}

.chip-on .chip-text {
  color: #C5342B;
  font-weight: 500;
}

/* ---------- 选中操作条 ---------- */
.actbar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: calc(var(--window-bottom, 0px) + 132rpx);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  padding: 16rpx 20rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.14);
}

.actbar-text {
  flex: 1;
  font-size: 22rpx;
  color: #5F5E5A;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.actbar-btns {
  display: flex;
  gap: 10rpx;
  flex-shrink: 0;
}

.act-btn {
  padding: 10rpx 20rpx;
  border-radius: 12rpx;
  background: #FDEDEB;
}

.act-btn.ghost {
  background: #F4F0EA;
}

.act-btn-text {
  font-size: 22rpx;
  color: #C5342B;
}

.act-btn-text.ghost {
  color: #7A6F63;
}

/* ---------- 底部工具 ---------- */
.tools {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--window-bottom, 0px);
  display: flex;
  gap: 12rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #F7EFE4;
}

.tool {
  flex: 1;
  height: 84rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.06);
}

.tool.primary {
  background: linear-gradient(135deg, #E8594F, #C5342B);
  box-shadow: 0 8rpx 20rpx rgba(197, 52, 43, 0.24);
}

/* 没改动时按钮变浅，一眼看出「现在按了也没用」 */
.tool.primary.off {
  background: #E3D8CB;
  box-shadow: none;
}

.tool-text {
  font-size: 26rpx;
  color: #5F5E5A;
}

.tool-text.primary {
  color: #FFFFFF;
  font-weight: 500;
}

/* ---------- 调整行列面板 ---------- */
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  /* H5 的 tabBar 是 DOM，会盖住 bottom:0 的弹层；小程序里 --window-bottom 是 0，等于贴底 */
  bottom: var(--window-bottom, 0px);
  background: rgba(43, 38, 34, 0.42);
  display: flex;
  align-items: flex-end;
  z-index: 20;
}

.sheet {
  width: 100%;
  background: #FFFDFA;
  border-radius: 28rpx 28rpx 0 0;
  padding: 32rpx 28rpx calc(28rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.sheet-tip {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #9A9186;
  line-height: 1.5;
}

.size-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 26rpx;
}

.size-label {
  font-size: 26rpx;
  color: #5F5E5A;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.step {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: #F4F0EA;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-text {
  font-size: 32rpx;
  color: #5F5E5A;
  line-height: 1;
}

.step-val {
  min-width: 60rpx;
  text-align: center;
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
}

.sheet-btns {
  display: flex;
  gap: 14rpx;
  margin-top: 36rpx;
}

.sheet-btn {
  flex: 1;
  height: 84rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  display: flex;
  align-items: center;
  justify-content: center;
}

.sheet-btn.ghost {
  background: #F4F0EA;
}

.sheet-btn-text {
  font-size: 28rpx;
  color: #FFFFFF;
  font-weight: 500;
}

.sheet-btn-text.ghost {
  color: #7A6F63;
}
</style>
