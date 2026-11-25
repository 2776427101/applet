<template>
  <view class="page">
    <!-- 筛选：在群 / 班委 / 成员 / 已移出 -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ on: tab === t.key }"
        @click="switchTab(t.key)"
      >
        <text class="tab-text">{{ t.label }}</text>
        <view v-if="tab === t.key" class="tab-line"></view>
      </view>
    </view>

    <view class="search">
      <input
        class="search-input"
        v-model="keyword"
        type="text"
        confirm-type="search"
        placeholder="搜姓名或群昵称"
        placeholder-style="color:#B4A99B;font-size:26rpx"
        @confirm="load"
      />
      <text v-if="keyword" class="search-clear" @click="clearKeyword">×</text>
    </view>

    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr && !list.length" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="init">重试</view>
    </view>

    <view v-else-if="!list.length" class="empty">
      <text class="empty-title">{{ emptyTitle }}</text>
      <text class="empty-text">{{ emptyText }}</text>
    </view>

    <view v-else class="list">
      <view v-if="loadErr" class="stale">刷新失败，下面是上次的数据</view>

      <view
        v-for="m in list"
        :key="m.userId"
        class="item"
        :class="{ disabled: tab === 'removed' }"
        hover-class="item-pressed"
        @click="onTap(m)"
      >
        <!-- 头像用首字占位：t_user.avatar 多数是空的，加载网络图会一片灰 -->
        <view class="avatar">
          <image v-if="m.avatar" class="avatar-img" :src="m.avatar" mode="aspectFill" />
          <text v-else class="avatar-text">{{ firstChar(m.displayName) }}</text>
        </view>

        <view class="main">
          <view class="name-line">
            <text class="name">{{ m.displayName }}</text>
            <text v-if="m.self" class="self">我</text>
            <role-tag :role="m.role" />
          </view>
          <view class="sub-line">
            <text v-if="m.muted" class="muted-tag">已禁言</text>
            <text class="sub-text">{{ subText(m) }}</text>
          </view>
        </view>

        <text v-if="canOperate(m)" class="more">⋯</text>
      </view>
    </view>

    <view class="note">
      <text class="note-text">
        群主可以设班委、禁言、移出成员；班委可以禁言普通成员、收录精华，但不能移出成员。
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getContainer, listMembers, muteMember, removeMember, setMemberRole } from '@/api/container'
import RoleTag from '@/components/role-tag/role-tag.vue'

const tabs = [
  { key: 'all', label: '在群' },
  { key: 'admin', label: '班委' },
  { key: 'member', label: '成员' },
  { key: 'removed', label: '已移出' }
]

const id = ref(0)
const myRole = ref(0)
const tab = ref('all')
const keyword = ref('')
const list = ref([])
const loading = ref(true)
const loadErr = ref('')

const emptyTitle = computed(() => (keyword.value ? '没有匹配的成员' : '这里还没有人'))
const emptyText = computed(() => {
  if (keyword.value) return '换个关键词试试，或者清空搜索'
  if (tab.value === 'removed') return '没有被移出的成员'
  return '把邀请码发给学生，他们加入后会出现在这里'
})

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
    myRole.value = d.myRole || 0
    await load()
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

async function load() {
  loadErr.value = ''
  const params = {}
  if (tab.value === 'removed') {
    params.status = 2
  } else if (tab.value === 'admin') {
    params.role = 2
  } else if (tab.value === 'member') {
    params.role = 3
  }
  if (keyword.value.trim()) params.keyword = keyword.value.trim()
  try {
    const d = await listMembers(id.value, params)
    list.value = (d && d.list) || []
  } catch (e) {
    if (!list.value.length) loadErr.value = (e && e.message) || '加载失败'
    else loadErr.value = (e && e.message) || '刷新失败'
  }
}

function switchTab(k) {
  if (tab.value === k) return
  tab.value = k
  list.value = []
  load()
}

function clearKeyword() {
  keyword.value = ''
  load()
}

const firstChar = (s) => (s ? String(s).slice(0, 1) : '?')

