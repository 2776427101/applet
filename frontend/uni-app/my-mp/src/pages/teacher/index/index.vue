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
        <!-- 班级栏整块可点：多班级下这里是"切换班级"的入口，
             不是"改班名"。改班名在右上角「设置」里。
             右侧 ⌄ 是下拉箭头，比 › 更能表达"会弹出一个列表" -->
        <view class="meta" hover-class="meta-pressed" @click="openPicker">
          <view class="name-line">
            <text class="name">{{ classInfo.name }}</text>
            <text class="name-edit">⌄</text>
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

    <!-- 通知：原来这里是每日金句。同样的位置、同样的高度，
         换成「现在要你处理什么」—— 金句读一次就够了，通知每天都有用。
         高度严格和原来一致（一行、22rpx 内边距），因为下面是四宫格，
         这个框一高，整页就要往下推。

         有新的（比上次点进通知页更新）时整条换红底 + 左侧呼吸红点 + 数量徽标。
         通知本身没有已读状态（按业务状态算出来的），红点只能客户端判，
         详见 student/home.vue 里同一套写法 -->
    <view
      class="notice"
      :class="{ off: !noticeTotal, hot: hasNew }"
      hover-class="notice-pressed"
      @click="openNotices"
    >
      <view v-if="hasNew" class="notice-dot"></view>
      <text class="notice-ic">{{ noticeIcon }}</text>
      <text class="notice-text">{{ noticeText }}</text>
      <!-- 用 total 不是 list.length：接口默认只给 20 条，
           有 25 条时写「20 条」就是错的 -->
      <view v-if="hasNew" class="notice-badge">{{ newCount > 99 ? '99+' : newCount }}</view>
      <text class="notice-chev">›</text>
    </view>
    </template>

    <!-- 课程群组入口。放在这里而不是四宫格里：四宫格是 2×2 的统计数字，
         塞第五个会破坏布局；而且这个入口不依赖首页接口数据，加载失败时也该能点 -->
    <view class="ce" hover-class="pressed" @click="goContainers">
      <view class="ce-icon">📚</view>
      <view class="ce-main">
        <text class="ce-title">课程群组</text>
        <text class="ce-sub">每门课一个群，学生凭邀请码加入</text>
      </view>
      <text class="ce-arrow">›</text>
    </view>

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

    <!-- 班级选择器：底部弹层，不占首页版面。
         用自定义弹层而不是 uni.showActionSheet：那个在微信上最多 6 项，
         老师带 7 个班时就选不全了 -->
    <view v-if="pickerOpen" class="picker-mask" @click="pickerOpen = false">
      <view class="picker" @click.stop>
        <view class="picker-head">
          <text class="picker-title">切换班级</text>
          <text class="picker-close" @click="pickerOpen = false">✕</text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view
            v-for="c in classes"
            :key="c.id"
            class="picker-row"
            :class="{ on: c.id === currentClassId }"
            hover-class="picker-row-pressed"
            @click="pickClass(c)"
          >
            <view class="picker-main">
              <text class="picker-name">{{ c.name }}</text>
              <text class="picker-meta">{{ c.studentCount }} 人{{ c.grade ? ' · ' + c.grade : '' }}</text>
            </view>
            <text v-if="c.id === currentClassId" class="picker-check">✓</text>
          </view>
          <view v-if="!classes.length" class="picker-empty">你还没有班级</view>
        </scroll-view>
        <view class="picker-foot" hover-class="pressed" @click="goCreateClass">＋ 新建班级</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
/* 页面生命周期要从 @dcloudio/uni-app 导入，Vue的setup里没有 onShow */
import { onShow } from '@dcloudio/uni-app'
import { fetchOverview, fetchNotices } from '@/api/home'
import {
  classes,
  currentClassId,
  loadClasses,
  setCurrentClass
} from '@/store/class'
import LoadError from '@/components/load-error/load-error.vue'
/* 状态栏高度：custom 导航下必须自己让出来 */
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 20
/* ---------- 接口数据 ---------- */
const overview = ref(null) /* 整个响应体，null 表示还没加载完 */
const loading = ref(true)
const err = ref('') /* 加载失败的原因。空串 = 没失败 */

/* 通知列表。空数组 = 没有要处理的事，不是加载失败 —— 两种情况在框里
   长得一样（都是一行灰字），但文案必须分开 */
const notices = ref([])
/* 真实条数。list 只有前 20 条，框里写「N 条」要用这个 */
const noticeTotal = ref(0)

/* 「看过了」的时间点，存本地。
   通知没有已读状态（后端按业务状态现算，不记谁看过），所以红点只能客户端判：
   比上次点进通知页更新，就是新的。详见 student/home.vue 里同一套写法 */
const SEEN_KEY = 'teacher_notice_seen_at'
const seenAt = ref(Number(uni.getStorageSync(SEEN_KEY) || 0))

const newCount = computed(() => {
  const t = seenAt.value
  return notices.value.filter((n) => Number(n.atMillis || 0) > t).length
})

const hasNew = computed(() => newCount.value > 0)

/* 班级选择器开关。多班级下首页第一件事是"看哪个班"，所以入口放在班级栏上 */
const pickerOpen = ref(false)

