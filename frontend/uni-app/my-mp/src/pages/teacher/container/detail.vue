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
      <!-- 头部：群名 / 班级 / 人数 / 群主 / 邀请码 -->
      <view class="head">
        <view class="head-top">
          <text class="subject">{{ d.subjectName || d.name }}</text>
          <role-tag :role="d.myRole" />
        </view>
        <!-- 名称与学科合并后同值，不重复显示 -->
        <text v-if="d.name && d.name !== d.subjectName" class="name">{{ d.name }}</text>

        <view class="meta">
          <text class="meta-item">{{ d.className || '未关联班级' }}</text>
          <text class="dot">·</text>
          <text class="meta-item">{{ (d.memberCount || 0) + (d.studentCount || 0) }} 人</text>
          <text v-if="d.adminCount" class="dot">·</text>
          <text v-if="d.adminCount" class="meta-item">{{ d.adminCount }} 位班委</text>
        </view>

        <text class="owner">群主：{{ d.ownerName }}</text>

        <!-- 邀请码只在有效期内展示。过期了还把码摆在这儿，老师很容易照着念一个作废的码 -->
        <view v-if="d.inviteCode && !d.inviteExpired" class="code-box">
          <view class="code-left">
            <text class="code-label">邀请码</text>
            <text class="code-val">{{ d.inviteCode }}</text>
          </view>
          <text class="copy" @click="copyCode">复制</text>
        </view>

        <view v-if="d.myMuted" class="muted">
          <text class="muted-text">
            你已被禁言{{ d.myMuteUntil === '9999-12-31 23:59:59' ? '（永久）' : '，到期时间 ' + d.myMuteUntil }}
          </text>
        </view>
      </view>

      <!-- 入口：一项一张卡，卡之间留空。
           原来是一张卡里用 1rpx 细线分隔，几行糊成一片，分不清点到哪一行 -->
      <view v-if="d.classId" class="item" hover-class="item-pressed" @click="goClass">
        <text class="item-label">所属班级</text>
        <text class="item-value">{{ d.className || '' }}</text>
        <text class="item-arrow">›</text>
      </view>

      <view class="item" hover-class="item-pressed" @click="go('members')">
        <text class="item-label">成员管理</text>
        <!-- 这一行只数老师：点进去的名单里也只有老师。
             上面那个「N 人」是群里总人数（含学生），两个数字不一样是对的 -->
        <text class="item-value">老师 {{ d.memberCount }} 人</text>
        <text class="item-arrow">›</text>
      </view>

      <view class="item" hover-class="item-pressed" @click="go('highlight')">
        <text class="item-label">精华消息</text>
        <text class="item-arrow">›</text>
      </view>

      <view
        v-if="d.permissions.canViewInvite"
        class="item"
        hover-class="item-pressed"
        @click="go('invite')"
      >
        <text class="item-label">邀请码与加入设置</text>
        <text class="item-value">{{ inviteSub }}</text>
        <text class="item-arrow">›</text>
      </view>

      <view
        v-if="d.permissions.canViewBlacklist"
        class="item"
        hover-class="item-pressed"
        @click="go('blacklist')"
      >
        <text class="item-label">黑名单</text>
        <text class="item-arrow">›</text>
      </view>

      <view
        v-if="d.permissions.canEdit"
        class="item"
        hover-class="item-pressed"
        @click="goEdit"
      >
        <text class="item-label">修改群组信息</text>
        <text class="item-arrow">›</text>
      </view>

      <!-- 危险操作：和上面拉开距离，红字。
           群主只会看到「解散」，普通成员只会看到「退出」，不会同时出现两个 -->
      <view
        v-if="d.myRole !== 1"
        class="item item-danger"
        hover-class="item-pressed"
        @click="onLeave"
      >
        <text class="item-label danger">退出该群组</text>
        <text class="item-arrow">›</text>
      </view>

      <view
        v-if="d.permissions.canDissolve"
        class="item item-danger"
        hover-class="item-pressed"
        @click="onDissolve"
      >
        <text class="item-label danger">解散群组</text>
        <text class="item-arrow">›</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getContainer, dissolveContainer, leaveContainer } from '@/api/container'
import RoleTag from '@/components/role-tag/role-tag.vue'

const id = ref(null)
const d = ref({ permissions: {} })
const loading = ref(true)
const loadErr = ref('')

/* 邀请码状态的短标签，放在「邀请码与加入设置」右侧 */
const inviteSub = computed(() => {
  if (d.value.inviteExpired) return '已过期'
  if (!d.value.inviteExpireAt) return '永久有效'
  return `有效至 ${String(d.value.inviteExpireAt).slice(5, 10)}`
})

