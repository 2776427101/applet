<template>
  <view class="page">
    <!-- 有对话时才出现的一条细栏：左边说清"这段对话是哪个班的"，
         右边给一个重开的出口。存档之后如果没有"新对话"，旧对话会一直赖着 -->
    <view v-if="list.length" class="head">
      <text class="head-class">{{ currentClassName }}</text>
      <text class="head-new" @click="clearChat">新对话</text>
    </view>

    <scroll-view
      class="msgs"
      scroll-y
      :scroll-into-view="anchor"
      :scroll-with-animation="anim"
      @click="blurInput"
    >
      <!-- 空状态：不写使用说明，直接给三个能问的问题。
           「点下面输入框开始聊天」这种话谁都知道，而"能问什么"才是第一次进来最想知道的 -->
      <view v-if="!list.length" class="intro">
        <view class="robot big">
          <view class="antenna-dot"></view>
          <view class="antenna-line"></view>
          <view class="rd-head">
            <view class="rd-eye left"></view>
            <view class="rd-eye right"></view>
            <view class="rd-mouth"></view>
          </view>
        </view>
        <text class="intro-name">乐迪</text>
        <text class="intro-tip">班里的事，问我</text>

        <view class="chips">
          <view
            class="chip"
            v-for="(q, i) in SUGGEST"
            :key="i"
            hover-class="chip-press"
            @click="askText(q)"
          >
            {{ q }}
          </view>
        </view>

        <text class="intro-note">改动会先让你确认 · 说「记住…」我就记下来</text>
      </view>

      <!-- 消息 -->
      <view v-for="(m, i) in list" :key="i" class="item">
        <view class="row" :class="{ mine: m.role === 'user' }">
          <view v-if="m.role !== 'user'" class="robot small">
            <view class="antenna-dot"></view>
            <view class="antenna-line"></view>
            <view class="rd-head">
              <view class="rd-eye left"></view>
              <view class="rd-eye right"></view>
              <view class="rd-mouth"></view>
            </view>
          </view>

          <view class="bubble" :class="{ self: m.role === 'user', bad: m.failed }">
            <text class="txt">{{ m.content }}</text>
            <text v-if="m.failed" class="retry" @click="retry(m)">重试</text>
          </view>
        </view>

        <!-- 确认卡：模型只提议，落库要等这一下点击。
             按钮文案由后端给（「确认通过」「确认录入」），前端不猜动作语义 -->
        <view v-if="m.action" class="act" :class="{ warn: m.action.danger }">
          <view class="act-head">
            <text class="act-title">{{ m.action.title }}</text>
            <text v-if="m.action.danger" class="act-flag">不可撤销</text>
          </view>
          <text class="act-summary">{{ m.action.summary }}</text>

          <view v-if="!m.acted" class="act-btns">
            <view class="act-btn ghost" hover-class="act-press" @click="cancelAction(m)">取消</view>
            <view
              class="act-btn"
              :class="{ warn: m.action.danger, busy: m.busy }"
              hover-class="act-press"
              @click="runAction(m)"
            >
              {{ m.busy ? '执行中…' : m.action.confirmText }}
            </view>
          </view>
          <text v-else class="act-done">{{ m.canceled ? '已取消，没有改动任何数据' : m.doneText }}</text>
          <text v-if="m.actError" class="act-err">{{ m.actError }}</text>
        </view>
      </view>

      <!-- 思考中：三个点。不要写「正在查询数据」——老师不关心中间过程，
           而且一旦它其实没查数据，这句话就是假的 -->
      <view v-if="thinking" class="row">
        <view class="robot small">
          <view class="antenna-dot"></view>
          <view class="antenna-line"></view>
          <view class="rd-head">
            <view class="rd-eye left"></view>
            <view class="rd-eye right"></view>
            <view class="rd-mouth"></view>
          </view>
        </view>
        <view class="bubble typing">
          <view class="dot d1"></view>
          <view class="dot d2"></view>
          <view class="dot d3"></view>
        </view>
      </view>

      <view id="bottom" class="pad"></view>
    </scroll-view>

    <view class="bar">
      <input
        class="input"
        v-model="draft"
        type="text"
        :disabled="thinking"
        confirm-type="send"
        :cursor-spacing="24"
        placeholder="问乐迪点什么…"
        placeholder-style="color:#B4A99B;font-size:27rpx"
        @confirm="askText()"
      />
      <view
        class="send"
        :class="{ off: !canSend }"
        hover-class="send-press"
        @click="askText()"
      >
        发送
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { askAidi, confirmAidiAction } from '@/api/aidi'
import { currentClassId, currentClassName } from '@/store/class'
import { saveChat, loadChat, dropChat } from '@/utils/aidi-store'

