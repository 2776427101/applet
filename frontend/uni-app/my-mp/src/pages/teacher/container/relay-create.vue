<template>
  <view class="page">
    <!-- 上半：内容。下半：规则。分开是因为这两块的修改频率完全不同 ——
         主题写完就不动了，人数上限和截止时间经常要临时改 -->
    <view class="card">
      <view class="row">
        <text class="label">主题<text class="req">*</text></text>
        <input
          class="input"
          v-model="title"
          type="text"
          maxlength="30"
          placeholder="如 春游报名"
          placeholder-style="color:#B4A99B;font-size:26rpx"
        />
      </view>
      <view class="row line tall">
        <text class="label">说明</text>
        <textarea
          class="area"
          v-model="description"
          maxlength="500"
          :auto-height="true"
          placeholder="时间、地点（可不填）"
          placeholder-style="color:#B4A99B;font-size:26rpx"
        />
      </view>
    </view>

    <view class="card">
      <view class="block-title">
        <text class="block-label">填写格式</text>
      </view>

      <view class="formats">
        <view
          v-for="f in formats"
          :key="f.v"
          class="format"
          :class="{ on: entryFormat === f.v }"
          hover-class="format-pressed"
          @click="entryFormat = f.v"
        >
          {{ f.name }}
        </view>
      </view>

      <!-- 一行说明，随选中项变。比每个格子下面挂一句小字省一半高度 -->
      <text class="fmt-note">{{ fmtNote }}</text>

      <!-- 数字格式的范围：不填就是任意整数 -->
      <view v-if="entryFormat === 2" class="sub">
        <text class="sub-label">数值范围</text>
        <view class="range">
          <input class="range-input" v-model="formatMin" type="number" placeholder="最小" placeholder-style="color:#B4A99B" />
          <text class="range-sep">—</text>
          <input class="range-input" v-model="formatMax" type="number" placeholder="最大" placeholder-style="color:#B4A99B" />
        </view>
      </view>

      <!-- 单选的选项：至少 2 个，最多 10 个 -->
      <view v-if="entryFormat === 5" class="sub">
        <text class="sub-label">选项</text>
        <view v-for="(o, i) in options" :key="i" class="opt-row">
          <input
            class="opt-input"
            v-model="options[i]"
            type="text"
            maxlength="20"
            :placeholder="'选项 ' + (i + 1)"
            placeholder-style="color:#B4A99B;font-size:25rpx"
          />
          <text v-if="options.length > 2" class="opt-del" @click="removeOption(i)">删除</text>
        </view>
        <view v-if="options.length < 10" class="opt-add" hover-class="opt-add-press" @click="addOption">
          ＋ 添加选项
        </view>
      </view>
    </view>

    <view class="card">
      <view class="row">
        <text class="label">人数上限</text>
        <view class="row-right">
          <input
            v-if="limitOn"
            class="num-input"
            v-model="maxCount"
            type="number"
            placeholder="30"
            placeholder-style="color:#B4A99B"
          />
          <text v-if="limitOn" class="unit">人</text>
          <switch :checked="limitOn" color="#C5342B" @change="onLimitSwitch" />
        </view>
      </view>

      <view class="row line">
        <text class="label">截止时间</text>
        <view class="row-right">
          <switch :checked="deadlineOn" color="#C5342B" @change="onDeadlineSwitch" />
        </view>
      </view>
      <view v-if="deadlineOn" class="row line">
        <text class="label"></text>
        <view class="dt">
          <picker mode="date" :value="deadlineDate || today" @change="onDate">
            <view class="dt-box" :class="{ empty: !deadlineDate }">{{ deadlineDate || '选择日期' }}</view>
          </picker>
          <picker mode="time" :value="deadlineTime" @change="onTime">
            <view class="dt-box">{{ deadlineTime }}</view>
          </picker>
        </view>
      </view>

      <view class="row line">
        <text class="label">允许改自己的</text>
        <view class="row-right">
          <switch :checked="allowSelfEdit" color="#C5342B" @change="onEditSwitch" />
        </view>
      </view>
    </view>

    <view class="save" :class="{ busy: saving }" @click="onSubmit">
      {{ saving ? '发布中…' : '发布接龙' }}
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createRelay } from '@/api/relay'

