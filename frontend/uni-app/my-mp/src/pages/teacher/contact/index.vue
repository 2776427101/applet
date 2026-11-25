<template>
  <view class="page">
    <!-- 加载失败：整块换掉，不显示 0 个班（那是假数据） -->
    <load-error
      v-if="failed"
      :message="err"
      hint="通讯录来自服务器，网络恢复后点重试就行"
      @retry="load(true)"
    />

    <template v-else>
      <view class="head">
        <text class="head-count">{{ classes.length }} 个班级</text>
        <text class="head-add" @click="goCreate">＋ 新建班级</text>
      </view>

      <!-- 骨架屏：结构跟真实内容一致，数据回来不会跳 -->
      <view v-if="loading" class="list">
        <view class="sk-label"></view>
        <view class="card card-groups">
          <view class="sk-row"></view>
          <view class="sk-row"></view>
        </view>
        <view class="sk-label"></view>
        <view class="card card-class" v-for="i in 2" :key="'sk' + i">
          <view class="sk-line" style="width: 200rpx"></view>
          <view class="sk-row"></view>
        </view>
      </view>

      <!-- 两个区：上面是群，下面是班。
           群和班是两种东西 —— 群是聊天的地方，班是名单。
           混在一张卡里只能靠 1rpx 的线区分，拆成两区之后一眼就分得清 -->
      <view v-else class="list">
        <template v-if="classes.length">
          <!-- ============ 私信 ============
               放在最上面：老师打开通讯录，最常见的目的就是"谁刚找过我"。
               聊过的人才出现，但空的时候也保留一区并给一句说明 ——
               整块藏起来的话，老师会以为这个功能不存在 -->
          <view class="section">
            <text class="section-title">私信</text>
            <text v-if="unreadTotal" class="section-note">{{ unreadTotal }} 条未读</text>
          </view>

          <view class="card card-dm">
            <view v-if="!threads.length" class="dm-empty">
              <text class="dm-empty-text">还没有私信。在学生详情页点「私信」，就能单独联系学生</text>
            </view>

            <view
              v-for="(t, i) in threads"
              :key="t.studentId"
              class="dm-row"
              :class="{ 'dm-row-first': i === 0 }"
              hover-class="dm-row-pressed"
              @click="openThread(t)"
            >
              <view class="dm-avatar">{{ initial(t.name) }}</view>
              <view class="dm-main">
                <view class="dm-line">
                  <text class="dm-name">{{ t.name }}</text>
                  <text class="dm-time">{{ t.lastTime }}</text>
                </view>
                <view class="dm-line">
                  <text class="dm-last">{{ lastText(t) }}</text>
                  <view v-if="t.unread" class="dm-badge">{{ t.unread > 99 ? '99+' : t.unread }}</view>
                </view>
              </view>
            </view>
          </view>

          <!-- ============ 班级群 ============ -->
          <view class="section">
            <text class="section-title">班级群</text>
            <text v-if="groupMissing" class="section-note">{{ groupMissing }} 个未创建</text>
          </view>

          <!-- 所有群放一张卡里：同一种东西，用细分隔线串起来比一张张卡更紧凑，
               也更容易一眼扫完「我一共几个群、各多少人」 -->
          <view class="card card-groups">
            <view
              class="grow"
              v-for="(c, i) in classes"
              :key="'g' + c.id"
              :class="{ 'grow-first': i === 0 }"
              hover-class="grow-pressed"
              @click="openGroup(c)"
            >
              <view class="grow-icon">群</view>
              <text class="grow-name">{{ c.containerName || c.name }}</text>
              <text class="grow-count" :class="{ todo: !c.containerId }">
                {{ c.containerId ? c.groupTotalCount + ' 人' : '未创建' }}
              </text>
              <text class="chev">›</text>
            </view>
          </view>

          <!-- ============ 班级 ============ -->
          <view class="section">
            <text class="section-title">班级</text>
          </view>

          <view class="card card-class" v-for="c in classes" :key="c.id">
            <view class="card-head">
              <view class="card-title-wrap">
                <text class="card-title">{{ c.name }}</text>
                <text class="card-sub">{{ classSub(c) }}</text>
              </view>
              <view class="card-right">
                <text class="card-count">{{ c.studentCount }} 人</text>
                <!-- 改名 / 删除。用 ⋯ 而不是把按钮摊平：
                     这两个是低频且有一个是危险操作，不该和「班级联系人」抢位置 -->
                <text class="card-more" hover-class="card-more-pressed" @click.stop="openInfo(c)">⋯</text>
              </view>
            </view>

            <!-- 联系人：这个班的学生名单 -->
            <view
              class="entry"
              hover-class="entry-pressed"
              @click="openStudents(c)"
            >
              <view class="entry-icon people">人</view>
              <view class="entry-main">
                <text class="entry-title">班级联系人</text>
                <text class="entry-meta">{{ c.studentCount }} 位学生</text>
              </view>
              <text class="chev">›</text>
            </view>
          </view>
        </template>

        <!-- 空状态：给一个明确的动作，不要只写「暂无数据」 -->
        <view v-if="!classes.length" class="empty">
          <text class="empty-title">你还没有班级</text>
          <text class="empty-tip">建一个班级，系统会自动建好这个班的班级群</text>
          <view class="empty-btn" @click="goCreate">新建班级</view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { ensureClassGroup } from '@/api/class'
