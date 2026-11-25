<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr && !list.length" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="init">重试</view>
    </view>

    <template v-else>
      <view v-if="!list.length" class="empty">
        <text class="empty-title">还没有精华消息</text>
        <text class="empty-text">
          精华是给全班留的"要点"，比如作业要求、易错点、复习提纲。
          {{ canHighlight ? '点右下角 ＋ 收录一条。' : '班委和老师可以收录。' }}
        </text>
      </view>

      <view v-else class="list">
        <view
          v-for="h in list"
          :key="h.id"
          class="card"
          hover-class="card-pressed"
          @click="onTap(h)"
        >
          <text class="content">{{ h.content }}</text>
          <view class="foot">
            <text class="who">{{ h.creatorName }}</text>
            <text class="dot">·</text>
            <text class="time">{{ (h.createdAt || '').slice(5, 16) }}</text>
            <text v-if="h.mine" class="mine">我收录</text>
          </view>
        </view>
      </view>

      <view class="note">
        <text class="note-text">
          本期还没有聊天模块，所以精华是直接写要点，不是"从消息里挑一条"。
          聊天上线后可以长按消息设为精华，这里的记录会继续保留。
        </text>
      </view>
    </template>

    <view v-if="canHighlight" class="fab" @click="onAdd">
      <text class="fab-plus">＋</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getContainer, listHighlights, addHighlight, cancelHighlight } from '@/api/container'

const id = ref(0)
const list = ref([])
const loading = ref(true)
const loadErr = ref('')
const canHighlight = ref(false)
const isOwner = ref(false)

onLoad((q) => {
  id.value = Number((q && q.id) || 0)
})

onShow(() => init())

async function init() {
  if (!id.value) {
    loadErr.value = '缺少群组 id'
    loading.value = false
    return
  }
  loading.value = true
  loadErr.value = ''
  try {
    const d = await getContainer(id.value)
    canHighlight.value = !!(d.permissions && d.permissions.canHighlight)
    isOwner.value = d.myRole === 1
    await load()
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

async function load() {
  loadErr.value = ''
  try {
    const rows = await listHighlights(id.value)
    list.value = rows || []
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  }
}

function onAdd() {
  uni.showModal({
    title: '收录精华',
    content: '',
    editable: true,
    placeholderText: '写下要点，最多 500 字',
    confirmText: '收录',
    confirmColor: '#C5342B',
    success: async (r) => {
      if (!r.confirm) return
      const content = (r.content || '').trim()
      if (!content) {
        uni.showToast({ title: '内容不能为空', icon: 'none' })
        return
      }
      try {
        await addHighlight(id.value, content)
        uni.showToast({ title: '已收录', icon: 'success' })
        load()
      } catch (e) {
        // request.js 已 toast
      }
    }
  })
}

/* 只有「群主」或「自己收录的」能取消。班委不能删别人的整理成果 */
function onTap(h) {
  const canCancel = isOwner.value || h.mine
  if (!canCancel) return

  uni.showModal({
    title: '取消精华',
    content: '取消后这条会从精华列表里移除，其他内容不受影响。',
    confirmText: '取消精华',
    confirmColor: '#C5342B',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await cancelHighlight(id.value, h.id)
        uni.showToast({ title: '已取消', icon: 'success' })
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

.card {
  background: #FFFFFF;
  border-radius: 26rpx;
  padding: 26rpx 28rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.card-pressed {
  background: #FDFAF6;
}

.content {
  font-size: 27rpx;
  color: #2B2622;
  line-height: 1.7;
  /* 保留用户输入的换行 */
  white-space: pre-wrap;
  word-break: break-all;
}

.foot {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.who {
  font-size: 21rpx;
  color: #9A9186;
}

.dot {
  font-size: 21rpx;
  color: #C6BAA9;
  margin: 0 8rpx;
}

.time {
  font-size: 21rpx;
  color: #B4A99B;
  flex: 1;
}

.mine {
  font-size: 19rpx;
  color: #8A6A2E;
  background: #FBF3DC;
  border-radius: 6rpx;
  padding: 2rpx 10rpx;
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

.fab {
  position: fixed;
  right: 40rpx;
  bottom: calc(56rpx + var(--window-bottom, 0px));
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14rpx 30rpx rgba(197, 52, 43, 0.32);
}

.fab-plus {
  color: #FFFFFF;
  font-size: 52rpx;
  line-height: 1;
  margin-top: -4rpx;
}
</style>
