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
      <!-- ============ 班主任 ============
           通讯录里唯一能一对一说话的人。
           同学不列在这里：这个系统没有加好友，把花名册摊开只会让人以为
           "这些人我能点" —— 点下去什么都不会发生，比不显示更糟 -->
      <view class="section">
        <text class="section-title">班主任</text>
      </view>

      <view class="card">
        <view class="person" hover-class="person-pressed" @click="openMessage">
          <view class="avatar">{{ teacherInitial }}</view>
          <view class="person-main">
            <view class="person-line">
              <text class="person-name">{{ me.teacherName || '班主任' }}</text>
              <text class="role-tag">班主任</text>
            </view>
            <text class="person-sub">{{ me.className }}</text>
          </view>
          <view class="chat-btn">
            <text class="chat-text">私信</text>
            <text v-if="me.unread > 0" class="chat-badge">{{ me.unread > 99 ? '99+' : me.unread }}</text>
          </view>
        </view>
      </view>

      <!-- ============ 群聊 ============
           这些群就是老师端「通讯录 → 班级群」里那些群，同一份消息。
           本班的群自动在里面（花名册里有你），另外还能用邀请码加别的群 -->
      <view class="section">
        <text class="section-title">群聊</text>
      </view>

      <view class="card">
        <view
          v-for="(g, i) in groups"
          :key="g.id"
          class="person"
          :class="{ 'row-line': i > 0 }"
          hover-class="person-pressed"
          @click="openGroup(g)"
        >
          <view class="avatar avatar-group">群</view>
          <view class="person-main">
            <view class="person-line">
              <text class="person-name">{{ g.name }}</text>
              <text v-if="g.classGroup" class="role-tag">本班</text>
            </view>
            <text class="person-sub">
              {{ g.memberCount }} 人<text v-if="g.lastContent">　{{ g.lastContent }}</text>
            </text>
          </view>
          <text class="chev">›</text>
        </view>

        <!-- 没群不是错误：班主任还没建群，学生什么都不该做 -->
        <view v-if="!groups.length" class="empty">
          <text class="empty-text">班主任还没建群</text>
        </view>
      </view>

      <!-- 用邀请码加别的群：跨班旁听、高数群这种。和下面「加入其他班级」是两件事 ——
           那个换班，这个只是多一个能看的群 -->
      <view class="card card-action">
        <view class="person" hover-class="person-pressed" @click="openGroupJoin">
          <view class="avatar avatar-add">＋</view>
          <view class="person-main">
            <text class="person-name">加入群聊</text>
            <text class="person-sub">用老师给的群邀请码</text>
          </view>
          <text class="chev">›</text>
        </view>
      </view>

      <!-- 加入另一个班：学生可能同时上几门课，一门课一个班 -->
      <view class="card card-action">
        <view class="person" hover-class="person-pressed" @click="openJoin">
          <view class="avatar avatar-add">班</view>
          <view class="person-main">
            <text class="person-name">加入其他班级</text>
            <text class="person-sub">用班主任给的班级邀请码</text>
          </view>
          <text class="chev">›</text>
        </view>
      </view>
    </template>

    <view class="bottom-space"></view>
    <student-tab-bar current="contact" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchStudentMe } from '@/api/student-portal'
import { fetchMyGroups } from '@/api/student-data'
import StudentTabBar from '@/components/student-tab-bar/student-tab-bar.vue'

const me = ref({})
const groups = ref([])
const loading = ref(true)
const err = ref('')

const teacherInitial = computed(() => (me.value.teacherName || '师').slice(0, 1))

