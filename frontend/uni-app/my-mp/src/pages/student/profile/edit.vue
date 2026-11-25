<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="err" class="state">
      <text class="state-title">{{ err }}</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else>
      <view class="card">
        <view class="field">
          <text class="label">手机号</text>
          <input
            class="input"
            v-model="form.phone"
            type="number"
            :maxlength="20"
            placeholder="没填就留空"
            placeholder-style="color:#B4A99B"
          />
        </view>

        <view class="field">
          <text class="label">微信号</text>
          <input
            class="input"
            v-model="form.wechat"
            :maxlength="64"
            placeholder="没填就留空"
            placeholder-style="color:#B4A99B"
          />
        </view>

        <view class="field field-last">
          <text class="label">宿舍</text>
          <input
            class="input"
            v-model="form.dormitory"
            :maxlength="32"
            placeholder="比如 6 号楼 302"
            placeholder-style="color:#B4A99B"
          />
        </view>
      </view>

      <view
        class="submit"
        :class="{ 'submit-off': saving }"
        hover-class="submit-pressed"
        @click="onSave"
      >
        <text class="submit-text">{{ saving ? '保存中…' : '保存' }}</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchMyProfile, updateMyProfile } from '@/api/student-data'

const loading = ref(true)
const saving = ref(false)
const err = ref('')

const form = reactive({
  phone: '',
  wechat: '',
  dormitory: ''
})

onLoad(load)

async function load() {
  err.value = ''
  try {
    const p = (await fetchMyProfile()) || {}
    form.phone = p.phone || ''
    form.wechat = p.wechat || ''
    form.dormitory = p.dormitory || ''
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onSave() {
  if (saving.value) return

  const phone = form.phone.trim()
  /* 手机号只做长度和字符校验，不硬套 11 位 ——
     家长可能填座机，留学生可能填境外号码，卡死格式只会让人填不进来 */
  if (phone && !/^[0-9+\-\s]{5,20}$/.test(phone)) {
    uni.showToast({ title: '手机号格式看着不对', icon: 'none' })
    return
  }

  saving.value = true
  try {
    /* 三个字段都传（哪怕是空串）。
       空串在后端表示"清空"，不传表示"不改" —— 这里用户是整表保存，
       所以三项都要显式传，否则删掉的内容不会生效 */
    await updateMyProfile({
      phone,
      wechat: form.wechat.trim(),
      dormitory: form.dormitory.trim()
    })
    uni.showToast({ title: '已保存', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    /* request.js 已经 toast 过 */
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 40rpx;
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

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 4rpx 26rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.field {
  padding: 26rpx 0;
  border-bottom: 1rpx solid #F5EDE2;
}

.field-last {
  border-bottom: none;
}

.label {
  display: block;
  font-size: 24rpx;
  color: #9A9186;
  margin-bottom: 12rpx;
}

.input {
  height: 64rpx;
  font-size: 28rpx;
  color: #2B2622;
  background: #FBF7F1;
  border-radius: 16rpx;
  padding: 0 20rpx;
  box-sizing: border-box;
}

/* 说明块：浅底、小字、不抢按钮。这段是"解释"，不是"警告" */
.submit {
  margin-top: 28rpx;
  height: 92rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #E8594F 0%, #C5342B 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 24rpx rgba(197, 52, 43, 0.24);
}

.submit-pressed {
  opacity: 0.9;
}

.submit-off {
  opacity: 0.6;
}

.submit-text {
  font-size: 30rpx;
  color: #FFFFFF;
  font-weight: 600;
}
</style>