/* 第一次进来最可能问的三件事。必须是工具真能回答的 ——
   放一个答不上来的示例，等于第一次就把信任用光 */
const SUGGEST = ['班上一共有多少人', '最近一次考试考得怎么样', '我还有哪些待办没做']

/* 每条消息：{ role, content, failed?, action?, busy?, acted?, canceled?, doneText?, actError? }
   action 只挂在 assistant 那条上，同一张卡不会跟着消息列表重排 */
const list = ref([])
const draft = ref('')
const thinking = ref(false)
const anchor = ref('')
const anim = ref(false)

/* 这段对话是哪个班的。老师切了班就清空 ——
   否则会出现"问的是二班、答案里是一班的学生"这种最难解释的错误 */
const convClassId = ref(0)

const canSend = computed(() => !!draft.value.trim() && !thinking.value)

onShow(() => {
  if (!list.value.length) {
    /* 冷启动 / 从别的 tab 回来：先把上次的对话捞回来 */
    restore()
    return
  }
  if (convClassId.value !== currentClassId.value) {
    list.value = []
    convClassId.value = 0
    dropChat()
  }
})

/* 消息一变就存档。深监听比在每个 push / splice 后面手写一次可靠：
   漏掉一处就是"刚才那句话重启后没了"，而这种 bug 没人会去复现 */
watch(list, () => saveChat(convClassId.value, list.value), { deep: true })

function restore() {
  const msgs = loadChat(currentClassId.value)
  if (!msgs) return
  /* 先定 convClassId 再灌 list：灌 list 会触发存档的 watch，
     顺序反了的话那一次存档会把 classId 写成 0，下次就恢复不出来了 */
  convClassId.value = currentClassId.value
  list.value = msgs
  scrollBottom()
}

function clearChat() {
  list.value = []
  convClassId.value = 0
  dropChat()
}

function blurInput() {
  /* 点消息区收起键盘：不然在小屏上，一半屏幕被键盘占着看不到回答 */
  uni.hideKeyboard()
}

function scrollBottom() {
  nextTick(() => {
    anim.value = true
    anchor.value = 'bottom'
  })
}

/**
 * 发给后端的历史：去掉失败的气泡，最多 8 条（后端还会再截一次）。
 *
 * 已执行的确认卡要把结果并进那条 assistant 消息里 —— 否则老师下一句问
 * "刚才那个录好了吗"，模型完全不知道自己做过什么。并进去而不是单开一条，
 * 是为了不出现连续两条 assistant。
 */
function history() {
  const out = []
  list.value.forEach((m) => {
    if (m.failed) return
    const extra = m.doneText ? `\n${m.doneText}` : ''
    out.push({ role: m.role, content: m.content + extra })
  })
  return out.slice(-8)
}

function askText(text) {
  const q = (typeof text === 'string' ? text : draft.value).trim()
  if (!q || thinking.value) return

  if (!list.value.length) {
    convClassId.value = currentClassId.value
  }
  draft.value = ''
  list.value.push({ role: 'user', content: q })
  request()
}

async function request() {
  thinking.value = true
  scrollBottom()
  try {
    const res = await askAidi({
      classId: currentClassId.value,
      messages: history()
    })
    const m = {
      role: 'assistant',
      content: (res && res.reply) || '我没太理解，换个问法再试试？'
    }
    /* 后端可能带回一张待确认的动作卡。参数原样存着，执行时原样发回去 */
    if (res && res.action) {
      m.action = res.action
    }
    list.value.push(m)
  } catch (e) {
    /* 失败也占一条气泡：直接静默的话，老师会以为发送没生效，又点一次 */
    list.value.push({
      role: 'assistant',
      content: (e && e.message) || '没连上乐迪',
      failed: true
    })
  } finally {
    thinking.value = false
    scrollBottom()
  }
}

/** 重试：把这条失败的气泡删掉再问一次。历史里本来就不含失败消息，最后一条正好是刚才那句 */
async function retry(m) {
  if (thinking.value) return
  const i = list.value.indexOf(m)
  if (i < 0) return
  list.value.splice(i, 1)
  await request()
}