import { fetchThreads } from '@/api/message'
import { loadClasses, setCurrentClass } from '@/store/class'
import LoadError from '@/components/load-error/load-error.vue'

/* 页面自己存一份，方便控制 loading / 错误态；
   数据来源和 store 是同一个接口，load 成功后同步写回 store，
   这样首页和成绩页切 tab 时不用再请求一次 */
const classes = ref([])
const loading = ref(true)
const err = ref('')
const failed = ref(false)

/* 还没建群的班有几个。写在分区标题右边，是因为「有几个没建」
   比「每个群多少人」更需要被提醒 —— 后者点进去就看得到 */
const groupMissing = computed(
  () => classes.value.filter((c) => !c.containerId).length
)

/* ---------- 私信会话 ---------- */
const threads = ref([])

/* 未读总数只放在分区标题右边。每一行自己也有红点，
   这里再列一遍"谁谁几条"就重复了 */
const unreadTotal = computed(() =>
  threads.value.reduce((n, t) => n + (t.unread || 0), 0)
)

/* 头像里放名字第一个字。名字为空（导入时可能缺）就给个「学」，
   不要留一个空格子 —— 空圆看着像加载失败 */
function initial(name) {
  return (name || '').trim().charAt(0) || '学'
}

/* 最后一句加「我：」前缀。不加的话，老师和学生说的混在一起分不清
   哪句是自己说的，而这恰恰是扫一眼列表时最想知道的事 */
function lastText(t) {
  const text = (t.lastContent || '').replace(/\s+/g, ' ')
  return t.lastSenderRole === 1 ? `我：${text}` : text
}

/* 私信区失败不影响通讯录主体：班级名单才是这一页的本职，
   私信是附加信息，为它整页报错不值得 */
async function loadThreads() {
  try {
    threads.value = (await fetchThreads()) || []
  } catch (e) {
    /* 保留上一次的列表，不闪成空 */
  }
}

function openThread(t) {
  uni.navigateTo({ url: `/pages/teacher/student/message?id=${t.studentId}` })
}

/* 班主任就是当前登录的老师。原来这行写死「班主任是你」——
   一行字全在说「你」，反而看不出这个班是谁的。
   登录时自动建的账号 name 是空串，所以取不到名字就只写「班主任」，
   不能硬拼一个空格或「未设置」出来 */
const teacherName = ref('')
try {
  const cached = uni.getStorageSync('userInfo') || {}
  teacherName.value = (cached.name || '').trim()
} catch (e) {
  /* 存储不可用就当没名字，不影响名单 */
}

function classSub(c) {
  const grade = c.grade || '未填年级'
  return teacherName.value ? `${grade} · 班主任 ${teacherName.value}` : `${grade} · 班主任`
}

async function load(force = false) {
  err.value = ''
  try {
    const rows = await loadClasses(force)
    classes.value = rows || []
    failed.value = false
  } catch (e) {
    /* 有旧数据就继续显示旧的：通讯录闪成空白比显示旧名单更糟 */
    if (!classes.value.length) {
      err.value = (e && e.message) || '加载失败'
      failed.value = true
    }
  } finally {
    loading.value = false
  }
}

