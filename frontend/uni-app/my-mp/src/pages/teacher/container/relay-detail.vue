<template>
  <view class="page">
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else>
      <!-- 头部：状态 + 主题 + 说明 + 人数。整块不做成卡片，
           而是和页面同色 —— 它是这页的"标题区"，不是内容 -->
      <view class="hero">
        <view class="hero-tag" :class="'tone-' + tone">{{ statusText }}</view>
        <text class="hero-title">{{ r.title }}</text>
        <text v-if="r.description" class="hero-desc">{{ r.description }}</text>

        <view class="hero-meta">
          <text class="meta-strong">已报 {{ r.entryCount || 0 }}{{ r.maxCount ? ' / ' + r.maxCount : '' }} 人</text>
          <text v-if="r.deadlineText" class="meta-dot">·</text>
          <text v-if="r.deadlineText">{{ r.deadlineText }} 截止</text>
          <text class="meta-dot">·</text>
          <text>{{ formatName }}</text>
        </view>

        <!-- 只有设了上限才画进度条：不设上限时画一条永远很短的条，会让人以为快满了 -->
        <view v-if="r.maxCount" class="bar">
          <view class="bar-in" :class="{ full: pct >= 100 }" :style="{ width: pct + '%' }"></view>
        </view>
      </view>

      <!-- 我的填写固定在名单上面。放在长列表里找自己是接龙类功能最常见的挫败点 -->
      <view v-if="myEntry" class="mine">
        <view class="mine-left">
          <text class="mine-label">我的填写</text>
          <text class="mine-value">{{ myEntry.value || '第 ' + myEntry.seq + ' 位' }}</text>
        </view>
        <text v-if="canEditMine" class="mine-act" @click="startEdit">修改</text>
      </view>

      <!-- 名单 -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">名单</text>
          <text v-if="canFillOthers" class="card-act" @click="openSheet">＋ 代填</text>
        </view>

        <view v-if="!entries.length" class="empty">
          <text class="empty-text">还没有人接龙</text>
          <text class="empty-hint">{{ canOperate ? '点下面的按钮，你是第一个' : '这张接龙已经不能填了' }}</text>
        </view>

        <view v-for="e in entries" :key="e.id" class="entry">
          <text class="entry-seq">{{ e.seq }}</text>
          <text class="entry-name">{{ e.name }}</text>
          <text v-if="e.value" class="entry-val">{{ e.value }}</text>
          <text v-if="e.mine" class="entry-flag">我</text>
        </view>
      </view>

      <view v-if="r.mine && r.status === 1" class="close" @click="onClose">结束接龙</view>

      <!-- 底部操作区。不能填的时候不显示按钮，而是给一句说明 ——
           留一个点不动的灰按钮，用户会一直点 -->
      <view v-if="!canOperate" class="locked">
        {{ lockText }}
      </view>

      <view v-else class="foot">
        <!-- 序号格式：一键接龙。让用户进页面再点提交，等于把最该省的一步留下了 -->
        <template v-if="r.entryFormat === 1">
          <view v-if="myEntry" class="done">已接龙，第 {{ myEntry.seq }} 位</view>
          <view v-else class="btn" :class="{ busy: busy }" hover-class="btn-press" @click="onJoin()">
            {{ busy ? '提交中…' : '我要接龙' }}
          </view>
        </template>

        <template v-else-if="showForm">
          <view v-if="r.entryFormat === 5" class="chips">
            <view
              v-for="(o, i) in r.options || []"
              :key="i"
              class="chip"
              :class="{ on: myValue === o }"
              @click="myValue = o"
            >
              {{ o }}
            </view>
          </view>
          <input
            v-else
            class="foot-input"
            v-model="myValue"
            :type="r.entryFormat === 2 || r.entryFormat === 3 ? 'number' : 'text'"
            :maxlength="r.entryFormat === 3 ? 11 : r.entryFormat === 4 ? 20 : 9"
            :placeholder="inputPlaceholder"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
          <view class="btn" :class="{ busy: busy }" hover-class="btn-press" @click="onJoin()">
            {{ busy ? '提交中…' : (myEntry ? '保存修改' : '提交') }}
          </view>
        </template>

        <view v-else-if="myEntry && !r.allowSelfEdit" class="done">
          已接龙，第 {{ myEntry.seq }} 位 · 发起人关闭了自行修改
        </view>
      </view>
    </template>

    <!-- 代填面板。序号格式批量勾选；其他格式一次一个人，填完不清空面板（方便连打） -->
    <view v-if="sheet" class="mask" @click="closeSheet">
      <view class="sheet" @click.stop>
        <view class="sheet-head">
          <text class="sheet-title">代填</text>
          <text class="sheet-close" @click="closeSheet">关闭</text>
        </view>

        <scroll-view class="sheet-list" scroll-y>
          <view v-if="!candidates.length" class="sheet-empty">
            这个群没关联班级，没有可代填的学生名单
          </view>

          <view
            v-for="c in candidates"
            :key="c.studentId"
            class="cand"
            :class="{ picked: c.picked, on: isChosen(c.studentId) }"
            hover-class="cand-press"
            @click="tapCandidate(c)"
          >
            <text class="cand-name">{{ c.name }}</text>
            <text v-if="c.picked" class="cand-flag">已填</text>
            <text v-else-if="isChosen(c.studentId)" class="cand-check">✓</text>
          </view>
        </scroll-view>

        <!-- 序号格式：勾完一次提交 -->
        <view v-if="r.entryFormat === 1" class="sheet-foot">
          <text class="sheet-count">已选 {{ chosen.length }} 人</text>
          <view class="sheet-btn" :class="{ busy: busy }" @click="onBatch">
            {{ busy ? '提交中…' : '加入名单' }}
          </view>
        </view>

        <!-- 其他格式：选中一个人后填内容 -->
        <view v-else class="sheet-foot column">
          <view v-if="!pickOne" class="sheet-tip">选一个学生，再填他/她的内容</view>
          <template v-else>
            <text class="sheet-who">正在给 {{ pickOne.name }} 填</text>
            <view v-if="r.entryFormat === 5" class="chips">
              <view
                v-for="(o, i) in r.options || []"
                :key="i"
                class="chip"
                :class="{ on: batchValue === o }"
                @click="batchValue = o"
              >
                {{ o }}
              </view>
            </view>
            <input
              v-else
              class="foot-input"
              v-model="batchValue"
              :type="r.entryFormat === 2 || r.entryFormat === 3 ? 'number' : 'text'"
              :maxlength="r.entryFormat === 3 ? 11 : 20"
              :placeholder="inputPlaceholder"
              placeholder-style="color:#B4A99B;font-size:26rpx"
            />
            <view class="sheet-btn" :class="{ busy: busy }" @click="onBatchOne">
              {{ busy ? '提交中…' : '加入名单' }}
            </view>
          </template>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { getRelay, joinRelay, batchRelay, updateRelayEntry, closeRelay } from '@/api/relay'

