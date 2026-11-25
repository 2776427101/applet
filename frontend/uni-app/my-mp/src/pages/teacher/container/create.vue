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
      <!-- 这是什么：第一次用的老师/学生都得一眼看懂，别指望他们从"容器"两个字里猜 -->
      <view v-if="!isEdit" class="intro">
        <text class="intro-title">一个群组 = 一门课的一个班</text>
        <text class="intro-text">
          建好后把邀请码发给学生，他们输入就能进群。成员、禁言、精华都在群组里管理。
        </text>
      </view>

      <!-- 基本信息 -->
      <view class="card">
        <view class="row">
          <text class="label">名称<text class="req">*</text></text>
          <input
            class="input"
            v-model="name"
            type="text"
            maxlength="32"
            placeholder="如 高等数学（上）"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <text class="hint">
          学生看到的就是这个名字，随意起 —— 高中科目、大学课程、考研班都行，
          比如「高一物理」「高等数学（上）」「考研英语冲刺」。
        </text>

        <!-- 关联班级：做成整行可选块。原来是右对齐的一行文字，
             看起来像只读信息，老师经常点不到 -->
        <view class="block-title">
          <text class="block-label">关联班级</text>
          <text class="block-optional">可不选</text>
        </view>
        <view class="options">
          <view
            v-for="o in classOptions"
            :key="String(o.id)"
            class="option"
            :class="{ on: form.classId === o.id }"
            hover-class="option-pressed"
            @click="pickClass(o.id)"
          >
            <text class="option-text">{{ o.name }}</text>
            <text v-if="form.classId === o.id" class="option-check">✓</text>
          </view>
        </view>
        <text class="hint">
          关联只是打个标签，方便你按班级筛选；不关联也能正常用（跨班选修、大学公共课都这样）。
        </text>
      </view>

      <!-- 邀请码设置：只在新建时出现。改有效期/密码在「邀请码」页，因为那是独立操作 -->
      <view v-if="!isEdit" class="card">
        <text class="group-title">邀请码</text>

        <view class="row">
          <text class="label">有效期</text>
          <view class="expire-chips">
            <view
              v-for="o in expireOptions"
              :key="String(o.value)"
              class="chip"
              :class="{ on: expireChoice === o.value }"
              @click="expireChoice = o.value"
            >
              {{ o.label }}
            </view>
          </view>
        </view>

        <!-- 自定义：日期 + 时间两个 picker，精确到分钟 -->
        <view v-if="expireChoice === 'custom'" class="row line">
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
          过期后这个码就加不进来了，但已经加入的成员不受影响。之后在「邀请码」页把时间改到未来，
          同一个码可以继续用。
        </text>

        <view class="row line">
          <text class="label">加入密码</text>
          <view class="switch-wrap">
            <switch :checked="usePassword" color="#C5342B" @change="onPasswordSwitch" />
          </view>
        </view>

        <!-- 开关打开后必须能填密码。之前这里因为 e.detail.checked 取错字段，
             开关点了不生效，输入框永远不出现 —— 老师以为"没有设置密码的地方" -->
        <view v-if="usePassword" class="row line">
          <text class="label">密码</text>
          <input
            class="input"
            v-model="password"
            :password="!pwdVisible"
            type="text"
            maxlength="20"
            placeholder="6-20 位，加入时要输入"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
          <text class="eye" @click="pwdVisible = !pwdVisible">{{ pwdVisible ? '隐藏' : '显示' }}</text>
        </view>
        <text class="hint">
          密码只存哈希，后端和日志里都看不到原文；页面上也不会记住，退出即清空。
        </text>
      </view>

      <view v-if="isEdit" class="tip">
        邀请码有效期、加入密码、重新生成邀请码，请到群组的「邀请码」页修改。
      </view>

      <view class="save" :class="{ busy: saving }" @click="onSubmit">
        {{ saving ? '保存中…' : (isEdit ? '保存修改' : '创建群组') }}
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createContainer, getContainer, updateContainer } from '@/api/container'
import { listClasses } from '@/api/class'

