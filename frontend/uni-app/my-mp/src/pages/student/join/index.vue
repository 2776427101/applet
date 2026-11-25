<template>
  <view class="page">
    <view class="brand">
      <view class="brand-mark">班</view>
      <text class="brand-name">加入班级</text>
      <text class="brand-slogan">填班主任给的邀请码</text>
    </view>

    <view class="form">
      <!-- 第一次进来：还没有"当前班级"，改说一句"你是谁"。
           显示「未绑定」只会让人以为哪里出错了 -->
      <view v-if="first" class="hello">
        <text class="hello-name">你好，{{ me.name || '同学' }}</text>
        <text class="hello-sub">学号 {{ me.studentNo || '—' }} · 还没加入任何班级</text>
      </view>

      <!-- 已经在某个班：先说清楚"你现在在哪个班"，否则学生不知道加入之后会变成什么 -->
      <view v-else class="now">
        <text class="now-label">当前班级</text>
        <text class="now-value">{{ me.className || '未绑定' }}</text>
      </view>

      <view class="field">
        <text class="label">邀请码</text>
        <input
          class="input input-code"
          v-model="code"
          type="text"
          :maxlength="16"
          placeholder="班主任给的那串，例如 628628"
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
import { onLoad } from '@dcloudio/uni-app'
import { joinClass } from '@/api/student-portal'

const me = ref(uni.getStorageSync('studentInfo') || {})
const code = ref('')
const busy = ref(false)

/* first=1：从登录页直接过来的（花名册里没有这个人）。
   只影响文案，不影响逻辑 —— 入班接口对"有没有班级"一视同仁 */
const first = ref(false)

const canSubmit = computed(() => !!code.value.trim() && !busy.value)

onLoad((q) => {
  first.value = !!(q && q.first === '1')
})

async function submit() {
  const v = code.value.trim()
  if (!v || busy.value) return
  busy.value = true
  try {
    const res = await joinClass(v)
    if (res && res.alreadyIn) {
      /* 已经在这个班：不跳页。跳回去反而让人以为"加入成功了"，
         而实际上什么都没变 —— 下次打开还是这个班 */
      uni.showToast({ title: '你已经在这个班里了', icon: 'none' })
      setTimeout(() => {
        /* first 模式下栈里只有这一页，navigateBack 会失败 */
        if (first.value) {
          uni.reLaunch({ url: '/pages/student/home' })
        } else {
          uni.navigateBack()
        }
      }, 900)
      return
    }
    uni.showToast({ title: '已加入 ' + (res?.student?.className || '班级'), icon: 'none' })
    /* reLaunch 而不是 navigateBack：换班之后栈里那些页面全是旧班的数据，
       退回去会看到"旧班的成绩配新班的班名"。整栈重开最省事也最不会错 */
    setTimeout(() => uni.reLaunch({ url: '/pages/student/home' }), 900)
  } catch (e) {
    /* 文案由后端给（邀请码不对 / 姓名对不上），request.js 已弹 toast */
  } finally {
    busy.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 60rpx 48rpx 60rpx;
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

/* 当前班级：一条灰底信息条，不占输入的位置 */
.now {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  margin-bottom: 32rpx;
  background: #FBF6EE;
  border-radius: 18rpx;
}

.now-label {
  font-size: 24rpx;
  color: #9A9186;
}

.now-value {
  font-size: 26rpx;
  font-weight: 600;
  color: #2B2622;
}

/* 第一次进来：用"你好 + 学号"代替"当前班级"那一条 */
.hello {
  margin-bottom: 32rpx;
}

.hello-name {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: #2B2622;
}

.hello-sub {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #9A9186;
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
