<template>
  <view class="page">
    <!-- 三个 tab：我创建的 / 我管理的 / 我加入的。
         一个人在一个容器里只有一个角色，所以同一个容器只会出现在一个 tab 里 -->
    <view class="tabbar">
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
      <!-- 加入别人的容器：有码的人多半是刚被拉进群，入口放显眼处 -->
      <text class="join-link" @click="goJoin">加入</text>
    </view>

    <!-- 搜索 + 邀请码状态筛选 -->
    <view class="filters">
      <view class="search">
        <input
          class="search-input"
          v-model="keyword"
          type="text"
          confirm-type="search"
          placeholder="搜群组名称"
          placeholder-style="color:#B4A99B;font-size:26rpx"
          @confirm="load"
        />
        <text v-if="keyword" class="search-clear" @click="clearKeyword">×</text>
      </view>
      <scroll-view class="chips" scroll-x :show-scrollbar="false">
        <view
          v-for="f in inviteFilters"
          :key="f.value"
          class="chip"
          :class="{ on: inviteStatus === f.value }"
          @click="setInviteFilter(f.value)"
        >
          {{ f.label }}
        </view>
      </scroll-view>
    </view>

    <!-- 加载中：骨架和真实卡片同结构，数据回来不跳 -->
    <view v-if="loading" class="skeleton">
      <view v-for="i in 3" :key="i" class="sk-card">
        <view class="sk-line w40"></view>
        <view class="sk-line w70"></view>
        <view class="sk-line w30"></view>
      </view>
    </view>

    <!-- 失败且手里没有旧数据 -->
    <load-error
      v-else-if="err && !list.length"
      :message="err"
      hint="群组列表来自服务器，网络恢复后点重试就行"
      @retry="load"
    />

    <!-- 空状态：区分「本来就没有」和「搜不到」 -->
    <view v-else-if="!list.length" class="empty">
      <text class="empty-title">{{ keyword ? '没有匹配的群组' : emptyTitle }}</text>
      <text class="empty-text">{{ emptyText }}</text>
      <view v-if="keyword" class="empty-btn" @click="clearAll">清空筛选</view>
      <view v-else-if="tab === 'created'" class="empty-btn" @click="goCreate">新建一个</view>
      <view v-else-if="tab === 'joined'" class="empty-btn" @click="goJoin">用邀请码加入</view>
    </view>

    <!-- 列表 -->
    <view v-else class="list">
      <!-- 有旧数据时刷新失败：保留旧数据，只在顶部提示一句，不整块盖掉 -->
      <view v-if="err" class="stale">刷新失败，下面是上次的数据</view>

      <view
        v-for="c in list"
        :key="c.id"
        class="card"
        hover-class="card-pressed"
        @click="goChat(c)"
      >
        <view class="card-head">
          <text class="subject">{{ c.subjectName || c.name }}</text>
          <role-tag :role="c.myRole" />
        </view>

        <!-- 名称与学科已经合并成一个字段，两者同值时不要再重复显示一遍 -->
        <text v-if="c.name && c.name !== c.subjectName" class="cname">{{ c.name }}</text>

        <view class="meta">
          <text class="meta-item">{{ c.className || '未关联班级' }}</text>
          <text class="dot">·</text>
          <!-- 人数 = 老师成员 + 本班学生。学生不是 t_user，不在成员表里，
               只显示 memberCount 的话，一个 57 人的班群会显示「1 人」 -->
          <text class="meta-item">{{ (c.memberCount || 0) + (c.studentCount || 0) }} 人</text>
          <text v-if="c.hasPassword" class="dot">·</text>
          <text v-if="c.hasPassword" class="meta-item">有密码</text>
        </view>

        <!-- 邀请码不在这里显示：它是有有效期的加入凭证，过期就作废。
             摆在列表上，老师很容易把一个已经失效的码念给学生；
             要看/换码统一去「详情 → 邀请码与加入设置」 -->
      </view>
    </view>

    <view class="bottom-space"></view>

    <!-- 新建：只在「我创建的」tab 显示。在别的 tab 里建出来也不会留在这个 tab，
         点了会让人困惑「我刚建的呢」 -->
    <view v-if="tab === 'created'" class="fab" @click="goCreate">
      <text class="fab-plus">＋</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listContainers } from '@/api/container'
import RoleTag from '@/components/role-tag/role-tag.vue'
import LoadError from '@/components/load-error/load-error.vue'

const tabs = [
  { key: 'created', label: '我创建的' },
  { key: 'managed', label: '我管理的' },
  { key: 'joined', label: '我加入的' }
]

/* value=null 表示不筛选 */
const inviteFilters = [
  { value: null, label: '全部' },
  { value: 1, label: '邀请码有效' },
  { value: 2, label: '已过期' },
  { value: 3, label: '永久有效' }
]

const tab = ref('created')
const keyword = ref('')
const inviteStatus = ref(null)

const list = ref([])
const loading = ref(true)
const err = ref('')

const emptyTitle = computed(() => ({
  created: '你还没有创建课程群组',
  managed: '还没有你管理的群组',
  joined: '你还没有加入别人的群组'
}[tab.value] || '还没有群组'))