function subText(m) {
  if (m.status === 2) return `已移出 · 加入于 ${(m.joinedAt || '').slice(0, 10)}`
  if (m.muted) {
    const until = m.muteUntil === '9999-12-31 23:59:59' ? '永久' : (m.muteUntil || '').slice(5, 16)
    return `禁言至 ${until}${m.muteReason ? ' · ' + m.muteReason : ''}`
  }
  return `加入于 ${(m.joinedAt || '').slice(0, 10)}`
}

/* 有没有可操作项。真正的权限判断在后端，这里只决定「要不要显示 ⋯」 */
function canOperate(m) {
  if (m.self) return false
  if (m.status !== 1) return false
  if (m.role === 1) return false
  if (myRole.value === 1) return true
  // 班委：只能操作普通成员
  return myRole.value === 2 && m.role === 3
}

function actionsFor(m) {
  const out = []
  if (myRole.value === 1) {
    if (m.role === 3) out.push({ key: 'toAdmin', label: '设为班委' })
    if (m.role === 2) out.push({ key: 'toMember', label: '取消班委' })
    out.push({ key: 'mute', label: m.muted ? '解除禁言' : '禁言' })
    out.push({ key: 'remove', label: '移出群组' })
  } else if (myRole.value === 2 && m.role === 3) {
    out.push({ key: 'mute', label: m.muted ? '解除禁言' : '禁言' })
  }
  return out
}

const sheet = (items) => new Promise((resolve) => {
  uni.showActionSheet({
    itemList: items,
    success: (r) => resolve(r.tapIndex),
    fail: () => resolve(-1)
  })
})

const ask = (opts) => new Promise((resolve) => {
  uni.showModal({ ...opts, success: (r) => resolve(r), fail: () => resolve({ confirm: false }) })
})

async function onTap(m) {
  if (!canOperate(m)) return
  const actions = actionsFor(m)
  if (!actions.length) return

  const idx = await sheet(actions.map((a) => a.label))
  if (idx < 0) return
  const act = actions[idx]

  if (act.key === 'toAdmin') return doSetRole(m, 2)
  if (act.key === 'toMember') return doSetRole(m, 3)
  if (act.key === 'mute') return doMute(m)
  if (act.key === 'remove') return doRemove(m)
}

async function doSetRole(m, role) {
  const isAdmin = role === 2
  const r = await ask({
    title: isAdmin ? '设为班委' : '取消班委',
    content: isAdmin
      ? `「${m.displayName}」可以禁言普通成员、收录精华，但不能移出成员。`
      : `「${m.displayName}」将变回普通成员。`,
    confirmText: '确定',
    confirmColor: '#C5342B'
  })
  if (!r.confirm) return
  try {
    await setMemberRole(id.value, m.userId, role)
    uni.showToast({ title: '已更新', icon: 'success' })
    load()
  } catch (e) {
    // request.js 已 toast
  }
}

const MUTE_OPTIONS = [
  { label: '10 分钟', minutes: 10 },
  { label: '1 小时', minutes: 60 },
  { label: '1 天', minutes: 1440 },
  { label: '7 天', minutes: 10080 },
  { label: '永久', minutes: -1 }
]

async function doMute(m) {
  // 已禁言 → 解除，不需要原因
  if (m.muted) {
    const r = await ask({
      title: '解除禁言',
      content: `恢复「${m.displayName}」的发言权限？`,
      confirmText: '解除',
      confirmColor: '#C5342B'
    })
    if (!r.confirm) return
    try {
      await muteMember(id.value, m.userId, { muteMinutes: 0 })
      uni.showToast({ title: '已解除', icon: 'success' })
      load()
    } catch (e) {
      // request.js 已 toast
    }
    return
  }

  const idx = await sheet(MUTE_OPTIONS.map((o) => o.label))
  if (idx < 0) return
  const opt = MUTE_OPTIONS[idx]

  // 原因必填：禁言要能对质，事后只看到"被禁言"没法跟家长解释
  const r = await ask({
    title: `禁言 ${opt.label}`,
    content: '',
    editable: true,
    placeholderText: '填写原因，如：上课讲话',
    confirmText: '禁言',
    confirmColor: '#C5342B'
  })
  if (!r.confirm) return
  const reason = (r.content || '').trim()
  if (!reason) {
    uni.showToast({ title: '请填写禁言原因', icon: 'none' })
    return
  }

  try {
    await muteMember(id.value, m.userId, { muteMinutes: opt.minutes, reason })
    uni.showToast({ title: '已禁言', icon: 'success' })
    load()
  } catch (e) {
    // request.js 已 toast
  }
}

