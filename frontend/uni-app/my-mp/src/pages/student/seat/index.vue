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
      <view v-if="!seat.seated" class="empty">
        <text class="empty-icon">🪑</text>
        <text class="empty-title">还没排座</text>
        <text class="empty-tip">班主任排好座位后会出现在这里</text>
      </view>

      <template v-else>
        <!-- 主卡：座位号大字。
             学生打开这一页只想知道"我坐哪"，所以字号给到最大，
             其他信息（教室尺寸）退到副标题 -->
        <view class="hero">
          <text class="hero-label">我的座位</text>
          <view class="hero-main">
            <text class="hero-num">{{ seat.rowNo }}</text>
            <text class="hero-unit">排</text>
            <text class="hero-num hero-num-2">{{ seat.colNo }}</text>
            <text class="hero-unit">列</text>
          </view>
          <text class="hero-sub">教室共 {{ seat.rows }} 排 {{ seat.cols }} 列</text>
        </view>

        <!-- 位置示意：只画格子，不画人。
             整张座位图会把全班每个人坐哪都发到每个学生手机上，
             那是老师的工作台，不是学生的信息 -->
        <view class="card">
          <text class="card-title">位置示意</text>
          <view class="grid">
            <view class="grid-row" v-for="r in seat.rows" :key="'r' + r">
              <view
                class="cell"
                :class="{ 'cell-mine': r === seat.rowNo && c === seat.colNo }"
                v-for="c in seat.cols"
                :key="'c' + c"
              >
                <text class="cell-text" v-if="r === seat.rowNo && c === seat.colNo">我</text>
              </view>
            </view>
          </view>
        </view>
      </template>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchMySeat } from '@/api/student-data'

const seat = ref({ seated: false })
const loading = ref(true)
const err = ref('')

async function load() {
  err.value = ''
  try {
    seat.value = (await fetchMySeat()) || { seated: false }
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

onShow(load)
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  /* 座位页是首页「常用」navigateTo 进来的下一级页面，没有底栏 */
  padding: 20rpx 24rpx 40rpx;
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

/* ---------- 主卡 ---------- */
.hero {
  background: linear-gradient(135deg, #E8594F 0%, #C5342B 100%);
  border-radius: 32rpx;
  padding: 40rpx 32rpx 34rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 12rpx 28rpx rgba(197, 52, 43, 0.2);
}

.hero-label {
  font-size: 23rpx;
  color: rgba(255, 255, 255, 0.85);
}

.hero-main {
  margin-top: 18rpx;
  display: flex;
  align-items: baseline;
}

/* 数字用衬线体 + 大字号：这一页的核心信息就这一个 */
.hero-num {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 96rpx;
  font-weight: 600;
  color: #FFFFFF;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

/* 第二数字（列）和第一个之间留大缝，否则「3排2列」会读成一串 */
.hero-num-2 {
  margin-left: 36rpx;
}

.hero-unit {
  margin-left: 8rpx;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.9);
}

.hero-sub {
  margin-top: 22rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.82);
}

/* ---------- 示意图 ---------- */
.card {
  margin-top: 20rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 28rpx 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.card-title {
  display: block;
  font-size: 26rpx;
  color: #2B2622;
  font-weight: 600;
  margin-bottom: 22rpx;
}

.grid {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.grid-row {
  display: flex;
  justify-content: center;
}

/* 格子用固定 44rpx + 8rpx 间距。
   不用 flex:1：列数一多，格子会被压成细条，反而看不出形状 */
.cell {
  width: 44rpx;
  height: 44rpx;
  border-radius: 10rpx;
  background: #F4ECE1;
  margin: 4rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 我的位置：红色实心 + 白字，和上面那张红卡是同一个语义 */
.cell-mine {
  background: #C5342B;
  box-shadow: 0 4rpx 12rpx rgba(197, 52, 43, 0.28);
}

.cell-text {
  font-size: 20rpx;
  color: #FFFFFF;
}

/* ---------- 空态 ---------- */
.empty {
  padding: 160rpx 40rpx;
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
