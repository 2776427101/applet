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
      </view>

      <!-- ============ 只读：邀请码 + 人数 ============ -->
      <view class="card">
        <text class="group-title">以下信息不可在这里修改</text>

        <view class="row">
          <text class="label">邀请码</text>
          <text class="value mono">{{ detail.inviteCode || '—' }}</text>
          <text v-if="detail.inviteCode" class="copy" @click="onCopy">复制</text>
        </view>

        <view class="row line">
          <text class="label">在读学生</text>
          <text class="value">{{ detail.studentCount == null ? '—' : detail.studentCount + ' 人' }}</text>
        </view>
      </view>

      <!-- ============ 保存 ============ -->
      <view class="save" :class="{ busy: saving }" @click="onSave">
        {{ saving ? '保存中…' : '完成' }}
      </view>

      <!-- ============ 危险操作 ============
           放最底部、单独一张卡、红色文字：删除是这一页唯一不可逆的操作，
           不能和「完成」挤在一起 —— 两个按钮相邻时，手滑的代价太大 -->
      <view class="danger">
        <view class="danger-row" hover-class="danger-pressed" @click="onDelete">
          <text class="danger-text">删除班级</text>
          <text class="danger-arrow">›</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getClassInfo, updateClassInfo, deleteClass } from '@/api/class'
import { currentClassId, loadClasses } from '@/store/class'

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
const deleting = ref(false)
const loadErr = ref('')

/* 名单导入页递过来的候选班名。空串表示这次不是从导入页来的 */
const suggest = ref('')

/* 这一页看的是哪个班。
   两个入口进来：① 首页右上角「设置」——不带参数，看当前班
                ② 通讯录卡片「⋯」——带 classId，看指定班
   不能直接用 currentClassId：从通讯录点二班的 ⋯ 时，当前班可能还是一班，
   那样会打开错的班，改完还以为改的是二班 */
const cid = ref(0)

/* 接口原始返回，只读展示用 */
const detail = ref({})

/* 可编辑的三项。初值给空串，等 load 回来填 */
const form = reactive({
  name: '',
  grade: '',
  termStartDate: ''
})

/* 有没有真的改过。没改的时候点「完成」不提交，直接返回。
   不做「按钮变灰 + 文案变成『没有改动』」那套 —— 变灰的按钮在手机上
   看着像坏了，而且没有任何信息量 */
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
  /* 带 classId 就编辑那个班；不带就编辑当前班（首页「设置」入口） */
  cid.value = Number((q && q.classId) || 0) || currentClassId.value

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
    const d = await getClassInfo(cid.value)
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

  /* 没改任何东西：直接返回，不发请求、不弹窗。
     原来这里会弹「确认改班级名」，但名字没变也弹，属于白给一次点击 */
  if (!dirty.value) {
    uni.navigateBack()
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
    await updateClassInfo(
      {
        name,
        /* 年级留空传空串而不是 null：后端 set 的是 "" 和 null 效果一样，
           但 DTO 上有 @Size 校验，null 也能过，这里统一成空串少一个分支 */
        grade: (form.grade || '').trim(),
        termStartDate: form.termStartDate
      },
      cid.value
    )

    /* 班名改了，选择器里的名字也得跟着变，否则首页还显示旧名字 */
    loadClasses(true).catch(() => {})
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

/* ---------- 删除班级 ---------- */
async function onDelete() {
  if (deleting.value || saving.value) return

  /* 确认框里把「会失去什么」写清楚。
     只写「确定删除吗」是最糟的确认框：老师不知道点下去会发生什么，
     要么不敢点，要么点完才发现整个班的成绩都没了 */
  const n = detail.value.studentCount || 0
  const content = n > 0
    ? `「${detail.value.name}」有 ${n} 名学生。删除后这个班的学生名单、成绩、座位、请假记录都不会再显示，班级群也会一起关闭。\n\n此操作无法撤销。`
    : `删除后「${detail.value.name}」的班级群会一起关闭。\n\n此操作无法撤销。`

  const ok = await new Promise((resolve) => {
    uni.showModal({
      title: '删除班级',
      content,
      confirmText: '删除',
      confirmColor: '#C5342B',
      success: (res) => resolve(res.confirm),
      fail: () => resolve(false)
    })
  })
  if (!ok) return

  deleting.value = true
  try {
    await deleteClass(cid.value)
    /* 刷新全局班级列表：如果删的正好是当前班，
       store 会发现 currentClassId 不在新列表里，自动回落到第一个班 */
    await loadClasses(true)
    uni.showToast({ title: '已删除', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 600)
  } catch (e) {
    /* api 层已提示 */
  } finally {
    deleting.value = false
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

.save.busy {
  opacity: 0.6;
}

/* ---------- 危险操作 ---------- */
/* 单独一张白卡 + 红字，和上面的保存按钮隔开：
   删除是这一页唯一不可逆的动作，不能和保存按钮挨着放 */
.danger {
  margin-top: 30rpx;
  padding: 4rpx 28rpx 22rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.danger-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 100rpx;
}

.danger-pressed {
  background: #FDF3F1;
}

.danger-text {
  font-size: 28rpx;
  color: #C5342B;
}

.danger-arrow {
  font-size: 30rpx;
  color: #D9B0AB;
}
</style>
