<template>
  <view class="page">
    <!-- ============ 考试名称 ============ -->
    <view class="card">
      <view class="row">
        <text class="label">考试名称<text class="req">*</text></text>
        <input
          class="input"
          v-model="form.name"
          type="text"
          maxlength="64"
          placeholder="如 第一次月考"
          placeholder-style="color:#B4A99B;font-size:26rpx"
          :focus="true"
        />
      </view>
      <text class="hint">
        年级排名靠「考试名 + 考试日期」跨班对齐，所以名字要和别的班约好，
        写成「月考」和「第一次月考」会被算成两场不同的考试。
      </text>
    </view>

    <!-- ============ 类型 ============ -->
    <view class="card">
      <view class="row row-col">
        <text class="label">类型</text>
        <view class="seg">
          <view
            class="seg-item"
            v-for="t in TYPES"
            :key="t.value"
            :class="{ on: form.type === t.value }"
            @click="form.type = t.value"
          >
            {{ t.label }}
          </view>
        </view>
      </view>
    </view>

    <!-- ============ 考试日期 ============ -->
    <view class="card">
      <view class="row">
        <text class="label">考试日期<text class="req">*</text></text>
        <picker class="picker-wrap" mode="date" :value="form.examDate" @change="onDate">
          <view class="picker-value">{{ form.examDate }}</view>
        </picker>
      </view>
      <text class="hint">可以选过去的日期 —— 考完了再补录很常见</text>
    </view>

    <!-- ============ 科目 ============ -->
    <view class="card">
      <text class="group-title">科目<text class="req">*</text>（至少选 1 个）</text>

      <view class="chips">
        <view
          class="chip"
          v-for="s in PRESET"
          :key="s"
          :class="{ on: selected.includes(s) }"
          @click="toggle(s)"
        >
          {{ s }}
        </view>
        <!-- 自定义科目也进同一个 selected 数组，取消时点一下就掉，
             不用区分「预置的」和「自己加的」 -->
        <view
          class="chip chip-custom"
          v-for="s in customSubjects"
          :key="s"
          :class="{ on: selected.includes(s) }"
          @click="toggle(s)"
        >
          {{ s }}
        </view>
      </view>

      <view class="add-row">
        <input
          class="add-input"
          v-model="customInput"
          type="text"
          maxlength="16"
          placeholder="其他科目，如 专业综合"
          placeholder-style="color:#B4A99B;font-size:25rpx"
          confirm-type="done"
          @confirm="addCustom"
        />
        <view class="add-btn" :class="{ off: !customInput.trim() }" @click="addCustom">添加</view>
      </view>
    </view>

    <!-- ============ 各科满分 ============ -->
    <view v-if="selected.length" class="card">
      <text class="group-title">各科满分</text>
      <view class="row line" v-for="s in selected" :key="'f' + s">
        <text class="label label-subject">{{ s }}</text>
        <input
          class="input input-num"
          v-model="form.fullScore[s]"
          type="digit"
          maxlength="4"
          placeholder="100"
          placeholder-style="color:#B4A99B;font-size:26rpx"
        />
        <text class="unit">分</text>
      </view>
      <text class="hint">
        不填就按 100 算。及格线是满分的 60%，所以填对了及格率才有意义。
      </text>
    </view>

    <!-- ============ 保存 ============ -->
    <view class="save" :class="{ busy: saving }" @click="onSave">
      {{ saving ? '创建中…' : '创建考试' }}
    </view>

    <!-- 建完要干什么，先说明白，省得建完不知道该点哪 -->
    <view class="note">
      <text class="note-text">
        创建后回到成绩页，就能点「录入本次成绩」把这场的分数录进去。
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { addExam } from '@/api/score'

/* 四个类型和后端枚举一致：1=周测 2=月考 3=期中 4=期末。
   默认月考：一学期建得最多的是月考 */
const TYPES = [
  { value: 1, label: '周测' },
  { value: 2, label: '月考' },
  { value: 3, label: '期中' },
  { value: 4, label: '期末' }
]

/* 常用科目。放这些是因为它们覆盖了绝大多数班级，
   少打几次字；不在里面的用下面的输入框自己加 */
const PRESET = [
  '语文', '数学', '英语',
  '物理', '化学', '生物',
  '政治', '历史', '地理',
  '体育', '信息技术'
]

/* 日期默认今天。用本地时间拼，不用 toISOString —— 那个是 UTC，
   东八区早上 8 点前会拿到昨天 */
function localToday() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const saving = ref(false)
const customInput = ref('')
/* 自己加的科目。单独存一份是为了让它们能一直显示在选片区里，
   取消勾选也不会消失（不然加错了就找不回来） */
const customSubjects = ref([])

const form = reactive({
  name: '',
  type: 2,
  examDate: localToday(),
  /* 键是科目名，值是满分字符串（空串 = 不填，后端按 100）。
     用对象而不是数组，是因为科目增删时按名字取比按下标稳 */
  fullScore: {}
})

/* 预置 + 自定义合起来是「所有能选的科目」，
   但只有 selected 里的才会提交 */
const allSubjects = computed(() => [...PRESET, ...customSubjects.value])

/* 默认选中语数英：最常用的三科，不选就得点三次；
   选错了点一下就能取消，成本很低 */
const selected = ref(['语文', '数学', '英语'])

function toggle(s) {
  const i = selected.value.indexOf(s)
  if (i > -1) {
    selected.value.splice(i, 1)
  } else {
    selected.value.push(s)
  }
}

