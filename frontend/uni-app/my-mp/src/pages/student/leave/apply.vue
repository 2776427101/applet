<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <text class="label">请假类型</text>
        <view class="chips">
          <view
            class="chip"
            :class="{ 'chip-on': form.type === t.v }"
            v-for="t in TYPES"
            :key="t.v"
            @click="form.type = t.v"
          >
            <text class="chip-text" :class="{ 'chip-text-on': form.type === t.v }">
              {{ t.label }}
            </text>
          </view>
        </view>
      </view>

      <view class="field">
        <text class="label">开始时间</text>
        <view class="dt">
          <picker mode="date" :value="form.startDate" @change="(e) => (form.startDate = e.detail.value)">
            <text class="value">{{ form.startDate }} ▾</text>
          </picker>
          <picker mode="time" :value="form.startTime" @change="(e) => (form.startTime = e.detail.value)">
            <text class="value">{{ form.startTime }} ▾</text>
          </picker>
        </view>
      </view>

      <view class="field">
        <text class="label">结束时间</text>
        <view class="dt">
          <picker mode="date" :value="form.endDate" @change="(e) => (form.endDate = e.detail.value)">
            <text class="value">{{ form.endDate }} ▾</text>
          </picker>
          <picker mode="time" :value="form.endTime" @change="(e) => (form.endTime = e.detail.value)">
            <text class="value">{{ form.endTime }} ▾</text>
          </picker>
        </view>
      </view>

      <!-- 时长实时算给用户看：选了 11-18 到 11-20，人心里想的是"3 天"，
           让他自己去数日历很容易数错，然后提交完才发现请多了 -->
      <view class="tip" v-if="lengthText">
        <text class="tip-text">共 {{ lengthText }}</text>
      </view>

      <view class="field field-last">
        <text class="label">请假原因</text>
        <textarea
          class="input"
          v-model="form.reason"
          :maxlength="255"
          placeholder="简单说一句，班主任好判断（可不填）"
          placeholder-style="color:#B4A99B;font-size:27rpx"
          :auto-height="true"
          :show-confirm-bar="false"
          :cursor-spacing="140"
        />
        <text class="counter">{{ form.reason.length }}/255</text>
      </view>
    </view>

    <view
      class="submit"
      :class="{ 'submit-off': saving }"
      hover-class="submit-pressed"
      @click="onSubmit"
    >
      <text class="submit-text">{{ saving ? '提交中…' : '提交申请' }}</text>
    </view>

    <view class="bottom-space"></view>
  </view>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { applyLeave } from '@/api/student-data'
import { duration, today } from '@/utils/leave-fmt'

const TYPES = [
  { v: 1, label: '病假' },
  { v: 2, label: '事假' },
  { v: 3, label: '其他' }
]

const saving = ref(false)

const form = reactive({
  type: 1,
  startDate: today(),
  startTime: '08:00',
  endDate: today(),
  endTime: '18:00',
  reason: ''
})

/* 拼成契约要求的 'yyyy-MM-dd HH:mm:ss'。
   这种格式可以直接用字符串比大小，不用先转 Date */
const startAt = computed(() => `${form.startDate} ${form.startTime}:00`)
const endAt = computed(() => `${form.endDate} ${form.endTime}:00`)

const lengthText = computed(() => duration(startAt.value, endAt.value))

async function onSubmit() {
  if (saving.value) return

  if (endAt.value <= startAt.value) {
    uni.showToast({ title: '结束时间要晚于开始时间', icon: 'none' })
    return
  }

  /* 天数上限和后端保持一致（后端 30 天）。
     前端先拦一次是为了不让用户填完才被拒；后端那次才是真正的闸 */
  const days = Math.round(
    (new Date(form.endDate.replace(/-/g, '/')) - new Date(form.startDate.replace(/-/g, '/'))) /
      86400000
  ) + 1
  if (days > 30) {
    uni.showToast({ title: '一次最多请 30 天，更长的找班主任', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await applyLeave({
      type: form.type,
      startAt: startAt.value,
      endAt: endAt.value,
      reason: form.reason.trim() || null
    })
    uni.showToast({ title: '已提交，等班主任审批', icon: 'none' })
    /* 回列表。列表用 onShow 刷新，不用在这里手动通知它 */
    setTimeout(() => uni.navigateBack(), 700)
  } catch (e) {
    /* request.js 已经 toast 过（比如「这个时间段已经提交过了」） */
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 40rpx;
  box-sizing: border-box;
}

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 4rpx 26rpx;
  box-shadow: 0 8rpx 24rpx rgba(160, 120, 80, 0.05);
}

.field {
  padding: 26rpx 0;
  border-bottom: 1rpx solid #F5EDE2;
}

/* 最后一行不画线：卡片有圆角，贴边的线会显得脏 */
.field-last {
  border-bottom: none;
}

.label {
  display: block;
  font-size: 24rpx;
  color: #9A9186;
  margin-bottom: 16rpx;
}

.chips {
  display: flex;
  flex-wrap: wrap;
}

.chip {
  padding: 12rpx 30rpx;
  border-radius: 999rpx;
  background: #FBF7F1;
  margin-right: 14rpx;
}

.chip-on {
  background: #FDEDEB;
}

.chip-text {
  font-size: 26rpx;
  color: #6B6259;
}

.chip-text-on {
  color: #C5342B;
  font-weight: 600;
}

.dt {
  display: flex;
  align-items: center;
}

/* 日期和时间两个 picker 各占一半，中间留缝。
   用 flex:1 + margin 而不是 calc：calc 在真机上会丢宽度 */
.dt picker {
  flex: 1;
  background: #FBF7F1;
  border-radius: 16rpx;
  padding: 20rpx 0;
  margin-right: 14rpx;
  text-align: center;
}

.dt picker:last-child {
  margin-right: 0;
}

.value {
  font-size: 27rpx;
  color: #2B2622;
  font-variant-numeric: tabular-nums;
}

/* 时长提示：浅色底 + 棕色字，是"补充信息"不是"警告" */
.tip {
  margin: 4rpx 0 0;
  padding: 14rpx 18rpx;
  background: #FBF3DC;
  border-radius: 14rpx;
}

.tip-text {
  font-size: 23rpx;
  color: #8A6A2E;
}

.input {
  width: 100%;
  min-height: 140rpx;
  box-sizing: border-box;
  font-size: 27rpx;
  color: #2B2622;
  line-height: 1.6;
}

.counter {
  display: block;
  text-align: right;
  font-size: 21rpx;
  color: #C4B9AB;
}

/* 提交按钮：红底白字、通栏、圆角药丸。
   和老师端「保存」按钮同一套视觉，学生在两个端看到的"主操作"长得一样 */
.submit {
  margin-top: 28rpx;
  height: 92rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #E8594F 0%, #C5342B 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 24rpx rgba(197, 52, 43, 0.24);
}

.submit-pressed {
  opacity: 0.9;
}

/* 提交中：降透明度 + 不可点。不用 loading 图标，
   文字改成「提交中…」已经足够，而且不会闪 */
.submit-off {
  opacity: 0.6;
}

.submit-text {
  font-size: 30rpx;
  color: #FFFFFF;
  font-weight: 600;
}

.bottom-space {
  height: 40rpx;
}
</style>
