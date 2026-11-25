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
      <view v-if="list.length" class="card">
        <view
          class="row"
          v-for="(n, i) in list"
          :key="i"
          hover-class="row-pressed"
          @click="open(n)"
        >
          <view class="ic" :class="'k-' + n.kind">{{ icon(n.kind) }}</view>
          <view class="main">
            <text class="title">{{ n.title }}</text>
            <text class="desc">
              {{ n.className ? n.className + ' · ' : '' }}{{ n.desc }}
            </text>
          </view>
          <view class="side">
            <text class="time">{{ n.atText }}</text>
            <text class="chev">›</text>
          </view>
        </view>
      </view>

      <view v-else class="empty">
        <text class="empty-icon">🔔</text>
        <text class="empty-title">暂无新通知</text>
        <text class="empty-tip">有学生请假、待办到期时会出现在这里</text>
      </view>

      <text v-if="list.length" class="foot">处理完就会自动消失，不用手动清</text>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { fetchNotices } from '@/api/home'

/* classId 只用于「生日 → 进那个班的学生名单」，不再用来过滤通知。
   v0.44 起通知跨班聚合：老师带两个班时，不用切班也能看到另一个班有人请假。
   带参数进来（旧链接）仍然能用，只是它不再收窄列表 */
const classId = ref(0)
const list = ref([])
const loading = ref(true)
const err = ref('')

/* 一页最多显示多少条。超过这个数说明积压太多，
   首页那个「N 条」本来也只是个提醒，不指望在这里全处理完 */
const LIMIT = 50

/* 和首页同一个 key：进来就算看过了，红点灭掉。
   从首页点进来时首页已经记过一次，这里再记一次是为了覆盖"从别处进来"的情况 */
const SEEN_KEY = 'teacher_notice_seen_at'

onLoad((q) => {
  classId.value = Number((q && q.classId) || 0)
})

/* 用 onShow：从请假页批完假返回时，那条通知应该已经没了 */
onShow(() => load())

async function load() {
  loading.value = true
  err.value = ''
  try {
    const res = await fetchNotices({ limit: LIMIT, silent: true })
    list.value = (res && res.list) || []
    markSeen()
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

function markSeen() {
  const top = list.value[0]
  const at = top ? Number(top.atMillis || 0) : 0
  if (!at) return
  try {
    const old = Number(uni.getStorageSync(SEEN_KEY) || 0)
    if (at > old) uni.setStorageSync(SEEN_KEY, at)
  } catch (e) {
    /* 存不进去不影响这一页 */
  }
}

/* 图标和首页保持一致，两处看到的「📝」必须是同一个意思 */
function icon(kind) {
  if (kind === 'leave') return '📝'
  if (kind === 'todo') return '⏰'
  if (kind === 'birthday') return '🎂'
  return '🔔'
}

function open(n) {
  if (n.kind === 'leave') {
    /* 请假页是 tabBar 页，只能 switchTab；它的默认 tab 就是「待审批」，
       所以不用（也没法）带参数 */
    uni.switchTab({ url: '/pages/teacher/leave/index' })
    return
  }
  if (n.kind === 'todo') {
    uni.navigateTo({ url: '/pages/teacher/todo/index' })
    return
  }
  if (n.kind === 'birthday') {
    /* 生日要进的是"这个学生那个班"的名单，不是当前班 ——
       跨班聚合之后，二班学生的生日点进去看到的应该是一班的名单才对，
       所以优先用通知自己带的 classId */
    const cid = n.classId || classId.value
    const url = cid
      ? `/pages/teacher/student/index?classId=${cid}`
      : '/pages/teacher/student/index'
    uni.navigateTo({ url })
  }
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

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 4rpx 24rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.row {
  display: flex;
  align-items: center;
  padding: 26rpx 0;
  border-top: 1rpx solid #F5EDE2;
}

/* 第一行不要上边框：卡片已经有圆角，顶边会切出一道硬线 */
.row:first-child {
  border-top: none;
}

.row-pressed {
  background: #FDFAF6;
}

.ic {
  width: 68rpx;
  height: 68rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  flex-shrink: 0;
}

/* 三种通知三种底色，和首页那个小图标是一套语义 */
.k-leave {
  background: #FDEDEB;
}

.k-todo {
  background: #FBF3DC;
}

.k-birthday {
  background: #EDF6EE;
}

.main {
  flex: 1;
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.title {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
  line-height: 1.35;
}

.desc {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9A9186;
  line-height: 1.4;
}

.side {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-left: 14rpx;
}

.time {
  font-size: 21rpx;
  color: #B4A99B;
}

.chev {
  font-size: 30rpx;
  color: #CFC5B8;
  margin-left: 6rpx;
  line-height: 1;
}

.empty {
  padding: 140rpx 0 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-icon {
  font-size: 60rpx;
  opacity: 0.5;
}

.empty-title {
  margin-top: 22rpx;
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
}

.empty-tip {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.foot {
  display: block;
  padding: 24rpx 8rpx 0;
  font-size: 21rpx;
  color: #B4A99B;
  text-align: center;
}
</style>