async function load() {
  err.value = ''
  try {
    /* 两个请求并行。群列表失败不连累班主任卡 ——
       私信是这一页最主要的功能，不该因为群拉不到就整页报错 */
    const [m, g] = await Promise.all([
      fetchStudentMe(),
      fetchMyGroups().catch(() => null)
    ])
    me.value = m || {}
    groups.value = g || []
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* onShow：从私信页/群聊回来时未读数和最后一条要更新 */
onShow(load)

function openMessage() {
  uni.navigateTo({ url: '/pages/student/message' })
}

function openGroup(g) {
  uni.navigateTo({ url: `/pages/student/group/index?id=${g.id}` })
}

/* 加群和加班是两条路：加群只多一个能看的群，加班换的是整个身份所在班 */
function openGroupJoin() {
  uni.navigateTo({ url: '/pages/student/group/join' })
}

function openJoin() {
  uni.navigateTo({ url: '/pages/student/join/index' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 20rpx;
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
  font-size: 27rpx;
  color: #6B6258;
  text-align: center;
  line-height: 1.6;
}

.state-btn {
  margin-top: 28rpx;
  padding: 14rpx 44rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #C5342B;
  font-size: 26rpx;
  border: 2rpx solid #F0DCD6;
}

/* 分区标题：和老师端通讯录同一种写法（左边一行小字，没有卡片底） */
.section {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 12rpx 8rpx 14rpx;
}

.section-title {
  font-size: 25rpx;
  font-weight: 600;
  color: #6B6258;
  letter-spacing: 1rpx;
}

/* 同一个卡片里的多行之间加一条细线，不然两行头像会粘成一块 */
.row-line {
  border-top: 1rpx solid #F3EADF;
}

.empty {
  padding: 40rpx 28rpx;
  display: flex;
  justify-content: center;
}

.empty-text {
  font-size: 24rpx;
  color: #B4A99B;
}

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
  overflow: hidden;
  margin-bottom: 12rpx;
}

/* 动作卡和内容卡之间多留一点：12rpx 会让它看起来像班级群那张卡的第二行 */
.card-action {
  margin-top: 20rpx;
}

.person {
  display: flex;
  align-items: center;
  padding: 26rpx 28rpx;
}

.person-pressed {
  background: #FBF6EE;
}

.avatar {
  width: 84rpx;
  height: 84rpx;
  border-radius: 50%;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 32rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 群头像用深一点的红：一眼能和"人"区分开，不用看文字 */
.avatar-group {
  background: #C5342B;
  color: #FFFFFF;
  font-size: 30rpx;
}

/* 「加入其他班级」的 ＋：虚线边框的空心圆，
   和上面两个实心头像拉开层级 —— 它是个动作，不是一个人/一个群 */
.avatar-add {
  background: #FFFFFF;
  border: 2rpx dashed #D8C8B4;
  color: #B48A5A;
  font-size: 34rpx;
  font-weight: 400;
  box-sizing: border-box;
}

.person-main {
  flex: 1;
  min-width: 0;
  margin-left: 22rpx;
  display: flex;
  flex-direction: column;
}

.person-line {
  display: flex;
  align-items: center;
}

.person-name {
  font-size: 29rpx;
  font-weight: 600;
  color: #2B2622;
}

.role-tag {
  margin-left: 10rpx;
  padding: 2rpx 10rpx;
  border-radius: 8rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 19rpx;
  flex-shrink: 0;
}

.person-sub {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #9A9186;
  /* 人数和最后一条挤在一起时只留一行，超出用省略号 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-btn {
  position: relative;
  padding: 10rpx 26rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  flex-shrink: 0;
}

.chat-text {
  font-size: 24rpx;
  color: #C5342B;
}

.chat-badge {
  position: absolute;
  top: -10rpx;
  right: -6rpx;
  min-width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  padding: 0 6rpx;
  border-radius: 999rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 19rpx;
  text-align: center;
}

.chev {
  margin-left: 12rpx;
  font-size: 34rpx;
  color: #C9BFB2;
  flex-shrink: 0;
}

/* 底栏 100rpx + 安全区 + 20rpx 余量 */
.bottom-space {
  height: calc(120rpx + env(safe-area-inset-bottom));
}
</style>