onLoad((q) => {
  id.value = Number((q && q.id) || 0)
})

/* 从成员页/邀请码页返回时，人数和码状态可能变了 */
onShow(() => {
  if (id.value) load()
})

async function load() {
  if (!id.value) {
    loadErr.value = '缺少群组 id'
    loading.value = false
    return
  }
  loadErr.value = ''
  try {
    const res = await getContainer(id.value)
    d.value = { ...res, permissions: res.permissions || {} }
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

function copyCode() {
  uni.setClipboardData({
    data: d.value.inviteCode,
    success: () => uni.showToast({ title: '已复制', icon: 'success' })
  })
}

function go(page) {
  uni.navigateTo({ url: `/pages/teacher/container/${page}?id=${id.value}` })
}

function goEdit() {
  uni.navigateTo({ url: `/pages/teacher/container/create?id=${id.value}` })
}

/* 进这个群所属班级的「班级信息」页。
   带 classId 而不是靠全局当前班：从课程群组列表点进来的群，可能不属于当前选中的班 */
function goClass() {
  if (!d.value.classId) return
  uni.navigateTo({ url: `/pages/teacher/class/setting?classId=${d.value.classId}` })
}

/* 退出：只影响自己，不需要打字确认，但要说清"还能再加回来" */
function onLeave() {
  uni.showModal({
    title: '退出该群组',
    content: '退出后你看不到这个群组的内容。只要老师没把你拉黑，随时可以用邀请码重新加入。',
    confirmText: '退出',
    confirmColor: '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await leaveContainer(id.value)
        uni.showToast({ title: '已退出', icon: 'success' })
        setTimeout(() => {
          // 详情页不能 navigateBack 回自己，直接回列表
          uni.redirectTo({ url: '/pages/teacher/container/index' })
        }, 600)
      } catch (e) {
        // request.js 已 toast
      }
    }
  })
}

/* 解散：要求手打群组名。一个「确定吗」的弹窗拦不住手滑，
   而解散之后全员都进不来，代价太大 */
function onDissolve() {
  uni.showModal({
    title: '解散群组',
    content: `解散后所有成员都无法再访问，且不可恢复。\n\n请输入群组名称「${d.value.name}」以确认：`,
    editable: true,
    placeholderText: d.value.name,
    confirmText: '解散',
    confirmColor: '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      const input = (res.content || '').trim()
      if (input !== d.value.name) {
        uni.showToast({ title: '群组名称不一致', icon: 'none' })
        return
      }
      try {
        await dissolveContainer(id.value, input)
        uni.showToast({ title: '已解散', icon: 'success' })
        setTimeout(() => {
          uni.redirectTo({ url: '/pages/teacher/container/index' })
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
  justify-content: space-between;
}

.subject {
  font-size: 34rpx;
  font-weight: 600;
  color: #2B2622;
}

.name {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #7A6F63;
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

.code-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding: 20rpx 24rpx;
  background: #FDF8F1;
  border-radius: 18rpx;
}

.code-left {
  display: flex;
  flex-direction: column;
}

.code-label {
  font-size: 20rpx;
  color: #B4A99B;
}

.code-val {
  margin-top: 6rpx;
  font-family: Menlo, Consolas, monospace;
  font-size: 36rpx;
  letter-spacing: 4rpx;
  color: #2B2622;
  font-weight: 600;
}

.copy {
  font-size: 24rpx;
  color: #C5342B;
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
}

.muted {
  margin-top: 18rpx;
  padding: 16rpx 20rpx;
  background: #FDEDEB;
  border-radius: 14rpx;
}

.muted-text {
  font-size: 22rpx;
  color: #C5342B;
}

/* ---------- 入口 ---------- */
/* 一项一张卡：卡片之间靠留白分界，不靠 1rpx 的细线。
   overflow: hidden 是为了让 hover 背景不溢出圆角 */
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

/* 右侧的值是信息，不是解释；字号比标题小一档，避免和标题抢注意力 */
.item-value {
  font-size: 23rpx;
  color: #A79B8C;
  margin-right: 10rpx;
  max-width: 300rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.item-arrow {
  font-size: 32rpx;
  color: #C6BAA9;
  line-height: 1;
}

/* 危险项：和上面的入口拉开距离，红字 */
.item-danger {
  margin-top: 36rpx;
}

.danger {
  color: #C5342B;
}
</style>
