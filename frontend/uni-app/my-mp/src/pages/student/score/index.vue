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
      <!-- 汇总：只有两次以上考试才值得汇总。
           只考过一次的时候，上面这张卡和下面那张内容完全重复 -->
      <view class="summary" v-if="summary.examCount >= 2">
        <view class="sum-cell">
          <text class="sum-num">{{ summary.examCount }}</text>
          <text class="sum-label">已出成绩</text>
        </view>
        <view class="sum-divider"></view>
        <view class="sum-cell">
          <text class="sum-num">{{ summary.bestRank || '—' }}</text>
          <text class="sum-label">最好排名</text>
        </view>
      </view>

      <view v-if="!list.length" class="empty">
        <text class="empty-icon">📊</text>
        <text class="empty-title">还没有成绩</text>
        <text class="empty-tip">老师录入后会自动出现在这里</text>
      </view>

      <view v-else>
        <view class="card" v-for="e in list" :key="e.examId">
          <view class="card-head">
            <view class="head-main">
              <text class="exam-name">{{ e.name }}</text>
              <text class="exam-date">{{ e.examDate || '—' }}</text>
            </view>
            <!-- 排名只在有排名时显示。显示「第 0 名」比不显示更糟 -->
            <view class="rank" v-if="e.rank">
              <text class="rank-num">{{ e.rank }}</text>
              <text class="rank-label">/ {{ e.classSize }} 人</text>
            </view>
            <text v-else class="rank-none">暂无排名</text>
          </view>

          <!-- 总分条：一眼看出"离满分差多少"。
               没有总分的考试（一科都没录）不画条，画了就是 0 -->
          <view class="total" v-if="e.total !== null && e.total !== undefined">
            <view class="total-row">
              <text class="total-label">总分</text>
              <text class="total-value">
                {{ num(e.total) }}<text class="total-full"> / {{ num(e.fullTotal) }}</text>
              </text>
            </view>
            <view class="bar">
              <view class="bar-fill" :style="{ width: percent(e) + '%' }"></view>
            </view>
            <text class="avg" v-if="e.classAvg !== null">班级平均 {{ num(e.classAvg) }}</text>
          </view>

          <!-- 各科：一行一科，右边是分数 / 满分。
               顺序完全按老师端表头（exam.subjects），不自己排序 -->
          <view class="subjects">
            <view class="sub-row" v-for="s in e.subjects" :key="s">
              <text class="sub-name">{{ s }}</text>
              <view class="sub-right">
                <text
                  class="sub-score"
                  :class="{ 'sub-missing': e.scores[s] === null || e.scores[s] === undefined }"
                >
                  {{ e.scores[s] === null || e.scores[s] === undefined ? '缺考' : num(e.scores[s]) }}
                </text>
                <text class="sub-full">/ {{ num(e.fullScore[s]) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchMyScore } from '@/api/student-data'

const list = ref([])
const summary = ref({ examCount: 0, bestRank: null })
const loading = ref(true)
const err = ref('')

async function load() {
  err.value = ''
  try {
    const res = (await fetchMyScore()) || {}
    list.value = res.list || []
    summary.value = res.summary || { examCount: 0, bestRank: null }
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* onShow：老师刚录完成绩，学生从别的页面切回来应该看到新的 */
onShow(load)

/* 分数保留一位小数，整数就不显示 .0。
   150 比 150.0 干净，而 88.5 又不能被截成 88 */
function num(v) {
  if (v === null || v === undefined) return '—'
  const n = Number(v)
  if (isNaN(n)) return '—'
  return Number.isInteger(n) ? String(n) : String(Math.round(n * 10) / 10)
}

/* 进度条百分比。分母是 0 时返回 0，否则会算出 Infinity，width 变成非法值 */
function percent(e) {
  const f = Number(e.fullTotal)
  const t = Number(e.total)
  if (!f || isNaN(f) || isNaN(t)) return 0
  return Math.max(0, Math.min(100, Math.round((t / f) * 100)))
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  /* 成绩页是首页「常用」navigateTo 进来的下一级页面，没有底栏，
     底部留 40rpx 就够，不需要给底栏让位 */
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

/* ---------- 汇总 ---------- */
.summary {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 30rpx 0;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.sum-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sum-num {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 52rpx;
  font-weight: 600;
  color: #C5342B;
  line-height: 1.1;
}

.sum-label {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.sum-divider {
  width: 1rpx;
  height: 56rpx;
  background: #F0E6D8;
}

/* ---------- 考试卡 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 26rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.head-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.exam-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.exam-date {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #9A9186;
}

/* 排名徽章：数字大、单位小，扫一眼就能读 */
.rank {
  flex-shrink: 0;
  margin-left: 16rpx;
  display: flex;
  align-items: baseline;
  background: #FDEDEB;
  border-radius: 16rpx;
  padding: 10rpx 16rpx;
}

.rank-num {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 38rpx;
  font-weight: 600;
  color: #C5342B;
  line-height: 1;
}

.rank-label {
  margin-left: 4rpx;
  font-size: 20rpx;
  color: #C07A72;
}

.rank-none {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

/* ---------- 总分 ---------- */
.total {
  margin-top: 22rpx;
  padding: 20rpx;
  background: #FBF7F1;
  border-radius: 20rpx;
}

.total-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.total-label {
  font-size: 23rpx;
  color: #9A9186;
}

.total-value {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 40rpx;
  font-weight: 600;
  color: #2B2622;
  font-variant-numeric: tabular-nums;
}

.total-full {
  font-size: 22rpx;
  font-weight: 400;
  color: #9A9186;
}

.bar {
  margin-top: 14rpx;
  height: 10rpx;
  border-radius: 999rpx;
  background: #EFE5D6;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #E8594F 0%, #C5342B 100%);
}

.avg {
  display: block;
  margin-top: 12rpx;
  font-size: 21rpx;
  color: #9A9186;
}

/* ---------- 各科 ---------- */
.subjects {
  margin-top: 8rpx;
}

.sub-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-top: 1rpx solid #F8F2EA;
}

.sub-name {
  font-size: 26rpx;
  color: #2B2622;
}

.sub-right {
  display: flex;
  align-items: baseline;
}

.sub-score {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

/* 缺考：灰字，和 0 分明确区分 —— 0 分是考了没得分，缺考是没考 */
.sub-missing {
  color: #C4B9AB;
  font-weight: 400;
}

.sub-full {
  margin-left: 6rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

/* ---------- 空态 ---------- */
.empty {
  padding: 140rpx 40rpx;
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