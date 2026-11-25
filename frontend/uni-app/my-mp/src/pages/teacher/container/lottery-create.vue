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
      <view class="card">
        <view class="chips">
          <view class="chip" :class="{ on: kind === 1 }" @click="kind = 1">随机点名</view>
          <view class="chip" :class="{ on: kind === 2 }" @click="kind = 2">随机分组</view>
        </view>

        <view class="row">
          <text class="label">{{ kind === 1 ? '抽取人数' : '分成几组' }}</text>
          <view class="stepper">
            <view
              class="step-btn"
              :class="{ off: kind === 1 ? drawCount <= 1 : groupCount <= 2 }"
              @click="kind === 1 ? stepDraw(-1) : stepGroup(-1)"
            >−</view>
            <text class="step-val">{{ kind === 1 ? drawCount : groupCount }}</text>
            <view
              class="step-btn"
              :class="{ off: kind === 1 ? drawCount >= poolSize : groupCount >= 20 }"
              @click="kind === 1 ? stepDraw(1) : stepGroup(1)"
            >＋</view>
          </view>
        </view>

        <text class="note">{{ poolHint }}</text>
      </view>

      <view class="card">
        <view class="chips">
          <view v-if="classId" class="chip" :class="{ on: source === 1 }" @click="source = 1">
            当前班学生
          </view>
          <view class="chip" :class="{ on: source === 2 }" @click="source = 2">
            手动勾选
          </view>
        </view>

        <template v-if="source === 1">
          <view class="pool">
            <text class="pool-num">{{ students.length }}</text>
            <text class="pool-unit">人可抽</text>
          </view>
          <text v-if="!students.length" class="note">这个班还没有在读学生</text>
        </template>

        <template v-else>
          <view class="pick-head">
            <text class="pick-count">已选 {{ picked.length }} / {{ students.length }}</text>
            <view class="pick-acts">
              <text class="pick-act" @click="pickAll">全选</text>
              <text class="pick-act" @click="picked = []">清空</text>
            </view>
          </view>

          <view v-if="!students.length" class="pick-empty">没有学生可选</view>

          <view
            v-for="s in students"
            :key="s.id"
            class="pick-row"
            :class="{ on: isPicked(s.id) }"
            hover-class="pick-press"
            @click="toggle(s.id)"
          >
            <text class="pick-name">{{ s.name }}</text>
            <text class="pick-check">{{ isPicked(s.id) ? '✓' : '' }}</text>
          </view>
        </template>
      </view>

      <view class="card">
        <view v-if="kind === 1" class="row">
          <text class="label">已抽过不再抽</text>
          <view class="row-right">
            <switch :checked="dedupe" color="#C5342B" @change="dedupe = $event.detail.value" />
          </view>
        </view>
        <view class="row" :class="{ line: kind === 1 }">
          <text class="label">结果群内公开</text>
          <view class="row-right">
            <switch :checked="publicResult" color="#C5342B" @change="publicResult = $event.detail.value" />
          </view>
        </view>
      </view>

      <view class="save" :class="{ busy: saving }" @click="onSubmit">
        {{ saving ? '发布中…' : '发布抽签' }}
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createLottery } from '@/api/lottery'
import { getContainer } from '@/api/container'
import { getStudentList } from '@/api/student'

const containerId = ref(0)
const classId = ref(null)

const loading = ref(true)
const loadErr = ref('')
const saving = ref(false)

const kind = ref(1)
const source = ref(1)
const students = ref([])
const picked = ref([])

const drawCount = ref(1)
const groupCount = ref(4)
const dedupe = ref(true)
const publicResult = ref(true)

/* 候选池大小：全班模式看班级人数，手动模式看勾了几个 */
const poolSize = computed(() => (source.value === 1 ? students.value.length : picked.value.length))

/* 一行摘要，替掉原来那几句长说明 */
const poolHint = computed(() => {
  const n = poolSize.value
  if (!n) return '还没有可抽的人'
  if (kind.value === 1) {
    return n > 1 ? `${n} 人 · 一次抽 ${drawCount.value} 个` : `${n} 人`
  }
  const per = Math.floor(n / groupCount.value)
  const rest = n % groupCount.value
  return rest
    ? `${n} 人 · ${groupCount.value} 组，每组 ${per}~${per + 1} 人`
    : `${n} 人 · ${groupCount.value} 组，每组 ${per} 人`
})

onLoad((q) => {
  containerId.value = Number((q && q.id) || 0)
  init()
})