const containerId = ref(0)
const relayId = ref(0)

const r = ref({})
const loading = ref(true)
const loadErr = ref('')
const busy = ref(false)

/* 底部输入的内容。进来时用我原来的填写回填，改起来不用重打一遍 */
const myValue = ref('')

/* 代填面板 */
const sheet = ref(false)
const chosen = ref([])
const pickOne = ref(null)
const batchValue = ref('')

const entries = computed(() => r.value.entries || [])
const candidates = computed(() => r.value.candidates || [])

/* 我的那条。不用 myEntryId 去 entries 里 find 之外再存一份 ——
   两个来源会打架，尤其是改完之后 */
const myEntry = computed(() => entries.value.find((e) => e.mine) || null)

const canOperate = computed(() => r.value.status === 1)
const canEditMine = computed(() => !!myEntry.value && canOperate.value && !!r.value.allowSelfEdit)
const canFillOthers = computed(() => !!r.value.mine && canOperate.value)
const showForm = computed(() => canOperate.value && (!myEntry.value || !!r.value.allowSelfEdit))

const pct = computed(() => {
  const max = r.value.maxCount
  if (!max) return 0
  const c = r.value.entryCount || 0
  return Math.min(100, Math.round((c / max) * 100))
})

const statusText = computed(() => {
  switch (r.value.status) {
    case 2: return '已截止'
    case 3: return '已满员'
    case 4: return '已结束'
    case 5: return '已取消'
    default: return '进行中'
  }
})

