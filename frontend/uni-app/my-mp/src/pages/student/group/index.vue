<template>
  <view class="page">
    <!-- 自定义导航：返回 / 群名 + 人数 / 详情。
         和老师端群聊同一个结构 —— 同一个群，两端看到的是同一张脸。
         原生导航栏放不下右上角的「详情」入口，微信自己也是这么做的（「···」）。 -->
    <view class="nav" :style="{ paddingTop: statusBar + 'px' }">
      <view class="nav-bar">
        <view class="nav-back" hover-class="nav-press" @click="goBack">
          <view class="chevron"></view>
        </view>
        <view class="nav-title">
          <text class="nav-name">{{ info.name || '群聊' }}</text>
          <text class="nav-sub">{{ info.memberCount || 0 }} 人</text>
        </view>
        <view class="nav-detail" hover-class="nav-press" @click="goDetail">详情</view>
      </view>
    </view>

    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="init">重试</view>
      <view class="state-btn ghost" @click="goBack">返回</view>
    </view>

    <template v-else>
      <scroll-view
        class="msgs"
        scroll-y
        :scroll-into-view="anchor"
        :scroll-with-animation="anim"
        @click="toolsOpen = false"
      >
        <!-- 空状态整块可点，点了聚焦输入框 ——
             否则这一大片空白点什么都没反应，看起来像页面死了 -->
        <view v-if="!list.length" class="empty" hover-class="empty-press" @click="focusInput">
          <text class="empty-title">群里还没有消息</text>
          <text class="empty-text">打个招呼吧</text>
          <view class="empty-tap">点这里开始输入</view>
        </view>

        <view v-for="(m, i) in list" :key="m.id">
          <view v-if="showTime(i)" class="time-sep">
            <text class="time-text">{{ fmtTime(m.createdAt) }}</text>
          </view>

          <view class="msg" :class="{ self: m.mine, 'card-msg': isCard(m) }">
            <view class="avatar">
              <text class="avatar-txt" :class="{ 'avatar-self': m.mine }">{{ initial(m.senderName) }}</text>
            </view>

            <view class="msg-body">
              <view v-if="!m.mine" class="msg-head">
                <text class="msg-name">{{ m.senderName }}</text>
                <!-- 学生端只有一个身份标签。群主/班委是老师端的管理概念，
                     学生不需要知道"谁是班委" -->
                <text v-if="m.senderType === 1" class="tag teacher">老师</text>
              </view>

              <!-- 图片：点开看大图，能左右滑同一批图片 -->
              <image
                v-if="m.type === 2"
                class="bubble-img"
                :src="fileUrl(m.mediaUrl)"
                mode="widthFix"
                @click="previewImage(m)"
              />

              <!-- 视频：不嵌 <video>（原生组件会盖住工具面板和输入栏），点开全屏播 -->
              <view v-else-if="m.type === 3" class="bubble-video" @click="openVideo(m)">
                <view class="play-btn"><view class="play-tri"></view></view>
                <text class="video-meta">{{ fmtDuration(m.duration) || '视频' }}</text>
              </view>

              <!-- 文件：点开先下载再交给系统预览 -->
              <view v-else-if="m.type === 4" class="bubble-file" @click="openFile(m)">
                <view class="file-badge">{{ extOf(m.fileName) }}</view>
                <view class="file-info">
                  <text class="file-name">{{ m.fileName }}</text>
                  <text class="file-size">{{ fmtSize(m.fileSize) }}</text>
                </view>
              </view>

              <!-- 接龙 / 抽签卡片：只读。
                   学生端没有"参与接龙"的权限（报名要填内容、要能改），
                   给一张只有标题的卡，比给一个点了报错的按钮好 -->
              <view v-else-if="m.type === 5 || m.type === 6" class="rcard" :class="m.mine ? 'rcard-self' : ''">
                <view class="rcard-ink"></view>
                <view class="rcard-in">
                  <text class="rcard-kind">{{ m.type === 5 ? '接龙' : '抽签' }}</text>
                  <text class="rcard-title">{{ cardTitle(m) }}</text>
                </view>
              </view>

              <view v-else class="bubble" :class="{ 'bubble-self': m.mine }">
                <text class="bubble-text">{{ m.content }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 两个锚点交替用：scroll-into-view 赋同一个值不会触发第二次滚动 -->
        <view id="anchor-a" class="anchor"></view>
        <view id="anchor-b" class="anchor"></view>
      </scroll-view>

      <!-- 工具面板：夹在消息区和输入栏之间，展开时把消息区顶上去（和微信一样） -->
      <view v-if="toolsOpen" class="tools">
        <view
          v-for="t in TOOLS"
          :key="t.key"
          class="tool"
          hover-class="tool-press"
          @click="useTool(t)"
        >
          <view class="tool-ic">
            <template v-if="t.key === 'media'">
              <view class="ic-sun"></view>
              <view class="ic-hill"></view>
            </template>
            <template v-else>
              <view class="ic-doc-line"></view>
              <view class="ic-doc-line short"></view>
            </template>
          </view>
          <text class="tool-label">{{ t.label }}</text>
        </view>
      </view>

      <view class="bar">
        <view class="bar-input-wrap" @click="focusInput">
          <input
            class="bar-input"
            v-model="draft"
            :focus="inputFocus"
            :maxlength="500"
            placeholder="说点什么…"
            placeholder-style="color:#B4A99B;font-size:26rpx"
            confirm-type="send"
            confirm-hold
            @confirm="onSend"
            @focus="toolsOpen = false"
            @blur="inputFocus = false"
          />
        </view>

        <!-- 有字才是「发送」，没字是「＋」。
             以前空着也显示发送，点了没反应，看着像坏了 -->
        <view
          v-if="hasText"
          class="bar-send"
          :class="{ off: !canSend }"
          hover-class="bar-send-press"
          @click="onSend"
        >
          {{ sending ? '…' : '发送' }}
        </view>
        <view v-else class="bar-plus" hover-class="bar-send-press" @click="toggleTools">
          <view class="plus-ic" :class="{ open: toolsOpen }">
            <view class="plus-h"></view>
            <view class="plus-v"></view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import {
  fetchGroupInfo,
  fetchGroupMessages,
  sendGroupMessage,
  uploadToGroup
} from '@/api/student-data'
import { fileUrl } from '@/api/container'

/* 3 秒拉一次增量，和老师端群聊同一个节奏。
   增量只有几百字节，比"下拉刷新"可靠 —— 有人说话，学生不该自己去戳一下才知道 */
const POLL_MS = 3000

/* 学生能用的只有两种：照片/视频、文件。
   接龙和抽签是老师发起的功能，学生端不出现入口 —— 放一个点了没反应的图标最糟 */
const TOOLS = [
  { key: 'media', label: '照片 / 视频' },
  { key: 'file', label: '文件' }
]

const gid = ref(null)
const info = ref({})
const list = ref([])
const loading = ref(true)
const loadErr = ref('')
const sending = ref(false)
const uploading = ref(false)
const draft = ref('')
const toolsOpen = ref(false)
const inputFocus = ref(false)
const anchor = ref('')
const anim = ref(false)
const statusBar = ref(20)

let anchorFlip = false
let timer = null

const canSend = computed(() => !sending.value && !!draft.value.trim())
const hasText = computed(() => !!draft.value.trim())

try {
  statusBar.value = uni.getSystemInfoSync().statusBarHeight || 20
} catch (e) {
  // 取不到就用默认值，不影响使用
}

onLoad((q) => {
  gid.value = q && q.id ? Number(q.id) : null
  /* 没有 id 必须当场结束 loading：onShow 里的 init 依赖 id，不会执行，
     页面就会永远停在"正在加载…"，一个能点的东西都没有 */
  if (!gid.value) {
    loadErr.value = '缺少群 id'
    loading.value = false
    return
  }
  init()
})

/* 首次已经由 onLoad 处理；这里只负责"从群信息页返回时刷新人数" */
let firstShow = true
onShow(() => {
  if (firstShow) {
    firstShow = false
    return
  }
  if (gid.value) refreshInfo()
})

onHide(stopPoll)
onUnload(stopPoll)

async function init() {
  if (!gid.value) {
    loadErr.value = '缺少群 id'
    loading.value = false
    return
  }
  loadErr.value = ''
  try {
    const g = await fetchGroupInfo(gid.value)
    info.value = g || {}
    await loadLatest()
    startPoll()
  } catch (e) {
    /* 3002 = 不在这个群 / 1004 = 已解散，文案由后端给 */
    loadErr.value = (e && e.message) || '加载失败'
    stopPoll()
  } finally {
    loading.value = false
  }
}

/** 只刷群名和人数，不动消息和滚动位置 */
async function refreshInfo() {
  try {
    info.value = (await fetchGroupInfo(gid.value)) || {}
  } catch (e) {
    // 人数刷不到不影响聊天
  }
}

async function loadLatest() {
  const rows = await fetchGroupMessages(gid.value)
  list.value = rows || []
  anim.value = false
  scrollBottom()
}

/* ---------- 轮询 ---------- */

function startPoll() {
  stopPoll()
  timer = setInterval(pull, POLL_MS)
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function lastId() {
  return list.value.length ? list.value[list.value.length - 1].id : 0
}

async function pull() {
  if (!gid.value || loadErr.value) return
  try {
    const rows = await fetchGroupMessages(gid.value, lastId())
    if (rows && rows.length) append(rows)
  } catch (e) {
    /* 轮询失败不提示：下一轮就好了。弹 toast 会变成每 3 秒弹一次 */
  }
}

/* 按 id 去重后追加。
   必须有这一步：发送成功会立刻把自己那条 push 进列表，而下一轮轮询的 afterId
   是"上一条的 id"，服务器会把刚发的那条再返回一次 —— 不去重就是两个一样的气泡 */
function append(rows) {
  const seen = new Set(list.value.map((m) => m.id))
  const add = rows.filter((m) => !seen.has(m.id))
  if (!add.length) return
  list.value = list.value.concat(add)
  scrollBottom()
}

/* ---------- 发送 ---------- */

async function onSend() {
  if (!canSend.value) return
  const text = draft.value.trim()
  if (!text) return
  sending.value = true
  try {
    const vo = await sendGroupMessage(gid.value, text)
    draft.value = ''
    append([vo])
  } catch (e) {
    // request.js 已 toast
  } finally {
    sending.value = false
  }
}

/* ---------- 工具面板 ---------- */

function toggleTools() {
  if (uploading.value) return
  // 展开面板时收起键盘，否则键盘和面板会叠在一起，屏幕只剩一条缝
  if (!toolsOpen.value) inputFocus.value = false
  toolsOpen.value = !toolsOpen.value
}

function useTool(t) {
  if (uploading.value) return
  if (t.key === 'media') {
    pickMedia()
    return
  }
  pickFile()
}

/** 选照片或视频（相册 / 拍照都行） */
function pickMedia() {
  if (uploading.value) return
  if (!uni.chooseMedia) {
    uni.showToast({ title: '当前环境不支持选图', icon: 'none' })
    return
  }
  uni.chooseMedia({
    count: 1,
    mediaType: ['image', 'video'],
    sourceType: ['album', 'camera'],
    sizeType: ['compressed'],
    maxDuration: 60,
    success: (res) => {
      const f = (res.tempFiles || [])[0]
      if (!f) return
      const isVideo = f.fileType === 'video'
      uploadAndSend(f.tempFilePath, isVideo ? 'video' : 'image', {
        duration: isVideo ? Math.round(f.duration || 0) : null
      })
    },
    fail: () => {
      // 用户点了取消也会走 fail，这里静默
    }
  })
}

/** 从微信聊天记录里选一个文件 */
function pickFile() {
  if (uploading.value) return
  if (typeof wx === 'undefined' || !wx.chooseMessageFile) {
    uni.showToast({ title: '当前环境不支持选文件', icon: 'none' })
    return
  }
  wx.chooseMessageFile({
    count: 1,
    type: 'file',
    success: (res) => {
      const f = (res.tempFiles || [])[0]
      if (!f) return
      uploadAndSend(f.path, 'file', { fileName: f.name, fileSize: f.size })
    },
    fail: () => {}
  })
}

/**
 * 先上传，再发一条引用它的消息。
 *
 * <p>和老师端同一条链路：用户选了图又反悔，文件已经传上来了但没发消息，
 * 这种情况下"没发出去"就等于没发；合成一步反而要做"取消时删文件"。
 */
async function uploadAndSend(filePath, kind, extra = {}) {
  if (uploading.value) return
  uploading.value = true
  uni.showLoading({ title: '发送中', mask: true })
  try {
    const up = await uploadToGroup(gid.value, filePath, kind)

    const payload = { type: kind === 'image' ? 2 : kind === 'video' ? 3 : 4, mediaUrl: up.url }
    if (kind === 'video') {
      payload.fileName = up.name
      payload.fileSize = up.size
      payload.duration = extra.duration || null
    } else if (kind === 'file') {
      payload.fileName = extra.fileName || up.name
      payload.fileSize = extra.fileSize || up.size
    }

    const vo = await sendGroupMessage(gid.value, payload)
    append([vo])
    toolsOpen.value = false
    uni.hideLoading()
  } catch (e) {
    // 先关 loading 再提示：两者共用一个浮层，顺序反了提示会被一起关掉
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '发送失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

/* ---------- 展示 ---------- */

function isCard(m) {
  return m.type === 5 || m.type === 6
}

/* 卡片消息的 content 是「[接龙] 明天春游报名」这种占位，标题在方括号后面 */
function cardTitle(m) {
  const s = String(m.content || '')
  const i = s.indexOf(']')
  return i > -1 ? s.slice(i + 1).trim() : s
}

function scrollBottom() {
  nextTick(() => {
    anchorFlip = !anchorFlip
    anchor.value = anchorFlip ? 'anchor-a' : 'anchor-b'
    setTimeout(() => {
      anim.value = true
    }, 50)
  })
}

function pad(n) {
  return String(n).padStart(2, '0')
}

/* 后端给的是 "2026-10-29 16:20:00" */
function fmtTime(s) {
  if (!s) return ''
  const d = s.slice(0, 10)
  const hm = s.slice(11, 16)
  const today = new Date()
  const t = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`
  return d === t ? hm : `${s.slice(5, 10)} ${hm}`
}

/* 相邻两条间隔超过 5 分钟才插一条时间，否则满屏都是时间 */
function showTime(i) {
  if (i === 0) return true
  const prev = list.value[i - 1]
  const cur = list.value[i]
  if (!prev || !cur || !prev.createdAt || !cur.createdAt) return false
  const a = new Date(prev.createdAt.replace(/-/g, '/')).getTime()
  const b = new Date(cur.createdAt.replace(/-/g, '/')).getTime()
  return b - a > 5 * 60 * 1000
}

function initial(name) {
  const s = (name || '').trim()
  return s ? s.slice(0, 1) : '同'
}

/* ---------- 媒体 ---------- */

function previewImage(m) {
  const urls = list.value.filter((x) => x.type === 2).map((x) => fileUrl(x.mediaUrl))
  uni.previewImage({ current: fileUrl(m.mediaUrl), urls })
}

/* 视频走系统播放器：<video> 是原生组件，会盖住工具面板和输入栏 */
function openVideo(m) {
  if (!uni.previewMedia) {
    uni.showToast({ title: '当前环境不支持播放', icon: 'none' })
    return
  }
  uni.previewMedia({ sources: [{ url: fileUrl(m.mediaUrl), type: 'video' }] })
}

function openFile(m) {
  uni.showLoading({ title: '打开中', mask: true })
  uni.downloadFile({
    url: fileUrl(m.mediaUrl),
    success: (res) => {
      uni.hideLoading()
      if (res.statusCode !== 200) {
        uni.showToast({ title: '下载失败', icon: 'none' })
        return
      }
      uni.openDocument({
        filePath: res.tempFilePath,
        showMenu: true,
        fail: () => uni.showToast({ title: '这个格式手机打不开', icon: 'none' })
      })
    },
    fail: () => {
      uni.hideLoading()
      uni.showToast({ title: '下载失败', icon: 'none' })
    }
  })
}

function fmtSize(n) {
  if (!n || n <= 0) return ''
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return Math.round(n / 1024) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

function fmtDuration(sec) {
  if (!sec || sec <= 0) return ''
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return m + ':' + String(s).padStart(2, '0')
}

function extOf(name) {
  const parts = String(name || '').split('.')
  if (parts.length < 2) return '文件'
  return parts.pop().slice(0, 4).toUpperCase()
}

/* ---------- 导航 ---------- */

function goBack() {
  const pages = getCurrentPages()
  if (pages && pages.length > 1) {
    uni.navigateBack()
  } else {
    /* 从通知/分享直接进来时栈里只有自己，退回通讯录而不是卡死 */
    uni.reLaunch({ url: '/pages/student/contact/index' })
  }
}

function goDetail() {
  uni.navigateTo({ url: `/pages/student/group/detail?id=${gid.value}` })
}

/* focus 只在值变化时生效，所以先置 false 再置 true —— 否则连续点两次第二次不响应 */
function focusInput() {
  inputFocus.value = false
  nextTick(() => {
    inputFocus.value = true
  })
}
</script>

<style lang="scss" scoped>
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F7EFE4;
  box-sizing: border-box;
  /* 整页自己管滚动，页面级不要再滚一层 —— 否则输入栏会被顶出可视区 */
  overflow: hidden;
}

/* ---------- 自定义导航 ---------- */
.nav {
  flex-shrink: 0;
  background: #F7EFE4;
  position: relative;
  z-index: 2;
}

.nav-bar {
  height: 44px;
  display: flex;
  align-items: center;
  padding: 0 8rpx;
}

.nav-back {
  width: 72rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 用两条边框拼一个 ‹ ，比文字箭头锐利，也不依赖字体 */
.chevron {
  width: 18rpx;
  height: 18rpx;
  border-left: 4rpx solid #2B2622;
  border-bottom: 4rpx solid #2B2622;
  transform: rotate(45deg);
  margin-left: 6rpx;
}

.nav-press {
  opacity: 0.5;
}

.nav-title {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.nav-name {
  max-width: 380rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-sub {
  font-size: 20rpx;
  color: #9A9186;
  margin-top: 2rpx;
}

.nav-detail {
  min-width: 72rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: #C5342B;
  flex-shrink: 0;
}

/* ---------- 状态 ---------- */
.state {
  flex: 1;
  padding: 140rpx 40rpx;
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
  margin-top: 32rpx;
  padding: 14rpx 48rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  font-size: 24rpx;
  color: #C5342B;
}

.state-btn.ghost {
  margin-top: 16rpx;
  background: transparent;
  color: #9A9186;
}

/* ---------- 消息区 ---------- */
.msgs {
  flex: 1;
  /* scroll-view 在 flex 列里必须给确定高度，只写 min-height 在部分基础库会按内容撑开 */
  height: 0;
  min-height: 0;
  padding: 0 24rpx;
  box-sizing: border-box;
}

.empty {
  min-height: 100%;
  box-sizing: border-box;
  padding: 120rpx 40rpx 40rpx;
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
  margin-top: 12rpx;
  font-size: 23rpx;
  color: #9A9186;
}

.empty-press {
  opacity: 0.6;
}

.empty-tap {
  margin-top: 36rpx;
  padding: 16rpx 44rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  border: 2rpx solid #EADFD0;
  font-size: 25rpx;
  color: #C5342B;
}

.time-sep {
  display: flex;
  justify-content: center;
  padding: 20rpx 0 10rpx;
}

.time-text {
  font-size: 20rpx;
  color: #B4A99B;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.04);
}

.msg {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.msg.self {
  flex-direction: row-reverse;
}

.avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 14rpx;
  overflow: hidden;
  flex-shrink: 0;
  background: #EADFD0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-txt {
  font-size: 30rpx;
  color: #8A7A66;
  font-weight: 600;
}

.avatar-self {
  background: #F2D6D2;
  color: #C5342B;
}

.msg-body {
  max-width: 74%;
  margin: 0 18rpx;
  display: flex;
  flex-direction: column;
}

.msg.self .msg-body {
  align-items: flex-end;
}

.msg-head {
  display: flex;
  align-items: center;
  margin-bottom: 8rpx;
}

.msg-name {
  font-size: 21rpx;
  color: #9A9186;
}

.tag {
  margin-left: 10rpx;
  padding: 0 10rpx;
  border-radius: 6rpx;
  font-size: 18rpx;
  line-height: 28rpx;
}

.tag.teacher {
  background: #FDEDEB;
  color: #C5342B;
}

.bubble {
  padding: 18rpx 22rpx;
  border-radius: 6rpx 20rpx 20rpx 20rpx;
  background: #FFFFFF;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.05);
}

.bubble-self {
  border-radius: 20rpx 6rpx 20rpx 20rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
}

.bubble-text {
  font-size: 28rpx;
  line-height: 1.55;
  color: #2B2622;
  word-break: break-all;
}

.bubble-self .bubble-text {
  color: #FFFFFF;
}

.anchor {
  height: 1rpx;
}

/* ---------- 输入栏 ---------- */
.bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  box-shadow: 0 -4rpx 18rpx rgba(160, 120, 80, 0.06);
  position: relative;
  z-index: 2;
}

.bar-input-wrap {
  flex: 1;
  height: 76rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #F7F1E8;
  display: flex;
  align-items: center;
}

.bar-input {
  flex: 1;
  font-size: 28rpx;
  color: #2B2622;
  height: 76rpx;
}

.bar-send {
  margin-left: 16rpx;
  padding: 0 34rpx;
  height: 76rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bar-send.off {
  opacity: 0.4;
}

.bar-send-press {
  opacity: 0.75;
}

/* ---------- ＋ 号（输入框为空时） ---------- */
.bar-plus {
  margin-left: 16rpx;
  width: 76rpx;
  height: 76rpx;
  border-radius: 999rpx;
  background: #F7F1E8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.plus-ic {
  width: 32rpx;
  height: 32rpx;
  position: relative;
  transition: transform 0.18s ease;
}

/* 展开时转 45°，加号变叉号，提示"再点一下收起" */
.plus-ic.open {
  transform: rotate(45deg);
}

.plus-h,
.plus-v {
  position: absolute;
  background: #C5342B;
  border-radius: 4rpx;
}

.plus-h {
  left: 0;
  top: 14rpx;
  width: 32rpx;
  height: 4rpx;
}

.plus-v {
  left: 14rpx;
  top: 0;
  width: 4rpx;
  height: 32rpx;
}

/* ---------- 工具面板 ---------- */
/* 4 列宫格，和老师端一致。学生只有两项，左边两个格子，右边留白 ——
   改成两列大按钮反而会和老师端长得不一样 */
.tools {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  padding: 32rpx 24rpx 40rpx;
  background: #FBF6EE;
  border-top: 2rpx solid #F0E6D8;
}

.tool {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 8rpx;
}

.tool-press {
  opacity: 0.6;
}

.tool-ic {
  width: 96rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: #F3E9DC;
  position: relative;
  overflow: hidden;
}

/* 用纯 CSS 画图标，省掉图片资源 */
.ic-sun {
  position: absolute;
  left: 20rpx;
  top: 20rpx;
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  background: #E8594F;
}

.ic-hill {
  position: absolute;
  left: 16rpx;
  bottom: 16rpx;
  width: 0;
  height: 0;
  border-left: 32rpx solid transparent;
  border-right: 32rpx solid transparent;
  border-bottom: 28rpx solid #C5342B;
}

.ic-doc-line {
  position: absolute;
  left: 24rpx;
  top: 30rpx;
  width: 48rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: #C5342B;
}

.ic-doc-line.short {
  top: 52rpx;
  width: 32rpx;
  background: #E8A79F;
}

.tool-label {
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #6B6259;
}

/* ---------- 图片 / 视频 / 文件气泡 ---------- */
.bubble-img {
  width: 320rpx;
  border-radius: 12rpx;
  background: #EFE6D8;
  display: block;
}

.bubble-video {
  width: 320rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background: #3A3330;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.play-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
}

.play-tri {
  width: 0;
  height: 0;
  margin-left: 6rpx;
  border-top: 16rpx solid transparent;
  border-bottom: 16rpx solid transparent;
  border-left: 26rpx solid #3A3330;
}

.video-meta {
  margin-top: 12rpx;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.75);
}

.bubble-file {
  min-width: 380rpx;
  max-width: 460rpx;
  padding: 22rpx;
  border-radius: 12rpx;
  background: #FFFFFF;
  border: 2rpx solid #EFE6D8;
  display: flex;
  align-items: center;
}

.file-badge {
  width: 68rpx;
  height: 68rpx;
  border-radius: 12rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 20rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.file-info {
  margin-left: 18rpx;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.file-name {
  font-size: 26rpx;
  color: #2B2622;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #9A9186;
}

/* ---------- 接龙 / 抽签卡片（只读） ---------- */
.msg.card-msg {
  flex-direction: row;
}

.msg.card-msg .msg-body {
  max-width: none;
  flex: 1;
  align-items: stretch;
}

.rcard {
  display: flex;
  background: #FFFFFF;
  border-radius: 22rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.07);
  overflow: hidden;
}

/* 左侧 6rpx 色条：一屏几张卡时，颜色比文字更快分辨 */
.rcard-ink {
  width: 6rpx;
  flex-shrink: 0;
  background: #C5342B;
}

.rcard-in {
  flex: 1;
  min-width: 0;
  padding: 22rpx 24rpx;
  display: flex;
  flex-direction: column;
}

.rcard-kind {
  font-size: 22rpx;
  color: #C5342B;
}

.rcard-title {
  margin-top: 8rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1.35;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