/* 五种格式。刻意不做「多选」—— 在接龙里「选两门课」的真实语义是
   两个接龙，不是一个多选接龙；合并起来会让名单算不清人数 */
const formats = [
  { v: 1, name: '仅序号' },
  { v: 2, name: '数字' },
  { v: 3, name: '手机号' },
  { v: 4, name: '文本' },
  { v: 5, name: '单选' }
]

const NOTES = {
  1: '点一下就算接上，不用填任何东西',
  2: '填一个数字，比如份数',
  3: '填手机号',
  4: '填一小段文字，比如姓名',
  5: '从你设的选项里选一个'
}

const containerId = ref(0)
const saving = ref(false)

const title = ref('')
const description = ref('')
const entryFormat = ref(1)
const formatMin = ref('')
const formatMax = ref('')
const options = ref(['参加', '不参加'])

const limitOn = ref(false)
const maxCount = ref('')

const deadlineOn = ref(false)
const deadlineDate = ref('')
const deadlineTime = ref('23:59')

const allowSelfEdit = ref(true)

const fmtNote = computed(() => NOTES[entryFormat.value] || '')

function pad(n) {
  return String(n).padStart(2, '0')
}

function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
const today = localToday()

onLoad((q) => {
  containerId.value = Number((q && q.id) || 0)
})

/* uni-app 的 switch change 事件是 e.detail.value。
   写成 e.detail.checked 会永远拿到 undefined，开关看起来点了没反应 */
function onLimitSwitch(e) {
  limitOn.value = !!e.detail.value
  if (!limitOn.value) maxCount.value = ''
}

function onDeadlineSwitch(e) {
  deadlineOn.value = !!e.detail.value
  if (deadlineOn.value && !deadlineDate.value) deadlineDate.value = today
}

function onEditSwitch(e) {
  allowSelfEdit.value = !!e.detail.value
}

function onDate(e) {
  deadlineDate.value = e.detail.value
}

function onTime(e) {
  deadlineTime.value = e.detail.value
}

function addOption() {
  if (options.value.length >= 10) return
  options.value.push('')
}

function removeOption(i) {
  if (options.value.length <= 2) return
  options.value.splice(i, 1)
}

/* 把「日期 + 时间」拼成后端要的 yyyy-MM-dd HH:mm:ss */
function deadlineAt() {
  const t = deadlineTime.value || '23:59'
  const full = t.length === 5 ? `${t}:00` : t
  return `${deadlineDate.value} ${full}`
}