/* 只有"进行中"是活的，其余全用灰色。一屏三张卡时，颜色比文字更快分辨 */
const tone = computed(() => (r.value.status === 1 ? (myEntry.value ? 'done' : 'live') : 'off'))

const lockText = computed(() => {
  switch (r.value.status) {
    case 2: return '接龙已截止，共 ' + (r.value.entryCount || 0) + ' 人'
    case 3: return '名额已满，共 ' + (r.value.entryCount || 0) + ' 人'
    case 4: return '发起人已经结束这次接龙'
    case 5: return '这次接龙已取消'
    default: return ''
  }
})

const formatName = computed(() => {
  const m = { 1: '仅序号', 2: '数字', 3: '手机号', 4: '短文本', 5: '单选' }
  return m[r.value.entryFormat] || '仅序号'
})

const inputPlaceholder = computed(() => {
  const m = { 2: '填一个数字', 3: '填 11 位手机号', 4: '最多 20 个字' }
  return m[r.value.entryFormat] || '填写内容'
})

onLoad((q) => {
  containerId.value = Number((q && q.id) || 0)
  relayId.value = Number((q && q.relayId) || 0)
  if (!containerId.value || !relayId.value) {
    loadErr.value = '缺少接龙信息'
    loading.value = false
    return
  }
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})

async function load() {
  loading.value = !r.value.id
  loadErr.value = ''
  try {
    const d = await getRelay(containerId.value, relayId.value)
    r.value = d || {}
    // 回填我的填写。已经参与过的人进来直接看到自己的值，不用翻名单
    myValue.value = d && d.myValue ? d.myValue : ''
  } catch (e) {
    loadErr.value = (e && e.message) || '加载失败'
  } finally {
    loading.value = false
  }
}

function startEdit() {
  if (!myEntry.value) return
  myValue.value = myEntry.value.value || ''
  // 单选格式回填的是选项文本，直接就是选中的那个 chip，不用额外处理
}

/* ---------- 自助接龙 ---------- */
async function onJoin() {
  if (busy.value) return

  const fmt = r.value.entryFormat
  let v = (myValue.value || '').trim()

  if (fmt !== 1) {
    if (!v) {
      uni.showToast({ title: '请填写内容', icon: 'none' })
      return
    }
    if (fmt === 3 && !/^\d{11}$/.test(v)) {
      uni.showToast({ title: '手机号要 11 位', icon: 'none' })
      return
    }
  }

  busy.value = true
  try {
    // 已经填过就改那条，没填过就新增。分成两个接口调是因为
    // 后端要区分"我能改自己的"和"我能改别人的"两套权限
    if (myEntry.value && myEntry.value.id) {
      await updateRelayEntry(containerId.value, relayId.value, myEntry.value.id, v)
      uni.showToast({ title: '已保存', icon: 'success' })
    } else {
      const card = await joinRelay(containerId.value, relayId.value, v)
      // 「第 N 位」比「成功」有用得多 —— 它同时回答了"我报上了吗"和"我排第几"
      const seq = card && card.entryCount ? card.entryCount : 0
      uni.showToast({ title: seq ? `已接龙，第 ${seq} 位` : '已接龙', icon: 'none' })
    }
    await load()
  } catch (e) {
    // 4101 已满 / 4102 已截止 / 4103 已结束：这三个都是"状态变了"，
    // 重新拉一次让页面跟上，比只弹个 toast 更有用
    const code = e && e.code
    if (code === 4101 || code === 4102 || code === 4103) {
      await load()
    }
  } finally {
    busy.value = false
  }
}

/* ---------- 代填 ---------- */
function openSheet() {
  chosen.value = []
  pickOne.value = null
  batchValue.value = ''
  sheet.value = true
}

function closeSheet() {
  sheet.value = false
}

function isChosen(sid) {
  return chosen.value.indexOf(sid) >= 0
}

function tapCandidate(c) {
  if (c.picked) return
  if (r.value.entryFormat === 1) {
    const i = chosen.value.indexOf(c.studentId)
    if (i >= 0) {
      chosen.value.splice(i, 1)
    } else {
      chosen.value.push(c.studentId)
    }
    return
  }
  // 其他格式：选中一个人，下面出现输入框
  pickOne.value = c
  batchValue.value = ''
}

