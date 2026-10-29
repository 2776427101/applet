<template>
  <view class="page">
    <!-- 加载失败时连顶部统计和筛选一起藏掉：
         显示「0 人在读 / 全部 0 / 男生 0」和显示「这个班还没有学生」
         是同一个错误 —— 拿不到数据，就不该编造 0 -->
    <template v-if="!pageFailed">
    <!-- 顶部统计：导航栏已经写了「学生」，这里不重复标题，只给数量 -->
    <view class="head">
      <text class="head-count">{{ inClass.length }} 人在读</text>
      <text class="head-left" v-if="leftCount">{{ leftCount }} 人已离班</text>
      <!-- 导入入口放在这里而不是悬浮按钮：一学期只用一两次，
           不该和「加一个人」的 ＋ 抢右下角那个最好点的位置 -->
      <text class="head-import" @click="onImport">导入</text>
    </view>

    <!-- 搜索 -->
    <view class="search">
      <image class="search-icon" src="/static/icons/top-search.png" mode="aspectFit" />
      <input
        class="search-input"
        v-model="keyword"
        type="text"
        placeholder="搜索姓名或学号"
        placeholder-style="color:#B4A99B;font-size:26rpx"
        confirm-type="search"
      />
      <text v-if="keyword" class="clear" @click="keyword = ''">×</text>
    </view>

    <!-- 筛选：全部 / 男生 / 女生 / 离班 -->
    <view class="seg">
      <view
        class="seg-item"
        :class="{ on: filter === t.key }"
        v-for="t in tabs"
        :key="t.key"
        @click="filter = t.key"
      >
        {{ t.label }} {{ t.count }}
      </view>
    </view>
    </template>

    <!-- 加载态：占位行，结构和真实列表一致，数据回来不会跳 -->
    <view v-if="loading" class="list">
      <view class="row" v-for="i in 6" :key="'sk' + i">
        <view class="ava ava-sk"></view>
        <view class="info">
          <view class="sk-line" style="width: 120rpx"></view>
          <view class="sk-line" style="width: 200rpx; margin-top: 10rpx"></view>
        </view>
      </view>
    </view>

    <!-- 加载失败：和「班里没人」必须分开。
         原来失败时 all 还是空数组，会显示「这个班还没有学生」——
         老师会以为数据丢了，反复下拉刷新 -->
    <load-error
      v-else-if="pageFailed"
      :message="err"
      hint="学生名单还在服务器上，网络恢复后点重试就行"
      @retry="load"
    />

    <!-- 列表 -->
    <view v-else-if="filtered.length" class="list">
      <view
        class="row"
        v-for="s in filtered"
        :key="s.id"
        hover-class="row-pressed"
        @click="onOpen(s)"
      >
        <view class="ava" :class="avatarTone(s)">{{ avatarText(s) }}</view>
        <view class="info">
          <text class="info-name">{{ s.name }}</text>
          <text class="info-meta">{{ metaText(s) }}</text>
        </view>
        <text class="tag">{{ genderText(s) }}</text>
        <text class="chev">›</text>
      </view>
    </view>

    <!-- 空状态：分两种，用户看到的原因不一样 -->
    <view v-else class="empty">
      <text class="empty-title">{{ emptyTitle }}</text>
      <text class="empty-tip">{{ emptyTip }}</text>
      <view v-if="keyword" class="empty-btn" @click="keyword = ''">清空搜索</view>
    </view>

    <!-- 悬浮新增：拇指够得着的位置 -->
    <view class="fab" hover-class="fab-pressed" @click="onAdd">＋</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
/* 页面生命周期要从 @dcloudio/uni-app 导入 */
import { onShow } from '@dcloudio/uni-app'
import { getStudentList } from '@/api/student'
import LoadError from '@/components/load-error/load-error.vue'

/* ---------- 数据 ---------- */
/* all 存全量（含已离班），筛选和计数都在前端算：
   一个班几十人，一次拉完比每次输入都请求接口快得多 */
const all = ref([])
const loading = ref(true)
const loaded = ref(false) /* 是否已经成功拉过一次，用于区分首次加载和切回来刷新 */
const err = ref('')      /* 加载失败的原因。空串 = 没失败 */

