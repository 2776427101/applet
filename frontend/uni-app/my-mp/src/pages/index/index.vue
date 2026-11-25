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
          <text class="role-desc">成绩 · 请假 · 座位 · 班级群</text>
        </view>
        <text class="role-arrow role-arrow-dark">›</text>
      </view>
    </view>

    <!-- 底部：版本号 + 当前连的后端地址。
         地址摆在这里有两个用处：① 连不上时一眼看出它连的是哪台机器；
         ② 它本身就是入口 —— 点一下就能改，换网络后不用重新打包 -->
    <view class="footer" @click="openHost">
      <text class="footer-text">v1.0.0 · {{ host }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '@/api/auth'
import { getBaseUrl, promptApiHost } from '@/utils/api-host'

/* 状态栏高度：custom 导航下页面从屏幕顶端开始画，必须自己让出状态栏 */
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 20

/* 当前后端地址，只显示主机部分（http:// 和 /api 对用户没意义，还占地方） */
const host = ref(getBaseUrl().replace(/^https?:\/\//, '').replace(/\/api$/, ''))

/* 改地址。改完立刻刷新下面显示的地址 —— 不刷新的话用户会以为没改成功 */
function openHost() {
  promptApiHost(() => {
    host.value = getBaseUrl().replace(/^https?:\/\//, '').replace(/\/api$/, '')
  })
}

/* 防连点：登录是异步的，手快连点两下会发两次请求 */
const entering = ref(false)

/* 必须是 async：里面要 await login()，不加会报 "await is only valid in async function" */
const enterTeacher = async () => {
  if (entering.value) return
  entering.value = true
  try {
    /* 已经有「老师」的 token 就直接进，省一次登录请求。
       注意要看 userRole：设备上可能留着的是学生 token，
       拿它去调教师接口会被后端 3001 挡回来，表现就是"进不去也不说为什么" */
    const token = uni.getStorageSync('token')
    const role = Number(uni.getStorageSync('userRole')) || 1
    if (!token || role !== 1) {
      await login()
      uni.setStorageSync('userRole', 1)
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

/**
 * 进学生端。
 *
 * 已经有学生 token 就直接进；没有就先去登录页输学号和姓名。
 * 这里不调任何接口 —— 学生登录必须由本人输学号，不能替他"自动注册"。
 */
const enterStudent = () => {
  const token = uni.getStorageSync('token')
  const role = Number(uni.getStorageSync('userRole')) || 0
  if (token && role === 2) {
    /* 登录过但还没入班（studentInfo 里没有 classId）：直接进入班页。
       去首页的话每个接口都返回 1009，学生看到的是"加载失败" */
    const info = uni.getStorageSync('studentInfo') || {}
    if (!info.classId) {
      uni.reLaunch({ url: '/pages/student/join/index?first=1' })
      return
    }
    /* 用 reLaunch 不用 navigateTo：学生端有底部导航（首页/通讯录/乐迪/请假/我的），
       底栏切换是"平级"，栈里不该留着一层入口页 —— 否则每次切栏目都会多出一个返回箭头，
       按返回会莫名其妙回到"选身份"那一屏。老师端 switchTab 的效果也是一样的。 */
    uni.reLaunch({ url: '/pages/student/home' })
  } else {
    uni.navigateTo({ url: '/pages/student/login' })
  }
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

/* 学生卡是白底，箭头必须换深色，否则白底白箭头等于没有 */
.role-arrow-dark {
  color: #C9BEB1;
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
