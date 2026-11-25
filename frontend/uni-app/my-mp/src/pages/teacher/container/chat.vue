<template>
  <view class="page">
    <!-- 自定义导航：左上返回 / 中间群名 / 右上详情。
         用 custom 而不是原生导航，是因为原生导航栏放不下"详情"这个入口 ——
         微信自己也是这么做的（右上角是「···」）。 -->
    <view class="nav" :style="{ paddingTop: statusBar + 'px' }">
      <view class="nav-bar">
        <view class="nav-back" hover-class="nav-press" @click="goBack">
          <view class="chevron"></view>
        </view>
        <view class="nav-title">
          <text class="nav-name">{{ info.name || '群聊' }}</text>
          <text class="nav-sub">{{ totalCount }} 人</text>
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
        <!-- 空状态。整块可点，点了直接聚焦输入框 ——
             否则这一大片空白点什么都没反应，看起来像页面死了 -->
        <view v-if="!list.length" class="empty" hover-class="empty-press" @click="focusInput">
          <text class="empty-title">群里还没有消息</text>
          <text class="empty-text">发第一条消息，学生进群就能看到</text>
          <view class="empty-tap">点这里开始输入</view>
        </view>

        <view v-for="(m, i) in list" :key="m.id">
          <view v-if="showTime(i)" class="time-sep">
            <text class="time-text">{{ fmtTime(m.createdAt) }}</text>
          </view>

          <view class="msg" :class="{ self: m.mine, 'card-msg': isCard(m) }">
            <view class="avatar">
              <image v-if="m.senderAvatar" class="avatar-img" :src="m.senderAvatar" mode="aspectFill" />
              <text v-else class="avatar-txt" :class="{ 'avatar-self': m.mine }">{{ initial(m.senderName) }}</text>
            </view>

            <view class="msg-body">
              <view v-if="!m.mine" class="msg-head">
                <text class="msg-name">{{ m.senderName }}</text>
                <!-- 学生用灰色标签，和群主/班委那两个"身份"标签分开：
                     它不是权限，只是"这个人是谁" -->
                <text v-if="m.senderType === 2" class="tag student">学生</text>
                <text v-else-if="m.senderRole === 1" class="tag owner">群主</text>
                <text v-else-if="m.senderRole === 2" class="tag admin">班委</text>
              </view>

              <!-- 图片：点开看大图，可以左右滑同一批图片 -->
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

              <!-- 接龙卡片：type=5。状态是每次拉列表时后端重算的，不是存库的，
                   所以这里的数字永远是最新的，不存在"卡片和名单对不上" -->
              <view
                v-else-if="m.type === 5 && m.relayCard"
                class="rcard"
                :class="['tone-' + relayTone(m.relayCard), { flash: flashIds[m.id] }]"
                @click="openRelay(m.relayCard)"
              >
                <view class="rcard-ink"></view>
                <view class="rcard-in">
                  <view class="rcard-head">
                    <view class="rcard-ico ico-relay">
                      <view class="rl-dot d1"></view>
                      <view class="rl-dot d2"></view>
                      <view class="rl-dot d3"></view>
                      <view class="rl-line l1"></view>
                      <view class="rl-line l2"></view>
                      <view class="rl-line l3"></view>
                    </view>
                    <text class="rcard-kind">接龙 · {{ relayLabel(m.relayCard) }}</text>
                  </view>

                  <text class="rcard-title">{{ m.relayCard.title }}</text>

                  <view class="rcard-meta">
                    <text>
                      已报 {{ m.relayCard.entryCount || 0 }}{{ m.relayCard.maxCount ? ' / ' + m.relayCard.maxCount : '' }} 人
                    </text>
                    <template v-if="m.relayCard.deadlineText">
                      <text class="rcard-dot">·</text>
                      <text>{{ m.relayCard.deadlineText }} 截止</text>
                    </template>
                  </view>

                  <!-- 只有设了上限才画进度条。不设上限时画一条永远很短的条，
                       会让人以为快满了 -->
                  <view v-if="m.relayCard.maxCount" class="rcard-prog">
                    <view class="rcard-prog-in" :style="{ width: relayPct(m.relayCard) + '%' }"></view>
                  </view>

                  <view
                    v-if="relayLabel(m.relayCard) !== '已取消'"
                    class="rcard-btn"
                    :class="{ line: relayBtnLine(m.relayCard) }"
                    hover-class="rcard-btn-press"
                    @click.stop="onRelayPrimary(m)"
                  >
                    {{ relayBtnText(m.relayCard) }}
                  </view>
                </view>
              </view>

              <!-- 抽签卡片：type=6。只带最新一轮结果，全部历史进结果页 ——
                   每张卡都塞 20 轮名单，一次轮询就是几十 KB -->
              <view
                v-else-if="m.type === 6 && m.lotteryCard"
                class="rcard"
                :class="['tone-' + lotteryTone(m.lotteryCard), { flash: flashIds[m.id] }]"
                @click="openLottery(m.lotteryCard)"
              >
                <view class="rcard-ink"></view>
                <view class="rcard-in">
                  <view class="rcard-head">
                    <view class="rcard-ico ico-lot">
                      <view class="lt-cup"></view>
                      <view class="lt-stick"></view>
                    </view>
                    <text class="rcard-kind">抽签 · {{ lotteryLabel(m.lotteryCard) }}</text>
                  </view>

                  <text class="rcard-title">{{ m.lotteryCard.kind === 2 ? '随机分组' : '随机点名' }}</text>

                  <view class="rcard-meta">
                    <text>名单 {{ m.lotteryCard.poolSize || 0 }} 人</text>
                    <text class="rcard-dot">·</text>
                    <text v-if="m.lotteryCard.kind === 2">{{ m.lotteryCard.groupSummary || '分 ' + (m.lotteryCard.groupCount || 0) + ' 组' }}</text>
                    <text v-else>抽 {{ m.lotteryCard.drawCount || 1 }} 个</text>
                  </view>

                  <!-- 中签名字：横向排，超过 3 个折行并给「等 N 人」 -->
                  <view v-if="lotteryDrawn(m.lotteryCard) && (m.lotteryCard.latestNames || []).length" class="rcard-names">
                    <text
                      v-for="(n, k) in (m.lotteryCard.latestNames || []).slice(0, 3)"
                      :key="k"
                      class="rcard-name"
                    >
                      {{ n }}
                    </text>
                    <text v-if="(m.lotteryCard.latestNames || []).length > 3" class="rcard-more">
                      等 {{ m.lotteryCard.latestNames.length }} 人
                    </text>
                  </view>

                  <view
                    class="rcard-btn"
                    :class="{ line: !m.lotteryCard.mine || drawingIds[m.id] }"
                    hover-class="rcard-btn-press"
                    @click.stop="onLotteryPrimary(m)"
                  >
                    {{ lotteryBtnText(m) }}
                  </view>
                </view>
              </view>

              <view v-else class="bubble" :class="{ 'bubble-self': m.mine }">
                <text class="bubble-text">{{ m.content }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 两个锚点交替用：scroll-into-view 赋同一个值不会触发第二次滚动，
             交替才能保证每条新消息都真的滚到底 -->
        <view id="anchor-a" class="anchor"></view>
        <view id="anchor-b" class="anchor"></view>
      </scroll-view>

      <!-- 工具面板：夹在消息区和输入栏之间，展开时把消息区顶上去（和微信一样），
           不盖在消息上面 —— 盖上去的话最后几条消息会被挡掉。
           底部安全区由下面的输入栏负责，这里不要再加一遍，否则会多出一条空白 -->
      <view v-if="toolsOpen" class="tools">
        <view
          v-for="t in TOOLS"
          :key="t.key"
          class="tool"
          hover-class="tool-press"
          @click="useTool(t)"
        >
          <view class="tool-ic">
            <!-- 照片 / 视频 -->
            <template v-if="t.key === 'media'">
              <view class="ic-sun"></view>
              <view class="ic-hill"></view>
            </template>
            <!-- 文件 -->
            <template v-else-if="t.key === 'file'">
              <view class="ic-doc-line"></view>
              <view class="ic-doc-line short"></view>
            </template>
            <!-- 接龙：三条带序号的横线 -->
            <view v-else-if="t.key === 'relay'" class="ic-relay">
              <view class="tr-row"><view class="tr-dot"></view><view class="tr-line"></view></view>
              <view class="tr-row"><view class="tr-dot"></view><view class="tr-line"></view></view>
              <view class="tr-row"><view class="tr-dot"></view><view class="tr-line"></view></view>
            </view>
            <!-- 抽签：签筒 + 一支斜出的签。刻意不用骰子，骰子会被读成"游戏" -->
            <template v-else>
              <view class="tl-cup"></view>
              <view class="tl-stick"></view>
            </template>
          </view>
          <text class="tool-label">{{ t.label }}</text>
        </view>
      </view>

      <view class="bar" :style="{ paddingBottom: safeBottom + 'px' }">
        <view class="bar-input-wrap" :class="{ off: muted }" @click="focusInput">
          <input
            class="bar-input"
            v-model="draft"
            :disabled="muted"
            :focus="inputFocus"
            :maxlength="500"
            :placeholder="placeholder"
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
        <view
          v-else
          class="bar-plus"
          :class="{ off: muted }"
          hover-class="bar-send-press"
          @click="toggleTools"
        >
          <view class="plus-ic" :class="{ open: toolsOpen }">
            <view class="plus-h"></view>
            <view class="plus-v"></view>
          </view>
          <!-- 红点必须在 ＋ 上，不在面板里的图标上 ——
               不打开面板就看不到的红点等于没有 -->
          <view v-if="hasNewTools" class="plus-dot"></view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app'
import { getContainer, listMessages, sendMessage, uploadToContainer, fileUrl } from '@/api/container'
import { joinRelay } from '@/api/relay'
import { drawLottery } from '@/api/lottery'

/* 轮询间隔。没有 WebSocket，3 秒是「够及时」和「别把服务器打疼」的折中；
   一次增量只有几百字节（afterId 之后的消息） */
const POLL_MS = 3000

const id = ref(0)
const info = ref({})
const list = ref([])
const draft = ref('')

/* 顶部那个「N 人」= 老师成员 + 本班学生。
   学生不是 t_user，不在成员表里，所以不能只显示 memberCount ——
   否则一个 57 个学生都在说话的群会显示「1 人」 */
const totalCount = computed(() => (info.value.memberCount || 0) + (info.value.studentCount || 0))

const loading = ref(true)
const loadErr = ref('')
const sending = ref(false)
const muted = ref(false)
/* input 的 focus 是"属性变化才触发"，所以要有个独立的开关来控制它 */
const inputFocus = ref(false)
/* 工具面板是否展开（＋号面板：照片/视频、文件、接龙、抽签） */
const toolsOpen = ref(false)
/* 上传中：防止连点重复传同一个文件 */
const uploading = ref(false)

/* ---------- 工具面板的四个入口 ---------- */

/* 位置永久固定：照片 / 文件 / 接龙 / 抽签。
   曾经做过「把上次用过的提到第一位」，实际用起来是"点谁谁跳到最前面"，
   手指记住的位置下一轮就变了，比省下的那一次移动更烦人。已去掉 */
const TOOLS = [
  { key: 'media', label: '照片 / 视频' },
  { key: 'file', label: '文件' },
  { key: 'relay', label: '接龙' },
  { key: 'lottery', label: '抽签' }
]

const hasNewTools = ref(false)

try {
  // 新功能红点：没打开过面板就亮着，打开一次即消除
  hasNewTools.value = !uni.getStorageSync('chat_tools_seen')
} catch (e) {
  // 存储不可用就当没红点，不影响使用
}

/* 一键接龙的请求中标记（防连点）。按消息 id 记，不是全局一个布尔值 ——
   一屏两张接龙卡时，全局标记会把另一张也锁住 */
const relayBusy = ref(false)
/* 正在抽的抽签卡（按消息 id） */
const drawingIds = ref({})
/* 刚变化的卡片：短暂高亮一下，让人知道"这张动了" */
const flashIds = ref({})

const anchor = ref('')
const anim = ref(false)
let anchorFlip = false
let timer = null

const statusBar = ref(20)
const safeBottom = ref(0)

const placeholder = computed(() => (muted.value ? '你已被禁言，暂时不能发言' : '说点什么…'))
const canSend = computed(() => !muted.value && !sending.value && !!draft.value.trim())
/* 输入框里有字就显示「发送」，没字显示「＋」—— 决定右下角是哪个按钮 */
const hasText = computed(() => !!draft.value.trim())

/* ---------- 系统信息 ---------- */
try {
  const sys = uni.getSystemInfoSync()
  statusBar.value = sys.statusBarHeight || 20
  // 底部安全区（iPhone 的横条），没有就是 0
  safeBottom.value = sys.safeAreaInsets ? sys.safeAreaInsets.bottom : 0
} catch (e) {
  // 取不到就用默认值，不影响使用
}

onLoad((q) => {
  id.value = Number((q && q.id) || 0)
  // 没有 id 必须当场结束 loading：onShow 里的 init() 依赖 id，不会执行，
  // 页面就会永远停在"正在加载…"，一个能点的东西都没有
  if (!id.value) {
    loadErr.value = '缺少群组 id'
    loading.value = false
    return
  }
  // 首次加载放在 onLoad，不放 onShow：onLoad 一定早于 onShow，
  // 少一个"万一 onShow 没触发就永远转圈"的可能
  init()
})

/* 首次显示已经由 onLoad 处理，这里只负责"从详情页返回时刷新人数和禁言状态" */
let firstShow = true
onShow(() => {
  if (firstShow) {
    firstShow = false
    return
  }
  if (id.value) init()
})
onHide(stopPoll)
onUnload(stopPoll)

async function init() {
  if (!id.value) {
    loadErr.value = '缺少群组 id'
    loading.value = false
    return
  }
  loadErr.value = ''
  try {
    // 群名、人数、我的禁言状态都在这一个接口里。
    // 注意：邀请码不在这里展示 —— 它是有有效期的加入凭证，只在「详情 → 邀请码」里看，
    // 摆在聊天页底下会让人以为它是群号/永久有效。
    const d = await getContainer(id.value)
    info.value = d || {}
    muted.value = !!d.myMuted
    await loadLatest()
    startPoll()
  } catch (e) {
    // 3002 = 不是成员（被移出/已退出），1004 = 已解散
    loadErr.value = (e && e.message) || '加载失败'
    stopPoll()
  } finally {
    loading.value = false
  }
}

async function loadLatest() {
  const rows = await listMessages(id.value, { size: 30 })
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

async function pull() {
  // 页面已经出错或没有消息时不做增量，避免在错误态下反复请求
  if (!id.value || loadErr.value) return
  const last = list.value[list.value.length - 1]
  try {
    if (!last) {
      const rows = await listMessages(id.value, { size: 30 })
      if (rows && rows.length) {
        list.value = rows
        scrollBottom()
      }
      return
    }
    const rows = await listMessages(id.value, { afterId: last.id })
    if (rows && rows.length) {
      append(rows)
    }
  } catch (e) {
    // 轮询失败不打扰用户：网络抖一下很常见，下一轮会自己补上
  }
}

/* 按 id 去重后追加。
   必须有这一步：发送成功会立刻把自己那条 push 进列表，而下一轮轮询的 afterId
   是"上一条的 id"，服务器会把刚发的那条再返回一次 —— 不去重就是两个一样的气泡，
   而且 :key 重复会让 Vue 复用到错的行。 */
function append(rows) {
  const seen = new Set(list.value.map((m) => m.id))
  const add = rows.filter((m) => !seen.has(m.id))
  if (!add.length) return
  list.value = list.value.concat(add)
  // 新出现的卡片短暂高亮：消息流里突然多一张卡片，没有提示很容易被忽略
  add.forEach((m) => {
    if (isCard(m)) flash(m.id)
  })
  scrollBottom()
}

/* ---------- 发送 ---------- */
async function onSend() {
  if (!canSend.value) return
  const text = draft.value.trim()
  if (!text) return

  sending.value = true
  try {
    const vo = await sendMessage(id.value, text)
    draft.value = ''
    // 走 append 而不是直接 concat：万一轮询刚好同时把这条取回来，也不会出现两个气泡
    append([vo])
  } catch (e) {
    // 3005 = 被禁言：把输入框置灰，比反复弹提示有用
    if (e && e.code === 3005) {
      muted.value = true
    }
    // 其余错误 request.js 已经 toast
  } finally {
    sending.value = false
  }
}

/* ---------- 工具面板：照片/视频、文件、接龙、抽签 ---------- */

function toggleTools() {
  if (muted.value || uploading.value) return
  // 展开面板时收起键盘，否则键盘和面板会叠在一起，屏幕只剩一条缝
  if (!toolsOpen.value) inputFocus.value = false
  toolsOpen.value = !toolsOpen.value
  // 打开即消除红点，不是"用过才消除" —— 后者会让人为了消红点乱点
  if (toolsOpen.value && hasNewTools.value) {
    hasNewTools.value = false
    try {
      uni.setStorageSync('chat_tools_seen', 1)
    } catch (e) {}
  }
}

function useTool(t) {
  if (muted.value || uploading.value) return
  if (t.key === 'media') {
    pickMedia()
    return
  }
  if (t.key === 'file') {
    pickFile()
    return
  }
  // 页面类入口先收起面板，否则返回时面板还开着，会盖住刚生成的卡片
  toolsOpen.value = false
  if (t.key === 'relay') {
    uni.navigateTo({ url: `/pages/teacher/container/relay-create?id=${id.value}` })
    return
  }
  uni.navigateTo({ url: `/pages/teacher/container/lottery-create?id=${id.value}` })
}

/** 选照片或视频（相册 / 拍照都行） */
function pickMedia() {
  if (muted.value || uploading.value) return
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
      // 用户点了取消也会走 fail，这里静默，不要弹"失败"
    }
  })
}

