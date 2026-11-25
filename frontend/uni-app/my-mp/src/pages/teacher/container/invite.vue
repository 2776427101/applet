<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="init">重试</view>
    </view>

    <template v-else>
      <!-- 邀请码 -->
      <view class="code-card">
        <text class="code-label">邀请码</text>
        <text class="code-val">{{ invite.inviteCode }}</text>
        <text class="code-state" :class="invite.inviteExpired ? 'warn' : 'ok'">
          {{ stateText }}
        </text>
        <view class="code-actions">
          <view class="btn-ghost" @click="copyCode">复制</view>
          <view v-if="canManage" class="btn-ghost danger" @click="onRegenerate">重新生成</view>
        </view>
      </view>

      <!-- 有效期 -->
      <view class="card">
        <text class="group-title">有效期</text>

        <view class="row">
          <view class="expire-chips">
            <view
              v-for="o in expireOptions"
              :key="String(o.value)"
              class="chip"
              :class="{ on: choice === o.value }"
              @click="choice = o.value"
            >
              {{ o.label }}
            </view>
          </view>
        </view>

        <view v-if="choice === 'custom'" class="row line">
          <text class="label">截止到</text>
          <view class="dt">
            <picker mode="date" :value="customDate || today" @change="onCustomDate">
              <view class="dt-box" :class="{ empty: !customDate }">{{ customDate || '选择日期' }}</view>
            </picker>
            <picker mode="time" :value="customTime" @change="onCustomTime">
              <view class="dt-box">{{ customTime }}</view>
            </picker>
          </view>
        </view>

        <text class="hint">
          改有效期不会换码。所以码过期之后，把时间改到未来，同一个码就能继续用。
        </text>

        <view v-if="canManage" class="save-line">
          <view class="btn-save" :class="{ busy: savingExpire }" @click="onSaveExpire">
            {{ savingExpire ? '保存中…' : '保存有效期' }}
          </view>
        </view>
      </view>

      <!-- 加入密码 -->
      <view class="card">
        <text class="group-title">加入密码</text>

        <view class="row">
          <text class="label">需要密码</text>
          <switch class="switch" :checked="usePassword" color="#C5342B" :disabled="!canManage" @change="onPasswordSwitch" />
        </view>

        <view v-if="usePassword" class="row line">
          <text class="label">新密码</text>
          <input
            class="input"
            v-model="password"
            :password="!pwdVisible"
            type="text"
            maxlength="20"
            :placeholder="invite.hasPassword ? '留空则不修改' : '6-20 位'"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
          <text class="eye" @click="pwdVisible = !pwdVisible">{{ pwdVisible ? '隐藏' : '显示' }}</text>
        </view>

        <text class="hint">
          密码只存哈希，任何接口都不会返回原文，日志里也没有。
          改密码后旧密码立即失效，已经加入的成员不受影响。
        </text>

        <view v-if="canManage" class="save-line">
          <view class="btn-save" :class="{ busy: savingPwd }" @click="onSavePassword">
            {{ savingPwd ? '保存中…' : '保存密码设置' }}
          </view>
        </view>
      </view>

      <view class="note">
        <text class="note-title">重新生成会发生什么</text>
        <text class="note-text">
          旧邀请码立刻失效，已经发出去的码都不能再用（拿到的人会看到"邀请码无效"）。
          已经在群组里的成员不受影响 —— 他们靠成员身份访问，不再用邀请码。
        </text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getContainer, getInvite, updateInviteExpire, setJoinPassword, regenerateInvite } from '@/api/container'

const expireOptions = [
  { value: 7, label: '7 天' },
  { value: 30, label: '30 天' },
  { value: null, label: '永久' },
  { value: 'custom', label: '自定义' }
]

const id = ref(0)
const invite = ref({})
const loading = ref(true)
const loadErr = ref('')

const canManage = ref(false) // 只有群主能改有效期/密码/重新生成

const choice = ref(30)
const customDate = ref('')
const customTime = ref('23:59')
const savingExpire = ref(false)

const usePassword = ref(false)
const password = ref('')
const pwdVisible = ref(false)
const savingPwd = ref(false)

