<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else>
      <view class="hero">
        <view class="hero-tag" :class="drawn ? 'tone-live' : 'tone-off'">
          {{ drawn ? '已开奖' : '未开始' }}
        </view>
        <text class="hero-title">{{ kindName }}</text>
        <view class="hero-meta">
          <text>名单 {{ r.poolSize || 0 }} 人</text>
          <text class="meta-dot">·</text>
          <text v-if="r.kind === 1">每次抽 {{ r.drawCount || 1 }} 个</text>
          <text v-else>分 {{ r.groupCount || 0 }} 组</text>
          <template v-if="r.kind === 1 && r.dedupe">
            <text class="meta-dot">·</text>
            <text>还剩 {{ r.remaining === null || r.remaining === undefined ? r.poolSize : r.remaining }} 人可抽</text>
          </template>
        </view>
      </view>

      <!-- 结果被设成「仅发起人可见」：非发起人拿到的是空数组。
           这里必须解释一句，否则页面空着像加载失败 -->
      <view v-if="hidden" class="notice">
        <text class="notice-title">结果仅发起人可见</text>
        <text class="notice-text">这张抽签已经抽过了，但发起人设置了不公开。</text>
      </view>

      <template v-else-if="!drawn">
        <view class="empty">
          <text class="empty-text">还没有抽</text>
          <text class="empty-hint">{{ mine ? '点下面的「开始抽」，结果会出现在这里和群卡片上' : '等发起人开始抽' }}</text>
        </view>
      </template>

      <!-- 点名：按轮次倒序，最近一轮在最上面。翻记录时最想看的就是刚才那次 -->
      <template v-else-if="r.kind === 1">
        <view v-for="(rd, i) in roundsDesc" :key="rd.round" class="round" :class="{ latest: i === 0 }">
          <view class="round-head">
            <text class="round-no">第 {{ rd.round }} 轮</text>
            <text v-if="i === 0" class="round-flag">最近</text>
          </view>
          <view class="names">
            <text v-for="(n, j) in rd.names" :key="j" class="name">{{ n }}</text>
          </view>
        </view>
      </template>

      <!-- 分组：两列，组名 + 人数 + 名字。名字竖排比横排好扫 -->
      <template v-else>
        <view class="groups">
          <view v-for="g in r.groups || []" :key="g.groupNo" class="group">
            <view class="group-head">
              <text class="group-no">第 {{ g.groupNo }} 组</text>
              <text class="group-count">{{ (g.names || []).length }} 人</text>
            </view>
            <text v-for="(n, j) in g.names || []" :key="j" class="group-name">{{ n }}</text>
          </view>
        </view>
      </template>

      <view v-if="mine && drawn" class="acts">
        <view class="act" hover-class="act-press" @click="onDraw">
          {{ r.kind === 1 ? '再抽一次' : '重新分组' }}
        </view>
        <view class="act ghost" hover-class="act-press" @click="onCopy">复制结果</view>
      </view>

      <view v-if="mine && !drawn" class="acts">
        <view class="act" hover-class="act-press" @click="onDraw">开始抽</view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getLottery, drawLottery } from '@/api/lottery'

const containerId = ref(0)
const lotteryId = ref(0)

const r = ref({})
const loading = ref(true)
const loadErr = ref('')
const busy = ref(false)

const drawn = computed(() => r.value.status === 2)
const mine = computed(() => !!r.value.mine)

/* 结果不公开时，服务端给的是空结果。用 kind 判断不可靠（两种模式都可能空），
   所以看"已开奖但没有内容"这个组合 */
const hidden = computed(() => {
  if (!drawn.value) return false
  if (r.value.kind === 1) return (r.value.rounds || []).length === 0
  return (r.value.groups || []).length === 0
})

const kindName = computed(() => (r.value.kind === 2 ? '随机分组' : '随机点名'))

/* 倒序：翻记录时最想看的是刚刚抽的那一轮 */
const roundsDesc = computed(() => {
  const list = (r.value.rounds || []).slice()
  list.sort((a, b) => b.round - a.round)
  return list
})

onLoad((q) => {
  containerId.value = Number((q && q.id) || 0)
  lotteryId.value = Number((q && q.lotteryId) || 0)
  if (!containerId.value || !lotteryId.value) {
    loadErr.value = '缺少抽签信息'
    loading.value = false
    return
  }
  load()
})

