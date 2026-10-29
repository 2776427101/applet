<template>
  <!--
    加载失败的统一展示块。

    <p>为什么单独抽一个组件：学生列表、成绩、待办、请假、座位、首页
    原来都是同一个毛病 —— catch 里把 loading 关掉就完事，
    于是「请求失败」和「真的没有数据」在界面上长得一模一样。
    老师看到「这个班还没有学生」，会以为数据丢了，反复下拉刷新。

    <p>这个块只在「没有数据可显示」时出现。如果已经有旧数据，
    只是刷新失败，就不要整块盖住 —— 那会让人以为原来的数据也没了。
  -->
  <view class="le" :class="{ 'le-compact': compact }">
    <view class="le-icon">!</view>

    <text class="le-title">{{ title }}</text>

    <!-- message 是技术原因（比如「网络异常，请检查后端是否启动」），
         不是所有用户都想看，但出了问题一定要能说出来 -->
    <text v-if="message" class="le-msg">{{ message }}</text>

    <text v-if="hint" class="le-hint">{{ hint }}</text>

    <view class="le-btn" hover-class="le-btn-pressed" @click="$emit('retry')">
      <text class="le-btn-text">重试</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  /** 标题，默认「加载失败」 */
  title: { type: String, default: '加载失败' },
  /** 技术原因，一般是 catch 到的 e.message */
  message: { type: String, default: '' },
  /** 补充说明，告诉用户「可以做什么」 */
  hint: { type: String, default: '' },
  /** 紧凑模式：嵌在卡片里时用，不撑满整页 */
  compact: { type: Boolean, default: false }
})

defineEmits(['retry'])
</script>

<style lang="scss" scoped>
.le {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 100rpx 48rpx;
}

.le-compact {
  padding: 56rpx 32rpx;
}

/* 用「!」而不是红叉：这里不是错误码，是「这次没拿到」，
   红叉会让人以为操作被拒绝了 */
.le-icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: #FBF0EE;
  color: #C5342B;
  font-size: 40rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: Georgia, 'Times New Roman', serif;
}

.le-title {
  margin-top: 22rpx;
  font-size: 28rpx;
  font-weight: 500;
  color: #2B2622;
}

.le-msg {
  margin-top: 12rpx;
  font-size: 23rpx;
  color: #8A8178;
  line-height: 1.6;
  text-align: center;
}

.le-hint {
  margin-top: 8rpx;
  font-size: 21rpx;
  color: #B4A99B;
  line-height: 1.6;
  text-align: center;
}

.le-btn {
  margin-top: 30rpx;
  padding: 16rpx 56rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  border: 2rpx solid #EADFD0;
}

.le-btn-pressed {
  background: #FAF5EF;
}

.le-btn-text {
  font-size: 25rpx;
  color: #C5342B;
  font-weight: 500;
}
</style>
