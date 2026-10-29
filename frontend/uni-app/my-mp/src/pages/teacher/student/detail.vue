<template>
  <view class="page">
    <!-- 加载态 -->
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <!-- 出错：1004 不存在 / 3002 不是你的班 / 网络 -->
    <view v-else-if="errMsg" class="state">
      <text class="state-title">{{ errMsg }}</text>
      <text class="state-tip">可以点下面重试，或者返回名单重新点一次</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else-if="d">
      <!-- ============ 身份卡 ============ -->
      <view class="hero">
        <view class="ava" :class="tone">{{ avatarText }}</view>
        <view class="hero-main">
          <text class="hero-name">{{ d.name }}</text>
          <text class="hero-meta">{{ heroMeta }}</text>
        </view>
        <view class="badge" :class="d.status === 1 ? 'on' : 'off'">
          {{ d.status === 1 ? '在读' : '已离班' }}
        </view>
      </view>

      <!-- ============ 座位 ============ -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">座位</text>
          <text class="card-more" @click="goSeat">座位图 ›</text>
        </view>
        <view v-if="d.seat" class="seat-line">
          <text class="seat-big">第 {{ d.seat.row }} 排</text>
          <text class="seat-dot">·</text>
          <text class="seat-big">第 {{ d.seat.col }} 列</text>
        </view>
        <text v-else class="muted">还没排座</text>
      </view>

      <!-- ============ 最近成绩 ============ -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">最近成绩</text>
          <text v-if="d.latestScore" class="card-sub">
            {{ d.latestScore.examName }} · {{ (d.latestScore.examDate || '').slice(5) }}
          </text>
        </view>

        <template v-if="d.latestScore">
          <view class="score-top">
            <view class="score-total">
              <!-- 总分可能是 null（全科缺考），这时不要用红色大字，
                   否则那个「—」看起来像加载失败 -->
              <text class="score-num" :class="{ empty: d.latestScore.total == null }">
                {{ fmt(d.latestScore.total) }}
              </text>
              <text class="score-unit">总分</text>
            </view>
            <!-- 排名可能为 null：全班都缺考时算不出来，显示「未统计」而不是「第 1 名」 -->
            <view v-if="d.latestScore.rank != null" class="rank-pill">
              班级第 {{ d.latestScore.rank }} / {{ d.latestScore.classSize }}
            </view>
            <view v-else class="rank-pill ghost">排名未统计</view>
          </view>

          <view class="subj" v-for="s in d.latestScore.subjects" :key="s.subject">
            <text class="subj-name">{{ s.subject }}</text>
            <view class="bar">
              <view
                class="bar-fill"
                :class="{ fail: isFail(s), absent: s.score == null }"
                :style="{ width: pct(s) }"
              ></view>
            </view>
            <text class="subj-score" :class="{ fail: isFail(s), absent: s.score == null }">
              {{ s.score == null ? '缺考' : fmt(s.score) }}
            </text>
          </view>
        </template>

        <text v-else class="muted">没有成绩记录</text>
      </view>

      <!-- ============ 请假 ============ -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">请假</text>
        </view>

        <view v-if="d.leave" class="leave-row">
          <view class="leave-item">
            <text class="leave-num">{{ d.leave.total }}</text>
            <text class="leave-label">累计请假</text>
          </view>
          <view class="leave-item">
            <text class="leave-num" :class="{ warn: d.leave.pending > 0 }">{{ d.leave.pending }}</text>
            <text class="leave-label">待审批</text>
          </view>
          <view class="leave-item">
            <text class="leave-num sm">{{ d.leave.lastDate ? d.leave.lastDate.slice(5) : '—' }}</text>
            <text class="leave-label">最近一次</text>
          </view>
        </view>

        <text v-else class="muted">没有请假记录</text>
      </view>

      <!-- ============ 基本信息 ============ -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">基本信息</text>
        </view>
        <view class="kv">
          <text class="k">学号</text>
          <text class="v">{{ d.studentNo || '未填' }}</text>
        </view>
        <view class="kv">
          <text class="k">性别</text>
          <text class="v">{{ genderText }}</text>
        </view>
        <view class="kv">
          <text class="k">生日</text>
          <text class="v">{{ d.birthday || '未填' }}{{ age != null ? `（${age} 岁）` : '' }}</text>
        </view>
        <view class="kv">
          <text class="k">入班时间</text>
          <text class="v">{{ d.createdAt ? d.createdAt.slice(0, 10) : '未记录' }}</text>
        </view>
      </view>

      <!-- ============ 联系方式 ============ -->
      <!-- 整组都空时只显示一句话，不列 4 行「未填」——
           47 个老学生的这些字段全是空的，全列出来会把页面拉得很长 -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">联系方式</text>
        </view>
        <template v-if="hasContact">
          <view class="kv" v-for="r in contactRows" :key="r.k">
            <text class="k">{{ r.k }}</text>
            <text class="v" :class="{ dim: !r.v }">{{ r.v || '未填' }}</text>
          </view>
        </template>
        <text v-else class="muted">还没有填写联系方式</text>
      </view>

      <!-- ============ 住宿与其他 ============ -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">住宿与其他</text>
        </view>
        <template v-if="hasOther">
          <view class="kv" v-for="r in otherRows" :key="r.k">
            <text class="k">{{ r.k }}</text>
            <text class="v" :class="{ dim: !r.v }">{{ r.v || '未填' }}</text>
          </view>
        </template>
        <text v-else class="muted">还没有填写这些信息</text>
      </view>

      <!-- 给底部固定操作栏让出位置，否则最后一张卡片会被盖住 -->
      <view class="actions-holder"></view>
    </template>

    <!-- ============ 底部操作栏 ============ -->
    <view v-if="d" class="actions">
      <view class="act act-main" hover-class="act-pressed" @click="goEdit">编辑资料</view>
      <!-- 已经离班的人不用再显示「移出」，点了也没意义 -->
      <view v-if="d.status === 1" class="act act-danger" hover-class="act-pressed" @click="onRemove">
        移出班级
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getStudentDetail, removeStudent } from '@/api/student'

