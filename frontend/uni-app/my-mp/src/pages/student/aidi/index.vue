<template>
  <view class="page">
    <!-- 有对话时才出现的一条细栏：右边给一个重开的出口。
         对话存档之后，没有「新对话」的话旧对话会一直赖着 -->
    <view v-if="list.length" class="head">
      <text class="head-new" @click="clearChat">新对话</text>
    </view>

    <scroll-view
      class="list"
      scroll-y
      :scroll-into-view="anchor"
      :scroll-with-animation="true"
      @click="hideKeyboard"
    >
      <!-- 开场：说明能问什么，给三个能直接点的例子 -->
      <view v-if="!list.length" class="intro">
        <view class="intro-mark">乐</view>
        <text class="intro-title">我是乐迪</text>
        <text class="intro-sub">你的事我都能查：请假批了没、上次考多少、坐哪。</text>

        <view class="chips">
          <view
            class="chip"
            v-for="q in samples"
            :key="q"
            hover-class="chip-pressed"
            @click="ask(q)"
          >
            <text class="chip-text">{{ q }}</text>
          </view>
        </view>
      </view>

      <view class="msg" v-for="(m, i) in list" :key="i" :id="'m' + i">
        <view class="row" :class="{ 'row-mine': m.role === 'user' }">
          <view v-if="m.role !== 'user'" class="ava">乐</view>

          <view class="bubble" :class="m.role === 'user' ? 'bubble-mine' : 'bubble-other'">
            <text class="bubble-text" :class="{ 'bubble-failed': m.failed }">{{ m.content }}</text>
            <!-- 失败给重试，不要让用户自己重打一遍 -->
            <text v-if="m.failed" class="retry" @click="retry(m)">重试</text>
          </view>
        </view>
      </view>

      <!-- 「正在查」是一个独立的气泡，不塞进 list。
           塞进去就得靠改一个对象上的字段来收尾，那个字段不在响应式里，
           气泡会一直停在"正在查"—— 这就是上一版"用不了"的样子。
           这里用 thinking 这个 ref 控制，收尾时置 false，一定会消失。 -->
      <view v-if="thinking" class="msg">
        <view class="row">
          <view class="ava">乐</view>
          <view class="bubble bubble-other">
            <view class="dots">
              <view class="dot"></view>
              <view class="dot"></view>
              <view class="dot"></view>
            </view>
            <text class="think-text">{{ thinkText }}</text>
          </view>
        </view>
      </view>

      <view class="list-end"></view>
    </scroll-view>

    <!-- 输入区。margin-bottom 让出底栏的高度，否则会被底栏压住 -->
    <view class="composer">
      <input
        class="input"
        v-model="draft"
        :maxlength="200"
        placeholder="问点你自己的事…"
        placeholder-style="color:#B4A99B"
        confirm-type="send"
        :confirm-hold="true"
        :disabled="thinking"
        @confirm="onConfirm"
      />
      <view
        class="send"
        :class="{ 'send-off': !draft.trim() || thinking }"
        hover-class="send-pressed"
        @click="onSend"
      >
        <text class="send-text">发送</text>
      </view>
    </view>

    <student-tab-bar current="aidi" />
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'
import { askStudentAidi } from '@/api/student-data'
import { saveChat, loadChat, dropChat } from '@/utils/student-aidi-store'
import StudentTabBar from '@/components/student-tab-bar/student-tab-bar.vue'

/* 对话存本地（utils/student-aidi-store.js）：切到别的页面再回来还在。
   按学号隔离、最多 40 条、7 天过期。 */
const me = uni.getStorageSync('studentInfo') || {}
const owner = me.studentNo || ''

const list = ref(loadChat(owner) || [])
const draft = ref('')
const thinking = ref(false)
const elapsed = ref(0)
const anchor = ref('')

/* 客户端硬超时。
   服务端自己也有 60 秒的读超时，但那条路要等 Tomcat 那侧报错才回来；
   小程序这边先掐，用户能早十几秒看到一个「重试」，而不是一直盯着"正在查"。 */