/* 每次进 tab 都刷新：老师可能刚在别处加了学生、改了班名，
   也可能刚在私信里回了话（未读红点要跟着消） */
onShow(() => {
  load(true)
  loadThreads()
})

/* 进群聊。班级群没有就先建一个（历史数据或建群失败的情况） */
async function openGroup(c) {
  let cid = c.containerId
  if (!cid) {
    try {
      const res = await ensureClassGroup(c.id)
      cid = res && res.containerId
      await load(true)
    } catch (e) {
      return
    }
  }
  if (!cid) {
    uni.showToast({ title: '班级群创建失败', icon: 'none' })
    return
  }
  /* 进群前把当前班切过去：群聊里右上角「详情」看到的就是这个班 */
  setCurrentClass(c.id)
  uni.navigateTo({ url: `/pages/teacher/container/chat?id=${cid}` })
}

function openStudents(c) {
  setCurrentClass(c.id)
  uni.navigateTo({ url: `/pages/teacher/student/index?classId=${c.id}` })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/teacher/class/create' })
}

/* ---------- 班级信息 ---------- */

/* 点 ⋯ 直接进「班级信息」页，不再弹 actionSheet。
   弹窗只能塞下"改名/删除"两个动作，改年级、改开学日期、看邀请码都放不进去；
   而且改名用系统弹窗的输入框，没法提示"这个名字还是名单导入的校验依据"。
   独立页面能把这些一次说清楚，删除也放在页面底部，不用和改名挤在一个弹层里 */
function openInfo(c) {
  /* 必须带 classId：从二班的 ⋯ 进去时，全局 currentClassId 可能还是一班 */
  uni.navigateTo({ url: `/pages/teacher/class/setting?classId=${c.id}` })
}

</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 24rpx 24rpx 60rpx;
  box-sizing: border-box;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6rpx 8rpx 20rpx;
}

.head-count {
  font-size: 25rpx;
  color: #8A8178;
}

.head-add {
  font-size: 26rpx;
  color: #C5342B;
  padding: 6rpx 4rpx;
}

/* ---------- 分区标题 ---------- */
/* 只写「班级群」「班级」两个词，不加「共 N 个」这类重复信息 ——
   人数和状态每一行自己就带着 */
.section {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 10rpx 8rpx 14rpx;
}

.section-title {
  font-size: 24rpx;
  color: #8A8178;
  letter-spacing: 1rpx;
}

/* 「N 个未创建」是提醒，不是说明，所以给主色而不是灰 */
.section-note {
  font-size: 21rpx;
  color: #C5342B;
}

/* ---------- 卡片 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
  /* hover-class 换背景时，不加 overflow:hidden 圆角会闪成直角 */
  overflow: hidden;
}

/* 群卡：里面是一行行紧凑的群，上下留白比班级卡小 */
.card-groups {
  padding: 6rpx 24rpx;
  margin-bottom: 30rpx;
}

/* 班级卡：班级头 + 一行联系人 */
.card-class {
  padding: 26rpx 24rpx 12rpx;
  margin-bottom: 20rpx;
}

/* ---------- 私信 ---------- */
/* 和群卡一样是"一行行紧凑条目"，所以上下留白也小 */
.card-dm {
  padding: 4rpx 24rpx;
  margin-bottom: 30rpx;
}

/* 没聊过的时候只留一句说明，不画图标也不放按钮 ——
   发起私信的地方在学生详情页，这里给个按钮反而会让人以为要在通讯录里先选人 */
.dm-empty {
  padding: 28rpx 8rpx;
}

.dm-empty-text {
  font-size: 23rpx;
  color: #9A9186;
  line-height: 1.6;
}

.dm-row {
  display: flex;
  align-items: center;
  padding: 20rpx 8rpx;
  border-top: 1rpx solid #F5EDE2;
}

.dm-row-first {
  border-top: none;
}

.dm-row-pressed {
  background: #FDF8F1;
}

.dm-avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
  background: #FDEDEB;
  color: #C5342B;
}

.dm-main {
  flex: 1;
  min-width: 0;
  margin-left: 18rpx;
}

.dm-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 第二行（最后一句 + 红点）和第一行拉开一点，两行贴太紧会看成一行 */
.dm-line + .dm-line {
  margin-top: 8rpx;
}