/** 从微信聊天记录里选一个文件 */
function pickFile() {
  if (muted.value || uploading.value) return
  // chooseMessageFile 是微信独有的，H5/支付宝端没有
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
 * <p>为什么分两步而不是一个接口：用户选了图又反悔，文件已经传上来了但没发消息，
 * 这种情况下"没发出去"就等于没发；合成一步反而要做"取消时删文件"。
 */
async function uploadAndSend(filePath, kind, extra = {}) {
  if (uploading.value) return
  uploading.value = true
  uni.showLoading({ title: '发送中', mask: true })
  try {
    const up = await uploadToContainer(id.value, filePath, kind)

    const payload = { type: kind === 'image' ? 2 : kind === 'video' ? 3 : 4, mediaUrl: up.url }
    if (kind === 'video') {
      payload.fileName = up.name
      payload.fileSize = up.size
      payload.duration = extra.duration || null
    } else if (kind === 'file') {
      payload.fileName = extra.fileName || up.name
      payload.fileSize = extra.fileSize || up.size
    }

    const vo = await sendMessage(id.value, payload)
    append([vo])
    toolsOpen.value = false
    uni.hideLoading()
  } catch (e) {
    // 先关 loading 再提示：toast 和 loading 用的是同一个浮层，
    // 顺序反了的话提示会被 hideLoading 一起关掉，用户什么都看不到
    uni.hideLoading()
    uni.showToast({ title: (e && e.message) || '发送失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

/* ---------- 卡片消息（接龙 / 抽签） ---------- */

/* 卡片有没有拿到业务数据。后端聚合失败时 relayCard 会是 null，
   这时退回普通文本气泡，比渲染一张空卡片好 */
function isCard(m) {
  return (m.type === 5 && !!m.relayCard) || (m.type === 6 && !!m.lotteryCard)
}

function flash(msgId) {
  flashIds.value = { ...flashIds.value, [msgId]: true }
  setTimeout(() => {
    const next = { ...flashIds.value }
    delete next[msgId]
    flashIds.value = next
  }, 700)
}

/* 拉一次最近的消息，把同 id 的卡片内容替换掉，不动顺序。
   只改列表里的旧卡片，不重新排序 —— 卡片更新不该把消息跳到别处 */
async function refreshCards() {
  try {
    const rows = await listMessages(id.value, { size: 30 })
    if (!rows || !rows.length) return
    const byId = new Map(rows.map((r) => [r.id, r]))
    list.value = list.value.map((x) => byId.get(x.id) || x)
  } catch (e) {
    // 刷不到就等下一轮轮询，不打扰用户
  }
}

/* ---- 接龙 ---- */

function relayLabel(c) {
  if (c.status === 2) return '已截止'
  if (c.status === 3) return '已满员'
  if (c.status === 4) return '已结束'
  if (c.status === 5) return '已取消'
  // 「已参与」必须单独一个状态。回来看到「我要接龙」会怀疑自己到底报没报，
  // 这是接龙类功能最常见的困惑
  return c.joined ? '已参与' : '进行中'
}

function relayTone(c) {
  if (c.status !== 1) return 'off'
  return c.joined ? 'done' : 'live'
}

function relayPct(c) {
  if (!c.maxCount) return 0
  return Math.min(100, Math.round(((c.entryCount || 0) / c.maxCount) * 100))
}

function relayBtnText(c) {
  if (c.status === 1) return c.joined ? '修改我的填写' : '我要接龙'
  if (c.status === 4) return '查看结果'
  return '查看名单'
}

/* 实心只在"进行中且没参与"时用：那是唯一一个点了立刻有结果的状态。
   其余全描边，避免一屏里好几个实心按钮抢注意力 */
function relayBtnLine(c) {
  return !(c.status === 1 && !c.joined)
}

function openRelay(c) {
  uni.navigateTo({ url: `/pages/teacher/container/relay-detail?id=${id.value}&relayId=${c.id}` })
}

/**
 * 卡片主按钮
 *
 * <p>序号格式 + 进行中 + 没参与 → 直接在卡片上完成，不进页面。
 * 报名类接龙 90% 是"我就是要去"，让人点两下纯属浪费；
 * 只有真的需要填内容（数字/手机号/文本/单选）时才进详情页。
 */
async function onRelayPrimary(m) {
  const c = m.relayCard
  const oneTap = c.status === 1 && !c.joined && c.entryFormat === 1
  if (!oneTap) {
    openRelay(c)
    return
  }
  if (relayBusy.value) return
  relayBusy.value = true
  try {
    const card = await joinRelay(id.value, c.id, '')
    // 后端返回的就是卡片本身，直接替换，不用等下一轮轮询
    m.relayCard = card
    const seq = card && card.entryCount
    uni.showToast({ title: seq ? `已接龙，第 ${seq} 位` : '已接龙', icon: 'none' })
    flash(m.id)
  } catch (e) {
    // 4101 已满 / 4102 已截止 / 4103 已结束：状态变了，把卡片刷成最新
    const code = e && e.code
    if (code === 4101 || code === 4102 || code === 4103) await refreshCards()
  } finally {
    relayBusy.value = false
  }
}

/* ---- 抽签 ---- */

function lotteryDrawn(c) {
  return c.status === 2
}

function lotteryLabel(c) {
  if (c.status === 3) return '已取消'
  return c.status === 2 ? '已开奖' : '未开始'
}

function lotteryTone(c) {
  if (c.status === 2) return 'live'
  return c.status === 3 ? 'off' : 'wait'
}

function lotteryBtnText(m) {
  if (drawingIds.value[m.id]) return '抽取中…'
  const c = m.lotteryCard
  if (!c.mine) return c.status === 2 ? '查看结果' : '等发起人开抽'
  if (c.status === 2) return c.kind === 2 ? '重新分组' : '再抽一次'
  return '开始抽'
}

function openLottery(c) {
  uni.navigateTo({ url: `/pages/teacher/container/lottery-result?id=${id.value}&lotteryId=${c.id}` })
}

async function onLotteryPrimary(m) {
  const c = m.lotteryCard
  if (!c.mine) {
    openLottery(c)
    return
  }
  if (drawingIds.value[m.id]) return

  drawingIds.value = { ...drawingIds.value, [m.id]: true }
  const started = Date.now()
  try {
    const d = await drawLottery(id.value, c.id)
    /* 至少留 900ms 的"抽取中"。网络快的时候瞬间出结果，
       抽签这件事的仪式感就没了；但也不能为了动画硬等，所以取两者较大值 */
    const wait = Math.max(0, 900 - (Date.now() - started))
    if (wait) await new Promise((r) => setTimeout(r, wait))
    // LotteryVO 继承 LotteryCardVO，返回的东西本身就是一张完整的卡片
    m.lotteryCard = d
    flash(m.id)
  } catch (e) {
    // 4105 = 名单抽完了，把卡片刷成"还剩 0 人"
    if (e && e.code === 4105) await refreshCards()
  } finally {
    const next = { ...drawingIds.value }
    delete next[m.id]
    drawingIds.value = next
  }
}

/* ---------- 展示 ---------- */
function scrollBottom() {
  nextTick(() => {
    anchorFlip = !anchorFlip
    anchor.value = anchorFlip ? 'anchor-a' : 'anchor-b'
    // 首次进入直接跳到底，之后有新消息才做动画
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
  return s ? s.slice(0, 1) : '?'
}

/* ---------- 媒体：预览 / 播放 / 打开 ---------- */

/** 点图片看大图，能左右滑同一批图片（只把图片消息串起来，文本不参与） */
function previewImage(m) {
  const urls = list.value.filter((x) => x.type === 2).map((x) => fileUrl(x.mediaUrl))
  uni.previewImage({ current: fileUrl(m.mediaUrl), urls })
}

/**
 * 视频走系统播放器，不在气泡里嵌 <video>。
 *
 * <p>{@code <video>} 在小程序里是<b>原生组件</b>，会盖在同层的普通元素上面 ——
 * 气泡里嵌一个，工具面板和输入栏都可能被它盖住，点不到。
 * 全屏播放既躲开这个问题，体验也更接近微信。
 */
function openVideo(m) {
  if (!uni.previewMedia) {
    uni.showToast({ title: '当前环境不支持播放', icon: 'none' })
    return
  }
  uni.previewMedia({ sources: [{ url: fileUrl(m.mediaUrl), type: 'video' }] })
}

/** 文件：先下载到临时目录，再交给系统预览（pdf/office/txt 能直接打开） */
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
        fail: () => {
          // zip/rar 这类系统没有对应查看器，会走到这里
          uni.showToast({ title: '这个格式手机打不开', icon: 'none' })
        }
      })
    },
    fail: () => {
      uni.hideLoading()
      uni.showToast({ title: '下载失败', icon: 'none' })
    }
  })
}

/** 字节数 -> 1.2 MB / 340 KB */
function fmtSize(n) {
  if (!n || n <= 0) return ''
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return Math.round(n / 1024) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

/** 秒 -> 1:05 */
function fmtDuration(sec) {
  if (!sec || sec <= 0) return ''
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return m + ':' + String(s).padStart(2, '0')
}

/** 取扩展名当文件角标，太长就截断 */
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
    // 从"创建成功"直接 redirect 过来时栈里只有自己，退回列表而不是卡死
    uni.reLaunch({ url: '/pages/teacher/container/index' })
  }
}