const TIMEOUT_MS = 45000

let tick = null

const samples = ['我上次考多少', '我请过几次假', '我坐第几排']

const thinkText = computed(() => {
  if (elapsed.value < 8) return '正在查…'
  return `还在查，${elapsed.value} 秒了`
})

function hideKeyboard() {
  uni.hideKeyboard()
}

async function scrollToEnd() {
  await nextTick()
  anchor.value = 'm' + Math.max(0, list.value.length - 1)
}

function clearChat() {
  uni.showModal({
    title: '新对话',
    content: '之前的问答会清掉，查过的东西随时能再问我。',
    confirmColor: '#C5342B',
    success: (r) => {
      if (!r.confirm) return
      list.value = []
      dropChat()
    }
  })
}

function onSend() {
  ask(draft.value)
}

/* 键盘上的发送键可能比最后一次 bindinput 先到，优先读事件里的值 */
function onConfirm(e) {
  ask((e && e.detail && e.detail.value) || draft.value)
}

/** 只把真正的对话发上去：失败气泡不算历史，否则下一轮会带着一条假回答去问 */
function history() {
  return list.value
    .filter((m) => !m.failed && m.content)
    .map((m) => ({ role: m.role, content: m.content }))
}

async function ask(text) {
  const content = String(text || '').trim()
  if (!content || thinking.value) return

  list.value.push({ role: 'user', content })
  draft.value = ''
  thinking.value = true
  elapsed.value = 0
  scrollToEnd()

  /* 提问先存一次：万一这一轮超时、页面被杀，至少问过什么还在 */
  saveChat(owner, list.value)

  tick = setInterval(() => {
    elapsed.value += 1
  }, 1000)

  try {
    const res = await withTimeout(askStudentAidi(history()), TIMEOUT_MS)
    list.value.push({
      role: 'assistant',
      content: (res && res.reply) || '我没查到相关的记录。'
    })
  } catch (e) {
    list.value.push({
      role: 'assistant',
      failed: true,
      content: (e && e.message) || '没答上来，再问一次试试'
    })
  } finally {
    stopTick()
    thinking.value = false
    saveChat(owner, list.value)
    scrollToEnd()
  }
}

/** 重试：删掉这条失败气泡，把上一句再问一次 */
function retry(m) {
  if (thinking.value) return
  const i = list.value.indexOf(m)
  if (i < 0) return
  list.value.splice(i, 1)

  /* 失败气泡前面那条一定是刚才问的那句。把它也摘掉 ——
     留着的话 ask() 会再 push 一条一样的，屏幕上出现两句相同的问题 */
  let at = -1
  for (let k = list.value.length - 1; k >= 0; k--) {
    if (list.value[k].role === 'user') {
      at = k
      break
    }
  }
  if (at < 0) return
  const q = list.value[at].content
  list.value.splice(at, 1)
  ask(q)
}

function stopTick() {
  if (tick) {
    clearInterval(tick)
    tick = null
  }
}

/* Promise.race 版的超时：小程序没有 AbortController，
   而且这里也不需要真的中断请求 —— 只要前端不再等它就行 */
function withTimeout(p, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      const e = new Error('乐迪这次没答上来，点重试再来一次')
      e.code = 'TIMEOUT'
      reject(e)
    }, ms)
    p.then(
      (v) => {
        clearTimeout(timer)
        resolve(v)
      },
      (e) => {
        clearTimeout(timer)
        reject(e)
      }
    )
  })
}
</script>

<style lang="scss" scoped>
/* 整页固定高度、列表自己滚。用 100vh 而不是 min-height：
   聊天气泡多了以后页面不能整体变长，否则输入框会被顶出屏幕 */
.page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F7EFE4;
  box-sizing: border-box;
}

.head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 12rpx 28rpx;
  background: #FBF6EE;
  border-bottom: 2rpx solid #F0E6D8;
}

.head-new {
  font-size: 23rpx;
  color: #C5342B;
  padding: 6rpx 4rpx 6rpx 20rpx;
}