const emptyText = computed(() => ({
  created: '一门课一个群。建好后把邀请码发给学生，他们输入就能进',
  managed: '老师把你设为班委后，这里会出现对应的群组',
  joined: '拿到老师发的 10 位邀请码，就能加入对应的课程群组'
}[tab.value] || ''))

async function load() {
  err.value = ''
  // 手里没数据才显示骨架；有数据时静默刷新，避免列表闪一下
  if (!list.value.length) {
    loading.value = true
  }
  try {
    const params = { tab: tab.value }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (inviteStatus.value !== null) params.inviteStatus = inviteStatus.value
    const d = await listContainers(params)
    list.value = (d && d.list) || []
  } catch (e) {
    // 只在没有旧数据时记错误；有旧数据就继续显示旧的
    if (!list.value.length) {
      err.value = (e && e.message) || '加载失败'
    } else {
      err.value = (e && e.message) || '刷新失败'
    }
  } finally {
    loading.value = false
  }
}

/* 用 onShow 而不是 onLoad：从创建页/详情页返回时要看到最新状态 */
onShow(() => load())

function switchTab(k) {
  if (tab.value === k) return
  tab.value = k
  // 换 tab 先清空，否则会短暂显示上一个 tab 的数据，看起来像串了
  list.value = []
  load()
}

function setInviteFilter(v) {
  inviteStatus.value = inviteStatus.value === v ? null : v
  load()
}

function clearKeyword() {
  keyword.value = ''
  load()
}

function clearAll() {
  keyword.value = ''
  inviteStatus.value = null
  load()
}

/* 点卡片进聊天页（和微信一致），群组详情在聊天页右上角 */
const goChat = (c) => uni.navigateTo({ url: `/pages/teacher/container/chat?id=${c.id}` })
const goCreate = () => uni.navigateTo({ url: '/pages/teacher/container/create' })
const goJoin = () => uni.navigateTo({ url: '/pages/container/join' })
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 16rpx 24rpx 0;
  box-sizing: border-box;
}

/* ---------- tab ---------- */
.tabbar {
  display: flex;
  align-items: center;
  padding: 8rpx 0 4rpx;
}

.tabs {
  flex: 1;
  display: flex;
  align-items: flex-end;
}

.tab {
  position: relative;
  margin-right: 34rpx;
  padding-bottom: 14rpx;
}

.tab-text {
  font-size: 28rpx;
  color: #9A9186;
}

.tab.on .tab-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.tab-line {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 36rpx;
  height: 6rpx;
  margin-left: -18rpx;
  border-radius: 3rpx;
  background: #C5342B;
}

.join-link {
  font-size: 26rpx;
  color: #C5342B;
  padding: 8rpx 22rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  flex-shrink: 0;
}

/* ---------- 搜索 / 筛选 ---------- */
.filters {
  margin-top: 8rpx;
}

.search {
  display: flex;
  align-items: center;
  height: 76rpx;
  padding: 0 24rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.05);
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

.chips {
  margin-top: 16rpx;
  white-space: nowrap;
}

.chip {
  display: inline-block;
  margin-right: 14rpx;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  font-size: 23rpx;
  color: #8A8178;
}

.chip.on {
  background: #FDEDEB;
  color: #C5342B;
  font-weight: 500;
}

/* ---------- 骨架 ---------- */
.skeleton {
  margin-top: 20rpx;
}

.sk-card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 30rpx 28rpx;
  margin-bottom: 18rpx;
}

.sk-line {
  height: 24rpx;
  border-radius: 12rpx;
  background: #F3ECE2;
  margin-bottom: 16rpx;
}

.sk-line:last-child {
  margin-bottom: 0;
}

.w70 { width: 70%; }
.w40 { width: 40%; }
.w30 { width: 30%; }

/* ---------- 空 / 错误 ---------- */
.empty {
  margin-top: 120rpx;
  padding: 0 40rpx;
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
  line-height: 1.7;
  text-align: center;
}

.empty-btn {
  margin-top: 36rpx;
  padding: 16rpx 52rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 26rpx;
  box-shadow: 0 10rpx 22rpx rgba(197, 52, 43, 0.24);
}

/* ---------- 列表 ---------- */
.list {
  margin-top: 20rpx;
}

.stale {
  margin-bottom: 14rpx;
  padding: 12rpx 22rpx;
  background: #FBF3DC;
  border-radius: 14rpx;
  font-size: 21rpx;
  color: #8A6A2E;
}

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 26rpx 28rpx 22rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.card-pressed {
  background: #FDFAF6;
  transform: scale(0.99);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.subject {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.cname {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: #7A6F63;
}

.meta {
  display: flex;
  align-items: center;
  margin-top: 12rpx;
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

/* ---------- 悬浮新建 ---------- */
.fab {
  position: fixed;
  right: 40rpx;
  /* 底部要给 tabBar 让位：H5 的 tabBar 是 DOM，会把按钮盖住 */
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

.bottom-space {
  height: 160rpx;
}
</style>
