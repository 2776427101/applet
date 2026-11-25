<template>
  <view class="page">
    <!-- 顶部：三个分段 + 申请按钮。
         老师端这里是「待审批 / 已处理」，学生端反过来按"我的申请走到哪一步"分 -->
    <view class="head">
      <view class="seg">
        <view
          class="seg-item"
          :class="{ 'seg-on': tab === 'pending' }"
          @click="tab = 'pending'"
        >
          <text class="seg-text" :class="{ 'seg-text-on': tab === 'pending' }">待审批</text>
          <text class="seg-badge" v-if="counts.pending">{{ counts.pending }}</text>
        </view>
        <view
          class="seg-item"
          :class="{ 'seg-on': tab === 'passed' }"
          @click="tab = 'passed'"
        >
          <text class="seg-text" :class="{ 'seg-text-on': tab === 'passed' }">已通过</text>
        </view>
        <view
          class="seg-item"
          :class="{ 'seg-on': tab === 'closed' }"
          @click="tab = 'closed'"
        >
          <text class="seg-text" :class="{ 'seg-text-on': tab === 'closed' }">已结束</text>
        </view>
      </view>

      <view class="add" hover-class="add-pressed" @click="goApply">
        <text class="add-plus">＋</text>
        <text class="add-text">申请</text>
      </view>
    </view>

    <!-- 骨架屏 -->
    <block v-if="loading">
      <view class="card" v-for="n in 3" :key="'sk' + n">
        <view class="sk-line" :style="{ width: 40 + n * 5 + '%' }"></view>
        <view class="sk-line sk-sm" style="width: 62%"></view>
      </view>
    </block>

    <!-- 加载失败：必须和「没有记录」分开。
         混在一起的话，网络断了会显示「还没请过假」，学生以为记录丢了 -->
    <view v-else-if="err && !list.length" class="card">
      <load-error
        :message="err"
        hint="请假记录还在服务器上，网络恢复后点重试就行"
        compact
        @retry="load"
      />
    </view>

    <!-- 空状态：三个 tab 三句话，不要一句「暂无数据」敷衍 -->
    <view class="empty" v-else-if="!shown.length">
      <view class="empty-circle">
        <text class="empty-tick">{{ tab === 'pending' ? '✓' : '·' }}</text>
      </view>
      <text class="empty-title">{{ emptyTitle }}</text>
      <text class="empty-sub">{{ emptySub }}</text>
    </view>

    <!-- 卡片列表 -->
    <block v-else>
      <view
        class="card"
        :class="{ 'card-faded': item.status !== 0 }"
        v-for="item in shown"
        :key="item.id"
        hover-class="card-pressed"
        @click="openDetail(item)"
      >
        <view class="card-top">
          <view class="card-left">
            <text class="type" :class="'type-' + item.type">{{ typeLabel(item.type) }}</text>
            <text class="dur" v-if="duration(item.startAt, item.endAt)">
              {{ duration(item.startAt, item.endAt) }}
            </text>
          </view>
          <text class="status" :class="'st-' + item.status">{{ statusLabel(item.status) }}</text>
        </view>

        <view class="time">
          <text class="time-text">{{ fmtTime(item.startAt) }} → {{ fmtTime(item.endAt) }}</text>
        </view>

        <view class="reason" v-if="item.reason">
          <text class="reason-text">{{ item.reason }}</text>
        </view>

        <!-- 驳回理由是最该被看见的一句话：学生点进来就是想知道"为什么"。
             所以它单独一块、带底色，不埋在灰色小字里 -->
        <view class="remark" v-if="item.status === 2 && item.auditRemark">
          <text class="remark-label">班主任说</text>
          <text class="remark-text">{{ item.auditRemark }}</text>
        </view>

        <!-- 操作按钮：只有能操作时才出现。
             能不能撤/能不能销由后端给，前端不自己判断 status -->
        <view class="actions" v-if="item.canCancel || item.canReport">
          <view
            v-if="item.canReport"
            class="btn btn-ghost"
            hover-class="btn-pressed"
            @click.stop="onReport(item)"
          >
            <text class="btn-ghost-text">销假</text>
          </view>
          <view
            v-if="item.canCancel"
            class="btn btn-ghost"
            hover-class="btn-pressed"
            @click.stop="onCancel(item)"
          >
            <text class="btn-ghost-text">撤销申请</text>
          </view>
        </view>
      </view>
    </block>

    <view class="bottom-space"></view>

    <student-tab-bar current="leave" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { fetchMyLeaves, cancelLeave, reportLeave } from '@/api/student-data'
import { fmtTime, duration, typeLabel, statusLabel } from '@/utils/leave-fmt'
import LoadError from '@/components/load-error/load-error.vue'
import StudentTabBar from '@/components/student-tab-bar/student-tab-bar.vue'

const list = ref([])
const loading = ref(true)
const tab = ref('pending')
const err = ref('')

/* 后端已按提交时间倒序排好，前端只负责分堆。
   分堆规则写在一处，tab 切换和计数都用它，不会出现
   「待审批显示 2 条，点进去只有 1 条」 */
const counts = computed(() => {
  const c = { pending: 0, passed: 0, closed: 0 }
  for (const i of list.value) {
    if (i.status === 0) c.pending++
    else if (i.status === 1) c.passed++
    else c.closed++
  }
  return c
})

const shown = computed(() => {
  if (tab.value === 'pending') return list.value.filter((i) => i.status === 0)
  if (tab.value === 'passed') return list.value.filter((i) => i.status === 1)
  /* 已结束 = 被驳回 + 已销假。都"走完了"，不需要再分两个 tab */
  return list.value.filter((i) => i.status === 2 || i.status === 3)
})