async function onBatch() {
  if (busy.value) return
  if (!chosen.value.length) {
    uni.showToast({ title: '先选几个学生', icon: 'none' })
    return
  }
  busy.value = true
  try {
    await batchRelay(
      containerId.value,
      relayId.value,
      chosen.value.map((sid) => ({ studentId: sid, value: '' }))
    )
    uni.showToast({ title: `已加入 ${chosen.value.length} 人`, icon: 'none' })
    chosen.value = []
    await load()
    // 面板不关：老师通常要连着补好几批
  } catch (e) {
    // 1006 已经在名单里 / 4101 名额不够，都在 request.js 里 toast 过了
    if (e && e.code === 1006) await load()
  } finally {
    busy.value = false
  }
}

async function onBatchOne() {
  if (busy.value) return
  if (!pickOne.value) return
  const v = (batchValue.value || '').trim()
  if (!v) {
    uni.showToast({ title: '请填写内容', icon: 'none' })
    return
  }
  busy.value = true
  try {
    await batchRelay(containerId.value, relayId.value, [{ studentId: pickOne.value.studentId, value: v }])
    uni.showToast({ title: '已加入', icon: 'none' })
    // 连续录入：清掉当前选中和输入，面板留着，直接点下一个
    pickOne.value = null
    batchValue.value = ''
    await load()
  } catch (e) {
    if (e && e.code === 1006) await load()
  } finally {
    busy.value = false
  }
}

