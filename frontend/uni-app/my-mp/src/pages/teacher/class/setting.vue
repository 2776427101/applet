<template>
  <view class="page">
    <!-- 加载中 / 出错：和 edit.vue 一致的两态 -->
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="load">重试</view>
    </view>

    <template v-else>
      <!-- ============ 可编辑的三项 ============ -->
      <view class="card">
        <view class="row">
          <text class="label">班级名称<text class="req">*</text></text>
          <input
            class="input"
            v-model="form.name"
            type="text"
            maxlength="32"
            placeholder="如 628班"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>

        <!-- 从名单导入页跳过来时，把它在文件里读到的班名递过来。
             目的只有一个：别让人照着屏幕手打一个长班名 -->
        <view v-if="suggest && suggest !== form.name" class="suggest" @click="useSuggest">
          <text class="suggest-label">名单里写的是</text>
          <text class="suggest-name">{{ suggest }}</text>
          <text class="suggest-act">点这里填入</text>
        </view>

        <text class="hint">
          这个名字会显示在首页顶部，也是名单导入时判断「这行是不是本班」的依据。
          改完返回导入页会自动重新解析。
        </text>

        <view class="row line">
          <text class="label">年级</text>
          <input
            class="input"
            v-model="form.grade"
            type="text"
            maxlength="16"
            placeholder="如 高一，可留空"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>

        <view class="row line">
          <text class="label">开学日期</text>
          <picker
            class="picker-wrap"
            mode="date"
            :value="form.termStartDate || today"
            @change="onTermStart"
          >
            <view class="picker-value" :class="{ empty: !form.termStartDate }">
              {{ form.termStartDate || '请选择' }}
            </view>
          </picker>
        </view>
        <text class="hint">
          填本学期第一周的周一。首页的「第几周」就是拿今天和它算的，改这里会直接改变显示。
        </text>
      </view>

      <!-- ============ 只读：邀请码 + 人数 ============ -->
      <view class="card">
        <text class="group-title">以下信息不可在这里修改</text>

        <view class="row">
          <text class="label">邀请码</text>
          <text class="value mono">{{ detail.inviteCode || '—' }}</text>
          <text v-if="detail.inviteCode" class="copy" @click="onCopy">复制</text>
        </view>
        <text class="hint">
          学生用这个码加入班级。改它会让你已经发出去的码全部失效，所以不放在这个表单里。
        </text>

        <view class="row line">
          <text class="label">在读学生</text>
          <text class="value">{{ detail.studentCount == null ? '—' : detail.studentCount + ' 人' }}</text>
        </view>
        <text class="hint">改名前先确认一下人数，别改错了班。</text>
      </view>

      <!-- ============ 保存 ============ -->
      <view class="save" :class="{ busy: saving, dim: !dirty }" @click="onSave">
        {{ saving ? '保存中…' : (dirty ? '保存修改' : '没有改动') }}
      </view>

      <!-- 说明这块为什么存在：老师第一次找不到入口时会困惑 -->
      <view class="note">
        <text class="note-title">为什么改班级名要单独一页？</text>
        <text class="note-text">
          班级名不是装饰，它同时是：首页顶部的标题、名单导入时校验「这行是不是本班」的依据、
          导出表格的表头。所以它不能随手在输入框里改，要有一次明确的「保存」。
        </text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getClassInfo, updateClassInfo } from '@/api/class'

/* 日期选择器默认值：今天。用本地时间拼，不用 toISOString ——
   那个是 UTC，东八区早上 8 点前会拿到昨天 */
function localToday() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
const today = localToday()

const loading = ref(true)
const saving = ref(false)
const loadErr = ref('')

/* 名单导入页递过来的候选班名。空串表示这次不是从导入页来的 */
const suggest = ref('')

/* 接口原始返回，只读展示用 */
const detail = ref({})

/* 可编辑的三项。初值给空串，等 load 回来填 */
const form = reactive({
  name: '',
  grade: '',
  termStartDate: ''
})

/* 有没有真的改过。用来把「保存」按钮变灰 ——
   不改也能点的话，老师会怀疑「我是不是误触了什么」 */
const dirty = computed(() => {
  const d = detail.value
  if (!d.id) return false
  return (
    form.name.trim() !== (d.name || '') ||
    form.grade.trim() !== (d.grade || '') ||
    form.termStartDate !== (d.termStartDate || '')
  )
})

onLoad((q) => {
  /* 有些端会把 query 自动 decode 一次，有些不会，所以先看原样、
     解不开就用原样 —— 班名里带「%」的情况极少，但解崩了会让整页白屏 */
  const raw = (q && q.suggest) || ''
  if (raw) {
    try {
      suggest.value = decodeURIComponent(raw)
    } catch (e) {
      suggest.value = raw
    }
  }
  load()
})

/* 填入候选班名。只改 form，不直接保存 ——
   保存要经过上面那个「确认改班级名」的弹窗，这里替用户点掉就失去了二次确认的意义 */