/* 有效期预设。'custom' 走下面的日期+时间选择器 */
const expireOptions = [
  { value: 7, label: '7 天' },
  { value: 30, label: '30 天' },
  { value: null, label: '永久' },
  { value: 'custom', label: '自定义' }
]

const loading = ref(false)
const saving = ref(false)
const loadErr = ref('')

const id = ref(null)
const isEdit = computed(() => !!id.value)

const form = reactive({
  classId: null
})

/* 名称与学科已合并：这一个值同时写进 subjectName 和 name */
const name = ref('')

const expireChoice = ref(30)
const customDate = ref('')
const customTime = ref('23:59')
const usePassword = ref(false)
const password = ref('')
const pwdVisible = ref(false)

/* 行政班下拉：第一项永远是「不关联」 */
const classOptions = ref([{ id: null, name: '不关联班级' }])

function pad(n) {
  return String(n).padStart(2, '0')
}

function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
const today = localToday()

onLoad((q) => {
  if (q && q.id) {
    id.value = Number(q.id)
  }
  init()
})

async function init() {
  loading.value = true
  loadErr.value = ''
  try {
    // 多班级下这里必须给出**全部**班级让老师选，不能只放第一个：
    // 否则第二门课的群永远挂不到第二个班上
    try {
      const list = await listClasses({ silent: true })
      classOptions.value = [{ id: null, name: '不关联班级' }].concat(
        (list || []).map((c) => ({ id: c.id, name: c.name }))
      )
    } catch (e) {
      // 没班是正常状态，不是错误
    }

    if (isEdit.value) {
      const d = await getContainer(id.value)
      if (!d.permissions || !d.permissions.canEdit) {
        loadErr.value = '只有群主可以修改群组信息'
        return
      }
      // 老数据里 name 可能带「· 班级」后缀，取信息更全的那个回填
      name.value = d.name || d.subjectName || ''
      form.classId = d.classId || null
    }
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

function pickClass(v) {
  form.classId = v
}

function onCustomDate(e) {
  customDate.value = e.detail.value
}

function onCustomTime(e) {
  customTime.value = e.detail.value
}

/* uni-app 的 switch change 事件是 e.detail.value，不是 e.detail.checked。
   写错的话拿到的永远是 undefined，开关看起来点了没反应 */
function onPasswordSwitch(e) {
  usePassword.value = !!e.detail.value
  if (!usePassword.value) {
    password.value = ''
    pwdVisible.value = false
  }
}

/* 把「日期 + 时间」拼成后端要的 yyyy-MM-dd HH:mm:ss */
function customExpireAt() {
  const time = customTime.value || '23:59'
  // 补秒，后端 @JsonFormat 是 HH:mm:ss
  const t = time.length === 5 ? `${time}:00` : time
  return `${customDate.value} ${t}`
}

async function onSubmit() {
  if (saving.value) return

  const nm = (name.value || '').trim()
  if (!nm) {
    uni.showToast({ title: '请填写群组名称', icon: 'none' })
    return
  }

  if (isEdit.value) {
    saving.value = true
    try {
      await updateContainer(id.value, {
        // 合并后两个字段同值；classId 传 null = 解除关联
        subjectName: nm,
        name: nm,
        classId: form.classId
      })
      uni.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 600)
    } catch (e) {
      // request.js 已经 toast 过具体原因
    } finally {
      saving.value = false
    }
    return
  }

  const data = { subjectName: nm, name: nm }
  if (form.classId) data.classId = form.classId

  // 有效期：自定义走精确时间，其余走天数 / 永久
  if (expireChoice.value === 'custom') {
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
    data.inviteExpireAt = at
  } else if (expireChoice.value !== null) {
    data.inviteExpireDays = expireChoice.value
  }

  if (usePassword.value) {
    const pwd = (password.value || '').trim()
    if (pwd.length < 6 || pwd.length > 20) {
      uni.showToast({ title: '密码需 6-20 位', icon: 'none' })
      return
    }
    data.joinPassword = pwd
  }

  saving.value = true
  try {
    const res = await createContainer(data)
    // 密码用完立刻清掉，不在内存/页面里多留一秒
    password.value = ''
    uni.showToast({ title: '已创建', icon: 'success' })
    // redirectTo 而不是 navigateTo：建完之后按返回不该回到「新建」表单。
    // 落到聊天页而不是详情页：建群之后老师下一件事就是发消息/发邀请码，
    // 详情是"管理"入口，从聊天页右上角进
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/teacher/container/chat?id=${res.id}` })
    }, 600)
  } catch (e) {
    // request.js 已 toast
  } finally {
    saving.value = false
  }
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

/* ---------- 顶部说明 ---------- */
.intro {
  padding: 24rpx 26rpx;
  margin-bottom: 18rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
}

.intro-title {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #2B2622;
}

.intro-text {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #9A9186;
  line-height: 1.7;
}

/* ---------- 卡片 ---------- */
.card {
  margin-bottom: 18rpx;
  padding: 8rpx 28rpx 4rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.row {
  display: flex;
  align-items: center;
  min-height: 100rpx;
}

.row.line {
  border-top: 1rpx solid #F5EDE2;
}

.group-title {
  display: block;
  padding: 26rpx 0 4rpx;
  font-size: 23rpx;
  color: #9A9186;
  letter-spacing: 1rpx;
}

.label {
  width: 150rpx;
  font-size: 27rpx;
  color: #2B2622;
  flex-shrink: 0;
}

.req {
  color: #C5342B;
  font-size: 26rpx;
  margin-left: 4rpx;
}

.input {
  flex: 1;
  font-size: 27rpx;
  color: #2B2622;
  height: 100rpx;
  line-height: 100rpx;
}

.eye {
  padding-left: 20rpx;
  font-size: 23rpx;
  color: #C5342B;
  flex-shrink: 0;
}

.hint {
  display: block;
  padding: 0 0 22rpx 150rpx;
  font-size: 21rpx;
  color: #B4A99B;
  line-height: 1.6;
}

/* ---------- 关联班级：整行可选块 ---------- */
.block-title {
  display: flex;
  align-items: baseline;
  padding: 26rpx 0 16rpx;
  border-top: 1rpx solid #F5EDE2;
}

.block-label {
  font-size: 27rpx;
  color: #2B2622;
}

.block-optional {
  margin-left: 12rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

/* 92rpx 高 + 边框 + 勾选态：一眼就知道能点，不用瞄着一行小字去戳 */
.option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 92rpx;
  padding: 0 26rpx;
  border: 2rpx solid #EFE5D8;
  border-radius: 18rpx;
  background: #FDFAF6;
}

.option-pressed {
  background: #F7F1E8;
}

.option.on {
  border-color: #E9A9A3;
  background: #FDEDEB;
}

.option-text {
  font-size: 27rpx;
  color: #7A6F63;
}

.option.on .option-text {
  color: #C5342B;
  font-weight: 500;
}

.option-check {
  font-size: 28rpx;
  color: #C5342B;
  font-weight: 600;
}

/* ---------- 有效期 ---------- */
.expire-chips {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  justify-content: flex-end;
}

.chip {
  padding: 12rpx 24rpx;
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

/* 日期 + 时间并排，两个都是 40% 宽的方块，点哪儿都行 */
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

.switch-wrap {
  flex: 1;
  display: flex;
  justify-content: flex-end;
}

.tip {
  margin-top: 4rpx;
  padding: 22rpx 26rpx;
  background: #F7EDD2;
  border-radius: 20rpx;
  font-size: 22rpx;
  color: #8A6A3A;
  line-height: 1.7;
}

.save {
  margin-top: 44rpx;
  height: 96rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 28rpx rgba(197, 52, 43, 0.28);
}

.save.busy {
  opacity: 0.6;
}
</style>
