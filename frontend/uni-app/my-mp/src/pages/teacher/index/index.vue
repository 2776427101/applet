<template>
  <view class="page">
    <view class="status-bar" :style="{ height: statusBarHeight + 'px' }"></view>

    <!-- 加载失败：数据区整块换掉。
         原来失败时 overview 是 null，页面会显示「未绑定班级」+ 四个 0，
         看起来像「这个班没了」，比白屏更吓人 -->
    <view v-if="err && !overview" class="err-wrap">
      <load-error
        :message="err"
        hint="首页的数字来自服务器，网络恢复后点重试就行"
        @retry="load"
      />
    </view>

    <!-- 正常数据区。下面这块用 template 包起来，
         是为了让「加载失败」时能整块换掉，而不用给每个卡片各加一个 v-if -->
    <template v-else>
    <!-- 顶部白卡：班级栏 + 欢迎卡 -->
    <view class="sheet">
      <view class="topbar">
        <image class="badge" src="/static/icons/badge-class.png" mode="aspectFit" />
        <!-- 班级名整块可点：这是老师想改班名时第一个会去点的地方。
             右侧给一个小箭头，否则没人知道这里能点 -->
        <view class="meta" hover-class="meta-pressed" @click="goClassSetting">
          <view class="name-line">
            <text class="name">{{ classInfo.name }}</text>
            <text class="name-edit">›</text>
          </view>
          <text class="sub">{{ teacherLabel }}</text>
        </view>
        <view class="actions">
          <view
            class="action"
            v-for="a in topActions"
            :key="a.key"
            hover-class="pressed"
            @click="onTopAction(a.label)"
          >
            <image class="action-icon" :src="a.icon" mode="aspectFit" />
          </view>
        </view>
      </view>

      <view class="hero">
        <view class="hero-top">
          <!-- 这里原来写的是「628班 · 高一」，和上面班级栏重复了。
               换成日期和周次：老师每天真正要看的是「今天周几、第几周」 -->
          <text class="hero-title">{{ dayLabel }} · {{ weekText }}</text>
          <view class="clock">
            <text class="clock-date">{{ dateText }}</text>
            <text class="clock-time">{{ timeText }}</text>
          </view>
        </view>

        <text class="greet">{{ greeting }}！{{ classInfo.teacherName }}老师，今天</text>
        <text class="greet">也辛苦了 😌</text>
      </view>
    </view>

    <!-- 数据四宫格：现在每个都能点，点完跳对应页面 -->
    <view class="stats">
      <view
        class="stat"
        v-for="s in stats"
        :key="s.label"
        hover-class="stat-pressed"
        @click="onStat(s)"
      >
        <text class="stat-num" :style="{ color: s.color }">{{ s.value }}</text>
        <text class="stat-label">{{ s.label }}</text>
      </view>
    </view>

    <!-- 每日金句 -->
    <view class="quote">
      <text class="quote-spark">✨</text>
      <text class="quote-text">{{ quote }}</text>
    </view>
    </template>

    <!-- 快捷操作：不依赖接口数据，加载失败时也保留。
         数据没了但导航还在，老师至少还能去别的页面干活 -->
    <view class="quick">
      <view class="quick-head">
        <view class="quick-bar"></view>
        <text class="quick-title">⚡ 快捷操作</text>
      </view>

      <view class="quick-grid">
        <view
          class="quick-item"
          v-for="q in quicks"
          :key="q.label"
          hover-class="pressed"
          @click="onQuick(q)"
        >
          <!-- 图标外面套一层浅色圆角底，四个入口靠颜色区分 -->
          <view class="quick-icon-wrap" :class="'tone-' + q.tone">
            <image class="quick-icon-img" :src="q.img" mode="aspectFit" />
          </view>
          <text class="quick-label">{{ q.label }}</text>
        </view>
      </view>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
/* 页面生命周期要从 @dcloudio/uni-app 导入，Vue的setup里没有 onShow */
import { onShow } from '@dcloudio/uni-app'
import { fetchOverview } from '@/api/home'
import LoadError from '@/components/load-error/load-error.vue'
/* 状态栏高度：custom 导航下必须自己让出来 */
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 20
/* ---------- 接口数据 ---------- */
const overview = ref(null) /* 整个响应体，null 表示还没加载完 */
const loading = ref(true)
const err = ref('') /* 加载失败的原因。空串 = 没失败 */