async function load() {
  loading.value = !r.value.id
  loadErr.value = ''
  try {
    const d = await getLottery(containerId.value, lotteryId.value)
    r.value = d || {}
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onDraw() {
  if (busy.value) return
  busy.value = true
  try {
    const d = await drawLottery(containerId.value, lotteryId.value)
    r.value = d || {}
  } catch (e) {
    // 4105 = 名单抽完了：重新拉一次，让「还剩 0 人」显示出来
    if (e && e.code === 4105) await load()
  } finally {
    busy.value = false
  }
}

/** 复制成纯文本，方便粘到别处（群里、文档里） */
function onCopy() {
  let text = ''
  if (r.value.kind === 2) {
    const lines = (r.value.groups || []).map(
      (g) => `第 ${g.groupNo} 组：${(g.names || []).join('、')}`
    )
    text = lines.join('\n')
  } else {
    const lines = roundsDesc.value.map((rd) => `第 ${rd.round} 轮：${(rd.names || []).join('、')}`)
    text = lines.join('\n')
  }
  if (!text) {
    uni.showToast({ title: '没有可复制的结果', icon: 'none' })
    return
  }
  uni.setClipboardData({
    data: text,
    success: () => uni.showToast({ title: '已复制', icon: 'none' })
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

/* ---------- 头部 ---------- */
.hero {
  padding: 24rpx 6rpx 26rpx;
}

.hero-tag {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  line-height: 30rpx;
}

.tone-live {
  background: #FDEDEB;
  color: #C5342B;
}

.tone-off {
  background: rgba(0, 0, 0, 0.05);
  color: #9A9186;
}

.hero-title {
  display: block;
  margin-top: 14rpx;
  font-size: 38rpx;
  font-weight: 600;
  color: #2B2622;
}

.hero-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.meta-dot {
  margin: 0 10rpx;
  color: #C9BEB0;
}

/* ---------- 空 / 提示 ---------- */
.empty {
  padding: 140rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-text {
  font-size: 28rpx;
  color: #7A6F63;
  font-weight: 500;
}

.empty-hint {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #B4A99B;
  text-align: center;
  line-height: 1.6;
}

.notice {
  padding: 40rpx 28rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.notice-title {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
}

.notice-text {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #9A9186;
  text-align: center;
  line-height: 1.7;
}

/* ---------- 点名：轮次 ---------- */
.round {
  margin-bottom: 16rpx;
  padding: 24rpx 26rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

/* 最近一轮加一条左色条。一屏几轮时，色条比"第 7 轮"这几个字更快定位 */
.round.latest {
  border-left: 6rpx solid #C5342B;
}

.round-head {
  display: flex;
  align-items: center;
}

.round-no {
  font-size: 22rpx;
  color: #9A9186;
}

.round-flag {
  margin-left: 12rpx;
  padding: 0 10rpx;
  border-radius: 6rpx;
  font-size: 18rpx;
  line-height: 28rpx;
  background: #FDEDEB;
  color: #C5342B;
}

.names {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx 22rpx;
  margin-top: 14rpx;
}

.name {
  font-size: 34rpx;
  font-weight: 600;
  color: #2B2622;
}

.round.latest .name {
  color: #C5342B;
}

/* ---------- 分组：两列 ---------- */
.groups {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

/* flex 0 0 48.6% + width 双保险：只写 calc(50% - 8rpx) 在部分真机上会被丢掉 */
.group {
  flex: 0 0 48.6%;
  width: 48.6%;
  box-sizing: border-box;
  padding: 20rpx 22rpx;
  background: #FFFFFF;
  border-radius: 22rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 12rpx;
  border-bottom: 1rpx solid #F5EDE2;
}

.group-no {
  font-size: 24rpx;
  font-weight: 600;
  color: #C5342B;
}

.group-count {
  font-size: 19rpx;
  color: #B4A99B;
}

.group-name {
  display: block;
  padding-top: 12rpx;
  font-size: 25rpx;
  color: #2B2622;
  line-height: 1.4;
}

/* ---------- 操作 ---------- */
.acts {
  display: flex;
  gap: 16rpx;
  margin-top: 40rpx;
}

.act {
  flex: 1;
  height: 92rpx;
  border-radius: 22rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}

.act.ghost {
  background: #FFFFFF;
  color: #C5342B;
  border: 2rpx solid #E9A9A3;
}

.act-press {
  opacity: 0.8;
}
</style>