const keyword = ref('')
const filter = ref('all') /* all | male | female | left */

async function load() {
  /* 首次进页面才显示占位行；从别的 tab 切回来静默刷新，不闪 */
  if (!loaded.value) loading.value = true
  /* 重试前必须清掉上一次的错误，否则即使这次成功了，错误块也不会消失 */
  err.value = ''
  try {
    /* status = -1 拿全部，这样「离班」也能在本页筛出来。
       silent：页面自己会画「加载失败 + 重试」，再弹 toast 就是同一句话说两遍 */
    const res = await getStudentList({ status: -1 }, { silent: true })
    all.value = res.list || []
    loaded.value = true
  } catch (e) {
    /* 只在「手里没有旧数据」时才记错误。
       已有名单的情况下刷新失败，继续显示旧名单比整块盖掉更有用 */
    if (!all.value.length) {
      err.value = (e && e.message) || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

onShow(() => load())

/* 页面级失败 = 有错误 且 一条数据都没有。
   有旧数据时刷新失败，继续显示旧数据，不算页面失败 */
const pageFailed = computed(() => !!err.value && !all.value.length)

/* ---------- 派生数据 ---------- */
const inClass = computed(() => all.value.filter((s) => s.status === 1))
const leftCount = computed(() => all.value.filter((s) => s.status === 0).length)

/* 四个筛选的计数：数字直接显示在按钮上，老师不用点进去才知道有多少人 */
const tabs = computed(() => [
  { key: 'all', label: '全部', count: inClass.value.length },
  { key: 'male', label: '男生', count: inClass.value.filter((s) => s.gender === 1).length },
  { key: 'female', label: '女生', count: inClass.value.filter((s) => s.gender === 2).length },
  { key: 'left', label: '离班', count: leftCount.value }
])

/* 先按 tab 筛，再按关键词筛 */
const filtered = computed(() => {
  let arr
  if (filter.value === 'left') {
    arr = all.value.filter((s) => s.status === 0)
  } else {
    arr = inClass.value
    if (filter.value === 'male') arr = arr.filter((s) => s.gender === 1)
    if (filter.value === 'female') arr = arr.filter((s) => s.gender === 2)
  }

  const k = keyword.value.trim().toLowerCase()
  if (k) {
    arr = arr.filter(
      (s) =>
        (s.name || '').toLowerCase().includes(k) ||
        (s.studentNo || '').toLowerCase().includes(k)
    )
  }
  return arr
})

/* 空状态要分清是「搜不到」还是「班里没人」，两种提示差别很大 */
const emptyTitle = computed(() => {
  if (keyword.value) return `没有找到「${keyword.value}」`
  if (filter.value === 'left') return '没有已离班的学生'
  return '这个班还没有学生'
})

const emptyTip = computed(() => {
  if (keyword.value) return '换个姓名或学号试试'
  if (filter.value === 'left') return '移出班级的学生会出现在这里'
  return '点右下角的 ＋ 添加第一个'
})

/* ---------- 展示辅助 ---------- */
/* 头像用姓名首字 + 色块：不用做上传，也不用管 47 张图加载失败 */
const avatarTone = (s) => (s.gender === 1 ? 'm' : s.gender === 2 ? 'f' : 'u')
const avatarText = (s) => (s.name || '?').charAt(0)
const genderText = (s) => ({ 0: '未知', 1: '男', 2: '女' }[s.gender] || '未知')

/* 副信息：学号 · 生日。生日只显示月日，老师关心的是「谁快过生日」，
   不关心哪年生的。学号可能为空，接口里是 nullable */
const metaText = (s) => {
  const no = s.studentNo || '无学号'
  const bd = s.birthday ? s.birthday.slice(5) : '生日未填'
  return `${no} · ${bd}`
}

/* ---------- 交互 ---------- */
const onOpen = (s) => {
  uni.navigateTo({
    url: `/pages/teacher/student/detail?id=${s.id}`,
    /* 详情页要自己拉数据，网络慢时先让用户看到点击反馈，
       否则点一下没反应会以为没点上，连着点好几次 */
    fail: () => {
      uni.showToast({ title: '打不开详情页', icon: 'none' })
    }
  })
}

const onAdd = () => {
  /* 不传 id = 新增模式，编辑页自己判断 */
  uni.navigateTo({ url: '/pages/teacher/student/edit' })
}

const onImport = () => {
  uni.navigateTo({ url: '/pages/teacher/student/import' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 60rpx;
  box-sizing: border-box;
}

/* 顶部统计 */
.head {
  display: flex;
  align-items: baseline;
  padding: 4rpx 8rpx 18rpx;
}

.head-count {
  font-size: 26rpx;
  color: #2B2622;
  font-weight: 500;
}

.head-left {
  font-size: 22rpx;
  color: #9A9186;
  margin-left: 14rpx;
}

/* margin-left: auto 把它推到最右边，不用改 .head 的 flex 布局 */
.head-import {
  margin-left: auto;
  font-size: 22rpx;
  color: #C5342B;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.08);
}

/* 搜索框 */
.search {
  height: 76rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  padding: 0 22rpx;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.05);
}

.search-icon {
  width: 28rpx;
  height: 28rpx;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  margin-left: 14rpx;
  font-size: 26rpx;
  color: #2B2622;
  height: 76rpx;
  line-height: 76rpx;
}

.clear {
  font-size: 34rpx;
  color: #B4A99B;
  padding: 0 6rpx;
  line-height: 1;
}

/* 筛选 */
.seg {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
}

.seg-item {
  flex: 1;
  text-align: center;
  font-size: 23rpx;
  padding: 14rpx 0;
  border-radius: 18rpx;
  background: #FFFFFF;
  color: #7A6F63;
  transition: background 0.15s;
}

.seg-item.on {
  background: #C5342B;
  color: #FFFFFF;
  font-weight: 500;
}

/* 列表 */
.list {
  margin-top: 18rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  overflow: hidden;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.row {
  display: flex;
  align-items: center;
  padding: 22rpx 26rpx;
  border-bottom: 1rpx solid #F0E8DC;
}

.row:last-child {
  border-bottom: none;
}

.row-pressed {
  background: #FAF6F1;
}

.ava {
  width: 64rpx;
  height: 64rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 500;
  flex-shrink: 0;
}

.ava.m {
  background: #EAF1F8;
  color: #2F6EA8;
}

.ava.f {
  background: #F9EAE8;
  color: #B0554E;
}

.ava.u {
  background: #F2EEE7;
  color: #9A9186;
}

/* 加载占位 */
.ava-sk {
  background: #F2EDE6;
}

.sk-line {
  height: 22rpx;
  border-radius: 8rpx;
  background: #F2EDE6;
}

.info {
  flex: 1;
  margin-left: 20rpx;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.info-name {
  font-size: 28rpx;
  font-weight: 500;
  color: #2B2622;
  line-height: 1.3;
}

.info-meta {
  font-size: 22rpx;
  color: #9A9186;
  margin-top: 4rpx;
}

.tag {
  font-size: 19rpx;
  padding: 4rpx 12rpx;
  border-radius: 10rpx;
  background: #F6F1EA;
  color: #9A9186;
  margin-left: 12rpx;
  flex-shrink: 0;
}

.chev {
  font-size: 30rpx;
  color: #CFC5B8;
  margin-left: 10rpx;
  line-height: 1;
}

/* 空状态 */
.empty {
  padding: 120rpx 0 80rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-title {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
}

.empty-tip {
  font-size: 23rpx;
  color: #9A9186;
  margin-top: 12rpx;
}

.empty-btn {
  margin-top: 32rpx;
  padding: 14rpx 40rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  font-size: 24rpx;
  color: #C5342B;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.08);
}

/* 悬浮新增 */
.fab {
  position: fixed;
  right: 40rpx;
  /* 加 --window-bottom：H5 里底部有 tabBar（约 50px），
     只写 56rpx 的话按钮会被压住一半。
     小程序里这个变量是 0，不影响原来位置 */
  bottom: calc(56rpx + var(--window-bottom, 0px));
  width: 96rpx;
  height: 96rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 46rpx;
  font-weight: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 28rpx rgba(197, 52, 43, 0.3);
  z-index: 10;
}

.fab-pressed {
  transform: scale(0.94);
  opacity: 0.92;
}
</style>