async function init() {
  loading.value = true
  loadErr.value = ''
  try {
    if (!containerId.value) {
      loadErr.value = '缺少群组信息'
      return
    }
    const c = await getContainer(containerId.value)
    classId.value = (c && c.classId) || null

    if (classId.value) {
      await loadStudents()
    } else {
      // 没关联班级：全班模式无从拉起，直接切到手动勾选，
      // 而不是留一个点了会报错的选项
      source.value = 2
    }
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadStudents() {
  try {
    const res = await getStudentList({ classId: classId.value, status: 1 }, { silent: true })
    students.value = (res && res.list) || []
  } catch (e) {
    // 学生列表拉不到不该让整页失败：手动勾选也还能用，只是没名字可选
    students.value = []
  }
}

function isPicked(id) {
  return picked.value.indexOf(id) >= 0
}

function toggle(id) {
  const i = picked.value.indexOf(id)
  if (i >= 0) {
    picked.value.splice(i, 1)
  } else {
    picked.value.push(id)
  }
}

function pickAll() {
  picked.value = students.value.map((s) => s.id)
}

function stepDraw(d) {
  const next = drawCount.value + d
  if (next < 1 || next > poolSize.value) return
  drawCount.value = next
}

function stepGroup(d) {
  const next = groupCount.value + d
  if (next < 2 || next > 20) return
  groupCount.value = next
}

async function onSubmit() {
  if (saving.value) return

  if (!poolSize.value) {
    uni.showToast({ title: '名单是空的', icon: 'none' })
    return
  }

  if (kind.value === 1 && drawCount.value > poolSize.value) {
    uni.showToast({ title: `名单只有 ${poolSize.value} 人`, icon: 'none' })
    return
  }
  if (kind.value === 2 && groupCount.value > poolSize.value) {
    uni.showToast({ title: `名单只有 ${poolSize.value} 人，分不了 ${groupCount.value} 组`, icon: 'none' })
    return
  }

  const data = {
    kind: kind.value,
    source: source.value,
    dedupe: dedupe.value,
    publicResult: publicResult.value
  }
  if (kind.value === 1) {
    data.drawCount = drawCount.value
  } else {
    data.groupCount = groupCount.value
  }
  if (source.value === 2) {
    data.studentIds = picked.value.slice()
  }

  saving.value = true
  try {
    await createLottery(containerId.value, data)
    uni.showToast({ title: '已发布', icon: 'success' })
    // 卡片消息由后端插入，返回聊天页时 onShow 会重新拉一次列表
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages && pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.redirectTo({ url: `/pages/teacher/container/chat?id=${containerId.value}` })
      }
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

.card {
  margin-bottom: 18rpx;
  padding: 8rpx 28rpx 4rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

/* ---------- chips ---------- */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  padding: 24rpx 0 6rpx;
}

.chip {
  padding: 14rpx 32rpx;
  border-radius: 999rpx;
  background: #F7F1E8;
  font-size: 25rpx;
  color: #7A6F63;
  border: 2rpx solid transparent;
}

.chip.on {
  background: #FDEDEB;
  border-color: #E9A9A3;
  color: #C5342B;
  font-weight: 500;
}

/* 一行摘要 / 空状态，统一用一种低调的小字 */
.note {
  display: block;
  padding: 0 0 22rpx;
  font-size: 22rpx;
  color: #B4A99B;
  line-height: 1.5;
}

/* ---------- 全班人数 ---------- */
.pool {
  display: flex;
  align-items: baseline;
  padding: 8rpx 0 4rpx;
}

.pool-num {
  font-size: 56rpx;
  font-weight: 600;
  color: #C5342B;
  font-variant-numeric: tabular-nums;
}

.pool-unit {
  margin-left: 12rpx;
  font-size: 24rpx;
  color: #9A9186;
}

/* ---------- 手动勾选 ---------- */
.pick-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0 14rpx;
}

.pick-count {
  font-size: 22rpx;
  color: #9A9186;
}

.pick-acts {
  display: flex;
  gap: 26rpx;
}

.pick-act {
  font-size: 23rpx;
  color: #C5342B;
}

.pick-empty {
  padding: 50rpx 0;
  text-align: center;
  font-size: 23rpx;
  color: #B4A99B;
}

.pick-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 88rpx;
  border-top: 1rpx solid #FAF4EC;
}

.pick-press {
  background: #FDFAF6;
}

.pick-name {
  font-size: 27rpx;
  color: #2B2622;
}

.pick-row.on .pick-name {
  color: #C5342B;
  font-weight: 500;
}

.pick-check {
  font-size: 26rpx;
  color: #C5342B;
  font-weight: 600;
  width: 30rpx;
  text-align: right;
}

/* ---------- 步进器 ---------- */
.row {
  display: flex;
  align-items: center;
  min-height: 100rpx;
}

.row.line {
  border-top: 1rpx solid #F5EDE2;
}

.label {
  flex: 1;
  font-size: 27rpx;
  color: #2B2622;
}

.row-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.stepper {
  display: flex;
  align-items: center;
  background: #F7F1E8;
  border-radius: 16rpx;
  overflow: hidden;
}

.step-btn {
  width: 72rpx;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  color: #C5342B;
}

/* 到边界时置灰但不隐藏：隐藏会让整排按钮左右跳 */
.step-btn.off {
  color: #D8CEC0;
}

.step-val {
  min-width: 76rpx;
  text-align: center;
  font-size: 28rpx;
  font-weight: 600;
  color: #2B2622;
  font-variant-numeric: tabular-nums;
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
