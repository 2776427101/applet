<template>
  <view class="page">
    <!-- 加载失败：整块换掉。原来失败时四个数字会显示 0，
         看起来像「我这学期没请过假、没考过试」，比白屏更容易误导 -->
    <view v-if="err && !home" class="err-wrap">
      <load-error
        :message="err"
        hint="首页的数字来自服务器，网络恢复后点重试就行"
        @retry="load"
      />
    </view>

    <template v-else>
      <!-- 顶部白卡：身份 + 欢迎卡。和老师端首页同一套骨架，
           只是把「班级 + 老师」换成「我自己 + 我的班」 -->
      <view class="sheet">
        <view class="topbar">
          <view class="avatar">{{ initial }}</view>
          <view class="meta">
            <view class="name-line">
              <text class="name">{{ me.name || '…' }}</text>
            </view>
            <text class="sub">{{ className }} · 学号 {{ me.studentNo || '—' }}</text>
          </view>
          <view class="edit" hover-class="edit-pressed" @click="goProfile">资料</view>
        </view>

        <view class="hero">
          <view class="hero-top">
            <text class="hero-title">{{ dayLabel }} · {{ weekText }}</text>
            <view class="clock">
              <text class="clock-date">{{ dateText }}</text>
              <text class="clock-time">{{ timeText }}</text>
            </view>
          </view>

          <text class="greet">{{ greeting }}，{{ me.name || '同学' }}</text>
          <text class="greet">{{ heroSub }}</text>
        </view>
      </view>

      <!-- 四宫格：老师看全班，学生看自己。
           四个数字全部可以点，点完进对应页面 -->
      <view class="stats">
        <view
          class="stat"
          v-for="s in stats"
          :key="s.label"
          hover-class="stat-pressed"
          @click="go(s.to)"
        >
          <text class="stat-num" :style="{ color: s.color }">{{ s.value }}</text>
          <text class="stat-label">{{ s.label }}</text>
        </view>
      </view>

      <!-- 通知条：一行高，和老师端同一个尺寸。
           学生端的「通知」不是待处理，而是「已经有结果的事」。
           有新的（比上次看过的更新）时整条变红底 + 左侧红点 + 数量徽标；
           没有新的就回到白底灰字，箭头常驻 —— 这条框是「我的通知」的唯一入口，
           没新通知时也要看得出能点进去看历史 -->
      <view
        class="notice"
        :class="{ off: !noticeTotal, hot: hasNew }"
        hover-class="notice-pressed"
        @click="openNotices"
      >
        <view v-if="hasNew" class="notice-dot"></view>
        <text class="notice-ic">{{ noticeIcon }}</text>
        <text class="notice-text">{{ noticeText }}</text>
        <view v-if="hasNew" class="notice-badge">{{ newCount > 99 ? '99+' : newCount }}</view>
        <text class="notice-chev">›</text>
      </view>

      <!-- 快捷操作：四个每天真会点的。
           老师端那四个是「记请假 / 待办」，学生端对应「申请请假 / 看成绩」 -->
      <view class="quick">
        <view class="quick-head">
          <view class="quick-bar"></view>
          <text class="quick-title">⚡ 常用</text>
        </view>

        <view class="quick-grid">
          <view
            class="quick-item"
            v-for="q in quicks"
            :key="q.label"
            hover-class="pressed"
            @click="go(q.to)"
          >
            <view class="quick-icon-wrap" :class="'tone-' + q.tone">
              <image class="quick-icon-img" :src="q.img" mode="aspectFit" />
            </view>
            <text class="quick-label">{{ q.label }}</text>
          </view>
        </view>
      </view>

      <!-- 私信、通知、退出都不放首页：
           私信归到「通讯录」（那是找人说话的地方），
           通知就是上面那条提醒条本身，
           退出登录归到「我的」最下面。首页只留"看一眼就要知道的数字"。 -->

      <view class="bottom-space"></view>
    </template>

    <!-- 底栏放在 template 外面：首页加载失败时也要能切到别的页面，
         否则错误块会把人困在这一屏 -->
    <student-tab-bar current="home" />
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchStudentHome, fetchMyNotices } from '@/api/student-data'
import LoadError from '@/components/load-error/load-error.vue'
import StudentTabBar from '@/components/student-tab-bar/student-tab-bar.vue'

