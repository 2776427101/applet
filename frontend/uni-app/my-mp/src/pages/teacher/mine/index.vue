<template>
  <view class="page">
    <!-- ============ 上半部分：个人信息 ============ -->
    <view class="profile">
      <view class="profile-top">
        <!-- 头像：没有真实头像时用姓名首字兜底。
             比放一张灰色的默认头像图更有人味，也不用多一个资源文件 -->
        <image v-if="user.avatar" class="avatar-img" :src="user.avatar" mode="aspectFill" />
        <view v-else class="avatar">{{ avatarText }}</view>

        <view class="profile-main">
          <text class="profile-name">{{ user.name || '未设置姓名' }}</text>
          <text class="profile-sub">{{ roleLabel }}</text>
        </view>

        <view class="edit" hover-class="edit-pressed" @click="goEdit">编辑</view>
      </view>

      <!-- 两行只读信息。这里放「单位」不放「班级」：
           教师端他挂的是学校，班级有自己该去的地方（通讯录 → ⋯ → 班级信息） -->
      <view class="rows">
        <view class="row">
          <text class="row-label">单位</text>
          <text class="row-value">{{ user.unit || '未填写' }}</text>
        </view>

        <view class="row">
          <text class="row-label">手机号</text>
          <text class="row-value">{{ user.phone || '未填写' }}</text>
        </view>
      </view>
    </view>

    <!-- ============ 下半部分：成绩 / 座位 ============ -->
    <!-- 版式和首页「今日请假 / 今日生日」保持一致：
         白卡、圆角、居中、两列并排，点整张卡都能进 -->
    <view class="grid">
      <view class="cell" hover-class="cell-pressed" @click="go('/pages/teacher/score/index')">
        <view class="cell-icon">
          <image class="cell-img" src="/static/icons/tab-score.png" mode="aspectFit" />
        </view>
        <text class="cell-label">成绩</text>
      </view>

      <view class="cell" hover-class="cell-pressed" @click="go('/pages/teacher/seat/index')">
        <view class="cell-icon">
          <image class="cell-img" src="/static/icons/tab-seat.png" mode="aspectFit" />
        </view>
        <text class="cell-label">座位</text>
      </view>
    </view>

    <!-- 退出登录：和「我的」页同一个位置、同一个样式（白底红字通栏按钮）。
         不做成红底：它不是这页的主操作，只是收尾 -->
    <view class="logout" hover-class="logout-pressed" @click="quit">
      <text class="logout-text">退出登录</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchMe, logout } from '@/api/auth'

/* 先用本地缓存的 userInfo 渲染，页面一打开就有名字，不用等接口回来。
   接口回来后覆盖一次 —— 改过姓名、手机号之后缓存是旧的 */
const user = ref(uni.getStorageSync('userInfo') || {})

onShow(() => {
  /* 每次进来都拉一次：从编辑页改完返回时要看到新名字。
     拿不到就继续显示缓存，不打扰用户 —— 个人中心不显示数据不算故障 */
  fetchMe()
    .then((me) => {
      if (!me) return
      user.value = me
      uni.setStorageSync('userInfo', me)
    })
    .catch(() => {})
})

/* 没有头像时的兜底字：姓名第一个字。姓名可能为空，那就显示「师」 */
const avatarText = computed(() => {
  const n = (user.value.name || '').trim()
  return n ? n.slice(0, 1) : '师'
})

const roleLabel = computed(() => (user.value.role === 1 ? '班主任' : '学生'))

/* 编辑资料页。改完返回时 onShow 会重新拉一次，页面上的名字跟着变 */
function goEdit() {
  uni.navigateTo({ url: '/pages/teacher/mine/edit' })
}

/* 成绩、座位都已经不在 tabBar 里了，只能用 navigateTo。
   用 switchTab 会直接报 "can not switch to no-tabBar page" */
function go(url) {
  uni.navigateTo({ url })
}

/* 退出登录。先问一句 —— 老师可能只是想去看看学生端，
   手一滑点了退出，再进来要重新走一次微信登录 */
function quit() {
  uni.showModal({
    title: '退出登录',
    content: '退出后要重新微信授权才能进来。',
    confirmText: '退出',
    confirmColor: '#C5342B',
    success: (r) => {
      if (r.confirm) logout()
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 24rpx 24rpx 60rpx;
  box-sizing: border-box;
}

/* ---------- 个人信息 ---------- */
.profile {
  background: #FFFFFF;
  border-radius: 32rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.06);
}

.profile-top {
  display: flex;
  align-items: center;
  padding: 36rpx 28rpx;
}

.avatar,
.avatar-img {
  width: 116rpx;
  height: 116rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(140deg, #E8544D, #C0322F);
  color: #FFFFFF;
  font-size: 48rpx;
  font-weight: 600;
}

.avatar-img {
  background: #F6EFE7;
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
  /* 姓名可能很长，别把卡片撑破 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.profile-sub {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #9A9186;
}

/* 编辑按钮做成一颗小药丸，比「›」明确：这里点了是改资料，不是进下一页 */
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

/* ---------- 三行信息 ---------- */
.rows {
  padding: 0 28rpx;
  border-top: 1rpx solid #F5EDE2;
}

.row {
  display: flex;
  align-items: center;
  min-height: 96rpx;
  border-bottom: 1rpx solid #F8F2EA;
}

/* 最后一行不画线：卡片底部有圆角，贴边的线会显得很脏 */
.row:last-child {
  border-bottom: none;
}

.row-label {
  width: 150rpx;
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

/* ---------- 成绩 / 座位 ---------- */
/* 和首页四宫格同一套写法：纯百分比 + space-between。
   不用 gap + calc(rpx) —— 那个组合真机上会把 width 整条丢掉，卡片会变成一行一张 */
.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-top: 24rpx;
}

.cell {
  flex: 0 0 48.6%;
  width: 48.6%;
  box-sizing: border-box;
  padding: 44rpx 0;
  background: #FFFFFF;
  border-radius: 28rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.cell-pressed {
  background: #FDFAF6;
  transform: scale(0.97);
}

/* 图标外面套一层浅色圆底，和首页快捷操作的手感一致；
   也顺便让 81×81 的图标在这个尺寸下不显得单薄 */
.cell-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: #FBF3EC;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cell-img {
  width: 52rpx;
  height: 52rpx;
}

.cell-label {
  margin-top: 18rpx;
  font-size: 27rpx;
  color: #2B2622;
}

/* ---------- 退出登录 ----------
   和学生端「我的」页同一套尺寸，两端看起来是同一个东西 */
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