function addCustom() {
  const t = (customInput.value || '').trim()
  if (!t) return
  /* 后端要求科目名 ≤16 字、不能重复，前端先拦一遍，
     省一次「提交了才报错」的往返 */
  if (t.length > 16) {
    uni.showToast({ title: '科目名最多 16 个字', icon: 'none' })
    return
  }
  if (allSubjects.value.includes(t)) {
    uni.showToast({ title: `已经有「${t}」了`, icon: 'none' })
    return
  }
  customSubjects.value.push(t)
  selected.value.push(t)
  customInput.value = ''
}

function onDate(e) {
  form.examDate = e.detail.value
}

/* ---------- 保存 ---------- */

async function onSave() {
  if (saving.value) return

  const name = (form.name || '').trim()
  if (!name) {
    uni.showToast({ title: '请填考试名称', icon: 'none' })
    return
  }
  if (!form.examDate) {
    uni.showToast({ title: '请选择考试日期', icon: 'none' })
    return
  }
  if (!selected.value.length) {
    uni.showToast({ title: '至少选 1 个科目', icon: 'none' })
    return
  }

  /* 满分：只提交真的填了并且是正整数的。
     留空的科目不放进 map，后端会按 100 处理 —— 这比自己补一堆 100 更明确：
     「老师没设」和「老师设成 100」以后要区分时，还能分得开 */
  const fullScore = {}
  for (const s of selected.value) {
    const raw = String(form.fullScore[s] ?? '').trim()
    if (!raw) continue
    const n = Number(raw)
    if (!Number.isFinite(n) || n <= 0) {
      uni.showToast({ title: `${s} 的满分要大于 0`, icon: 'none' })
      return
    }
    fullScore[s] = Math.round(n)
  }

  saving.value = true
  try {
    const res = await addExam({
      name,
      type: form.type,
      examDate: form.examDate,
      /* 按选中的顺序提交，后端会用这个顺序存（成绩录入页的表头也按它排） */
      subjects: selected.value.slice(),
      fullScore
    })

    /* 告诉成绩页「刚建的是这个 id」。不通知的话，成绩页会保持上次选中的考试，
       老师建完看到的还是上一场，会以为没建成 */
    uni.$emit('exam:created', res && res.id)

    uni.showToast({ title: '已创建', icon: 'success' })
    /* 停 600ms 再返回：toast 还没消失就跳走，用户看不到「已创建」 */
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    /* request.js 已经 toast 过具体原因（比如「已存在同名的考试」） */
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

.row-col {
  flex-direction: column;
  align-items: flex-start;
  padding: 22rpx 0;
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

/* 满分那一列科目名长短不一（「信息技术」4 个字），
   150rpx 够用，但字号小一档，免得和上面的表单标签抢注意力 */
.label-subject {
  font-size: 26rpx;
  color: #5A524A;
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

.input-num {
  text-align: right;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 30rpx;
}

.unit {
  font-size: 24rpx;
  color: #9A9186;
  margin-left: 10rpx;
}

.hint {
  display: block;
  padding: 0 0 22rpx 150rpx;
  font-size: 21rpx;
  color: #B4A99B;
  line-height: 1.65;
}

/* ---------- 类型胶囊 ---------- */
.seg {
  display: flex;
  gap: 14rpx;
  width: 100%;
  margin-top: 16rpx;
}

.seg-item {
  flex: 1;
  text-align: center;
  font-size: 25rpx;
  padding: 18rpx 0;
  border-radius: 18rpx;
  background: #F7F2EB;
  color: #7A6F63;
}

.seg-item.on {
  background: #C5342B;
  color: #FFFFFF;
  font-weight: 500;
}

/* ---------- 日期 ---------- */
.picker-wrap {
  flex: 1;
}

.picker-value {
  font-size: 27rpx;
  color: #2B2622;
  line-height: 100rpx;
}

/* ---------- 科目芯片 ---------- */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 10rpx 0 6rpx;
}

.chip {
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
  background: #F7F2EB;
  color: #7A6F63;
  font-size: 25rpx;
  border: 2rpx solid transparent;
}

.chip.on {
  background: #FDEDEB;
  border-color: #C5342B;
  color: #C5342B;
  font-weight: 500;
}

/* 自定义科目用虚线边区分，让老师看得出「这个是我加的」 */
.chip-custom {
  border-style: dashed;
  border-color: #DCCFC0;
}

/* ---------- 自定义科目输入 ---------- */
.add-row {
  display: flex;
  align-items: center;
  margin: 8rpx 0 20rpx;
}

.add-input {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 22rpx;
  background: #FBF7F2;
  border-radius: 16rpx;
  font-size: 25rpx;
  color: #2B2622;
}

.add-btn {
  margin-left: 14rpx;
  padding: 16rpx 34rpx;
  border-radius: 16rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 25rpx;
  flex-shrink: 0;
}

/* 输入框是空的时候按钮变灰：点了没反应比变灰更让人困惑 */
.add-btn.off {
  background: #E0D6C8;
}

/* ---------- 保存 ---------- */
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

/* ---------- 底部说明 ---------- */
.note {
  margin-top: 28rpx;
  padding: 24rpx 28rpx;
  background: #F7EDD2;
  border-radius: 22rpx;
}

.note-text {
  font-size: 22rpx;
  color: #9A8158;
  line-height: 1.7;
}
</style>
