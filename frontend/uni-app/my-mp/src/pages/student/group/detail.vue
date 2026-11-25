<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else>
      <!-- 头部：群名 / 班级 / 人数 / 群主。
           和老师端群信息页同一套版式 —— 同一个群，两端打开应该长得一样，
           只是学生这边没有成员管理、邀请码、黑名单这些管理项 -->
      <view class="head">
        <view class="head-top">
          <text class="name">{{ g.name || g.subjectName }}</text>
          <text v-if="g.classGroup" class="tag">本班</text>
        </view>

        <view class="meta">
          <text class="meta-item">{{ g.className || '未关联班级' }}</text>
          <text class="dot">·</text>
          <text class="meta-item">{{ g.memberCount || 0 }} 人</text>
        </view>

        <text v-if="g.ownerName" class="owner">群主：{{ g.ownerName }}</text>

        <!-- 老师 / 学生分开数：一个班群里学生几十个、老师一个，
             只给总数看不出"这个群里有没有老师" -->
        <view class="counts">
          <view class="count">
            <text class="count-num">{{ g.teacherCount || 0 }}</text>
            <text class="count-label">老师</text>
          </view>
          <view class="count-line"></view>
          <view class="count">
            <text class="count-num">{{ g.studentCount || 0 }}</text>
            <text class="count-label">学生</text>
          </view>
        </view>
      </view>

      <!-- 退出：只给"用邀请码加进来的群"。
           本班的群退不掉（在不在群里由班级决定），所以连按钮都不给 ——
           给一个点了报错的按钮比不给更糟 -->
      <view v-if="g.canLeave" class="item item-danger" hover-class="item-pressed" @click="onLeave">
        <text class="item-label danger">退出群聊</text>
        <text class="item-arrow">›</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { fetchGroupInfo, leaveGroup } from '@/api/student-data'

const id = ref(0)
const g = ref({})
const loading = ref(true)
const loadErr = ref('')

onLoad((q) => {
  id.value = Number((q && q.id) || 0)
})

/* 从聊天页返回时人数可能变了，每次显示都重拉一次 */
onShow(() => {
  if (id.value) load()
})

async function load() {
  if (!id.value) {
    loadErr.value = '缺少群 id'
    loading.value = false
    return
  }
  loadErr.value = ''
  try {
    g.value = (await fetchGroupInfo(id.value)) || {}
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

function onLeave() {
  uni.showModal({
    title: '退出群聊',
    content: `退出后你看不到「${g.value.name || '这个群'}」的消息，要再进来得重新找老师要邀请码。`,
    confirmText: '退出',
    confirmColor: '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await leaveGroup(id.value)
        uni.showToast({ title: '已退出', icon: 'success' })
        setTimeout(() => {
          /* 群已经不在列表里了，退回自己会白屏，直接回通讯录 */
          uni.reLaunch({ url: '/pages/student/contact/index' })
        }, 600)
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

/* ---------- 加载 / 出错 ---------- */
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

/* ---------- 头部 ---------- */
.head {
  background: #FFFFFF;
  border-radius: 32rpx;
  padding: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.06);
}

.head-top {
  display: flex;
  align-items: center;
}

.name {
  max-width: 480rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: #2B2622;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag {
  margin-left: 12rpx;
  padding: 0 12rpx;
  border-radius: 8rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 19rpx;
  line-height: 30rpx;
  flex-shrink: 0;
}

.meta {
  display: flex;
  align-items: center;
  margin-top: 14rpx;
}

.meta-item {
  font-size: 22rpx;
  color: #9A9186;
}

.dot {
  font-size: 22rpx;
  color: #C6BAA9;
  margin: 0 10rpx;
}

.owner {
  display: block;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #B4A99B;
}

/* 两个数字并排，中间一条竖线。不做成进度条 —— 老师和学生不是比例关系 */
.counts {
  display: flex;
  align-items: center;
  margin-top: 24rpx;
  padding: 22rpx 0;
  background: #FDF8F1;
  border-radius: 18rpx;
}

.count {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.count-num {
  font-size: 34rpx;
  font-weight: 600;
  color: #2B2622;
}

.count-label {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #9A9186;
}

.count-line {
  width: 1rpx;
  height: 48rpx;
  background: #F0E4D4;
}

/* ---------- 入口 ---------- */
.item {
  display: flex;
  align-items: center;
  min-height: 108rpx;
  margin-top: 16rpx;
  padding: 0 28rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.item-pressed {
  background: #FBF5EC;
}

.item-label {
  flex: 1;
  font-size: 29rpx;
  color: #2B2622;
}

.item-arrow {
  font-size: 32rpx;
  color: #C6BAA9;
  line-height: 1;
}

/* 危险项：和上面的内容拉开距离，红字 */
.item-danger {
  margin-top: 36rpx;
}

.danger {
  color: #C5342B;
}
</style>
