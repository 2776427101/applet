<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr && !list.length" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else>
      <view v-if="!list.length" class="empty">
        <text class="empty-title">黑名单是空的</text>
        <text class="empty-text">
          移出成员时选择「移出并拉黑」，对方就会出现在这里，且不能再用邀请码加入。
        </text>
      </view>

      <view v-else class="list">
        <view v-for="b in list" :key="b.userId" class="item">
          <view class="main">
            <view class="name-line">
              <text class="name">{{ b.displayName }}</text>
            </view>
            <text class="reason">{{ b.reason || '未填写原因' }}</text>
            <text class="meta">
              {{ b.operatorName }} 于 {{ (b.createdAt || '').slice(0, 10) }} 拉黑
            </text>
          </view>
          <view class="release" @click="onRelease(b)">解除</view>
        </view>
      </view>

      <view class="note">
        <text class="note-text">
          解除拉黑后，对方不会自动回到群组里，需要他自己重新用邀请码加入。
          这里只有群主能看到 —— 被谁拉黑、为什么拉黑，不展示给班委。
        </text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { listBlacklist, releaseBlacklist } from '@/api/container'

const id = ref(0)
const list = ref([])
const loading = ref(true)
const loadErr = ref('')

onLoad((q) => {
  id.value = Number((q && q.id) || 0)
})

onShow(() => load())

async function load() {
  if (!id.value) {
    loadErr.value = '缺少群组 id'
    loading.value = false
    return
  }
  loadErr.value = ''
  try {
    const rows = await listBlacklist(id.value)
    list.value = rows || []
  } catch (e) {
    if (!list.value.length) loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

function onRelease(b) {
  uni.showModal({
    title: '解除黑名单',
    content: `解除后「${b.displayName}」可以重新用邀请码加入。不会自动回群。`,
    confirmText: '解除',
    confirmColor: '#C5342B',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await releaseBlacklist(id.value, b.userId)
        uni.showToast({ title: '已解除', icon: 'success' })
        load()
      } catch (e) {
        // request.js 已 toast
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 60rpx;
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

.empty {
  padding: 140rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-title {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
}

.empty-text {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: #9A9186;
  text-align: center;
  line-height: 1.7;
}

.list {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 0 26rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.item {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-bottom: 1rpx solid #F5EDE2;
}

.item:last-child {
  border-bottom: none;
}

.main {
  flex: 1;
  min-width: 0;
}

.name-line {
  display: flex;
  align-items: center;
}

.name {
  font-size: 28rpx;
  color: #2B2622;
}

.reason {
  display: block;
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #7A6F63;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.meta {
  display: block;
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

.release {
  flex-shrink: 0;
  margin-left: 18rpx;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 24rpx;
}

.note {
  margin-top: 22rpx;
  padding: 20rpx 24rpx;
  background: #F7EDD2;
  border-radius: 18rpx;
}

.note-text {
  font-size: 21rpx;
  color: #9A8158;
  line-height: 1.7;
}
</style>