async function load() {
  /* 重试前清掉上次的错误，否则即使这次成功了错误块也不会消失 */
  err.value = ''
  try {
    /* silent：页面自己会画「加载失败 + 重试」，再弹 toast 就是同一句话说两遍 */
    overview.value = await fetchOverview({ silent: true })
  } catch (e) {
    /* 只在手里没有旧数据时才记错误。
       已经有数据的情况下刷新失败，继续显示旧数据比整块盖掉更有用 */
    if (!overview.value) {
      err.value = (e && e.message) || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

/* 用 onShow 不用 onLoad：从别的 tab 切回来时，数字要是最新的 */
onShow(() => load())
/* 班级信息：接口没回来、或者老师还没绑班时给个兜底对象，
   否则模板里 classInfo.name 会报 undefined */
const classInfo = computed(() => overview.value?.classInfo || {
  name: '未绑定班级',
  grade: '',
  teacherName: '',
  teacherTitle: ''
})
/* 顶部那行「班主任 · 李光」：接口里没有现成字段，由两个字段拼出来 */
const teacherLabel = computed(() => {
  const c = classInfo.value
  return c.teacherName ? `${c.teacherTitle} · ${c.teacherName}` : '点击绑定班级'
})
/* 四个数字：值来自接口，文案和颜色留在前端（属于展示层，不该让后端管）
   to = 点击后要跳的页面，必须是 tabBar 里注册过的；空字符串表示那个页面还没做 */
const stats = computed(() => {
  const s = overview.value?.stats || {}
  /* 只有真的在加载时才显示破折号，加载完一律显示数字 */
  const dash = loading.value ? '—' : null
  return [
    {
      value: dash ?? String(s.leaveToday ?? 0),
      label: '今日请假',
      color: '#C9A227',
      to: '/pages/teacher/leave/index'
    },
    {
      /* 原来是「生日为 0 就显示 —」，和「今日请假 0」口径不一致，
         用户会以为生日数据没加载出来。现在统一显示数字 */
      value: dash ?? String(s.birthdayToday ?? 0),
      label: '今日生日',
      color: '#8C3B34',
      to: '/pages/teacher/student/index'
    },
    {
      value: dash ?? String(s.studentCount ?? 0),
      label: '学生',
      color: '#A81E28',
      to: '/pages/teacher/student/index'
    },
    {
      value: dash ?? String(s.todoUndone ?? 0),
      label: '待办未完成',
      color: '#8A6A2E',
      to: '/pages/teacher/todo/index'
    }
  ]
})
/* 第几周：后端按 term_start_date 算好的，前端不要自己算（两边算会不一致） */
const weekNo = computed(() => overview.value?.term?.weekNo || 0)
const weekText = computed(() => (weekNo.value > 0 ? `第${weekNo.value}周` : '未开学'))
/* 金句：后端先写死的常量，以后可能换成表 */
const quote = computed(() => overview.value?.quote || '耐心是教育的底色。')
/* 顶部操作：原来有 5 个（设备/校历/搜索/设置/扫一扫），
   「设备」和「扫一扫」班主任日常根本用不到，却占了右上角最贵的位置。
   只留两个真正会点的，其余等设置页做出来再收进去 */
const topActions = [
  { key: 'search', icon: '/static/icons/top-search.png', label: '搜索' },
  { key: 'setting', icon: '/static/icons/top-setting.png', label: '设置' }
]

/* 快捷操作：go() 会自己判断该用 switchTab 还是 navigateTo，
   所以这里只写目标页，不用管它在不在 tabBar 里。
   「加学生」要直接进表单页，不是名单页 —— 老师的意图是"加一个人"，
   先进名单再点右下角 ＋ 就多绕了一步 */
const quicks = [
  { img: '/static/icons/quick-add.png',   label: '加学生', tone: 'r', to: '/pages/teacher/student/edit' },
  { img: '/static/icons/quick-score.png', label: '记成绩', tone: 'g', to: '/pages/teacher/score/index' },
  { img: '/static/icons/quick-leave.png', label: '记请假', tone: 'b', to: '/pages/teacher/leave/index' },
  { img: '/static/icons/quick-todo.png',  label: '待办',   tone: 'y', to: '/pages/teacher/todo/index' }
]

/* ---------- 时间 ---------- */
const now = ref(new Date())
const pad = (n) => String(n).padStart(2, '0')

/* 原来每秒把 now 换成一个新对象，下面 4 个 computed 跟着全部重算。
   但界面上只显示「时:分」，秒数对班主任没有任何意义。
   现在每秒只做一次很轻的比较，分钟真的变了才触发重渲染 */
const timer = setInterval(() => {
  const d = new Date()
  if (d.getMinutes() !== now.value.getMinutes()) {
    now.value = d
  }
}, 1000)

onUnmounted(() => clearInterval(timer))

/* 「周五」这种星期几，红色卡首行要用 */
const dayLabel = computed(() => {
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][now.value.getDay()]
})

/* 只显示「月-日」：星期几已经放到首行了，这里再写一遍是重复 */
const dateText = computed(() => {
  const d = now.value
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

/* 去掉了秒：秒数只会让数字一直跳，没人看 */
const timeText = computed(() => {
  const d = now.value
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
})

const greeting = computed(() => {
  const h = now.value.getHours()
  if (h < 6) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

/* ---------- 交互 ---------- */
/* tabBar 里注册过的页面只能用 switchTab 跳，用 navigateTo 会失败；
   反过来，没进 tabBar 的页面（比如待办）只能用 navigateTo。
   把这份名单放在一处，以后新增页面只改这里，不用在每个点击事件里判断 */
const TAB_PAGES = [
  '/pages/teacher/index/index',
  '/pages/teacher/student/index',
  '/pages/teacher/score/index',
  '/pages/teacher/seat/index',
  '/pages/teacher/leave/index'
]

const go = (to) => {
  if (TAB_PAGES.indexOf(to) > -1) {
    uni.switchTab({ url: to })
  } else {
    uni.navigateTo({ url: to })
  }
}

/* 四宫格：有目标页就跳过去，没有就提示「还没做」 */
const onStat = (s) => {
  if (s.to) {
    go(s.to)
  } else {
    uni.showToast({ title: `${s.label} · 页面还没做`, icon: 'none' })
  }
}

/* 快捷操作：同上 */
const onQuick = (q) => {
  if (q.to) {
    go(q.to)
  } else {
    uni.showToast({ title: `${q.label} · 页面还没做`, icon: 'none' })
  }
}

/* 顶部两个图标：搜索还没做，设置直接进班级设置页 ——
   右上角「设置」是老师找「改班级名」的第二个地方（第一个是点班级名），
   两个入口指向同一页，不用猜 */
const onTopAction = (label) => {
  if (label === '设置') {
    goClassSetting()
    return
  }
  uni.showToast({ title: `${label} · 开发中`, icon: 'none' })
}

/* 班级设置页不是 tabBar 页，用 navigateTo */
const goClassSetting = () => {
  uni.navigateTo({ url: '/pages/teacher/class/setting' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 0 24rpx;
  box-sizing: border-box;
}

.status-bar {
  width: 100%;
}

/* 加载失败：套一张白卡，否则错误块会直接浮在米色背景上，和整页风格断开 */
.err-wrap {
  background: #FFFFFF;
  border-radius: 36rpx;
  box-shadow: 0 10rpx 30rpx rgba(160, 120, 80, 0.06);
}

/* 顶部白卡 */
.sheet {
  background: #FFFFFF;
  border-radius: 36rpx;
  /* 顶部多留一点：里面的班级徽章是 76rpx，18rpx 的顶距会显得挤 */
  padding: 24rpx 18rpx 18rpx;
  box-shadow: 0 10rpx 30rpx rgba(160, 120, 80, 0.06);
}

/* 班级栏 */
.topbar {
  display: flex;
  align-items: center;
  padding: 6rpx 6rpx 16rpx;
}

.badge {
  width: 76rpx;
  height: 76rpx;
  border-radius: 22rpx;
  flex-shrink: 0;
}

.meta {
  flex: 1;
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 按下反馈：整块 meta 是个按钮，没反馈会让人以为点空了 */
.meta-pressed {
  opacity: 0.55;
}

/* 班级名 + 右箭头同一行 */
.name-line {
  display: flex;
  align-items: center;
}

/* 箭头颜色比正文浅两档：是提示不是内容，不能抢班名的注意力 */
.name-edit {
  font-size: 34rpx;
  color: #C6BAA9;
  margin-left: 8rpx;
  line-height: 1;
}

.name {
  font-size: 36rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1.2;
}

.sub {
  font-size: 22rpx;
  color: #9A9186;
  margin-top: 6rpx;
}

.actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.action {
  width: 58rpx;
  height: 58rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-shadow: 0 4rpx 12rpx rgba(120, 96, 72, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 10rpx;
}

.action-icon {
  width: 34rpx;
  height: 34rpx;
}

/* 红色欢迎卡 */
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

/* 数据四宫格 */
.stats {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  margin-top: 20rpx;
}

.stat {
  width: calc(50% - 10rpx);
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
  /* 数字从「—」变成「47」时宽度会变，卡片会抖一下。
     锁定最小宽度，让切换过程看不出来 */
  min-width: 2ch;
  text-align: center;
}

.stat-label {
  font-size: 24rpx;
  color: #9A9186;
  margin-top: 10rpx;
}

/* 四宫格按下去的手感 */
.stat-pressed {
  background: #FDFAF6;
  transform: scale(0.97);
}

/* 金句 */
.quote {
  margin-top: 20rpx;
  background: #F7EDD2;
  border-radius: 22rpx;
  padding: 22rpx 26rpx;
  display: flex;
  align-items: center;
}

.quote-spark {
  font-size: 24rpx;
  margin-right: 12rpx;
}

.quote-text {
  font-size: 26rpx;
  color: #8A6A3A;
}

/* 快捷操作 */
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
  gap: 16rpx;
}

.quick-item {
  width: calc(50% - 8rpx);
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

/* 图标外面那层浅色圆角底：四个入口靠底色区分功能类型 */
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

.bottom-space {
  height: 30rpx;
}
</style>
