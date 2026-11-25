<template>
  <view class="page">
    <view class="card">
      <view class="row">
        <text class="label">姓名<text class="req">*</text></text>
        <input
          class="input"
          v-model="form.name"
          type="text"
          maxlength="16"
          placeholder="请填写姓名"
          placeholder-style="color:#B4A99B;font-size:26rpx"
        />
      </view>

      <view class="row line">
        <text class="label">单位</text>
        <input
          class="input"
          v-model="form.unit"
          type="text"
          maxlength="64"
          placeholder="如 市第一中学，选填"
          placeholder-style="color:#B4A99B;font-size:26rpx"
        />
      </view>

      <view class="row line">
        <text class="label">手机号</text>
        <input
          class="input"
          v-model="form.phone"
          type="number"
          maxlength="11"
          placeholder="选填"
          placeholder-style="color:#B4A99B;font-size:26rpx"
        />
      </view>
    </view>

    <!-- 用「完成」不用「保存修改」：这一页就两行，按钮文案越短越不占注意力 -->
    <view class="save" :class="{ busy: saving }" @click="onSave">
      {{ saving ? '保存中…' : '完成' }}
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { fetchMe, updateProfile } from '@/api/auth'

const saving = ref(false)

const form = reactive({
  name: '',
  unit: '',
  phone: ''
})

/* 初值先用本地缓存填上，页面一打开就是当前值；
   再用接口校正一次 —— 缓存可能是很久以前存的 */
onLoad(() => {
  const cached = uni.getStorageSync('userInfo') || {}
  form.name = cached.name || ''
  form.unit = cached.unit || ''
  form.phone = cached.phone || ''

  fetchMe()
    .then((me) => {
      if (!me) return
      form.name = me.name || ''
      form.unit = me.unit || ''
      form.phone = me.phone || ''
    })
    .catch(() => {
      /* 拿不到就继续用缓存，不挡着用户改 */
    })
})

async function onSave() {
  if (saving.value) return

  const name = (form.name || '').trim()
  if (!name) {
    uni.showToast({ title: '请填写姓名', icon: 'none' })
    return
  }

  const phone = (form.phone || '').trim()
  /* 手机号是选填的，填了才校验。前端先拦一次，
     省得为了一个明显的格式错误往返一次服务器 */
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
    uni.showToast({ title: '手机号格式不对', icon: 'none' })
    return
  }

  saving.value = true
  try {
    const me = await updateProfile({ name, phone, unit: (form.unit || '').trim() })
    /* 接口返回的就是改完的最新资料，直接覆盖缓存，
       个人中心返回时用 onShow 重新拉，能看到新名字 */
    if (me) uni.setStorageSync('userInfo', me)
    uni.showToast({ title: '已保存', icon: 'success' })
    /* 停 600ms 再返回：toast 还没消失就跳走，用户看不到「已保存」 */
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    /* request.js 已 toast 具体原因（比如「姓名最多 16 个字」） */
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 24rpx 24rpx 60rpx;
  box-sizing: border-box;
}

.card {
  padding: 8rpx 28rpx 4rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.row {
  display: flex;
  align-items: center;
  min-height: 104rpx;
}

/* 分隔线只画在第二行顶部，第一行不画 —— 卡片顶部不该有一条悬空的线 */
.row.line {
  border-top: 1rpx solid #F5EDE2;
}

.label {
  width: 150rpx;
  flex-shrink: 0;
  font-size: 27rpx;
  color: #2B2622;
}

.req {
  color: #C5342B;
  font-size: 26rpx;
  margin-left: 4rpx;
}

.input {
  flex: 1;
  height: 104rpx;
  line-height: 104rpx;
  font-size: 27rpx;
  color: #2B2622;
}

.save {
  margin-top: 44rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 28rpx rgba(197, 52, 43, 0.28);
}

.save.busy {
  opacity: 0.6;
}
</style>