/* 学生首页**不用**自定义导航栏（老师端首页是 tab 页，才用 custom）。
   这里是从入口页 navigateTo 进来的，保留系统导航栏才有返回键 ——
   自定义导航会把返回键一起干掉，学生只能靠「退出身份」回入口，那太绕了 */

/* 姓名/学号先读登录时存的，页面一打开就有字，不用等接口。
   接口回来后不覆盖 —— 学生改不了自己的姓名学号，没有刷新的必要 */
const me = ref(uni.getStorageSync('studentInfo') || {})

const home = ref(null)
const loading = ref(true)
const err = ref('')

const notices = ref([])
const noticeTotal = ref(0)

/* 「看过了」的时间点，存本地。
   通知本身没有已读状态（它是按业务状态算出来的，后端不记谁看过），
   所以红点只能由客户端判：比上次点进通知页更新，就是新的。
   存在本地意味着换设备会重新亮一次 —— 这个偏差可以接受，
   比"永远亮着"（按 total 判就会永远亮）好得多。 */
const SEEN_KEY = 'student_notice_seen_at'
const seenAt = ref(Number(uni.getStorageSync(SEEN_KEY) || 0))

/* 比 seenAt 新的有几条。第一条就是最新的，所以只需要看第一条有没有超过 */
const newCount = computed(() => {
  const t = seenAt.value
  return notices.value.filter((n) => Number(n.atMillis || 0) > t).length
})

const hasNew = computed(() => newCount.value > 0)

const className = computed(() => home.value?.classInfo?.name || '我的班级')

const initial = computed(() => (me.value.name || '生').slice(0, 1))