const emptyTitle = computed(() => {
  if (tab.value === 'pending') return '没有待审批的请假'
  if (tab.value === 'passed') return '还没有通过的请假'
  return '还没有已结束的记录'
})

const emptySub = computed(() => {
  if (tab.value === 'pending') return '提交后班主任审批前会出现在这里'
  if (tab.value === 'passed') return '班主任通过后会出现在这里'
  return '被驳回或已销假的会出现在这里'
})

async function load() {
  err.value = ''
  try {
    const res = await fetchMyLeaves()
    list.value = Array.isArray(res) ? res : []
  } catch (e) {
    /* 手里有旧数据就继续显示，别整块盖掉 */
    if (!list.value.length) {
      err.value = (e && e.message) || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

/* onShow：从申请页/详情页返回时要看到最新状态 */
onShow(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

function goApply() {
  uni.navigateTo({ url: '/pages/student/leave/apply' })
}

function openDetail(item) {
  /* 详情页只带 id，不带学生 id —— 学生端没有"看别人"这个入口 */
  uni.navigateTo({ url: `/pages/student/leave/detail?id=${item.id}` })
}

/* ---------- 撤销 / 销假 ---------- */

function onCancel(item) {
  uni.showModal({
    title: '撤销这条申请？',
    content: '班主任还没审批，撤销后这条记录会被删掉，需要重新提交。',
    confirmText: '撤销',
    confirmColor: '#C5342B',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await cancelLeave(item.id)
        uni.showToast({ title: '已撤销', icon: 'none' })
        await load()
      } catch (e) {
        /* request.js 已经 toast 过 */
      }
    }
  })
}

function onReport(item) {
  uni.showModal({
    title: '销假',
    content: '人已经回校了就点确认，班主任会看到这条请假已结束。',
    confirmText: '确认销假',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await reportLeave(item.id)
        uni.showToast({ title: '已销假', icon: 'none' })
        await load()
      } catch (e) {
        /* 同上 */
      }
    }
  })
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
  padding: 12rpx 22rpx;
  border-radius: 999rpx;
}

.seg-on {
  background: #FFFFFF;
  box-shadow: 0 4rpx 12rpx rgba(160, 120, 80, 0.1);
}

.seg-text {
  font-size: 25rpx;
  color: #8A8178;
}

.seg-text-on {
  color: #2B2622;
  font-weight: 600;
}

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
  flex-shrink: 0;
  margin-left: 12rpx;
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

/* ---------- 骨架屏 ---------- */
.sk-line {
  height: 26rpx;
  border-radius: 8rpx;
  background: #F4ECE1;
  margin-bottom: 14rpx;
}

.sk-sm {
  height: 20rpx;
}

/* ---------- 卡片 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 28rpx 26rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

/* 已结束的降透明度，不抢"待审批"的注意力 */
.card-faded {
  opacity: 0.78;
}

.card-pressed {
  background: #FDFAF6;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-left {
  display: flex;
  align-items: center;
  min-width: 0;
}

.type {
  font-size: 22rpx;
  padding: 6rpx 16rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

/* 三种类型三种底色，和老师端请假页完全一致：
   同一个概念在两个端不能有两种颜色 */
.type-1 {
  background: #FDEDEB;
  color: #C5342B;
}

.type-2 {
  background: #FBF3DC;
  color: #8A6A2E;
}

.type-3 {
  background: #EEF1F4;
  color: #6B7280;
}

.dur {
  margin-left: 12rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.status {
  font-size: 24rpx;
  flex-shrink: 0;
}

.st-0 {
  color: #C9A227;
}

.st-1 {
  color: #2E7D5B;
}

.st-2 {
  color: #C5342B;
}

.st-3 {
  color: #8A8178;
}

.time {
  margin-top: 18rpx;
}

.time-text {
  font-size: 27rpx;
  color: #2B2622;
  font-variant-numeric: tabular-nums;
}

.reason {
  margin-top: 14rpx;
  padding: 16rpx 18rpx;
  background: #FBF7F1;
  border-radius: 16rpx;
}

.reason-text {
  font-size: 24rpx;
  color: #6B6259;
  line-height: 1.5;
}

/* 驳回理由：单独一块，颜色比正文重 */
.remark {
  margin-top: 14rpx;
  padding: 16rpx 18rpx;
  background: #FDEDEB;
  border-radius: 16rpx;
  display: flex;
  flex-direction: column;
}

.remark-label {
  font-size: 21rpx;
  color: #C5342B;
}

.remark-text {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #8C3B34;
  line-height: 1.5;
}

/* ---------- 操作 ---------- */
.actions {
  margin-top: 20rpx;
  display: flex;
  justify-content: flex-end;
}

.btn {
  min-width: 150rpx;
  height: 64rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 14rpx;
}

.btn-ghost {
  border: 2rpx solid #E7DBC9;
  background: #FFFFFF;
}

.btn-ghost-text {
  font-size: 25rpx;
  color: #6B6259;
}

.btn-pressed {
  background: #FAF5EF;
}

/* ---------- 空状态 ---------- */
.empty {
  padding: 120rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-circle {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.06);
}

.empty-tick {
  font-size: 44rpx;
  color: #C9BEB1;
}

.empty-title {
  margin-top: 28rpx;
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
}

.empty-sub {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: #9A9186;
}

/* 底栏 100rpx + 安全区 + 20rpx 余量。
   写死高度的话，刘海屏上最后一行会被底栏盖住 */
.bottom-space {
  height: calc(120rpx + env(safe-area-inset-bottom));
}
</style>
