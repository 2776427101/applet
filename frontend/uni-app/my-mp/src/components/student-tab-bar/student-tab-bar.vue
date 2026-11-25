<template>
  <view class="stb">
    <view
      class="stb-item"
      v-for="t in list"
      :key="t.key"
      @click="onTap(t)"
    >
      <image
        class="stb-icon"
        :src="t.key === current ? t.on : t.off"
        mode="aspectFit"
      />
      <text class="stb-text" :class="{ 'stb-text-on': t.key === current }">{{ t.text }}</text>
    </view>
    <!-- 底部安全区：iPhone 那条横线的高度。不加的话最后一排图标会被挡住 -->
    <view class="stb-safe"></view>
  </view>
</template>

<script setup>
/**
 * 学生端底部导航栏
 *
 * <p><b>为什么不用原生 tabBar：</b>小程序的 tabBar 是全局唯一的，
 * 一个 app.json 只能有一份 list。老师端已经占了那 5 个位置，
 * 学生端不可能再注册一套 —— 硬塞进去会导致老师的页面出现在学生底栏里。
 *
 * <p>所以做成一个固定定位的组件，放在每个"一级页面"的最下面。
 * 视觉参数（#8A8178 / 选中 #C5342B / 白底 / 81×81 图标）和老师端原生 tabBar
 * 逐项对齐，两端看起来是同一个底栏，只是条目不同。
 *
 * <p>跳转用 <b>redirectTo</b> 不是 navigateTo：底栏是"平级切换"，
 * 用 navigateTo 会把页面一层层堆起来，点五次底栏要按五次返回。
 * redirectTo 替换当前页，栈里永远只有一层。
 */
const props = defineProps({
  /** 当前高亮的 key：home / leave / score / seat / profile */
  current: { type: String, default: 'home' }
})

/* 五个一级页面，和老师端一一对应：首页 / 通讯录 / 乐迪 / 请假 / 我的。
   连图标都用的同一组（通讯录=tab-student，乐迪=tab-aidi），
   两端底栏放在一起，除了文字几乎看不出差别。
   成绩和座位没进底栏 —— 它们是"查一下就走"的页面，首页常用里已经有入口。 */
const list = [
  { key: 'home', text: '首页', off: '/static/icons/tab-home.png', on: '/static/icons/tab-home-on.png', to: '/pages/student/home' },
  { key: 'contact', text: '通讯录', off: '/static/icons/tab-student.png', on: '/static/icons/tab-student-on.png', to: '/pages/student/contact/index' },
  { key: 'aidi', text: '乐迪', off: '/static/icons/tab-aidi.png', on: '/static/icons/tab-aidi-on.png', to: '/pages/student/aidi/index' },
  { key: 'leave', text: '请假', off: '/static/icons/tab-leave.png', on: '/static/icons/tab-leave-on.png', to: '/pages/student/leave/index' },
  { key: 'profile', text: '我的', off: '/static/icons/tab-mine.png', on: '/static/icons/tab-mine-on.png', to: '/pages/student/profile/index' }
]

function onTap(t) {
  /* 点当前页什么都不做。不判断的话会 redirectTo 到自己，
     页面重新 onShow 一次，看起来像闪了一下 */
  if (t.key === props.current) return
  uni.redirectTo({ url: t.to })
}
</script>

<style lang="scss" scoped>
/* 尺寸按原生 tabBar 来：高度 50px ≈ 100rpx，图标 24px ≈ 48rpx，文字 10px ≈ 20rpx。
   照抄这几个数，自定义底栏和老师端原生底栏放在一起才看不出差别 */
.stb {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  flex-wrap: wrap;
  background: #FFFFFF;
  border-top: 1rpx solid #F0E6D8;
}

.stb-item {
  flex: 0 0 20%;
  width: 20%;
  height: 100rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.stb-icon {
  width: 48rpx;
  height: 48rpx;
}

.stb-text {
  margin-top: 4rpx;
  font-size: 20rpx;
  line-height: 1.2;
  color: #8A8178;
}

.stb-text-on {
  color: #C5342B;
}

/* 安全区：单独一行放在图标行下面。
   用 flex-wrap + 100% 宽度让它换行，比给 .stb 加 padding-bottom 更好算 ——
   后者会让 100rpx 的图标行被压扁 */
.stb-safe {
  flex: 0 0 100%;
  width: 100%;
  height: env(safe-area-inset-bottom);
}
</style>