function goDetail() {
  uni.navigateTo({ url: `/pages/teacher/container/detail?id=${id.value}` })
}

/* 点空白处或输入框外壳都能唤起键盘。
   focus 只在值变化时生效，所以先置 false 再置 true —— 否则连续点两次，
   第二次不会响应，用户会觉得"点了没反应"。 */
function focusInput() {
  if (muted.value) return
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
  /* 整页自己管滚动，页面级不要再滚一层 ——
     否则输入栏会被顶到可视区外面，看起来像"下面的东西点不到" */
  overflow: hidden;
}

/* ---------- 自定义导航 ---------- */
.nav {
  flex-shrink: 0;
  background: #F7EFE4;
  /* 抬一层：消息区是 scroll-view，个别基础库上会盖住相邻节点 */
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
  /* scroll-view 在 flex 容器里必须给一个确定的高度（height:0 + flex:1 撑开），
     只写 min-height:0 在部分基础库上会按内容撑大，把输入栏顶出屏幕 */
  height: 0;
  min-height: 0;
  padding: 0 24rpx;
  box-sizing: border-box;
}

.empty {
  /* 撑满整个消息区：空白区域也要能点（点了聚焦输入框），
     否则用户点哪都没反应，会以为页面卡死了 */
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

/* 空状态整块可点（点了聚焦输入框），按下给一点反馈 */
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

.avatar-img {
  width: 100%;
  height: 100%;
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

.tag.owner {
  background: #FDEDEB;
  color: #C5342B;
}

.tag.admin {
  background: #FBF0D8;
  color: #9A7A2E;
}

.tag.student {
  background: #EFE9E1;
  color: #7A6F63;
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

.bar-input-wrap.off {
  opacity: 0.7;
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
  position: relative;
}

/* 新功能红点。放在 ＋ 上而不是面板里 —— 不打开面板就看不到的红点等于没有 */
.plus-dot {
  position: absolute;
  right: 6rpx;
  top: 6rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #E8594F;
  border: 3rpx solid #FFFFFF;
}

.bar-plus.off {
  opacity: 0.4;
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
/* 4 列宫格。为什么是 4 列不是 3 列：3 列每项 234rpx，图标和文字之间会留一大片空白，
   看起来像"没排满"；4 列和微信/QQ 的手感一致，用户不用重新学 */
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
  /* 图标是 104rpx 的方块，比 25% 的格子（约 176rpx）窄，居中即可 */
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

/* 用纯 CSS 画四个图标，省掉四张图片资源 */
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

/* 接龙：三行「小圆点 + 横线」，就是名单的样子 */
.ic-relay {
  position: absolute;
  left: 0;
  top: 0;
  width: 96rpx;
  height: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9rpx;
}

.tr-row {
  width: 50rpx;
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.tr-dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: #C5342B;
  flex-shrink: 0;
}

.tr-line {
  flex: 1;
  height: 6rpx;
  border-radius: 3rpx;
  background: #E8A79F;
}

/* 抽签：签筒 + 一支斜出的签 */
.tl-cup {
  position: absolute;
  left: 22rpx;
  bottom: 18rpx;
  width: 52rpx;
  height: 40rpx;
  background: #C5342B;
  border-radius: 4rpx 4rpx 14rpx 14rpx;
}

.tl-stick {
  position: absolute;
  left: 42rpx;
  top: 14rpx;
  width: 8rpx;
  height: 38rpx;
  border-radius: 4rpx;
  background: #E8A79F;
  transform: rotate(16deg);
  transform-origin: bottom center;
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

/* ---------- 接龙 / 抽签卡片 ----------
   卡片比气泡宽：它是功能性内容，不是对话。宽度算下来 =
   屏幕 - 页面左右 padding(48rpx) - 头像(76rpx) - 两边间距(36rpx) ≈ 容器宽 - 96rpx */
.msg.card-msg {
  flex-direction: row;
}

.msg.card-msg .msg-body {
  max-width: none;
  flex: 1;
  align-items: stretch;
}

.rcard {
  position: relative;
  display: flex;
  background: #FFFFFF;
  border-radius: 22rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.07);
  overflow: hidden;
  /* 硬上限 320rpx：接龙说明写 500 字也不能把整屏吃掉，超出的进详情页 */
  max-height: 320rpx;
  transition: background-color 0.3s ease;
}

/* 刚变化的卡片短暂高亮，让人知道"这张动了" */
.rcard.flash {
  background: #FFF6F4;
}

/* 6rpx 左侧色条：一屏三张卡时，颜色比文字更快分辨哪张还活着 */
.rcard-ink {
  width: 6rpx;
  flex-shrink: 0;
  background: #C5342B;
}

.tone-done .rcard-ink {
  background: #3B6D11;
}

.tone-off .rcard-ink,
.tone-wait .rcard-ink {
  background: #D8CEC0;
}

.rcard-in {
  flex: 1;
  min-width: 0;
  padding: 22rpx 24rpx 20rpx;
  display: flex;
  flex-direction: column;
}

.rcard-head {
  display: flex;
  align-items: center;
}

.rcard-ico {
  width: 34rpx;
  height: 34rpx;
  position: relative;
  flex-shrink: 0;
  margin-right: 10rpx;
}

.ico-relay .rl-dot {
  position: absolute;
  left: 1rpx;
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: #C5342B;
}

.ico-relay .rl-line {
  position: absolute;
  left: 13rpx;
  width: 20rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: #E8A79F;
}

.ico-relay .d1 { top: 6rpx; }
.ico-relay .d2 { top: 15rpx; }
.ico-relay .d3 { top: 24rpx; }
.ico-relay .l1 { top: 7rpx; }
.ico-relay .l2 { top: 16rpx; }
.ico-relay .l3 { top: 25rpx; }

.ico-lot .lt-cup {
  position: absolute;
  left: 4rpx;
  bottom: 3rpx;
  width: 20rpx;
  height: 15rpx;
  background: #C5342B;
  border-radius: 2rpx 2rpx 7rpx 7rpx;
}

.ico-lot .lt-stick {
  position: absolute;
  left: 15rpx;
  top: 0;
  width: 4rpx;
  height: 15rpx;
  border-radius: 2rpx;
  background: #E8A79F;
  transform: rotate(16deg);
  transform-origin: bottom center;
}

.rcard-kind {
  font-size: 22rpx;
  color: #9A9186;
}

.tone-live .rcard-kind {
  color: #C5342B;
}

.tone-done .rcard-kind {
  color: #3B6D11;
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

.tone-off .rcard-title {
  color: #8A8178;
}

.rcard-meta {
  margin-top: 8rpx;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  font-size: 22rpx;
  color: #9A9186;
}

.rcard-dot {
  margin: 0 8rpx;
  color: #C9BEB0;
}

.rcard-prog {
  margin-top: 12rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.rcard-prog-in {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #E8594F, #C5342B);
  transition: width 0.3s ease;
}

.rcard-names {
  margin-top: 12rpx;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6rpx 20rpx;
}

.rcard-name {
  font-size: 34rpx;
  font-weight: 600;
  color: #C5342B;
}

.rcard-more {
  font-size: 21rpx;
  color: #9A9186;
}

.rcard-btn {
  margin-top: 16rpx;
  height: 84rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 描边态用于"不需要立刻行动"的场合，避免一屏里好几个实心按钮抢注意力 */
.rcard-btn.line {
  background: transparent;
  border: 2rpx solid #E2D5C3;
  color: #7A6F63;
  font-weight: 400;
}

.rcard-btn-press {
  opacity: 0.82;
}
</style>