.list {
  flex: 1;
  /* scroll-view 在 flex 列里必须给 0 高，否则会按内容撑开、把输入框顶下去 */
  height: 0;
  padding: 24rpx 24rpx 0;
  box-sizing: border-box;
}

/* ---------- 开场 ---------- */
.intro {
  padding: 60rpx 20rpx 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.intro-mark {
  width: 108rpx;
  height: 108rpx;
  border-radius: 32rpx;
  background: linear-gradient(140deg, #E8544D, #C0322F);
  color: #FFFFFF;
  font-size: 46rpx;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 28rpx rgba(197, 52, 43, 0.2);
}

.intro-title {
  margin-top: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #2B2622;
}

.intro-sub {
  margin-top: 10rpx;
  font-size: 23rpx;
  color: #9A9186;
  text-align: center;
  line-height: 1.6;
}

.chips {
  margin-top: 36rpx;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
}

.chip {
  padding: 14rpx 26rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  margin: 0 8rpx 16rpx;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.06);
}

.chip-pressed {
  background: #FAF5EF;
}

.chip-text {
  font-size: 24rpx;
  color: #6B6259;
}

/* ---------- 消息 ---------- */
.msg {
  margin-bottom: 22rpx;
}

.row {
  display: flex;
  align-items: flex-start;
}

/* 自己发的靠右：不用 flex-direction: row-reverse，
   那样头像和气泡的间距会跑到另一边，两边对不齐 */
.row-mine {
  justify-content: flex-end;
}

.ava {
  width: 64rpx;
  height: 64rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
  margin-right: 14rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bubble {
  max-width: 76%;
  padding: 22rpx 24rpx;
  border-radius: 22rpx;
  box-sizing: border-box;
}

.bubble-other {
  background: #FFFFFF;
  border-top-left-radius: 8rpx;
  box-shadow: 0 6rpx 18rpx rgba(160, 120, 80, 0.05);
}

.bubble-mine {
  background: linear-gradient(135deg, #E8594F 0%, #C5342B 100%);
  border-top-right-radius: 8rpx;
}

.bubble-text {
  font-size: 28rpx;
  line-height: 1.6;
  color: #2B2622;
  word-break: break-all;
}

.bubble-mine .bubble-text {
  color: #FFFFFF;
}

.bubble-failed {
  color: #C5342B;
}

/* 重试：贴在失败气泡下面，红字，点击区域给够 */
.retry {
  display: inline-block;
  margin-top: 14rpx;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  font-size: 24rpx;
  color: #C5342B;
}

/* ---------- 正在查 ---------- */
.think-text {
  display: block;
  margin-top: 10rpx;
  font-size: 23rpx;
  color: #B4A99B;
}

/* 三个点依次亮，证明"它还活着"。
   纯静态的"正在查…"和不动的页面分不出来，用户只能靠猜 */
.dots {
  display: flex;
  align-items: center;
  height: 28rpx;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #C5342B;
  margin-right: 10rpx;
  opacity: 0.25;
  animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
  margin-right: 0;
}

@keyframes blink {
  0%,
  100% {
    opacity: 0.25;
  }
  40% {
    opacity: 1;
  }
}

.list-end {
  height: 30rpx;
}

/* ---------- 输入区 ---------- */
.composer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: #FFFFFF;
  border-top: 1rpx solid #F0E6D8;
  /* 让出底栏：100rpx + 安全区。少了这一条，输入框会被底栏盖住一半 */
  margin-bottom: calc(100rpx + env(safe-area-inset-bottom));
}

.input {
  flex: 1;
  height: 76rpx;
  padding: 0 24rpx;
  background: #FBF7F1;
  border-radius: 38rpx;
  font-size: 27rpx;
  color: #2B2622;
}

.send {
  flex-shrink: 0;
  margin-left: 14rpx;
  height: 76rpx;
  padding: 0 32rpx;
  border-radius: 38rpx;
  background: #C5342B;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-off {
  background: #E3D5C8;
}

.send-pressed {
  opacity: 0.9;
}

.send-text {
  font-size: 27rpx;
  color: #FFFFFF;
}
</style>