/* ---------- 结束 ---------- */
function onClose() {
  uni.showModal({
    title: '结束接龙',
    content: '结束后卡片变灰，谁都不能再填。已经报上的名单会保留。',
    confirmText: '结束',
    confirmColor: '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await closeRelay(containerId.value, relayId.value)
        uni.showToast({ title: '已结束', icon: 'success' })
        await load()
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
  padding: 20rpx 24rpx 200rpx;
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

/* ---------- 头部 ---------- */
.hero {
  padding: 24rpx 6rpx 26rpx;
}

.hero-tag {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  line-height: 30rpx;
}

.tone-live {
  background: #FDEDEB;
  color: #C5342B;
}

.tone-done {
  background: #EAF3DF;
  color: #3B6D11;
}

.tone-off {
  background: rgba(0, 0, 0, 0.05);
  color: #9A9186;
}

.hero-title {
  display: block;
  margin-top: 14rpx;
  font-size: 38rpx;
  font-weight: 600;
  color: #2B2622;
  line-height: 1.4;
}

.hero-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 25rpx;
  color: #7A6F63;
  line-height: 1.7;
}

.hero-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: #9A9186;
}

.meta-strong {
  color: #C5342B;
  font-weight: 500;
}

.meta-dot {
  margin: 0 10rpx;
  color: #C9BEB0;
}

.bar {
  margin-top: 16rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.bar-in {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #E8594F, #C5342B);
  transition: width 0.3s ease;
}

.bar-in.full {
  background: #9A9186;
}

/* ---------- 我的填写 ---------- */
.mine {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 26rpx;
  margin-bottom: 18rpx;
  border-radius: 22rpx;
  background: #EAF3DF;
}

.mine-left {
  display: flex;
  align-items: baseline;
  overflow: hidden;
}

.mine-label {
  font-size: 21rpx;
  color: #6C8A4A;
  flex-shrink: 0;
}

.mine-value {
  margin-left: 14rpx;
  font-size: 27rpx;
  color: #3B6D11;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mine-act {
  padding-left: 20rpx;
  font-size: 23rpx;
  color: #3B6D11;
  flex-shrink: 0;
}

/* ---------- 名单 ---------- */
.card {
  padding: 8rpx 26rpx 12rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 84rpx;
  border-bottom: 1rpx solid #F5EDE2;
}

.card-title {
  font-size: 25rpx;
  color: #9A9186;
}

.card-act {
  padding: 10rpx 0 10rpx 24rpx;
  font-size: 24rpx;
  color: #C5342B;
}

.empty {
  padding: 70rpx 0 76rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-text {
  font-size: 26rpx;
  color: #7A6F63;
}

.empty-hint {
  margin-top: 10rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

.entry {
  display: flex;
  align-items: center;
  min-height: 88rpx;
  border-bottom: 1rpx solid #FAF4EC;
}

.entry:last-child {
  border-bottom: none;
}

/* 序号用等宽数字，1 和 11 才对得齐 */
.entry-seq {
  width: 56rpx;
  font-size: 24rpx;
  color: #B4A99B;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.entry-name {
  flex: 1;
  font-size: 27rpx;
  color: #2B2622;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.entry-val {
  margin-left: 16rpx;
  max-width: 40%;
  font-size: 24rpx;
  color: #7A6F63;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.entry-flag {
  margin-left: 12rpx;
  padding: 0 10rpx;
  border-radius: 6rpx;
  font-size: 18rpx;
  line-height: 28rpx;
  background: #EAF3DF;
  color: #3B6D11;
  flex-shrink: 0;
}

/* ---------- 结束 ---------- */
.close {
  margin-top: 32rpx;
  height: 84rpx;
  border-radius: 24rpx;
  border: 2rpx solid #E2D5C3;
  font-size: 26rpx;
  color: #9A9186;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---------- 底部操作 ---------- */
.foot {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #FFFFFF;
  box-shadow: 0 -6rpx 24rpx rgba(160, 120, 80, 0.08);
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  z-index: 3;
}

.locked {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 34rpx 24rpx calc(34rpx + env(safe-area-inset-bottom));
  background: #FBF6EE;
  border-top: 2rpx solid #F0E6D8;
  font-size: 24rpx;
  color: #9A9186;
  text-align: center;
  z-index: 3;
}

.foot-input {
  height: 84rpx;
  padding: 0 24rpx;
  border-radius: 18rpx;
  background: #F7F1E8;
  font-size: 27rpx;
  color: #2B2622;
}

.btn {
  height: 88rpx;
  border-radius: 20rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn.busy {
  opacity: 0.6;
}

.btn-press {
  opacity: 0.85;
}

.done {
  height: 88rpx;
  border-radius: 20rpx;
  background: #EAF3DF;
  color: #3B6D11;
  font-size: 27rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 0 20rpx;
}

/* ---------- 单选 chips ---------- */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.chip {
  padding: 16rpx 30rpx;
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

/* ---------- 代填面板 ---------- */
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 20;
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  max-height: 76vh;
  background: #FFFFFF;
  border-radius: 28rpx 28rpx 0 0;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 28rpx 20rpx;
  border-bottom: 1rpx solid #F5EDE2;
  flex-shrink: 0;
}

.sheet-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2B2622;
}

.sheet-close {
  font-size: 25rpx;
  color: #9A9186;
}

/* scroll-view 在 flex 里必须有确定高度，只写 flex:1 在部分基础库上会被内容撑开，
   把底部的输入框顶出面板 */
.sheet-list {
  flex: 1;
  height: 0;
  min-height: 0;
  padding: 8rpx 28rpx;
  box-sizing: border-box;
}

.sheet-empty {
  padding: 70rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #B4A99B;
}

.cand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 92rpx;
  border-bottom: 1rpx solid #FAF4EC;
}

.cand-press {
  background: #FDFAF6;
}

.cand.picked {
  opacity: 0.42;
}

.cand.on {
  background: #FDEDEB;
}

.cand-name {
  font-size: 27rpx;
  color: #2B2622;
}

.cand-flag {
  font-size: 21rpx;
  color: #9A9186;
}

.cand-check {
  font-size: 26rpx;
  color: #C5342B;
  font-weight: 600;
}

.sheet-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx 24rpx;
  border-top: 1rpx solid #F5EDE2;
  flex-shrink: 0;
}

.sheet-foot.column {
  flex-direction: column;
  align-items: stretch;
  gap: 16rpx;
}

.sheet-count {
  font-size: 24rpx;
  color: #9A9186;
}

.sheet-tip {
  font-size: 23rpx;
  color: #B4A99B;
  text-align: center;
  padding: 8rpx 0;
}

.sheet-who {
  font-size: 24rpx;
  color: #C5342B;
}

.sheet-btn {
  padding: 0 40rpx;
  height: 80rpx;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 27rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sheet-btn.busy {
  opacity: 0.6;
}
</style>
