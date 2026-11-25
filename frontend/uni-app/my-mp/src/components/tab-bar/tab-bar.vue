<template>
  <view class="tabbar">
    <view
      class="tab-item"
      v-for="(item, i) in list"
      :key="item.text"
      @click="onTap(item, i)"
    >
      <text class="tab-icon" :class="{ 'tab-icon-dim': i !== current }">{{ item.icon }}</text>
      <text class="tab-text" :class="{ 'tab-text-active': i === current }">{{ item.text }}</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  current: { type: Number, default: 0 }
})

const list = [
  { text: '首页', icon: '🏠' },
  { text: '学生', icon: '👤' },
  { text: '成绩', icon: '📊' },
  { text: '座位', icon: '▦' },
  { text: '请假', icon: '📄' }
]

const onTap = (item, i) => {
  if (i === props.current) return
  uni.showToast({ title: `${item.text}页面开发中`, icon: 'none' })
}
</script>

<style lang="scss" scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  height: 108rpx;
  padding-bottom: env(safe-area-inset-bottom);
  background: #FFFFFF;
  border-radius: 28rpx 28rpx 0 0;
  box-shadow: 0 -6rpx 24rpx rgba(150, 110, 100, 0.08);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.tab-icon {
  font-size: 40rpx;
  line-height: 1.1;
  transition: opacity 0.15s;
}

.tab-icon-dim {
  opacity: 0.55;
}

.tab-text {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9C938A;
}

.tab-text-active {
  color: #D2403A;
  font-weight: 600;
}
</style>