async function load() {
  err.value = ''
  try {
    /* 两个请求并行。通知失败不连累首页：catch 成 null，保留上一次的列表 */
    const [h, nt] = await Promise.all([
      fetchStudentHome(),
      fetchMyNotices(20).catch(() => null)
    ])
    home.value = h
    if (nt) {
      notices.value = nt.list || []
      noticeTotal.value = nt.total || 0
    }
  } catch (e) {
    /* 1009 = 还没入班（token 里没有 classId）。把他送进入班页，
       不要停在"加载失败 + 重试"上 —— 他需要的不是重试，是邀请码 */
    if (e && e.code === 1009) {
      uni.reLaunch({ url: '/pages/student/join/index?first=1' })
      return
    }
    if (!home.value) {
      err.value = (e && e.message) || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

/* onShow 不用 onLoad：从请假/成绩页返回时，数字要是最新的 */
onShow(load)

/* ---------- 四宫格 ---------- */
const stats = computed(() => {
  const s = home.value?.stats || {}
  const dash = loading.value ? '—' : null
  const rank = home.value?.latestExam?.rank
  return [
    {
      value: dash ?? String(s.leavePending ?? 0),
      label: '待审批请假',
      color: '#C9A227',
      to: '/pages/student/leave/index'
    },
    {
      value: dash ?? String(s.leaveThisMonth ?? 0),
      label: '本月请假',
      color: '#8C3B34',
      to: '/pages/student/leave/index'
    },
    {
      /* 排名是"最近一次考试"的，不是历史最好。
         没有成绩时显示「—」，不能显示 0 —— 第 0 名不存在 */
      value: dash ?? (rank ? String(rank) : '—'),
      label: '班级排名',
      color: '#A81E28',
      to: '/pages/student/score/index'
    },
    {
      /* 私信的「家」在通讯录：这里只留一个数字（有几条没读），
         点进去也是先到通讯录，再从班主任那栏的「私信」进对话 ——
         不直接从首页跳进聊天页，否则通讯录就成了摆设 */
      value: dash ?? String(s.unread ?? 0),
      label: '未读私信',
      color: '#8A6A2E',
      to: '/pages/student/contact/index'
    }
  ]
})

/* ---------- 欢迎卡 ---------- */
const now = ref(new Date())
const pad = (n) => String(n).padStart(2, '0')

/* 每秒只比较分钟，真的变了才触发重渲染 —— 秒数没人看 */
const timer = setInterval(() => {
  const d = new Date()
  if (d.getMinutes() !== now.value.getMinutes()) {
    now.value = d
  }
}, 1000)
onUnmounted(() => clearInterval(timer))

const dayLabel = computed(
  () => ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.value.getDay()]
)
const dateText = computed(
  () => `${pad(now.value.getMonth() + 1)}-${pad(now.value.getDate())}`
)
const timeText = computed(
  () => `${pad(now.value.getHours())}:${pad(now.value.getMinutes())}`
)
const greeting = computed(() => {
  const h = now.value.getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const weekText = computed(() => {
  const w = home.value?.term?.weekNo || 0
  return w > 0 ? `第${w}周` : '未开学'
})

/* 第二行问候语：有成绩就说成绩，没成绩就说一句不催人的话。
   这里不放「你要努力」之类的鼓励 —— 学生打开小程序是来查事的，不是来被说的 */
const heroSub = computed(() => {
  const e = home.value?.latestExam
  if (!e) {
    return loading.value ? '正在加载…' : '还没有成绩记录'
  }
  const rank = e.rank ? `班级第 ${e.rank}` : '暂无排名'
  return `最近一次 ${e.name}：总分 ${fmtScore(e.total)}，${rank}`
})

function fmtScore(v) {
  if (v === null || v === undefined) return '—'
  return String(Math.round(Number(v) * 10) / 10)
}

/* ---------- 通知条 ---------- */
const noticeIcon = computed(() => {
  const k = notices.value[0] && notices.value[0].kind
  if (k === 'leave') return '📝'
  if (k === 'message') return '✉'
  return '🔔'
})

const noticeText = computed(() => {
  const n = notices.value[0]
  if (n) return n.title
  /* 加载中写「正在加载」，不写「暂无」—— 后者是结论，数据没回来就下结论是错的 */
  return loading.value ? '正在加载…' : '暂时没有新通知'
})

/* ---------- 快捷操作 ---------- */
const quicks = [
  { img: '/static/icons/quick-leave.png', label: '申请请假', tone: 'r', to: '/pages/student/leave/apply' },
  { img: '/static/icons/tab-score.png', label: '我的成绩', tone: 'b', to: '/pages/student/score/index' },
  { img: '/static/icons/tab-seat.png', label: '我的座位', tone: 'g', to: '/pages/student/seat/index' },
  { img: '/static/icons/tab-mine.png', label: '我的资料', tone: 'y', to: '/pages/student/profile/index' }
]

/* ---------- 跳转 ---------- */
/* 底栏那五个页面是"平级"，必须用 redirectTo；其余是"往下钻一层"，用 navigateTo。
   混着用的后果很具体：首页 navigateTo 进成绩，再点底栏「首页」redirectTo 回首页，
   栈里就会变成 [首页, 首页] —— 按返回会退到另一个一模一样的首页。
   和老师端首页那份 TAB_PAGES 是同一个道理（那边用 switchTab）。

   注意：成绩、座位**不在**这个名单里。它们已经不是底栏页面了，
   从首页常用点进去是"往下钻一层"，用 navigateTo 才对，返回能回到首页。 */
const TAB_PAGES = [
  '/pages/student/home',
  '/pages/student/contact/index',
  '/pages/student/aidi/index',
  '/pages/student/leave/index',
  '/pages/student/profile/index'
]

function go(url) {
  if (TAB_PAGES.indexOf(url) > -1) {
    uni.redirectTo({ url })
  } else {
    uni.navigateTo({ url })
  }
}

function goProfile() {
  go('/pages/student/profile/index')
}

function openNotices() {
  /* 点进去就算看过了：把"最新的那条"记成已看，红点立刻灭。
     先记再跳 —— 跳过去之后这一页可能被销毁，回来时 onShow 会重新算 */
  markSeen()
  go('/pages/student/notice/index')
}

function markSeen() {
  const top = notices.value[0]
  const at = top ? Number(top.atMillis || 0) : 0
  if (!at || at <= seenAt.value) return
  seenAt.value = at
  try {
    uni.setStorageSync(SEEN_KEY, at)
  } catch (e) {
    /* 存不进去就只在本次会话里生效，不影响跳转 */
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 0;
  box-sizing: border-box;
}

/* 加载失败：套一张白卡，否则错误块直接浮在米色背景上，和整页风格断开 */
.err-wrap {
  background: #FFFFFF;
  border-radius: 36rpx;
  margin-top: 20rpx;
  box-shadow: 0 10rpx 30rpx rgba(160, 120, 80, 0.06);
}

/* ---------- 顶部白卡 ---------- */
.sheet {
  background: #FFFFFF;
  border-radius: 36rpx;
  padding: 24rpx 18rpx 18rpx;
  box-shadow: 0 10rpx 30rpx rgba(160, 120, 80, 0.06);
}

.topbar {
  display: flex;
  align-items: center;
  padding: 6rpx 6rpx 16rpx;
}

/* 学生没有头像，用姓名首字兜底 —— 比一张灰色默认图更有人味，
   也不用多一个资源文件。和老师端个人中心是同一套做法 */
.avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 22rpx;
  background: linear-gradient(140deg, #E8544D, #C0322F);
  color: #FFFFFF;
  font-size: 34rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.meta {
  flex: 1;
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.name-line {
  display: flex;
  align-items: center;
}

.name {
  font-size: 36rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1.2;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sub {
  font-size: 22rpx;
  color: #9A9186;
  margin-top: 6rpx;
}

/* 右上角「资料」：做成小药丸，比图标明确 —— 点了是看资料，不是设置 */
.edit {
  flex-shrink: 0;
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 24rpx;
}

.edit-pressed {
  background: #F9DAD6;
}

/* ---------- 红色欢迎卡 ---------- */
.hero {
  position: relative;
  border-radius: 28rpx;
  padding: 30rpx;
  background: linear-gradient(135deg, #E8594F 0%, #C5342B 100%);
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -70rpx;
  top: -80rpx;
  width: 280rpx;
  height: 280rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  position: relative;
  z-index: 1;
}

.hero-title {
  font-size: 30rpx;
  font-weight: 500;
  color: #FFFFFF;
  padding-top: 8rpx;
}

.clock {
  background: rgba(255, 255, 255, 0.16);
  border-radius: 16rpx;
  padding: 10rpx 18rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.clock-date {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.86);
}

.clock-time {
  font-size: 34rpx;
  font-weight: 600;
  color: #FFFFFF;
  margin-top: 2rpx;
  font-variant-numeric: tabular-nums;
}

.greet {
  position: relative;
  z-index: 1;
  display: block;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.95);
  line-height: 1.55;
  margin-top: 8rpx;
}

.greet:first-of-type {
  margin-top: 22rpx;
}

/* ---------- 四宫格 ---------- */
/* 不用 gap + calc(rpx)：那个组合真机上 width 会被整条丢掉，
   卡片会变成一行一张。纯百分比 + space-between 最稳 */
.stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-top: 20rpx;
}

.stat {
  flex: 0 0 48.6%;
  width: 48.6%;
  box-sizing: border-box;
  margin-bottom: 20rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 32rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.stat-num {
  font-family: Georgia, 'Times New Roman', 'Songti SC', serif;
  font-size: 60rpx;
  font-weight: 600;
  line-height: 1.1;
  /* 数字从「—」变成「3」时宽度会变，卡片会抖。
     锁最小宽度，切换过程看不出来 */
  min-width: 2ch;
  text-align: center;
}

.stat-label {
  font-size: 24rpx;
  color: #9A9186;
  margin-top: 10rpx;
}

.stat-pressed {
  background: #FDFAF6;
  transform: scale(0.97);
}

/* ---------- 通知条 ---------- */
.notice {
  margin-top: 4rpx;
  background: #FFFFFF;
  border-radius: 22rpx;
  padding: 22rpx 26rpx;
  display: flex;
  align-items: center;
  box-shadow: 0 6rpx 18rpx rgba(160, 120, 80, 0.05);
}

.notice-pressed {
  background: #FDFAF6;
}

/* 有新的：整条换红底。只加一个点的话，学生滑过去看不见 */
.notice.hot {
  background: #FDEDEB;
  box-shadow: 0 8rpx 22rpx rgba(197, 52, 43, 0.1);
}

.notice.off {
  background: #FBF7F1;
  box-shadow: none;
}

/* 左侧呼吸红点：动画比静态点更容易被余光扫到。
   1.6s 一次，幅度只到 0.35，太快会像在报错 */
.notice-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #C5342B;
  margin-right: 12rpx;
  flex-shrink: 0;
  animation: notice-breathe 1.6s ease-in-out infinite;
}

@keyframes notice-breathe {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.35;
    transform: scale(0.8);
  }
}

/* 数量徽标：红底白字的小药丸，和私信未读用的是同一套 */
.notice-badge {
  flex-shrink: 0;
  margin-left: 12rpx;
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 21rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.notice-chev {
  flex-shrink: 0;
  margin-left: 10rpx;
  font-size: 26rpx;
  color: #C6BAA9;
  line-height: 1;
}

.notice.hot .notice-chev {
  color: #C5342B;
}

.notice-ic {
  font-size: 26rpx;
  margin-right: 12rpx;
  flex-shrink: 0;
  line-height: 1;
}

.notice-text {
  flex: 1;
  font-size: 26rpx;
  color: #2B2622;
  line-height: 1.4;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.notice.off .notice-text {
  color: #B4A99B;
}

.notice.hot .notice-text {
  color: #C5342B;
  font-weight: 500;
}

/* ---------- 快捷操作 ---------- */
.quick {
  margin-top: 20rpx;
  background: #FFFFFF;
  border-radius: 36rpx;
  padding: 26rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.quick-head {
  display: flex;
  align-items: center;
  margin-bottom: 22rpx;
}

.quick-bar {
  width: 6rpx;
  height: 30rpx;
  border-radius: 3rpx;
  background: #C5342B;
  margin-right: 12rpx;
}

.quick-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
}

.quick-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.quick-item {
  flex: 0 0 48.6%;
  width: 48.6%;
  box-sizing: border-box;
  margin-bottom: 16rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 24rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 6rpx 18rpx rgba(160, 120, 80, 0.07);
}

.pressed {
  background: #FAF5EF;
  transform: scale(0.985);
}

.quick-icon-wrap {
  width: 72rpx;
  height: 72rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tone-r {
  background: #FDEDEB;
}

.tone-g {
  background: #EDF6EE;
}

.tone-b {
  background: #EAF1F8;
}

.tone-y {
  background: #FBF3DC;
}

.quick-icon-img {
  width: 40rpx;
  height: 40rpx;
}

.quick-label {
  font-size: 26rpx;
  color: #2B2622;
  margin-top: 12rpx;
}

/* 私信长条、通知长条、退出按钮的样式已随模板一起删掉 ——
   私信搬去通讯录，通知就是上面那条提醒条，退出搬去「我的」 */

.bottom-space {
  /* 底栏 100rpx + 安全区 + 一点余量。
     不能写死 140rpx：刘海屏的安全区有 68rpx，写死就会把最后一张卡压在底栏下面 */
  height: calc(140rpx + env(safe-area-inset-bottom));
}
</style>