/**
 * 点确认。这一步不经过模型，就是一次确定的写操作。
 * 前端不做任何"能不能做"的判断 —— 后端会重新校验归属，被拒就显示原因。
 */
async function runAction(m) {
  if (m.busy || m.acted) return
  m.busy = true
  m.actError = ''
  try {
    const res = await confirmAidiAction({
      classId: currentClassId.value,
      action: { type: m.action.type, params: m.action.params }
    })
    m.acted = true
    m.doneText = (res && res.reply) || '已完成'
  } catch (e) {
    /* 失败不改 acted：卡还留着，老师可以再点一次 */
    m.actError = (e && e.message) || '执行失败，请再点一次'
  } finally {
    m.busy = false
    scrollBottom()
  }
}

/** 点取消。纯前端标记，不发请求 —— 没有落库就没有要撤销的东西 */
function cancelAction(m) {
  if (m.busy || m.acted) return
  m.acted = true
  m.canceled = true
}
</script>

<style lang="scss" scoped>
.page {
  height: 100vh;
  background: #F7EFE4;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.msgs {
  flex: 1;
  overflow: hidden;
  padding: 0 24rpx;
  box-sizing: border-box;
}

.pad {
  height: 24rpx;
}

/* ---------- 顶部细栏（有对话才出现）---------- */
.head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 32rpx 4rpx;
}

.head-class {
  font-size: 22rpx;
  color: #A79C8E;
}

.head-new {
  font-size: 22rpx;
  color: #C5342B;
  padding: 6rpx 4rpx 6rpx 24rpx;
}

