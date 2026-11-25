<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="err" class="state">
      <text class="state-title">{{ err }}</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else>
      <view v-if="list.length" class="card">
        <view
          class="row"
          v-for="(n, i) in list"
          :key="i"
          hover-class="row-pressed"
          @click="open(n)"
        >
          <view class="ic" :class="tone(n)">{{ icon(n) }}</view>
          <view class="main">
            <text class="title">{{ n.title }}</text>
            <text class="desc">{{ n.desc }}</text>
          </view>
          <view class="side">
            <text class="time">{{ n.atText }}</text>
            <text class="chev">›</text>
          </view>
        </view>
      </view>

      <view v-else class="empty">
        <text class="empty-icon">🔔</text>
        <text class="empty-title">还没有通知</text>
        <text class="empty-tip">请假有结果、班主任发消息时会出现在这里</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchMyNotices } from '@/api/student-data'

const list = ref([])
const loading = ref(true)
const err = ref('')

async function load() {
  err.value = ''
  try {
    const res = (await fetchMyNotices(50)) || {}
    list.value = res.list || []
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* onShow：从请假详情销假回来时，这里那条通知的文案可能已经变了 */
onShow(load)

/* 图标和老师端通知页同一套语义：📝 是请假，✉ 是私信 */
function icon(n) {
  if (n.kind === 'leave') return '📝'
  if (n.kind === 'message') return '✉'
  return '🔔'
}

/* 底色按结果分：通过绿、驳回红、销假灰。
   老师端是「待处理」用红，学生端反过来 —— 红在这里表示"没批" */
function tone(n) {
  if (n.kind === 'message') return 'k-message'
  if (n.status === 1) return 'k-pass'
  if (n.status === 2) return 'k-reject'
  return 'k-done'
}

function open(n) {
  if (n.kind === 'leave' && n.leaveId) {
    uni.navigateTo({ url: `/pages/student/leave/detail?id=${n.leaveId}` })
    return
  }
  if (n.kind === 'message') {
    uni.navigateTo({ url: '/pages/student/message' })
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 60rpx;
  box-sizing: border-box;
}

.state {
  padding: 160rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.state-text {
  font-size: 26rpx;
  color: #9A9186;
}

.state-title {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
  text-align: center;
}

.state-btn {
  margin-top: 36rpx;
  padding: 14rpx 48rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  font-size: 24rpx;
  color: #C5342B;
}

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 4rpx 24rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-top: 1rpx solid #F5EDE2;
}

/* 第一行不要上边框：卡片有圆角，顶边会切出一道硬线 */
.row:first-child {
  border-top: none;
}

.row-pressed {
  background: #FDFAF6;
}

.ic {
  width: 68rpx;
  height: 68rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  flex-shrink: 0;
}

.k-pass {
  background: #EDF6EE;
}

.k-reject {
  background: #FDEDEB;
}

.k-done {
  background: #F1EEE9;
}

.k-message {
  background: #FBF3DC;
}

.main {
  flex: 1;
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.title {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
  line-height: 1.35;
}

.desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9A9186;
  line-height: 1.4;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.side {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-left: 14rpx;
}

.time {
  font-size: 21rpx;
  color: #B4A99B;
}

.chev {
  font-size: 30rpx;
  color: #CFC5B8;
  margin-left: 6rpx;
  line-height: 1;
}

.empty {
  padding: 140rpx 0 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 60rpx;
  opacity: 0.5;
}

.empty-title {
  margin-top: 22rpx;
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
}

.empty-tip {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #9A9186;
}
</style>