function useSuggest() {
  form.name = suggest.value
}

async function load() {
  loading.value = true
  loadErr.value = ''
  try {
    const d = await getClassInfo()
    detail.value = d || {}
    /* grade 可能是 null（注册时没填），统一转空串给 input，
       否则 v-model 绑 null 时输入框会显示 "null" */
    form.name = d.name || ''
    form.grade = d.grade || ''
    form.termStartDate = d.termStartDate || ''
  } catch (e) {
    loadErr.value = e && e.message ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function onTermStart(e) {
  form.termStartDate = e.detail.value
}

function onCopy() {
  uni.setClipboardData({
    data: detail.value.inviteCode,
    success: () => uni.showToast({ title: '已复制', icon: 'success' })
  })
}

async function onSave() {
  if (saving.value) return

  const name = (form.name || '').trim()
  if (!name) {
    uni.showToast({ title: '请填班级名称', icon: 'none' })
    return
  }
  if (!form.termStartDate) {
    uni.showToast({ title: '请选择开学日期', icon: 'none' })
    return
  }

  /* 改班级名会影响名单导入的校验，先问一次。
     名字没变（只改了年级/日期）就不用问，省一次点击 */
  const nameChanged = name !== (detail.value.name || '')
  if (nameChanged) {
    const ok = await new Promise((resolve) => {
      uni.showModal({
        title: '确认改班级名',
        content: `「${detail.value.name}」→「${name}」\n\n名单导入时会按新名字校验班级列，之前导不进来的表可能就对了。`,
        confirmText: '确认修改',
        confirmColor: '#C5342B',
        success: (res) => resolve(res.confirm),
        fail: () => resolve(false)
      })
    })
    if (!ok) return
  }

  saving.value = true
  try {
    await updateClassInfo({
      name,
      /* 年级留空传空串而不是 null：后端 set 的是 "" 和 null 效果一样，
         但 DTO 上有 @Size 校验，null 也能过，这里统一成空串少一个分支 */
      grade: (form.grade || '').trim(),
      termStartDate: form.termStartDate
    })
    uni.showToast({ title: '已保存', icon: 'success' })

    /* 停 600ms 再返回：toast 还没消失就跳走，用户看不到「已保存」 */
    setTimeout(() => {
      /* 返回上一页。上一页是首页，首页用 onShow 拉数据，回去自动就是新名字 */
      uni.navigateBack()
    }, 600)
  } catch (e) {
    /* request.js 已 toast 具体原因（比如「班级名称不能为空」） */
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

/* ---------- 加载 / 出错 ---------- */
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
  box-shadow: 0 4rpx 14rpx rgba(160, 120, 80, 0.08);
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

/* ---------- 候选班名 ---------- */
/* 用浅黄底而不是浅红：这是个「帮你省事」的提示，不是警告 */
.suggest {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  margin: 0 0 18rpx 150rpx;
  padding: 14rpx 18rpx;
  background: #FDF6E9;
  border-radius: 14rpx;
}

.suggest-label {
  font-size: 21rpx;
  color: #9A8158;
}

/* 班名本身要能读清，用深色 + 半粗，长班名允许换行 */
.suggest-name {
  font-size: 23rpx;
  color: #2B2622;
  font-weight: 500;
  margin: 0 10rpx;
  flex: 1;
  min-width: 0;
}

.suggest-act {
  font-size: 21rpx;
  color: #C5342B;
  flex-shrink: 0;
}

/* 只读值：比可编辑的浅一档，一眼能看出「这个改不了」 */
.value {
  flex: 1;
  font-size: 27rpx;
  color: #7A6F63;
  line-height: 100rpx;
}

/* 邀请码是给眼睛抄的，用等宽字体，字符不容易看错 */
.mono {
  font-family: Menlo, Consolas, monospace;
  letter-spacing: 2rpx;
}

.copy {
  font-size: 24rpx;
  color: #C5342B;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #FDEDEB;
  flex-shrink: 0;
}

.hint {
  display: block;
  padding: 0 0 22rpx 150rpx;
  font-size: 21rpx;
  color: #B4A99B;
  line-height: 1.6;
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

.picker-value.empty {
  color: #B4A99B;
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

/* 没有改动时按钮变灰：点它没有意义，不如直接告诉用户 */
.save.dim {
  background: #D9CFC2;
  box-shadow: none;
}

.save.busy {
  opacity: 0.6;
}

/* ---------- 底部说明 ---------- */
.note {
  margin-top: 34rpx;
  padding: 26rpx 28rpx;
  background: #F7EDD2;
  border-radius: 22rpx;
}

.note-title {
  display: block;
  font-size: 24rpx;
  color: #8A6A3A;
  font-weight: 500;
  margin-bottom: 10rpx;
}

.note-text {
  font-size: 22rpx;
  color: #9A8158;
  line-height: 1.7;
}
</style>
