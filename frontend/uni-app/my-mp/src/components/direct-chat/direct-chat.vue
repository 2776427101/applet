<template>
  <!--
    师生私信共用的聊天界面。

    老师和学生两个页面结构一模一样，差别只有三处：谁是"我"、气泡靠哪边、调哪个接口。
    所以这里只负责画，数据怎么来由页面决定（页面监听 send / refresh 事件）。

    为什么不把接口调用塞进组件：组件一旦知道自己调哪个接口，
    老师页和学生页就得各传一堆"用什么函数"，比现在这样更绕。
  -->
  <view class="dc">
    <!-- 标题一般由导航栏承担（改成对方姓名），这里只留副标题那一行信息，
         比如「学号 202411753」。两处都写名字会上下重复两遍 -->
    <view v-if="title || subtitle" class="dc-head" :class="{ 'dc-head-meta': !title }">
      <text v-if="title" class="dc-title">{{ title }}</text>
      <text v-if="subtitle" class="dc-sub">{{ subtitle }}</text>
    </view>

    <scroll-view
      class="dc-msgs"
      scroll-y
      :scroll-into-view="anchor"
      :scroll-with-animation="true"
      @click="hideKeyboard"
    >
      <!-- 加载中：只在第一次进来、还没有任何消息时显示。
           已经有消息就别整块盖住，否则刷新时旧消息会闪一下消失 -->
      <view v-if="loading && !messages.length" class="dc-tip">
        <text class="dc-tip-text">加载中…</text>
      </view>

      <!-- 空对话的说明。不传 emptyText 就什么都不显示（私信页不需要：
           没聊过就是一个空输入框，本来就是 IM 的正常形态） -->
      <view v-else-if="!loading && !messages.length && emptyText" class="dc-tip">
        <text class="dc-tip-text">{{ emptyText }}</text>
      </view>

      <view
        v-for="(m, i) in messages"
        :key="m.id || i"
        class="dc-row"
        :class="mine(m) ? 'mine' : 'other'"
      >
        <!-- 群聊才显示名字：一对一里对方是谁已经写在导航栏上了，再标一遍是废话。
             自己的消息也不标，气泡靠右就说明是谁说的 -->
        <text v-if="group && !mine(m) && m.senderName" class="dc-name">{{ m.senderName }}</text>

        <view class="dc-bubble" :class="{ self: mine(m) }">
          <text class="dc-text">{{ m.content }}</text>
        </view>
        <text v-if="!group && mine(m) && i === lastIndex" class="dc-state">
          {{ m.read ? '已读' : '未读' }}
        </text>
      </view>

      <view id="dc-bottom" class="dc-pad"></view>
    </scroll-view>

    <view class="dc-bar">
      <input
        class="dc-input"
        v-model="draft"
        type="text"
        :disabled="sending"
        confirm-type="send"
        :cursor-spacing="24"
        :placeholder="placeholder"
        placeholder-style="color:#B4A99B;font-size:27rpx"
        @confirm="submit"
      />
      <view
        class="dc-send"
        :class="{ off: !canSend }"
        hover-class="dc-send-pressed"
        @click="submit"
      >
        {{ sending ? '…' : '发送' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

const props = defineProps({
  /** 对话标题：老师页是学生名，学生页是班主任名 */
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  /** 消息数组，[{ id, senderRole, content, time, read }] */
  messages: { type: Array, default: () => [] },
  /** "我"是哪种角色：1=老师 2=学生 */
  mineRole: { type: Number, default: 1 },
  loading: { type: Boolean, default: false },
  sending: { type: Boolean, default: false },
  /* 输入框提示语。原来模板里绑了 :placeholder="placeholder" 但 props 里没声明，
     编译后是 undefined —— 输入框里一个字提示都没有，看着像不能打字 */
  placeholder: { type: String, default: '说点什么…' },
  /**
   * 群聊模式（班级群）。
   *
   * 三处不同：别人的消息上面标名字、不显示已读未读、"是不是我发的"看 m.mine。
   * 最后一条最容易踩：群里一条消息的 senderRole 也是 2，用 mineRole 判断的话
   * 别人发的消息会全跑到右边当成自己说的。
   *
   * 默认 false，老师和学生的私信页行为完全不变。
   */
  group: { type: Boolean, default: false },
  /** 一条消息都没有时显示的话。不传就不显示 */
  emptyText: { type: String, default: '' }
})

const emit = defineEmits(['send', 'refresh'])

const draft = ref('')
const anchor = ref('')

const canSend = computed(() => !!draft.value.trim() && !props.sending)

/** 最后一条的下标：只有最后一条自己的消息才显示已读/未读 */
const lastIndex = computed(() => props.messages.length - 1)

/**
 * 是不是我发的。
 *
 * 私信用 senderRole 比：一条消息的"我"就是登录角色。
 * 群聊不能这么比 —— 群里别的同学 senderRole 也是 2，会全被当成我发的。
 * 所以群聊用后端算好的 m.mine。
 */
function mine(m) {
  if (!m) return false
  return props.group ? !!m.mine : m.senderRole === props.mineRole
}

function hideKeyboard() {
  uni.hideKeyboard()
}

function scrollBottom() {
  nextTick(() => {
    anchor.value = 'dc-bottom'
  })
}

/* 消息一变就滚到底：发出去、收到、刷新都要滚。
   不滚的话新消息在屏幕外面，用户以为没发出去，又点一次 */
watch(
  () => props.messages.length,
  () => scrollBottom()
)

function submit(e) {
  /* 优先用事件里的值。键盘上的「发送」键（confirm）有可能比最后一次
     bindinput 更早到达，这时候 draft 还是上一帧的内容 ——
     打字快一点就会出现「点了发送没反应」。按钮点击没有 detail.value，
     自然回落到 draft */
  const fromEvent = e && e.detail && typeof e.detail.value === 'string' ? e.detail.value : ''
  const text = (fromEvent || draft.value).trim()
  if (!text || props.sending) return
  emit('send', text)
  draft.value = ''
}

defineExpose({ scrollBottom })
</script>

<style lang="scss" scoped>
.dc {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #F7EFE4;
  box-sizing: border-box;
}

.dc-head {
  flex-shrink: 0;
  padding: 20rpx 32rpx 16rpx;
  background: #FBF6EE;
  border-bottom: 2rpx solid #F0E6D8;
  display: flex;
  flex-direction: column;
}

/* 只有副标题时（导航栏已经写了对方名字），把这条压成一窄行，
   否则会凭空多出一大块空白 */
.dc-head-meta {
  padding: 12rpx 32rpx;
  align-items: center;
}

.dc-head-meta .dc-sub {
  margin-top: 0;
}

.dc-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
}

.dc-sub {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.dc-msgs {
  flex: 1;
  overflow: hidden;
  padding: 20rpx 24rpx 0;
  box-sizing: border-box;
}

.dc-pad {
  height: 24rpx;
}

.dc-tip {
  padding: 80rpx 0;
  display: flex;
  justify-content: center;
}

.dc-tip-text {
  font-size: 24rpx;
  color: #B4A99B;
}

.dc-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 22rpx;
}

.dc-row.mine {
  align-items: flex-end;
}

/* 群聊里别人的名字：小一号、灰一点，贴着气泡上方。
   做成和气泡同一行会把气泡挤窄，长消息会变成一条竖条 */
.dc-name {
  margin: 0 0 8rpx 8rpx;
  font-size: 21rpx;
  color: #9A9186;
}

.dc-bubble {
  max-width: 74%;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  border-radius: 22rpx 22rpx 22rpx 6rpx;
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.05);
}

.dc-bubble.self {
  background: #C5342B;
  border-radius: 22rpx 22rpx 6rpx 22rpx;
  box-shadow: 0 4rpx 14rpx rgba(197, 52, 43, 0.16);
}

.dc-text {
  font-size: 28rpx;
  line-height: 1.6;
  color: #2B2622;
  white-space: pre-wrap;
  word-break: break-word;
}

.dc-bubble.self .dc-text {
  color: #FFFFFF;
}

.dc-state {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #B4A99B;
}

.dc-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: #FBF6EE;
  border-top: 2rpx solid #F0E6D8;
}

.dc-input {
  flex: 1;
  height: 76rpx;
  padding: 0 26rpx;
  background: #FFFFFF;
  border-radius: 999rpx;
  font-size: 27rpx;
  color: #2B2622;
}

.dc-send {
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

.dc-send.off {
  background: #E3D5C8;
}

.dc-send-pressed {
  opacity: 0.85;
}
</style>
