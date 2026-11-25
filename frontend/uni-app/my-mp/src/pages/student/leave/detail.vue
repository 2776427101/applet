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
      <!-- 状态横幅：整页最上面，一眼看到"批没批"。
           颜色和列表里那个状态文字是同一套，两处不能有第二种绿 -->
      <view class="banner" :class="'banner-' + item.status">
        <text class="banner-title">{{ statusLabel(item.status) }}</text>
        <text class="banner-sub">{{ bannerSub }}</text>
      </view>

      <view class="card">
        <view class="row">
          <text class="row-label">类型</text>
          <text class="row-value">{{ typeLabel(item.type) }}</text>
        </view>
        <view class="row">
          <text class="row-label">开始</text>
          <text class="row-value">{{ fmtDay(item.startAt) }}</text>
        </view>
        <view class="row">
          <text class="row-label">结束</text>
          <text class="row-value">{{ fmtDay(item.endAt) }}</text>
        </view>
        <view class="row">
          <text class="row-label">时长</text>
          <text class="row-value">{{ duration(item.startAt, item.endAt) || '—' }}</text>
        </view>
        <view class="row row-last">
          <text class="row-label">提交</text>
          <text class="row-value">{{ fmtDay(item.createdAt) }}</text>
        </view>
      </view>

      <view class="card card-block">
        <text class="block-title">请假原因</text>
        <text class="block-text">{{ item.reason || '没填' }}</text>
      </view>

      <!-- 审批备注：只在有内容时出现。
           通过时老师通常不写，留一个空块反而让人以为"这里应该有东西" -->
      <view class="card card-block" v-if="item.auditRemark">
        <text class="block-title">班主任备注</text>
        <text class="block-text">{{ item.auditRemark }}</text>
      </view>

      <!-- 时间线：待审批 → 已通过 → 已销假，把"现在到哪一步"画出来。
           被驳回是另一条分支，不画进度条，只说明结果 -->
      <view class="card card-block" v-if="item.status !== 2">
        <text class="block-title">进度</text>
        <view class="steps">
          <view class="step" v-for="(s, i) in steps" :key="s.key">
            <view class="dot" :class="{ 'dot-on': s.done, 'dot-now': s.now }"></view>
            <view class="step-main">
              <text class="step-title" :class="{ 'step-title-on': s.done }">{{ s.title }}</text>
              <text class="step-sub">{{ s.sub }}</text>
            </view>
            <view class="line" v-if="i < steps.length - 1" :class="{ 'line-on': s.done }"></view>
          </view>
        </view>
      </view>

      <!-- 操作：能不能撤/能不能销全看后端给的布尔值。
           按钮点了会二次确认，撤销是不可逆的 -->
      <view class="actions" v-if="item.canCancel || item.canReport">
        <view
          v-if="item.canReport"
          class="btn btn-ghost"
          hover-class="btn-pressed"
          @click="onReport"
        >
          <text class="btn-ghost-text">销假</text>
        </view>
        <view
          v-if="item.canCancel"
          class="btn btn-danger"
          hover-class="btn-pressed"
          @click="onCancel"
        >
          <text class="btn-danger-text">撤销申请</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchLeaveDetail, cancelLeave, reportLeave } from '@/api/student-data'
import { fmtDay, duration, typeLabel, statusLabel } from '@/utils/leave-fmt'

const id = ref(0)
const item = ref({})
const loading = ref(true)
const err = ref('')

onLoad((q) => {
  id.value = Number((q && q.id) || 0)
  load()
})

async function load() {
  loading.value = true
  err.value = ''
  if (!id.value) {
    err.value = '没找到这条请假记录'
    loading.value = false
    return
  }
  try {
    item.value = (await fetchLeaveDetail(id.value)) || {}
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* 横幅副标题：每个状态一句话，说清"接下来该干什么" */
const bannerSub = computed(() => {
  switch (item.value.status) {
    case 0:
      return '班主任还没处理，着急可以直接私信他'
    case 1:
      return '人回校了记得点下面的「销假」'
    case 2:
      return '这次没批，看看备注里怎么说的'
    case 3:
      return '这条请假已经结束了'
    default:
      return ''
  }
})

/* 进度条三步。被驳回时不走这条线，所以这里只管 0/1/3 */
const steps = computed(() => {
  const st = item.value.status
  return [
    { key: 'submit', title: '已提交', sub: fmtDay(item.value.createdAt), done: true, now: st === 0 },
    { key: 'audit', title: '班主任审批', sub: st >= 1 ? '已通过' : '等待中', done: st >= 1, now: st === 1 },
    { key: 'report', title: '销假', sub: st === 3 ? '已销假' : '回校后自己点', done: st === 3, now: false }
  ]
})

function onCancel() {
  uni.showModal({
    title: '撤销这条申请？',
    content: '撤销后记录会被删掉，需要重新提交。',
    confirmText: '撤销',
    confirmColor: '#C5342B',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await cancelLeave(id.value)
        uni.showToast({ title: '已撤销', icon: 'none' })
        /* 回列表而不是留在详情页：记录已经删了，留在这里下次刷新就是错误块 */
        setTimeout(() => uni.navigateBack(), 600)
      } catch (e) {
        /* 已 toast */
      }
    }
  })
}