async function load() {
  /* 重试前清掉上次的错误，否则即使这次成功了错误块也不会消失 */
  err.value = ''
  try {
    /* 先确保班级列表已加载：多班级下首页必须知道"看哪个班"，
       否则会拿到后端兜底的第一个班，老师切换后数字不变 */
    await loadClasses()
    /* 两个请求并行发，不要串行 —— 串行就是白等一个来回。
       silent：页面自己会画「加载失败 + 重试」，再弹 toast 就是同一句话说两遍
       通知失败不连累整页：catch 成 null，下面保留上一次的列表 */
    const [ov, nt] = await Promise.all([
      fetchOverview({ classId: currentClassId.value, silent: true }),
      /* 通知不传 classId = 我的所有班。
         切班才看得见另一个班的请假，等于没提醒 —— 数字看当前班，通知跨班 */
      fetchNotices({ silent: true }).catch(() => null)
    ])
    overview.value = ov
    if (nt) {
      notices.value = nt.list || []
      noticeTotal.value = nt.total || 0
    }
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
/* ---------- 通知框 ---------- */
/* 框只有一行高，所以只显示第一条；右边的「N 条」提示还有别的，点进去看全部。
   图标按类型换，比清一色的铃铛更能一眼分辨"是请假还是待办" */
const noticeIcon = computed(() => {
  const k = notices.value[0] && notices.value[0].kind
  if (k === 'leave') return '📝'
  if (k === 'todo') return '⏰'
  if (k === 'birthday') return '🎂'
  return '🔔'
})

const noticeText = computed(() => {
  const n = notices.value[0]
  if (n) {
    /* 带两个班时必须写清是哪个班 —— 通知是跨班聚合的，
       只写"王小明提交了请假申请"，老师不知道是哪个班的王小明。
       只有一个班就不写，省下来的宽度留给标题本身 */
    return classes.value.length > 1 && n.className ? `${n.className} · ${n.title}` : n.title
  }
  /* 加载中写「加载中」而不是「暂无」：后者是结论，数据还没回来就下结论
     会让老师以为真的没事 */
  return loading.value ? '正在加载…' : '暂无新通知'
})
/* 顶部操作：原来有 5 个（设备/校历/搜索/设置/扫一扫），
   「设备」和「扫一扫」班主任日常根本用不到，却占了右上角最贵的位置。
   只留两个真正会点的，其余等设置页做出来再收进去 */
const topActions = [
  { key: 'search', icon: '/static/icons/top-search.png', label: '搜索' },
  { key: 'setting', icon: '/static/icons/top-setting.png', label: '设置' }
]

/* 快捷操作：go() 会自己判断该用 switchTab 还是 navigateTo，
   所以这里只写目标页，不用管它在不在 tabBar 里。

   首页只留「记请假」和「待办」。
   「加学生」「记成绩」从首页撤掉了，但功能没删、页面都还在：
     - 加学生：通讯录 → 某个班 → 班级联系人 → 右下角 ＋
     - 记成绩：个人中心 → 成绩
   撤掉的理由是使用频率。加学生是一次导入几十个人、之后基本不动；
   记成绩是期中期末各一次。这两件事占着首页最显眼的四个格子之一，
   而老师每天真正要点的只有「今天谁请假」「今天要做什么」。
   首页的格子应该按「多久点一次」排，不是按「功能重要不重要」排 */
const quicks = [
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
  '/pages/teacher/contact/index',
  '/pages/teacher/leave/index',
  '/pages/teacher/mine/index'
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
  if (!s.to) {
    uni.showToast({ title: `${s.label} · 页面还没做`, icon: 'none' })
    return
  }
  /* 学生 / 生日这两个入口进的是"当前班的学生名单"。
     名单页已经不是 tab 页了，所以能带参数；带上 classId，
     否则从二班点进来会看到一班的名单。
     classId 为 0 时**不要**带 —— 后端会把 classId=0 当成"查 id 为 0 的班"，
     直接回 1004，名单页就变成一个错误块 */
  if (s.to === '/pages/teacher/student/index') {
    const cid = currentClassId.value
    uni.navigateTo({ url: cid ? `${s.to}?classId=${cid}` : s.to })
    return
  }
  go(s.to)
}

/* ---------- 班级选择 ---------- */
function openPicker() {
  pickerOpen.value = true
  /* 打开时顺手刷新一次：老师可能刚在通讯录里加了学生，
     不刷的话这里的人数还是上次的 */
  loadClasses(true).catch(() => {})
}

async function pickClass(c) {
  pickerOpen.value = false
  if (c.id === currentClassId.value) return

  setCurrentClass(c.id)
  /* 切换后整页数据都要换：班级名、四个数字、第几周。
     置 loading 是为了让数字先变「—」，否则会短暂显示上个班的数字 */
  loading.value = true
  await load()
  uni.showToast({ title: `已切换到 ${c.name}`, icon: 'none' })
}

function goCreateClass() {
  pickerOpen.value = false
  uni.navigateTo({ url: '/pages/teacher/class/create' })
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

/* 通知：有内容才跳转页。空的时候点了给一句提示，
   而不是跳到一个空列表让人以为加载失败 */
const openNotices = () => {
  if (!noticeTotal.value) {
    uni.showToast({ title: '暂时没有新通知', icon: 'none' })
    return
  }
  /* 点进去就算看过了：红点立刻灭。先记再跳 ——
     跳过去之后这一页可能被销毁，回来时 onShow 会重新算 */
  markNoticesSeen()
  /* 不带 classId：通知页也是跨班的，和首页那个框同一个口径 */
  uni.navigateTo({ url: '/pages/teacher/notice/index' })
}

function markNoticesSeen() {
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

/* 班级设置页不是 tabBar 页，用 navigateTo */
const goClassSetting = () => {
  uni.navigateTo({ url: '/pages/teacher/class/setting' })
}

/* 学科容器列表同样不是 tab 页 */
const goContainers = () => {
  uni.navigateTo({ url: '/pages/teacher/container/index' })
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
/* 这里刻意不用 gap + calc(50% - 10rpx)。
   原因：calc() 里面写 rpx，开发者工具能算对，但真机上这条 width 会被
   整个丢掉 —— 卡片失去宽度就变成「一张占一行」，四张卡竖着排下来，
   把首页撑得很长。改成纯百分比 + space-between：
   两个数都是渲染内核最老最稳的能力，不存在算错的可能。 */
.stats {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-top: 20rpx;
}

.stat {
  /* flex-basis 和 width 同时给 48.6%：两个都写是有意的。
     只写 width 的话，某些内核把这条丢掉后卡片会退化成「整行一张」；
     flex-basis 由 flex 布局自己消费，和 width 是两条独立的路径，
     只要有一条生效，就还是两列。 */
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

/* 通知框。尺寸完全沿用原来的金句框：同样的 margin / padding / 圆角，
   一行文字 26rpx —— 换内容不换高度，下面的四宫格位置不会动 */
.notice {
  margin-top: 20rpx;
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

/* 没有通知时整块降低对比度，一眼能看出"这条不用管" */
.notice.off {
  background: #FBF7F1;
  box-shadow: none;
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
  /* 单行省略：接龙/请假标题都可能很长，换行会把框撑高 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.notice.off .notice-text {
  color: #B4A99B;
}

/* 有新的：整条换红底。只加一个点的话，老师扫一眼就过去了 */
.notice.hot {
  background: #FDEDEB;
  box-shadow: 0 8rpx 22rpx rgba(197, 52, 43, 0.1);
}

.notice.hot .notice-text {
  color: #C5342B;
  font-weight: 500;
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

/* 数量徽标：红底白字的小药丸，和私信未读用同一套 */
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

/* 快捷操作 */
.quick {
  margin-top: 20rpx;
  background: #FFFFFF;
  border-radius: 36rpx;
  padding: 26rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

/* 学科容器入口卡 */
.ce {
  margin-top: 20rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 26rpx 24rpx;
  display: flex;
  align-items: center;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.ce-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 24rpx;
  background: #EAF1F8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  flex-shrink: 0;
}

.ce-main {
  flex: 1;
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ce-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #2B2622;
}

.ce-sub {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #9A9186;
}

.ce-arrow {
  font-size: 32rpx;
  color: #C6BAA9;
  flex-shrink: 0;
  margin-left: 12rpx;
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
  /* 和上面的四宫格同一个理由：不用 gap + calc(rpx)，改成百分比 + space-between */
  justify-content: space-between;
}

.quick-item {
  /* 同 .stat：flex-basis + width 双保险 */
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

/* 图标外面那层浅色圆角底：入口靠底色区分功能类型 */
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

/* ---------- 班级选择弹层 ---------- */
.picker-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(43, 38, 34, 0.42);
  z-index: 900;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.picker {
  background: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  padding: 28rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  max-height: 76vh;
  display: flex;
  flex-direction: column;
}

.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx 20rpx;
}

.picker-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.picker-close {
  font-size: 30rpx;
  color: #B4A99B;
  padding: 4rpx 12rpx;
}

.picker-list {
  /* scroll-view 在 flex 列里要给 0 高，否则会按内容撑开把底部按钮顶出去 */
  flex: 1;
  height: 0;
  min-height: 0;
}

.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 20rpx;
  border-radius: 20rpx;
  margin-bottom: 10rpx;
  background: #FDF8F1;
}

.picker-row.on {
  background: #FDEDEB;
}

.picker-row-pressed {
  opacity: 0.7;
}

.picker-main {
  display: flex;
  flex-direction: column;
}

.picker-name {
  font-size: 29rpx;
  color: #2B2622;
  font-weight: 500;
}

.picker-meta {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.picker-check {
  font-size: 30rpx;
  color: #C5342B;
  font-weight: 600;
}

.picker-empty {
  padding: 60rpx 0;
  text-align: center;
  font-size: 25rpx;
  color: #B4A99B;
}

.picker-foot {
  margin-top: 14rpx;
  height: 88rpx;
  border-radius: 999rpx;
  border: 1rpx dashed #E3D6C6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 27rpx;
  color: #8A6A3E;
}
</style>
