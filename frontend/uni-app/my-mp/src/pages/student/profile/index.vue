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
      <!-- 上半部分：身份卡。和老师端个人中心同一个版式 -->
      <view class="profile">
        <view class="profile-top">
          <view class="avatar">{{ initial }}</view>
          <view class="profile-main">
            <text class="profile-name">{{ p.name || '未设置姓名' }}</text>
            <text class="profile-sub">{{ p.className }} · {{ p.studentNo }}</text>
          </view>
          <view class="edit" hover-class="edit-pressed" @click="goEdit">编辑</view>
        </view>

        <view class="rows">
          <view class="row">
            <text class="row-label">班主任</text>
            <text class="row-value">{{ p.teacherName || '—' }}</text>
          </view>
          <view class="row">
            <text class="row-label">性别</text>
            <text class="row-value">{{ p.gender === 1 ? '男' : p.gender === 2 ? '女' : '—' }}</text>
          </view>
          <view class="row">
            <text class="row-label">生日</text>
            <text class="row-value">{{ p.birthday || '—' }}</text>
          </view>
        </view>
      </view>

      <!-- 联系方式：这三项学生自己能改，所以单独一张卡，
           和上面那张"只能找班主任改"的信息卡分开。
           混在一张卡里，学生会以为所有行都能点 -->
      <view class="profile">
        <view class="group-head">
          <text class="group-title">联系方式</text>
        </view>
        <view class="rows rows-flush">
          <view class="row" hover-class="row-pressed" @click="goEdit">
            <text class="row-label">手机号</text>
            <text class="row-value" :class="{ 'row-empty': !p.phone }">{{ p.phone || '未填写' }}</text>
            <text class="row-chev">›</text>
          </view>
          <view class="row" hover-class="row-pressed" @click="goEdit">
            <text class="row-label">微信号</text>
            <text class="row-value" :class="{ 'row-empty': !p.wechat }">{{ p.wechat || '未填写' }}</text>
            <text class="row-chev">›</text>
          </view>
          <view class="row" hover-class="row-pressed" @click="goEdit">
            <text class="row-label">宿舍</text>
            <text class="row-value" :class="{ 'row-empty': !p.dormitory }">{{ p.dormitory || '未填写' }}</text>
            <text class="row-chev">›</text>
          </view>
        </view>
      </view>

      <!-- 其余学籍信息：只读。写清楚"改不了"，比让学生反复点更好。
           家庭电话和培养方向不在这里 —— 前者是家长的联系方式，
           后者是教务口径，学生看自己的档案都不需要，问班主任就行 -->
      <view class="profile">
        <view class="group-head">
          <text class="group-title">学籍信息</text>
        </view>
        <view class="rows rows-flush">
          <view class="row">
            <text class="row-label">民族</text>
            <text class="row-value" :class="{ 'row-empty': !p.nation }">{{ p.nation || '未填写' }}</text>
          </view>
          <view class="row">
            <text class="row-label">政治面貌</text>
            <text class="row-value" :class="{ 'row-empty': !p.politicalStatus }">{{ p.politicalStatus || '未填写' }}</text>
          </view>
        </view>
      </view>

      <!-- 退出登录：放在整页最下面，做成一颗通栏按钮。
           原来是一行灰字，学生找不到；现在是明确的一个操作，
           但用白底红字而不是红底 —— 它不是这页的主操作，只是收尾 -->
      <view class="logout" hover-class="logout-pressed" @click="quit">
        <text class="logout-text">退出登录</text>
      </view>
    </template>

    <view class="bottom-space"></view>
    <student-tab-bar current="profile" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchMyProfile } from '@/api/student-data'
import { studentLogout } from '@/api/student-portal'
import StudentTabBar from '@/components/student-tab-bar/student-tab-bar.vue'

const p = ref({})
const loading = ref(true)
const err = ref('')

const initial = computed(() => (p.value.name || '生').slice(0, 1))

async function load() {
  err.value = ''
  try {
    p.value = (await fetchMyProfile()) || {}
  } catch (e) {
    err.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

/* onShow：从编辑页改完返回时要看到新号码 */
onShow(load)

function goEdit() {
  uni.navigateTo({ url: '/pages/student/profile/edit' })
}

function quit() {
  uni.showModal({
    title: '退出登录',
    content: '退出后要重新输学号才能进来。',
    confirmText: '退出',
    confirmColor: '#C5342B',
    success: (r) => {
      if (r.confirm) studentLogout()
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 24rpx 24rpx 20rpx;
  box-sizing: border-box;
}

/* 底栏 100rpx + 安全区 + 20rpx 余量。
   写死高度的话，刘海屏上最后一行会被底栏盖住 */
.bottom-space {
  height: calc(120rpx + env(safe-area-inset-bottom));
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

/* ---------- 卡片 ---------- */
.profile {
  background: #FFFFFF;
  border-radius: 32rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.06);
  margin-bottom: 20rpx;
  overflow: hidden;
}

.profile-top {
  display: flex;
  align-items: center;
  padding: 36rpx 28rpx;
}

.avatar {
  width: 116rpx;
  height: 116rpx;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #E8544D, #C0322F);
  color: #FFFFFF;
  font-size: 48rpx;
  font-weight: 600;
}

.profile-main {
  flex: 1;
  min-width: 0;
  margin-left: 28rpx;
  display: flex;
  flex-direction: column;
}

.profile-name {
  font-size: 38rpx;
  font-weight: 600;
  color: #2B2622;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.profile-sub {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #9A9186;
}

/* 编辑做成小药丸：点了是改资料，不是进下一页 */
.edit {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 24rpx;
}

.edit-pressed {
  background: #F9DAD6;
}

/* 分组头：把"能改"和"不能改"在视觉上先分开 */
.group-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 28rpx 28rpx 4rpx;
}

.group-title {
  font-size: 27rpx;
  font-weight: 600;
  color: #2B2622;
}

.rows {
  padding: 0 28rpx;
  border-top: 1rpx solid #F5EDE2;
}

/* 有分组头的卡，行区域不再单独画顶线 —— 头下面已经有留白 */
.rows-flush {
  border-top: none;
  padding-top: 12rpx;
}

.row {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  border-bottom: 1rpx solid #F8F2EA;
}

.row:last-child {
  border-bottom: none;
}

.row-pressed {
  background: #FDFAF6;
}

.row-label {
  width: 160rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: #9A9186;
}

.row-value {
  flex: 1;
  font-size: 27rpx;
  color: #2B2622;
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 没填：浅灰，一眼能看出是空的而不是写着"未填写"三个字 */
.row-empty {
  color: #C4B9AB;
}

.row-chev {
  flex-shrink: 0;
  margin-left: 8rpx;
  font-size: 28rpx;
  color: #CFC5B8;
  line-height: 1;
}

/* ---------- 退出登录 ---------- */
.logout {
  margin-top: 40rpx;
  height: 92rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  border: 2rpx solid #E7DBC9;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logout-pressed {
  background: #FAF5EF;
}

.logout-text {
  font-size: 28rpx;
  color: #C5342B;
}
</style>