function onReport() {
  uni.showModal({
    title: '销假',
    content: '人已经回校了就点确认。',
    confirmText: '确认销假',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await reportLeave(id.value)
        uni.showToast({ title: '已销假', icon: 'none' })
        await load()
      } catch (e) {
        /* 已 toast */
      }
    }
  })
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

/* ---------- 状态横幅 ---------- */
.banner {
  border-radius: 28rpx;
  padding: 32rpx 28rpx;
  display: flex;
  flex-direction: column;
  margin-bottom: 20rpx;
}

.banner-0 {
  background: linear-gradient(135deg, #E0B34A 0%, #C9A227 100%);
}

.banner-1 {
  background: linear-gradient(135deg, #3D9A72 0%, #2E7D5B 100%);
}

.banner-2 {
  background: linear-gradient(135deg, #E8594F 0%, #C5342B 100%);
}

.banner-3 {
  background: linear-gradient(135deg, #9A9186 0%, #7C746A 100%);
}

.banner-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.banner-sub {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.5;
}

/* ---------- 信息卡 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 4rpx 26rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.row {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  border-bottom: 1rpx solid #F8F2EA;
}

.row-last {
  border-bottom: none;
}

.row-label {
  width: 120rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: #9A9186;
}

.row-value {
  flex: 1;
  font-size: 27rpx;
  color: #2B2622;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* 纯文本块（原因 / 备注 / 进度）单独一种卡 */
.card-block {
  padding: 26rpx;
}

.block-title {
  display: block;
  font-size: 24rpx;
  color: #9A9186;
  margin-bottom: 12rpx;
}

.block-text {
  font-size: 27rpx;
  color: #2B2622;
  line-height: 1.6;
  word-break: break-all;
}

/* ---------- 进度 ---------- */
.steps {
  padding-top: 6rpx;
}

.step {
  position: relative;
  display: flex;
  align-items: flex-start;
  padding-bottom: 34rpx;
}

.step:last-child {
  padding-bottom: 0;
}

.dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: #E7DBC9;
  margin-top: 8rpx;
  flex-shrink: 0;
  z-index: 1;
}

.dot-on {
  background: #2E7D5B;
}

/* 当前这一步：外圈加一层浅色，比单纯换色更容易看出"停在这" */
.dot-now {
  background: #C9A227;
  box-shadow: 0 0 0 8rpx rgba(201, 162, 39, 0.16);
}

/* 连接线：绝对定位在 dot 下面，宽 2rpx。
   用 absolute 而不是 flex 里的一个 div，是为了不被文字高度影响 */
.line {
  position: absolute;
  left: 9rpx;
  top: 28rpx;
  bottom: 6rpx;
  width: 2rpx;
  background: #F0E6D8;
}

.line-on {
  background: #BFE0D0;
}

.step-main {
  flex: 1;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
}

.step-title {
  font-size: 26rpx;
  color: #9A9186;
}

.step-title-on {
  color: #2B2622;
  font-weight: 500;
}

.step-sub {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

/* ---------- 操作 ---------- */
.actions {
  margin-top: 12rpx;
  display: flex;
}

.btn {
  flex: 1;
  height: 88rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn + .btn {
  margin-left: 16rpx;
}

.btn-ghost {
  background: #FFFFFF;
  border: 2rpx solid #E7DBC9;
}

.btn-ghost-text {
  font-size: 27rpx;
  color: #6B6259;
}

/* 撤销是破坏性操作：红字白底，不和「销假」抢同样的视觉重量 */
.btn-danger {
  background: #FDEDEB;
}

.btn-danger-text {
  font-size: 27rpx;
  color: #C5342B;
}

.btn-pressed {
  opacity: 0.8;
}
</style>
