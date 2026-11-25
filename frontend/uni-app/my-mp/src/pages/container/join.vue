<template>
  <view class="page">
    <view class="hero">
      <text class="hero-title">加入课程群组</text>
      <text class="hero-sub">输入老师发给你的 10 位邀请码</text>
    </view>

    <!-- 邀请码输入 -->
    <view class="card">
      <input
        class="code-input"
        v-model="code"
        type="text"
        :maxlength="10"
        placeholder="K7M2QX9B4T"
        placeholder-style="color:#D9CFC2;font-size:40rpx;letter-spacing:8rpx"
        @input="onCodeInput"
      />
      <view class="query" :class="{ busy: querying }" @click="onQuery">
        {{ querying ? '查询中…' : '查询群组' }}
      </view>
      <text class="hint">码里的字母不区分大小写，中间有空格也没关系。</text>
    </view>

    <!-- 预览结果 -->
    <view v-if="preview" class="card result">
      <view class="r-head">
        <text class="r-subject">{{ preview.subjectName }}</text>
        <text class="r-name">{{ preview.name }}</text>
      </view>

      <view class="r-meta">
        <text class="r-meta-item">{{ preview.className || '未关联班级' }}</text>
        <text class="dot">·</text>
        <text class="r-meta-item">{{ preview.memberCount }} 人</text>
      </view>

      <!-- 不能加入的三种情况，各说各的，不要合成一句"加入失败" -->
      <view v-if="preview.blacklisted" class="warn-block">
        <text class="warn-text">你已被移出该群组，暂时不能加入。请联系老师解除。</text>
      </view>

      <view v-else-if="preview.myRole > 0" class="ok-block">
        <text class="ok-text">你已经在这个群组里了</text>
        <view class="btn-primary" @click="goChat">进入群组</view>
      </view>

      <view v-else-if="preview.expired" class="warn-block">
        <text class="warn-text">邀请码已过期，请联系老师重新获取。</text>
      </view>

      <template v-else>
        <view v-if="preview.needPassword" class="pwd-row">
          <text class="pwd-label">加入密码</text>
          <input
            class="pwd-input"
            v-model="password"
            :password="true"
            type="text"
            maxlength="20"
            placeholder="请输入老师给的密码"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>

        <view class="btn-primary" :class="{ busy: joining }" @click="onJoin">
          {{ joining ? '加入中…' : '加入群组' }}
        </view>
      </template>
    </view>

  </view>
</template>

<script setup>
import { ref } from 'vue'
import { previewByCode, joinByCode } from '@/api/container'

const code = ref('')
const password = ref('')
const preview = ref(null)
const querying = ref(false)
const joining = ref(false)

/* 输入即转大写、去空格：码本身只有大写，让用户在大小写上纠结没有意义。
   这里不校验长度，因为用户可能还没输完 */
function onCodeInput(e) {
  const v = String(e.detail.value || '').replace(/\s+/g, '').toUpperCase()
  code.value = v
  // 改了码，之前查出来的结果就作废了，否则会出现"码是新的、信息是旧的"
  preview.value = null
  password.value = ''
}

async function onQuery() {
  if (querying.value) return
  const c = (code.value || '').trim()
  if (c.length !== 10) {
    uni.showToast({ title: '邀请码是 10 位', icon: 'none' })
    return
  }
  querying.value = true
  preview.value = null
  try {
    preview.value = await previewByCode(c)
  } catch (e) {
    // 1008 = 码无效/群组已解散，request.js 已经 toast 过
  } finally {
    querying.value = false
  }
}

async function onJoin() {
  if (joining.value) return
  if (preview.value.needPassword && !password.value.trim()) {
    uni.showToast({ title: '请输入加入密码', icon: 'none' })
    return
  }
  joining.value = true
  try {
    const res = await joinByCode(code.value.trim(), preview.value.needPassword ? password.value.trim() : null)
    // 密码用完立刻清掉
    password.value = ''
    uni.showToast({ title: '已加入', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/teacher/container/chat?id=${res.containerId}` })
    }, 600)
  } catch (e) {
    // 1006（已在群组中）/ 1007（密码错）/ 1005（锁定）/ 3003（黑名单）都由 request.js toast
  } finally {
    joining.value = false
  }
}

function goChat() {
  uni.redirectTo({ url: `/pages/teacher/container/chat?id=${preview.value.containerId}` })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 30rpx 24rpx 60rpx;
  box-sizing: border-box;
}

.hero {
  padding: 20rpx 8rpx 30rpx;
}

.hero-title {
  display: block;
  font-size: 40rpx;
  font-weight: 600;
  color: #2B2622;
}

.hero-sub {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #9A9186;
}

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 30rpx 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

/* 邀请码输入框：等宽 + 大字号 + 字距，像在填验证码 */
.code-input {
  height: 110rpx;
  text-align: center;
  font-family: Menlo, Consolas, monospace;
  font-size: 40rpx;
  letter-spacing: 8rpx;
  color: #2B2622;
  background: #FDF8F1;
  border-radius: 18rpx;
}

.query {
  margin-top: 24rpx;
  height: 88rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.query.busy {
  opacity: 0.6;
}

.hint {
  display: block;
  margin-top: 18rpx;
  font-size: 21rpx;
  color: #B4A99B;
  text-align: center;
}

/* ---------- 预览结果 ---------- */
.result {
  margin-top: 20rpx;
}

.r-head {
  display: flex;
  flex-direction: column;
}

.r-subject {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.r-name {
  margin-top: 8rpx;
  font-size: 25rpx;
  color: #7A6F63;
}

.r-meta {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
}

.r-meta-item {
  font-size: 22rpx;
  color: #9A9186;
}

.dot {
  font-size: 22rpx;
  color: #C6BAA9;
  margin: 0 10rpx;
}

.warn-block {
  margin-top: 24rpx;
  padding: 20rpx 22rpx;
  background: #FDEDEB;
  border-radius: 16rpx;
}

.warn-text {
  font-size: 23rpx;
  color: #C5342B;
  line-height: 1.6;
}

.ok-block {
  margin-top: 24rpx;
}

.ok-text {
  display: block;
  font-size: 23rpx;
  color: #5C8A5F;
  margin-bottom: 18rpx;
}

.pwd-row {
  display: flex;
  align-items: center;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #F5EDE2;
}

.pwd-label {
  width: 150rpx;
  font-size: 26rpx;
  color: #2B2622;
  flex-shrink: 0;
}

.pwd-input {
  flex: 1;
  font-size: 26rpx;
  color: #2B2622;
  height: 80rpx;
}

.btn-primary {
  margin-top: 26rpx;
  height: 92rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 29rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 26rpx rgba(197, 52, 43, 0.26);
}

.btn-primary.busy {
  opacity: 0.6;
}
</style>