/* ---------- 空状态 ---------- */
.intro {
  padding: 100rpx 16rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.intro-name {
  margin-top: 26rpx;
  font-size: 38rpx;
  font-weight: 600;
  color: #2B2622;
  letter-spacing: 2rpx;
}

.intro-tip {
  margin-top: 8rpx;
  font-size: 25rpx;
  color: #9A9186;
}

.chips {
  margin-top: 48rpx;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.chip {
  margin-bottom: 18rpx;
  padding: 20rpx 32rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: #4A423B;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.06);
}

.chip-press {
  background: #FDF6EE;
}

.intro-note {
  margin-top: 26rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

/* ---------- 机器人头像（和 tabBar 图标同一套比例）---------- */
.robot {
  position: relative;
  flex-shrink: 0;
}

.robot.big {
  width: 160rpx;
  height: 160rpx;
}

.robot.small {
  width: 64rpx;
  height: 64rpx;
  margin-right: 14rpx;
}

/* 大号 */
.robot.big .antenna-dot {
  position: absolute;
  left: 72rpx;
  top: 8rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #C5342B;
}

.robot.big .antenna-line {
  position: absolute;
  left: 77rpx;
  top: 22rpx;
  width: 6rpx;
  height: 26rpx;
  border-radius: 3rpx;
  background: #C5342B;
}

.robot.big .rd-head {
  position: absolute;
  left: 14rpx;
  top: 44rpx;
  width: 132rpx;
  height: 96rpx;
  border-radius: 30rpx;
  background: #C5342B;
}

.robot.big .rd-eye {
  position: absolute;
  top: 32rpx;
  width: 22rpx;
  height: 22rpx;
  border-radius: 8rpx;
  background: #FFF7F1;
}

.robot.big .rd-eye.left {
  left: 32rpx;
}

.robot.big .rd-eye.right {
  right: 32rpx;
}

.robot.big .rd-mouth {
  position: absolute;
  left: 53rpx;
  bottom: 24rpx;
  width: 26rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background: #FFF7F1;
}

/* 小号：同样的形状按 0.4 缩，位置全部手算过，别随手改 */
.robot.small .antenna-dot {
  position: absolute;
  left: 29rpx;
  top: 3rpx;
  width: 7rpx;
  height: 7rpx;
  border-radius: 50%;
  background: #C5342B;
}

.robot.small .antenna-line {
  position: absolute;
  left: 31rpx;
  top: 9rpx;
  width: 3rpx;
  height: 11rpx;
  background: #C5342B;
}

.robot.small .rd-head {
  position: absolute;
  left: 6rpx;
  top: 18rpx;
  width: 53rpx;
  height: 39rpx;
  border-radius: 12rpx;
  background: #C5342B;
}

.robot.small .rd-eye {
  position: absolute;
  top: 13rpx;
  width: 9rpx;
  height: 9rpx;
  border-radius: 3rpx;
  background: #FFF7F1;
}

.robot.small .rd-eye.left {
  left: 13rpx;
}

.robot.small .rd-eye.right {
  right: 13rpx;
}

.robot.small .rd-mouth {
  position: absolute;
  left: 21rpx;
  bottom: 10rpx;
  width: 11rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: #FFF7F1;
}

/* ---------- 气泡 ---------- */
.row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 26rpx;
}

.row.mine {
  justify-content: flex-end;
}

.bubble {
  max-width: 76%;
  padding: 22rpx 26rpx;
  background: #FFFFFF;
  border-radius: 24rpx 24rpx 24rpx 6rpx;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.05);
}

.bubble.self {
  background: #C5342B;
  border-radius: 24rpx 24rpx 6rpx 24rpx;
  box-shadow: 0 4rpx 14rpx rgba(197, 52, 43, 0.18);
}

.bubble.bad {
  background: #FDF3F2;
  border: 1rpx solid #F3D6D3;
}

.txt {
  font-size: 28rpx;
  line-height: 1.62;
  color: #2B2622;
  /* 模型会返回换行，不加这两行会被压成一整段 */
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble.self .txt {
  color: #FFFFFF;
}

.bubble.bad .txt {
  color: #A33A32;
}

.retry {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #C5342B;
  font-weight: 500;
}

/* ---------- 确认卡 ---------- */
/* 左边空出 78rpx = 头像 64 + 间距 14，和上面那条气泡的左边缘对齐，
   让老师一眼看出这张卡是"乐迪提的"，不是自己发的 */
.act {
  margin: -10rpx 0 30rpx 78rpx;
  padding: 22rpx 24rpx 20rpx;
  background: #FFFFFF;
  border: 2rpx solid #EADFD0;
  border-radius: 20rpx;
  box-shadow: 0 6rpx 18rpx rgba(160, 120, 80, 0.07);
}

/* 不可撤销的动作描一圈红边：卡片本身不吓人，但要让手指停一下 */
.act.warn {
  background: #FFFDFC;
  border-color: #F0CFCB;
}

.act-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.act-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #2B2622;
}

.act-flag {
  flex-shrink: 0;
  margin-left: 12rpx;
  padding: 2rpx 12rpx;
  border-radius: 6rpx;
  background: #FBEDEB;
  font-size: 20rpx;
  color: #C5342B;
}

.act-summary {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: #5A5148;
  white-space: pre-wrap;
  word-break: break-word;
}

.act-btns {
  display: flex;
  align-items: center;
  margin-top: 22rpx;
}

.act-btn {
  flex: 1;
  height: 68rpx;
  line-height: 68rpx;
  text-align: center;
  border-radius: 12rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 26rpx;
}

.act-btn.ghost {
  flex: 0 0 150rpx;
  margin-right: 16rpx;
  background: #F5EDE3;
  color: #6B6259;
}

.act-btn.warn {
  background: #A82B22;
}

/* 执行中：颜色变浅就够，不要改成 loading 转圈——
   这一步最多几百毫秒，动画反而显得卡 */
.act-btn.busy {
  background: #D9A9A5;
}

.act-press {
  opacity: 0.85;
}

.act-done {
  display: block;
  margin-top: 18rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: #6E8A5E;
  white-space: pre-wrap;
  word-break: break-word;
}

.act-err {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: #C5342B;
}

/* ---------- 思考中 ---------- */
.bubble.typing {
  display: flex;
  align-items: center;
  padding: 26rpx 28rpx;
}

.dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #C9BEB1;
  margin-right: 8rpx;
  animation: blink 1.2s infinite ease-in-out;
}

.dot:last-child {
  margin-right: 0;
}

.d2 {
  animation-delay: 0.2s;
}

.d3 {
  animation-delay: 0.4s;
}

@keyframes blink {
  0%,
  60%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  30% {
    opacity: 1;
    transform: translateY(-4rpx);
  }
}

/* ---------- 输入栏 ---------- */
.bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FBF6EE;
  border-top: 2rpx solid #F0E6D8;
}

.input {
  flex: 1;
  height: 76rpx;
  padding: 0 26rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
  font-size: 27rpx;
  color: #2B2622;
}

.send {
  margin-left: 16rpx;
  padding: 0 30rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: 999rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 27rpx;
  flex-shrink: 0;
}

.send.off {
  background: #E3D5C8;
  color: #FFFFFF;
}

.send-press {
  opacity: 0.85;
}
</style>