.dm-name {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  color: #2B2622;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.dm-time {
  flex-shrink: 0;
  margin-left: 12rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

/* 最后一句必须单行截断：一条 500 字的消息不能把列表撑成一屏 */
.dm-last {
  flex: 1;
  min-width: 0;
  font-size: 23rpx;
  color: #9A9186;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.dm-badge {
  flex-shrink: 0;
  margin-left: 12rpx;
  min-width: 34rpx;
  height: 34rpx;
  padding: 0 9rpx;
  border-radius: 999rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

/* ---------- 群的一行 ---------- */
/* 高度 96rpx：比班级卡里的入口（112rpx）矮一档，
   因为这里只需要「点进去聊天」，不需要承载两行信息 */
.grow {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  padding: 0 8rpx;
  border-top: 1rpx solid #F5EDE2;
}

/* 第一行上面不要那条线：它贴着卡片顶边，是一条多余的横杠 */
.grow-first {
  border-top: none;
}

.grow-pressed {
  background: #FDF8F1;
}

.grow-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: 16rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 600;
  background: #FDEDEB;
  color: #C5342B;
}

.grow-name {
  flex: 1;
  min-width: 0;
  margin-left: 16rpx;
  font-size: 27rpx;
  color: #2B2622;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.grow-count {
  flex-shrink: 0;
  margin-left: 12rpx;
  font-size: 22rpx;
  color: #9A9186;
}

/* 未创建：这一行需要被点一下，所以用主色而不是灰 */
.grow-count.todo {
  color: #C5342B;
}

/* ---------- 班级卡头部 ---------- */
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 0 6rpx 18rpx;
}

.card-title-wrap {
  display: flex;
  flex-direction: column;
}

.card-title {
  font-size: 31rpx;
  font-weight: 600;
  color: #2B2622;
}

.card-sub {
  margin-top: 8rpx;
  font-size: 21rpx;
  color: #9A9186;
}

.card-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.card-count {
  font-size: 22rpx;
  color: #B4A99B;
  padding-top: 6rpx;
}

/* ⋯ 是这一页唯一的管理入口，点击区域要给够。
   只按字号大小给 padding 的话，实际可点范围只有 20rpx 左右，
   手指按不准 —— 这里撑到 56rpx 见方，视觉上仍是一个小点 */
.card-more {
  margin-left: 12rpx;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  line-height: 1;
  color: #B4A99B;
  /* ⋯ 这个字符本身在字框里偏下，微调一下才和右边的人数对齐 */
  padding-bottom: 10rpx;
  box-sizing: border-box;
}

.card-more-pressed {
  background: #F3EBE0;
  color: #6B6157;
}

/* ---------- 班级卡里的入口 ---------- */
.entry {
  display: flex;
  align-items: center;
  padding: 20rpx 8rpx;
  border-top: 1rpx solid #F5EDE2;
}

.entry-pressed {
  background: #FDF8F1;
}

.entry-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 600;
}

.entry-icon.people {
  background: #EAF1F8;
  color: #3F6A8C;
}

.entry-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-left: 18rpx;
  min-width: 0;
}

.entry-title {
  font-size: 28rpx;
  color: #2B2622;
}

.entry-meta {
  margin-top: 6rpx;
  font-size: 21rpx;
  color: #9A9186;
}

.chev {
  font-size: 30rpx;
  color: #C6BAA9;
  padding-left: 10rpx;
}

/* ---------- 骨架屏 ---------- */
.sk-label {
  height: 24rpx;
  width: 100rpx;
  border-radius: 8rpx;
  background: #EFE4D6;
  margin: 14rpx 8rpx 16rpx;
}

.sk-line {
  height: 26rpx;
  border-radius: 8rpx;
  background: #F3EBE0;
  margin: 8rpx 6rpx 22rpx;
}

.sk-row {
  height: 76rpx;
  border-radius: 16rpx;
  background: #FAF5EE;
  margin-bottom: 14rpx;
}

/* ---------- 空状态 ---------- */
.empty {
  padding: 120rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-title {
  font-size: 30rpx;
  color: #2B2622;
  font-weight: 500;
}

.empty-tip {
  margin-top: 14rpx;
  font-size: 23rpx;
  color: #9A9186;
  text-align: center;
  line-height: 1.6;
}

.empty-btn {
  margin-top: 34rpx;
  padding: 0 60rpx;
  height: 82rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 27rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