const d = ref(null)
const loading = ref(true)
const errMsg = ref('')

/* onLoad 只跑一次，id 存外面，重试和刷新时不用再解一遍参数 */
let sid = null

onLoad((opt) => {
  sid = opt && opt.id
})

/*
 * 用 onShow 而不是 onLoad 拉数据：
 * 从编辑页返回时，姓名/学号可能已经改了，必须重新拉一次。
 * 放在 onLoad 里的话，页面还显示改之前的样子，用户会以为没保存成功。
 */
onShow(() => {
  load()
})

async function load() {
  if (!sid) {
    errMsg.value = '没有拿到学生 ID'
    loading.value = false
    return
  }
  loading.value = true
  errMsg.value = ''
  try {
    d.value = await getStudentDetail(sid)
    /* 导航栏标题换成学生姓名：返回时列表页标题还是「学生」，
       这样「谁的详情」一眼能认出来 */
    if (d.value && d.value.name) {
      uni.setNavigationBarTitle({ title: d.value.name })
    }
  } catch (e) {
    /* request.js 已经 toast 过，这里把错误显示在页面上，方便重试 */
    errMsg.value = e && e.message ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

/* ---------- 展示辅助 ---------- */

const tone = computed(() => {
  if (!d.value) return 'u'
  return d.value.gender === 1 ? 'm' : d.value.gender === 2 ? 'f' : 'u'
})

const avatarText = computed(() => (d.value && d.value.name ? d.value.name.charAt(0) : '?'))

const genderText = computed(() => {
  if (!d.value) return '未知'
  return { 0: '未知', 1: '男', 2: '女' }[d.value.gender] || '未知'
})

/**
 * 周岁。生日是 yyyy-MM-dd 字符串。
 * 必须 replace(/-/g,'/')：iOS 上 new Date('2009-03-12') 是 Invalid Date，
 * 换成斜杠才能解析 —— 这个坑在小程序真机上很常见
 */
const age = computed(() => {
  if (!d.value || !d.value.birthday) return null
  const b = new Date(d.value.birthday.replace(/-/g, '/'))
  if (isNaN(b.getTime())) return null
  const now = new Date()
  let a = now.getFullYear() - b.getFullYear()
  const m = now.getMonth() - b.getMonth()
  if (m < 0 || (m === 0 && now.getDate() < b.getDate())) a--
  return a >= 0 && a < 150 ? a : null
})

const heroMeta = computed(() => {
  if (!d.value) return ''
  const parts = []
  if (d.value.studentNo) parts.push(d.value.studentNo)
  parts.push(genderText.value)
  if (age.value != null) parts.push(`${age.value} 岁`)
  return parts.join(' · ')
})

/**
 * 两组扩展字段。
 *
 * 写成数组而不是在模板里一个个写 kv，是因为「有值显示、没值显示未填」
 * 的逻辑要复用 —— 两组各写 4 遍 v-if 很容易漏掉一个字段，
 * 而漏掉的字段在页面上「消失」比显示「未填」更难被发现。
 */
const contactRows = computed(() => {
  if (!d.value) return []
  return [
    { k: '手机号', v: d.value.phone },
    { k: '微信号', v: d.value.wechat },
    { k: '家人电话1', v: d.value.familyPhone1 },
    { k: '家人电话2', v: d.value.familyPhone2 }
  ]
})

const hasContact = computed(() => contactRows.value.some((r) => r.v))

const otherRows = computed(() => {
  if (!d.value) return []
  return [
    { k: '宿舍', v: d.value.dormitory },
    { k: '培训方向', v: d.value.trainingDirection },
    { k: '民族', v: d.value.nation },
    { k: '政治面貌', v: d.value.politicalStatus },
    { k: '积极分子', v: d.value.partyActivist }
  ]
})

const hasOther = computed(() => otherRows.value.some((r) => r.v))

/* 分数去掉没意义的小数尾巴：95.5 显示 95.5，95.0 显示 95 */
function fmt(n) {
  if (n == null) return '—'
  return String(Number(n))
}

/* 及格线按满分 60% 算：100 分制 60，150 分制 90 */
function isFail(s) {
  if (s.score == null) return false
  const full = s.fullScore == null ? 100 : Number(s.fullScore)
  return Number(s.score) < full * 0.6
}

function pct(s) {
  if (s.score == null) return '0%'
  const full = s.fullScore == null ? 100 : Number(s.fullScore)
  if (!full) return '0%'
  const p = (Number(s.score) / full) * 100
  /* 最低给 4%，否则 5 分和 0 分看起来一样，都是空条 */
  return Math.min(100, Math.max(4, p)) + '%'
}

/* ---------- 交互 ---------- */

/* 座位页在 tabBar 里，只能用 switchTab（navigateTo 会直接失败） */
const goSeat = () => {
  uni.switchTab({ url: '/pages/teacher/seat/index' })
}

/* 编辑页是普通页，用 navigateTo */
const goEdit = () => {
  uni.navigateTo({ url: `/pages/teacher/student/edit?id=${sid}` })
}

/* 移出班级：不可撤销的软删除，必须二次确认 */
function onRemove() {
  if (!d.value) return
  uni.showModal({
    title: '移出班级',
    content: `确定把「${d.value.name}」移出班级吗？\n成绩和请假记录会保留，之后能在「离班」里找到。`,
    confirmText: '移出',
    confirmColor: '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await removeStudent(sid)
        uni.showToast({ title: '已移出', icon: 'success' })
        /* 列表页的 onShow 会自动刷新，这个人会从「全部」里消失、出现在「离班」下 */
        setTimeout(() => uni.navigateBack(), 600)
      } catch (e) {
        /* api 层已经 toast 过了 */
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

.state-tip {
  font-size: 23rpx;
  color: #9A9186;
  margin-top: 12rpx;
  text-align: center;
}

.state-btn {
  margin-top: 36rpx;
  padding: 14rpx 48rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  font-size: 24rpx;
  color: #C5342B;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.08);
}

/* ---------- 身份卡 ---------- */
.hero {
  display: flex;
  align-items: center;
  padding: 30rpx 28rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.ava {
  width: 104rpx;
  height: 104rpx;
  border-radius: 34rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 44rpx;
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

.hero-main {
  flex: 1;
  margin-left: 24rpx;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.hero-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1.25;
}

.hero-meta {
  font-size: 23rpx;
  color: #9A9186;
  margin-top: 8rpx;
}

.badge {
  font-size: 20rpx;
  padding: 6rpx 16rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
}

.badge.on {
  background: #EDF4EC;
  color: #4A7C4E;
}

.badge.off {
  background: #F2EEE7;
  color: #9A9186;
}

/* ---------- 卡片 ---------- */
.card {
  margin-top: 18rpx;
  padding: 26rpx 28rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.card-title {
  font-size: 27rpx;
  font-weight: 600;
  color: #2B2622;
}

.card-sub {
  font-size: 21rpx;
  color: #9A9186;
}

.card-more {
  font-size: 22rpx;
  color: #C5342B;
}

.muted {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #B4A99B;
}

/* ---------- 座位 ---------- */
.seat-line {
  display: flex;
  align-items: baseline;
  margin-top: 16rpx;
}

.seat-big {
  font-size: 32rpx;
  font-weight: 500;
  color: #2B2622;
}

.seat-dot {
  font-size: 26rpx;
  color: #CFC5B8;
  margin: 0 12rpx;
}

/* ---------- 成绩 ---------- */
.score-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 18rpx;
  margin-bottom: 8rpx;
}

.score-total {
  display: flex;
  align-items: baseline;
}

.score-num {
  font-size: 52rpx;
  font-weight: 600;
  color: #C5342B;
  line-height: 1;
}

/* 没有总分时缩成灰色小字：「—」用 52rpx 红色渲染像加载失败 */
.score-num.empty {
  font-size: 34rpx;
  font-weight: 500;
  color: #B4A99B;
}

.score-unit {
  font-size: 21rpx;
  color: #9A9186;
  margin-left: 10rpx;
}

.rank-pill {
  font-size: 21rpx;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: #FDF0EE;
  color: #C5342B;
}

.rank-pill.ghost {
  background: #F2EEE7;
  color: #9A9186;
}

.subj {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
}

.subj-name {
  width: 96rpx;
  font-size: 24rpx;
  color: #5C5348;
  flex-shrink: 0;
}

.bar {
  flex: 1;
  height: 14rpx;
  border-radius: 999rpx;
  background: #F2EDE6;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #E8594F, #C5342B);
  transition: width 0.3s;
}

.bar-fill.fail {
  background: #D98324;
}

/* 缺考画一条虚线，和「考了 0 分」区分开 ——
   0 分是有分数的，缺考是没去考，两条记录对学生的影响完全不同 */
.bar-fill.absent {
  background: repeating-linear-gradient(
    90deg,
    #E2D9CC 0,
    #E2D9CC 8rpx,
    transparent 8rpx,
    transparent 16rpx
  );
}

.subj-score {
  width: 92rpx;
  text-align: right;
  font-size: 24rpx;
  color: #2B2622;
  flex-shrink: 0;
}

.subj-score.fail {
  color: #D98324;
}

.subj-score.absent {
  color: #B4A99B;
  font-size: 22rpx;
}

/* ---------- 请假 ---------- */
.leave-row {
  display: flex;
  margin-top: 20rpx;
}

.leave-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.leave-num {
  font-size: 38rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1.2;
}

.leave-num.sm {
  font-size: 28rpx;
  padding-top: 8rpx;
}

.leave-num.warn {
  color: #C5342B;
}

.leave-label {
  font-size: 21rpx;
  color: #9A9186;
  margin-top: 8rpx;
}

/* ---------- 基本信息 ---------- */
.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #F6F0E8;
}

.kv:last-child {
  border-bottom: none;
  padding-bottom: 4rpx;
}

.k {
  font-size: 24rpx;
  color: #9A9186;
}

.v {
  font-size: 25rpx;
  color: #2B2622;
}

/* 没填的项用浅色：「未填」三个字如果和真实值一样黑，
   一屏 9 行全是「未填」时根本看不出哪些填了 */
.v.dim {
  color: #C4B9AB;
}

/* ---------- 底部操作栏 ---------- */
/* 占位块：内容滚到底时最后一张卡片不会被固定栏盖住 */
.actions-holder {
  height: 150rpx;
}

.actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(247, 239, 228, 0.96);
  z-index: 20;
}

.act {
  height: 88rpx;
  border-radius: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 500;
}

/* 主操作占满剩余宽度 */
.act-main {
  flex: 1;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  box-shadow: 0 10rpx 24rpx rgba(197, 52, 43, 0.26);
}

/* 危险操作给固定宽度：两边等宽会让人误以为「移出」和「编辑」同等重要 */
.act-danger {
  flex: 0 0 200rpx;
  background: #FFFFFF;
  color: #C5342B;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.06);
}

.act-pressed {
  transform: scale(0.97);
  opacity: 0.9;
}
</style>
