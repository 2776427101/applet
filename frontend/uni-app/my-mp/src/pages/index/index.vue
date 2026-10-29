<template>
  <view class="page">
    <view class="status-bar" :style="{ height: statusBarHeight + 'px' }"></view>

    <!-- 品牌区 -->
    <view class="brand">
      <view class="brand-mark">班</view>
      <text class="brand-name">班级助手</text>
      <text class="brand-slogan">让班级管理更简单</text>
    </view>

    <!-- 身份入口 -->
    <view class="roles">
      <view class="role-card role-teacher" hover-class="role-pressed" @click="enterTeacher">
        <view class="role-icon">师</view>
        <view class="role-main">
          <text class="role-title role-title-light">教师端</text>
          <text class="role-desc role-desc-light">班级 · 学生 · 成绩 · 29 个管理模块</text>
        </view>
        <text class="role-arrow">›</text>
      </view>

      <view class="role-card role-student" hover-class="role-pressed" @click="enterStudent">
        <view class="role-icon role-icon-muted">生</view>
        <view class="role-main">
          <text class="role-title">学生端</text>
          <text class="role-desc">课表 · 作业 · 成绩查询</text>
        </view>
        <text class="role-badge">即将开放</text>
      </view>
    </view>

    <view class="footer">
      <text class="footer-text">v1.0.0</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '@/api/auth'

/* 状态栏高度：custom 导航下页面从屏幕顶端开始画，必须自己让出状态栏 */
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 20

/* 防连点：登录是异步的，手快连点两下会发两次请求 */
const entering = ref(false)

/* 必须是 async：里面要 await login()，不加会报 "await is only valid in async function" */
const enterTeacher = async () => {
  if (entering.value) return
  entering.value = true
  try {
    /* 已经有 token 就直接进，省一次登录请求
       token 是否还有效由后端判断：真过期了，请求会返回 1002，
       request.js 会自动清 token 并跳回来 */
    const token = uni.getStorageSync('token')
    if (!token) {
      await login()
    }
    /* 教师端首页是 tabBar 页面，必须用 switchTab，navigateTo 会失败 */
    uni.switchTab({
      url: '/pages/teacher/index/index',
      fail: () => {
        uni.showToast({ title: '教师端页面未注册', icon: 'none' })
      }
    })
  } catch (e) {
    /* login 内部已经 toast 过了，这里不用再提示，避免弹两次 */
  } finally {
    entering.value = false
  }
}

const enterStudent = () => {
  uni.showToast({ title: '学生端开发中', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #FBF6F1;
  padding: 0 40rpx;
  display: flex;
  flex-direction: column;
}

/* 品牌区 */
.brand {
  margin-top: 140rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-mark {
  width: 132rpx;
  height: 132rpx;
  border-radius: 36rpx;
  background: linear-gradient(140deg, #E8544D, #C0322F);
  color: #fff;
  font-size: 56rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16rpx 36rpx rgba(201, 56, 47, 0.26);
}

.brand-name {
  margin-top: 32rpx;
  font-size: 44rpx;
  font-weight: 600;
  color: #2B2622;
  letter-spacing: 2rpx;
}

.brand-slogan {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #9A9186;
}

/* 身份卡片 */
.roles {
  margin-top: 96rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.role-card {
  display: flex;
  align-items: center;
  padding: 36rpx 32rpx;
  border-radius: 32rpx;
}

.role-teacher {
  background: linear-gradient(135deg, #E8544D 0%, #C9382F 100%);
  box-shadow: 0 14rpx 34rpx rgba(201, 56, 47, 0.24);
}

.role-student {
  background: #FFFFFF;
  box-shadow: 0 8rpx 24rpx rgba(120, 96, 72, 0.06);
}

.role-pressed {
  transform: scale(0.985);
  opacity: 0.92;
}

.role-icon {
  width: 84rpx;
  height: 84rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
  font-size: 34rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.role-icon-muted {
  background: #F6EFE7;
  color: #B0A69A;
}

.role-main {
  flex: 1;
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
}

.role-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #2B2622;
}

.role-title-light {
  color: #fff;
}

.role-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9A9186;
}

.role-desc-light {
  color: rgba(255, 255, 255, 0.82);
}

.role-arrow {
  font-size: 44rpx;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1;
  margin-left: 12rpx;
}

.role-badge {
  font-size: 22rpx;
  color: #B0A69A;
  background: #F6EFE7;
  padding: 8rpx 18rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

/* 底部 */
.footer {
  margin-top: auto;
  padding-bottom: calc(60rpx + env(safe-area-inset-bottom));
  display: flex;
  justify-content: center;
}

.footer-text {
  font-size: 22rpx;
  color: #C4BAAE;
}
</style>