async function doRemove(m) {
  // 把「是否拉黑」做成两个明确选项，而不是一个复选框：
  // 这两件事的后果差别很大，藏在二级弹窗里的勾选框很容易被忽略
  const idx = await sheet(['移出（可再次加入）', '移出并拉黑（不能再加入）'])
  if (idx < 0) return
  const blacklist = idx === 1

  const r = await ask({
    title: blacklist ? '移出并拉黑' : '移出成员',
    content: '',
    editable: true,
    placeholderText: blacklist ? '原因（可选，只有群主能看到）' : '原因（可选）',
    confirmText: '移出',
    confirmColor: '#C5342B'
  })
  if (!r.confirm) return

  try {
    await removeMember(id.value, m.userId, blacklist, (r.content || '').trim())
    uni.showToast({ title: blacklist ? '已移出并拉黑' : '已移出', icon: 'success' })
    load()
  } catch (e) {
    // request.js 已 toast
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 16rpx 24rpx 60rpx;
  box-sizing: border-box;
}

/* ---------- tab ---------- */
.tabs {
  display: flex;
  align-items: flex-end;
  padding: 8rpx 0 4rpx;
}

.tab {
  position: relative;
  margin-right: 30rpx;
  padding-bottom: 14rpx;
}

.tab-text {
  font-size: 27rpx;
  color: #9A9186;
}

.tab.on .tab-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
}

.tab-line {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 32rpx;
  height: 6rpx;
  margin-left: -16rpx;
  border-radius: 3rpx;
  background: #C5342B;
}

/* ---------- 搜索 ---------- */
.search {
  display: flex;
  align-items: center;
  height: 76rpx;
  padding: 0 24rpx;
  margin-top: 8rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
}

.search-input {
  flex: 1;
  font-size: 26rpx;
  color: #2B2622;
}

.search-clear {
  font-size: 34rpx;
  color: #C6BAA9;
  padding-left: 16rpx;
  line-height: 1;
}

/* ---------- 状态 ---------- */
.state {
  padding: 140rpx 40rpx;
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

/* ---------- 列表 ---------- */
.list {
  margin-top: 18rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 0 24rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.stale {
  margin: 16rpx 0;
  padding: 12rpx 20rpx;
  background: #FBF3DC;
  border-radius: 12rpx;
  font-size: 21rpx;
  color: #8A6A2E;
}

.item {
  display: flex;
  align-items: center;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #F5EDE2;
}

.item:last-child {
  border-bottom: none;
}

.item-pressed {
  background: #FDFAF6;
}

.item.disabled {
  opacity: 0.6;
}

.avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: #F3ECE2;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}

.avatar-img {
  width: 100%;
  height: 100%;
}

.avatar-text {
  font-size: 30rpx;
  color: #8A8178;
}

.main {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}

.name-line {
  display: flex;
  align-items: center;
}

.name {
  font-size: 28rpx;
  color: #2B2622;
  margin-right: 12rpx;
  max-width: 320rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.self {
  font-size: 19rpx;
  color: #9A9186;
  background: #F0EDE8;
  border-radius: 6rpx;
  padding: 2rpx 8rpx;
  margin-right: 10rpx;
}

.sub-line {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
}

.muted-tag {
  font-size: 19rpx;
  color: #C5342B;
  background: #FDEDEB;
  border-radius: 6rpx;
  padding: 2rpx 10rpx;
  margin-right: 10rpx;
  flex-shrink: 0;
}

.sub-text {
  font-size: 21rpx;
  color: #B4A99B;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.more {
  font-size: 34rpx;
  color: #C6BAA9;
  padding: 10rpx 6rpx 10rpx 16rpx;
  flex-shrink: 0;
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