function localToday() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const today = localToday()

const stateText = computed(() => {
  const i = invite.value
  if (i.inviteExpired) return `已过期（${(i.inviteExpireAt || '').slice(0, 10)}）`
  if (!i.inviteExpireAt) return '永久有效'
  return `有效至 ${(i.inviteExpireAt || '').slice(0, 16)}`
})

onLoad((q) => {
  id.value = Number((q && q.id) || 0)
  init()
})

async function init() {
  if (!id.value) {
    loadErr.value = '缺少群组 id'
    loading.value = false
    return
  }
  loading.value = true
  loadErr.value = ''
  try {
    const d = await getContainer(id.value)
    canManage.value = !!(d.permissions && d.permissions.canRegenerateCode)
    await load()
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

async function load() {
  const i = await getInvite(id.value)
  invite.value = i || {}
  usePassword.value = !!i.hasPassword
  password.value = ''
  pwdVisible.value = false
  // 有效期选项回显：永久 → 永久；否则默认高亮 30 天（不猜用户当初选的是几天）
  if (!i.inviteExpireAt) {
    choice.value = null
  } else {
    choice.value = 30
  }
  customDate.value = ''
  customTime.value = '23:59'
}

function copyCode() {
  uni.setClipboardData({
    data: invite.value.inviteCode,
    success: () => uni.showToast({ title: '已复制', icon: 'success' })
  })
}

function onCustomDate(e) {
  customDate.value = e.detail.value
}

function onCustomTime(e) {
  customTime.value = e.detail.value
}

/* 「日期 + 时间」→ 后端要的 yyyy-MM-dd HH:mm:ss */
function customExpireAt() {
  const time = customTime.value || '23:59'
  const t = time.length === 5 ? `${time}:00` : time
  return `${customDate.value} ${t}`
}

async function onSaveExpire() {
  if (savingExpire.value) return

  let body = {}
  if (choice.value === 'custom') {
    if (!customDate.value) {
      uni.showToast({ title: '请选择截止日期', icon: 'none' })
      return
    }
    const at = customExpireAt()
    // iOS 上 new Date('2026-12-31 23:59:00') 是 Invalid Date，必须换斜杠
    if (new Date(at.replace(/-/g, '/')) <= new Date()) {
      uni.showToast({ title: '截止时间要晚于现在', icon: 'none' })
      return
    }
    body = { expireAt: at }
  } else if (choice.value === null) {
    body = {} // 永久
  } else {
    body = { days: choice.value }
  }

  savingExpire.value = true
  try {
    await updateInviteExpire(id.value, body)
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } catch (e) {
    // request.js 已 toast
  } finally {
    savingExpire.value = false
  }
}

function onPasswordSwitch(e) {
  // uni-app 的 switch change 事件是 e.detail.value，不是 e.detail.checked
  usePassword.value = !!e.detail.value
  if (!usePassword.value) {
    password.value = ''
    pwdVisible.value = false
  }
}

async function onSavePassword() {
  if (savingPwd.value) return

  let pwd = null
  if (usePassword.value) {
    pwd = (password.value || '').trim()
    // 原本就有密码且这次没填 → 不改密码，只保持开启
    if (!pwd && invite.value.hasPassword) {
      uni.showToast({ title: '密码未修改', icon: 'none' })
      return
    }
    if (pwd.length < 6 || pwd.length > 20) {
      uni.showToast({ title: '密码需 6-20 位', icon: 'none' })
      return
    }
  }

  savingPwd.value = true
  try {
    await setJoinPassword(id.value, pwd)
    password.value = ''
    uni.showToast({ title: usePassword.value ? '已设置密码' : '已关闭密码', icon: 'success' })
    await load()
  } catch (e) {
    // request.js 已 toast
  } finally {
    savingPwd.value = false
  }
}

function onRegenerate() {
  uni.showModal({
    title: '重新生成邀请码',
    content: '旧码会立刻失效，已经发给学生的码将无法加入。已加入的成员不受影响。',
    confirmText: '重新生成',
    confirmColor: '#C5342B',
    success: async (r) => {
      if (!r.confirm) return
      try {
        const res = await regenerateInvite(id.value)
        invite.value = { ...invite.value, inviteCode: res.inviteCode, inviteExpired: false }
        uni.showToast({ title: '已生成新码', icon: 'success' })
      } catch (e) {
        // request.js 已 toast
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 60rpx;
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

/* ---------- 邀请码卡 ---------- */
.code-card {
  background: linear-gradient(135deg, #E8594F, #C5342B);
  border-radius: 32rpx;
  padding: 34rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 14rpx 32rpx rgba(197, 52, 43, 0.26);
}

.code-label {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.8);
}

.code-val {
  margin-top: 12rpx;
  font-family: Menlo, Consolas, monospace;
  font-size: 62rpx;
  font-weight: 600;
  letter-spacing: 8rpx;
  color: #FFFFFF;
  /* 10 位码 + 字距在小屏上会挤，允许缩小一点 */
  line-height: 1.2;
}

.code-state {
  margin-top: 10rpx;
  font-size: 21rpx;
}

.code-state.ok {
  color: rgba(255, 255, 255, 0.86);
}

.code-state.warn {
  color: #FFE1A8;
}

.code-actions {
  display: flex;
  margin-top: 26rpx;
}

.btn-ghost {
  padding: 12rpx 40rpx;
  margin: 0 8rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.18);
  color: #FFFFFF;
  font-size: 25rpx;
}

.btn-ghost.danger {
  background: rgba(0, 0, 0, 0.14);
}

/* ---------- 卡片 ---------- */
.card {
  margin-top: 18rpx;
  padding: 8rpx 28rpx 4rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.group-title {
  display: block;
  padding: 26rpx 0 4rpx;
  font-size: 23rpx;
  color: #9A9186;
  letter-spacing: 1rpx;
}

.row {
  display: flex;
  align-items: center;
  min-height: 100rpx;
}

.row.line {
  border-top: 1rpx solid #F5EDE2;
}

.label {
  width: 150rpx;
  font-size: 27rpx;
  color: #2B2622;
  flex-shrink: 0;
}

.input {
  flex: 1;
  font-size: 27rpx;
  color: #2B2622;
  height: 100rpx;
  line-height: 100rpx;
}

.expire-chips {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.chip {
  padding: 10rpx 22rpx;
  border-radius: 999rpx;
  background: #F7F1E8;
  font-size: 23rpx;
  color: #7A6F63;
}

.chip.on {
  background: #FDEDEB;
  color: #C5342B;
  font-weight: 500;
}

.picker-wrap {
  flex: 1;
}

.picker-value {
  font-size: 27rpx;
  color: #2B2622;
  line-height: 100rpx;
  text-align: right;
}

.picker-value.empty {
  color: #B4A99B;
}

/* 日期 + 时间并排，两个方块都够大好点 */
.dt {
  flex: 1;
  display: flex;
  justify-content: flex-end;
  gap: 12rpx;
}

.dt-box {
  min-width: 190rpx;
  padding: 16rpx 18rpx;
  border-radius: 14rpx;
  background: #F7F1E8;
  font-size: 25rpx;
  color: #2B2622;
  text-align: center;
}

.dt-box.empty {
  color: #B4A99B;
}

.eye {
  padding-left: 20rpx;
  font-size: 23rpx;
  color: #C5342B;
  flex-shrink: 0;
}

.switch {
  transform: scale(0.86);
  transform-origin: left center;
}

.hint {
  display: block;
  padding: 0 0 22rpx;
  font-size: 21rpx;
  color: #B4A99B;
  line-height: 1.6;
}

.save-line {
  padding: 0 0 26rpx;
}

.btn-save {
  height: 84rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  color: #C5342B;
  font-size: 27rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-save.busy {
  opacity: 0.6;
}

.note {
  margin-top: 26rpx;
  padding: 24rpx 26rpx;
  background: #F7EDD2;
  border-radius: 20rpx;
}

.note-title {
  display: block;
  font-size: 23rpx;
  color: #8A6A3A;
  font-weight: 500;
  margin-bottom: 8rpx;
}

.note-text {
  font-size: 21rpx;
  color: #9A8158;
  line-height: 1.7;
}
</style>