async function onSubmit() {
  if (saving.value) return
  if (!containerId.value) {
    uni.showToast({ title: '缺少群组信息', icon: 'none' })
    return
  }

  const t = (title.value || '').trim()
  if (!t) {
    uni.showToast({ title: '请填写接龙主题', icon: 'none' })
    return
  }

  const data = {
    title: t,
    description: (description.value || '').trim(),
    entryFormat: entryFormat.value,
    allowSelfEdit: allowSelfEdit.value
  }

  if (entryFormat.value === 2) {
    const lo = formatMin.value === '' ? null : Number(formatMin.value)
    const hi = formatMax.value === '' ? null : Number(formatMax.value)
    if (lo !== null && hi !== null && lo > hi) {
      uni.showToast({ title: '最小值不能大于最大值', icon: 'none' })
      return
    }
    data.formatMin = lo
    data.formatMax = hi
  }

  if (entryFormat.value === 5) {
    const opts = options.value.map((x) => (x || '').trim()).filter(Boolean)
    if (opts.length < 2) {
      uni.showToast({ title: '单选至少要 2 个选项', icon: 'none' })
      return
    }
    data.options = opts
  }

  if (limitOn.value) {
    const n = Number(maxCount.value)
    if (!n || n < 1) {
      uni.showToast({ title: '请填写人数上限', icon: 'none' })
      return
    }
    data.maxCount = n
  }

  if (deadlineOn.value) {
    if (!deadlineDate.value) {
      uni.showToast({ title: '请选择截止日期', icon: 'none' })
      return
    }
    const at = deadlineAt()
    // iOS 上 new Date('2026-11-08 18:00:00') 是 Invalid Date，必须换成斜杠
    if (new Date(at.replace(/-/g, '/')) <= new Date()) {
      uni.showToast({ title: '截止时间要晚于现在', icon: 'none' })
      return
    }
    data.deadline = at
  }

  saving.value = true
  try {
    await createRelay(containerId.value, data)
    uni.showToast({ title: '已发布', icon: 'success' })
    // 返回聊天页。不用手动插卡片 —— 后端已经发了一条 type=5 的消息，
    // 聊天页的 onShow 会重新拉一次列表，正好带出来
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages && pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.redirectTo({ url: `/pages/teacher/container/chat?id=${containerId.value}` })
      }
    }, 600)
  } catch (e) {
    // request.js 已经 toast 过具体原因
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

/* 说明那行要能长高，textarea 是自适应的，行高不能锁死 */
.row.tall {
  align-items: flex-start;
  padding: 26rpx 0;
}

.label {
  width: 190rpx;
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

.area {
  flex: 1;
  font-size: 27rpx;
  color: #2B2622;
  line-height: 1.6;
  min-height: 100rpx;
  width: auto;
}

.row-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14rpx;
}

.num-input {
  width: 130rpx;
  height: 64rpx;
  padding: 0 18rpx;
  border-radius: 14rpx;
  background: #F7F1E8;
  font-size: 26rpx;
  color: #2B2622;
  text-align: center;
}

.unit {
  font-size: 24rpx;
  color: #9A9186;
  margin-right: 8rpx;
}

/* ---------- 填写格式 ---------- */
.block-title {
  padding: 26rpx 0 18rpx;
}

.block-label {
  font-size: 27rpx;
  color: #2B2622;
}

/* 五个平分一行。用 flex:1 而不是算百分比 —— 算出来的 calc(20% - x)
   在部分真机上会被丢掉 */
.formats {
  display: flex;
  gap: 10rpx;
  padding-bottom: 16rpx;
}

.format {
  flex: 1;
  height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #EFE5D8;
  border-radius: 16rpx;
  background: #FDFAF6;
  font-size: 24rpx;
  color: #7A6F63;
}

.format-pressed {
  background: #F7F1E8;
}

.format.on {
  border-color: #E9A9A3;
  background: #FDEDEB;
  color: #C5342B;
  font-weight: 600;
}

.fmt-note {
  display: block;
  padding: 0 0 22rpx;
  font-size: 22rpx;
  color: #B4A99B;
  line-height: 1.5;
}

/* ---------- 数字范围 / 单选选项 ---------- */
.sub {
  padding: 20rpx 0 4rpx;
  border-top: 1rpx solid #F5EDE2;
}

.sub-label {
  display: block;
  font-size: 23rpx;
  color: #9A9186;
  margin-bottom: 14rpx;
}

.range {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding-bottom: 20rpx;
}

.range-input {
  flex: 1;
  height: 76rpx;
  padding: 0 20rpx;
  border-radius: 14rpx;
  background: #F7F1E8;
  font-size: 26rpx;
  color: #2B2622;
  text-align: center;
}

.range-sep {
  font-size: 24rpx;
  color: #B4A99B;
}

.opt-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 14rpx;
}

.opt-input {
  flex: 1;
  height: 80rpx;
  padding: 0 20rpx;
  border-radius: 14rpx;
  background: #F7F1E8;
  font-size: 26rpx;
  color: #2B2622;
}

.opt-del {
  font-size: 23rpx;
  color: #9A9186;
  padding: 10rpx 6rpx;
  flex-shrink: 0;
}

.opt-add {
  margin-bottom: 20rpx;
  height: 80rpx;
  border-radius: 14rpx;
  border: 2rpx dashed #E2D5C3;
  font-size: 24rpx;
  color: #9A9186;
  display: flex;
  align-items: center;
  justify-content: center;
}

.opt-add-press {
  background: #FBF6EE;
}

/* ---------- 时间 ---------- */
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
