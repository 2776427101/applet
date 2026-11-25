<template>
  <view class="page">
    <view class="brand">
      <view class="brand-mark">生</view>
      <text class="brand-name">学生端</text>
      <text class="brand-slogan">学号 + 姓名，没进过班也能先登录</text>
    </view>

    <view class="form">
      <view class="field">
        <text class="label">学号</text>
        <input
          class="input"
          v-model="studentNo"
          type="text"
          :maxlength="32"
          placeholder="完整学号，例如 202411753"
          placeholder-style="color:#B4A99B"
        />
      </view>

      <view class="field">
        <text class="label">姓名</text>
        <input
          class="input"
          v-model="name"
          type="text"
          :maxlength="32"
          placeholder="和班主任名单上写的一致"
          placeholder-style="color:#B4A99B"
        />
      </view>

      <view
        class="btn"
        :class="{ off: !canSubmit }"
        hover-class="btn-pressed"
        @click="submit"
      >
        {{ busy ? '进入中…' : '进入' }}
      </view>
    </view>

    <!-- 班级选择器：只有"一个学号对应多个班"时才出现。
         用底部弹层而不是 showActionSheet —— 那个最多 6 项，而且没法显示班主任 -->
    <view v-if="pickerOpen" class="mask" @click="cancelPick">
      <view class="picker" @click.stop>
        <view class="picker-head">
          <text class="picker-title">你在 {{ classes.length }} 个班里</text>
          <text class="picker-close" @click="cancelPick">✕</text>
        </view>

        <scroll-view scroll-y class="picker-list">
          <view
            v-for="c in classes"
            :key="c.classId"
            class="picker-row"
            hover-class="picker-row-pressed"
            @click="pickClass(c)"
          >
            <view class="picker-main">
              <text class="picker-name">{{ c.className }}</text>
              <text class="picker-meta">班主任 {{ c.teacherName }}</text>
            </view>
            <text class="picker-arrow">›</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { studentLogin } from '@/api/student-portal'

const studentNo = ref('')
const name = ref('')
const busy = ref(false)

const classes = ref([])
const pickerOpen = ref(false)

const canSubmit = computed(
  () => !!studentNo.value.trim() && !!name.value.trim() && !busy.value
)

async function submit() {
  if (!canSubmit.value) return
  busy.value = true
  try {
    const res = await studentLogin({
      studentNo: studentNo.value,
      name: name.value
    })
    /* 一个学号在多个班：不存 token，先让他选 */
    if (res && res.needClass) {
      classes.value = res.classes || []
      pickerOpen.value = true
      return
    }
    /* 花名册里没有这个人：token 是"未入班"的，只能进班。
       直接跳入班页，不要往首页走 —— 首页那几个接口会 1009 */
    if (res && res.needJoin) {
      goJoin()
      return
    }
    enter()
  } catch (e) {
    /* request.js 已经弹过提示了（1004 那种），这里不再重复弹 */
  } finally {
    busy.value = false
  }
}

/* 未入班：reLaunch 到入班页，把登录页从栈里清掉。
   first=1 告诉入班页"这是第一次进来"，它会把"当前班级"那一行藏起来 ——
   这时候还没有班级，显示"未绑定"只是让人紧张 */
function goJoin() {
  uni.reLaunch({ url: '/pages/student/join/index?first=1' })
}

async function pickClass(c) {
  if (busy.value) return
  busy.value = true
  try {
    await studentLogin({
      studentNo: studentNo.value,
      name: name.value,
      classId: c.classId
    })
    pickerOpen.value = false
    enter()
  } catch (e) {
    /* 已 toast */
  } finally {
    busy.value = false
  }
}

/* 取消选班：留在登录页，不要存任何东西。
   直接关弹层不清表单，学生可能是想换个学号输 */
function cancelPick() {
  pickerOpen.value = false
  classes.value = []
}

function enter() {
  /* 学生首页是普通页面（不是 tabBar 页），用 reLaunch 把登录页从栈里清掉 ——
     否则按返回会回到登录页，看起来像被登出了 */
  uni.reLaunch({ url: '/pages/student/home' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #FBF6F1;
  padding: 0 48rpx;
  box-sizing: border-box;
}

.brand {
  padding-top: 120rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-mark {
  width: 120rpx;
  height: 120rpx;
  border-radius: 32rpx;
  background: #C5342B;
  color: #fff;
  font-size: 52rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14rpx 32rpx rgba(197, 52, 43, 0.22);
}

.brand-name {
  margin-top: 28rpx;
  font-size: 40rpx;
  font-weight: 600;
  color: #2B2622;
  letter-spacing: 2rpx;
}

.brand-slogan {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #9A9186;
}

.form {
  margin-top: 80rpx;
}

.field {
  margin-bottom: 32rpx;
}

.label {
  font-size: 24rpx;
  color: #6B6259;
  margin-bottom: 12rpx;
  display: block;
}

.input {
  height: 88rpx;
  padding: 0 28rpx;
  background: #FFFFFF;
  border-radius: 20rpx;
  font-size: 28rpx;
  color: #2B2622;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.05);
}

.btn {
  margin-top: 48rpx;
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  border-radius: 46rpx;
  background: #C5342B;
  color: #fff;
  font-size: 30rpx;
  box-shadow: 0 10rpx 26rpx rgba(197, 52, 43, 0.22);
}

.btn.off {
  background: #E3D5C8;
  box-shadow: none;
}

.btn-pressed {
  opacity: 0.9;
}

/* ---------- 班级选择器 ---------- */
.mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(43, 38, 34, 0.42);
  z-index: 900;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.picker {
  background: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  padding: 28rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx;
}

.picker-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.picker-close {
  font-size: 30rpx;
  color: #B4A99B;
  padding: 4rpx 12rpx;
}

/* scroll-view 在 flex 列里要给 0 高，否则会按内容撑开 */
.picker-list {
  flex: 1;
  height: 0;
  min-height: 0;
}

.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 20rpx;
  border-radius: 20rpx;
  margin-bottom: 10rpx;
  background: #FDF8F1;
}

.picker-row-pressed {
  background: #FAF0E6;
}

.picker-main {
  display: flex;
  flex-direction: column;
}

.picker-name {
  font-size: 29rpx;
  color: #2B2622;
  font-weight: 500;
}

.picker-meta {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.picker-arrow {
  font-size: 32rpx;
  color: #C6BAA9;
  line-height: 1;
}
</style>
