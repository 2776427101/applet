<template>
  <view class="page">
    <view class="brand">
      <view class="brand-mark">群</view>
      <text class="brand-name">加入群聊</text>
      <text class="brand-slogan">填老师给的群邀请码</text>
    </view>

    <view class="form">
      <view class="field">
        <text class="label">邀请码</text>
        <input
          class="input input-code"
          v-model="code"
          type="text"
          :maxlength="16"
          placeholder="例如 953738D26C"
          placeholder-style="color:#B4A99B"
          @confirm="submit"
        />
      </view>

      <view class="btn" :class="{ off: !canSubmit }" hover-class="btn-pressed" @click="submit">
        {{ busy ? '加入中…' : '加入' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { joinGroupByCode } from '@/api/student-data'

const code = ref('')
const busy = ref(false)

const canSubmit = computed(() => !!code.value.trim() && !busy.value)

async function submit() {
  const v = code.value.trim()
  if (!v || busy.value) return
  busy.value = true
  try {
    const res = await joinGroupByCode(v)
    const g = (res && res.group) || {}
    if (res && res.alreadyIn) {
      uni.showToast({ title: '你已经在这个群里了', icon: 'none' })
    } else {
      uni.showToast({ title: '已加入 ' + (g.name || '群聊'), icon: 'none' })
    }
    /* 直接进群：加群的下一步一定是"看看里面说了什么"，
       退回通讯录还要再点一次 */
    setTimeout(() => {
      if (g.id) {
        uni.redirectTo({ url: '/pages/student/group/index?id=' + g.id })
      } else {
        uni.navigateBack()
      }
    }, 800)
  } catch (e) {
    /* 文案由后端给（邀请码不对 / 过期 / 设了密码），request.js 已弹 toast */
  } finally {
    busy.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 60rpx 48rpx;
  box-sizing: border-box;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 0 48rpx;
}

.brand-mark {
  width: 108rpx;
  height: 108rpx;
  border-radius: 32rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 46rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 28rpx rgba(197, 52, 43, 0.24);
}

.brand-name {
  margin-top: 24rpx;
  font-size: 38rpx;
  font-weight: 600;
  color: #2B2622;
  letter-spacing: 2rpx;
}

.brand-slogan {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #9A9186;
}

.form {
  background: #FFFFFF;
  border-radius: 32rpx;
  padding: 36rpx 32rpx 40rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.field {
  margin-bottom: 32rpx;
}

.label {
  font-size: 25rpx;
  color: #6B6258;
}

.input {
  margin-top: 14rpx;
  height: 92rpx;
  padding: 0 26rpx;
  background: #FBF6EE;
  border-radius: 20rpx;
  font-size: 30rpx;
  color: #2B2622;
}

/* 邀请码是大写字母 + 数字，字距拉开一点更好认、更好抄 */
.input-code {
  letter-spacing: 4rpx;
  font-weight: 600;
}

.btn {
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 999rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 30rpx;
  text-align: center;
  letter-spacing: 4rpx;
  box-shadow: 0 10rpx 22rpx rgba(197, 52, 43, 0.2);
}

.btn.off {
  background: #E3D5C8;
  box-shadow: none;
}

.btn-pressed {
  opacity: 0.9;
}
</style>
