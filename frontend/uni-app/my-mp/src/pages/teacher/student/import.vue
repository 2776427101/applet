<template>
  <view class="page">
    <!-- ========== 1. 还没选文件 ========== -->
    <block v-if="!preview && !result">
      <view class="card intro">
        <text class="intro-title">从 Excel 导入学生名单</text>
        <view class="intro-list">
          <text class="intro-item">· 支持 .xlsx（Excel 2007 及以上）</text>
          <text class="intro-item">· 表头不用在第 1 行，程序会在前 10 行里自己找</text>
          <text class="intro-item">· 必须有「姓名」列，其他列能认出多少算多少</text>
          <text class="intro-item">· 学号已存在的人不会重复插入，导入前你可以选跳过或更新</text>
        </view>
      </view>

      <view class="pick" hover-class="pick-pressed" @click="onChoose">
        <text class="pick-icon">＋</text>
        <text class="pick-text">选择 Excel 文件</text>
        <text class="pick-tip">{{ loading ? '正在解析…' : '从聊天记录里选，或从手机文件里选' }}</text>
      </view>
    </block>

    <!-- ========== 2. 解析中 ========== -->
    <view v-else-if="loading" class="card loading">
      <view class="spinner"></view>
      <text class="loading-text">正在解析名单…</text>
      <text class="loading-tip">69 行大概要 1 秒</text>
    </view>

    <!-- ========== 3. 预览 ========== -->
    <block v-else-if="preview && !result">
      <!-- 文件信息 -->
      <view class="card file">
        <view class="file-main">
          <text class="file-name">{{ fileName }}</text>
          <text class="file-meta">
            来自 sheet「{{ preview.sheetName }}」· 表头在第 {{ preview.headerRowNo }} 行 ·
            {{ preview.dataRows }} 行数据
          </text>
        </view>
        <text class="file-change" @click="onChoose">重新选择</text>
      </view>

      <!-- 统计 -->
      <view class="card">
        <text class="sec-title">
          {{ preview.stats.importable }} 行可以导入
        </text>
        <view class="stats">
          <view class="stat">
            <text class="stat-num c-create">{{ preview.stats.create }}</text>
            <text class="stat-label">新增</text>
          </view>
          <view class="stat">
            <text class="stat-num c-dup">{{ preview.stats.duplicate }}</text>
            <text class="stat-label">学号已存在</text>
          </view>
          <view class="stat">
            <text class="stat-num c-err">{{ preview.stats.error }}</text>
            <text class="stat-label">导不进来</text>
          </view>
          <view class="stat">
            <text class="stat-num c-gray">{{ preview.stats.noStudentNo }}</text>
            <text class="stat-label">没有学号</text>
          </view>
        </view>

        <!-- 没学号是唯一一个「看起来没事、其实有坑」的情况，单独说一句 -->
        <view v-if="preview.stats.noStudentNo" class="warn">
          <text class="warn-text">
            有 {{ preview.stats.noStudentNo }} 行没填学号，无法判断是不是已经存在的学生，会全部当作新同学导入。
          </text>
        </view>
      </view>

      <!-- 全班都因为「班级对不上」被拦：这是最让人懵的一种失败 ——
           名单本身是好的，只是库里班名不对。单独给一条出路 -->
      <view v-if="classMismatchAll" class="card bad-class">
        <text class="bad-title">整张表都不是「{{ preview.className }}」的学生</text>
        <text class="bad-text">
          这份名单的「班级」列写的是
          <text class="bad-names">{{ (preview.otherClassNames || []).join('、') || '别的班' }}</text>
          ，和本班对不上，所以一行都导不进来。
        </text>
        <text class="bad-text">
          两种可能：① 这份表里混了别的班的人，该先在 Excel 里拆开；
          ② 本班的班名起错了（比如当初测试随手写的），该改班名。
        </text>

        <!-- 一键改名：班名已经在后端算出来了，没必要让人照着屏幕手打一遍。
             长班名（「2028届软通动力订单班」）手打极易错一个字，
             而错一个字的结果就是「改了还是对不上」，白折腾一轮 -->
        <view v-if="classNames.length" class="rename">
          <text class="rename-label">
            {{ classNames.length === 1 ? '用名单里写的班名' : '名单里有这几个班名，点一个用' }}
          </text>
          <view
            class="rename-btn"
            v-for="n in classNames"
            :key="'rn' + n"
            :class="{ busy: renaming }"
            @click="renameTo(n)"
          >
            {{ renaming ? '正在改…' : `改成「${n}」` }}
          </view>
        </view>

        <text class="bad-alt" @click="goClassSetting">不，我去班级设置页手动改</text>
      </view>

      <!-- 表头映射 -->
      <view class="card">
        <text class="sec-title">认出来的列</text>
        <view class="chips">
          <view class="chip" v-for="c in mappedCols" :key="'m' + c.column">
            <text class="chip-h">{{ c.header }}</text>
            <!-- 表头和字段名一样时只显示一遍。原来的「姓名 姓名」「学号 学号」
                 看着像渲染错了，其实只是两个值恰好相同 -->
            <text v-if="c.header !== c.fieldLabel" class="chip-f">{{ c.fieldLabel }}</text>
          </view>
        </view>
        <view v-if="preview.unmappedHeaders.length" class="unmapped">
          <text class="unmapped-label">没用上的列：</text>
          <text class="unmapped-text">{{ preview.unmappedHeaders.join('、') }}</text>
        </view>
      </view>

      <!-- 导不进来的行。
           全班都是同一个原因时不列了 —— 69 行重复同一句「班级不是本班」，
           除了把页面撑长没有任何信息量，上面那张卡已经说清楚了 -->
      <view v-if="errorRows.length && !classMismatchAll" class="card">
        <text class="sec-title">这 {{ errorRows.length }} 行导不进来</text>
        <view class="row-item" v-for="r in errorRows" :key="'e' + r.excelRowNo">
          <text class="row-no">第 {{ r.excelRowNo }} 行</text>
          <view class="row-body">
            <text class="row-name">{{ r.name || '（没名字）' }}</text>
            <text class="row-reason">{{ r.errors.join('；') }}</text>
          </view>
        </view>
        <text class="hint">在 Excel 里改好这几行，重新选择文件就行。其他行不受影响。</text>
      </view>

      <!-- 学号已存在的行 + 策略 -->
      <view v-if="dupRows.length" class="card">
        <text class="sec-title">{{ dupRows.length }} 人学号已存在</text>

        <view class="modes">
          <view class="mode" :class="{ on: mode === 'skip' }" @click="mode = 'skip'">
            <view class="mode-head">
              <view class="dot" :class="{ on: mode === 'skip' }"></view>
              <text class="mode-name">跳过</text>
            </view>
            <text class="mode-desc">库里现有的资料一个字都不动</text>
          </view>
          <view class="mode" :class="{ on: mode === 'update' }" @click="mode = 'update'">
            <view class="mode-head">
              <view class="dot" :class="{ on: mode === 'update' }"></view>
              <text class="mode-name">更新</text>
            </view>
            <text class="mode-desc">用名单里的姓名、性别、生日、联系方式覆盖</text>
          </view>
        </view>

        <view class="row-item" v-for="r in dupRows" :key="'d' + r.excelRowNo">
          <text class="row-no">第 {{ r.excelRowNo }} 行</text>
          <view class="row-body">
            <text class="row-name">{{ r.name }}</text>
            <text class="row-reason">{{ r.reason }}</text>
          </view>
        </view>
      </view>

      <!-- 底部按钮 -->
      <view class="bottom-space"></view>
      <view class="bar">
        <view class="bar-btn" :class="{ off: !willWrite || loading }" @click="onCommit">
          {{ loading ? '正在写入…' : `确认导入 ${willWrite} 人` }}
        </view>
      </view>
    </block>

    <!-- ========== 4. 结果 ========== -->
    <block v-else>
      <view class="card done">
        <view class="done-icon">✓</view>
        <text class="done-title">导入完成</text>
        <view class="done-nums">
          <text class="done-num">新增 {{ result.created }}</text>
          <text class="done-sep">·</text>
          <text class="done-num">更新 {{ result.updated }}</text>
          <text class="done-sep">·</text>
          <text class="done-num">跳过 {{ result.skipped }}</text>
          <text class="done-sep">·</text>
          <text class="done-num" :class="{ bad: result.failed }">失败 {{ result.failed }}</text>
        </view>
      </view>

      <view v-if="result.failures && result.failures.length" class="card">
        <text class="sec-title">这 {{ result.failures.length }} 行没写进去</text>
        <view class="row-item" v-for="(f, i) in result.failures" :key="'f' + i">
          <text class="row-no">第 {{ f.excelRowNo }} 行</text>
          <view class="row-body">
            <text class="row-name">{{ f.name || '（没名字）' }}</text>
            <text class="row-reason">{{ f.reason }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-space"></view>
      <view class="bar">
        <view class="bar-btn" @click="backToList">返回学生列表</view>
      </view>
    </block>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { chooseFile, previewImport, commitImport } from '@/api/studentImport'
import { updateClassInfo } from '@/api/class'

const fileName = ref('')
const preview = ref(null)
const result = ref(null)
const mode = ref('skip')
const loading = ref(false)

/* 正在改班名。和 loading 分开：改完还要重解析，
   如果共用一个标志，按钮文案会在两个阶段之间跳来跳去 */
const renaming = ref(false)

/* 上一次选中的文件。留着是为了「去改班级名」回来时能自动重解析 ——
   老师改完班名回到这页，如果还得重新选一遍文件，体验很差 */
let lastPick = null

/* 是不是刚从班级设置页回来。只在「整张表都因班级被拦」时置位，
   别的情况下重解析只是白等一秒 */
let reparseOnShow = false

/* 从班级设置页返回时重新解析。
   用 onShow 不用 onLoad：navigateTo 返回不会重新触发 onLoad */
onShow(async () => {
  if (!reparseOnShow || !lastPick) return
  reparseOnShow = false
  await doPreview(lastPick)
})

/* 只展示认出来的列。没认出来的放在下面一行文字里，不占地方 */
const mappedCols = computed(() =>
  preview.value ? preview.value.columns.filter((c) => c.mapped) : []
)

const errorRows = computed(() =>
  preview.value ? preview.value.rows.filter((r) => r.action === 'error') : []
)

const dupRows = computed(() =>
  preview.value ? preview.value.rows.filter((r) => r.action === 'duplicate') : []
)

/* 实际会写库的人数：skip 模式下重复的人不动，所以不算进去。
   按钮上写「确认导入 8 人」，结果里却新增 8、更新 1，老师会怀疑是不是算错了 */
const willWrite = computed(() => {
  const s = preview.value && preview.value.stats
  if (!s) return 0
  return mode.value === 'update' ? s.create + s.duplicate : s.create
})

/* 每一行都因「班级不是本班」被拦。
   判定条件是「全部行都错」且「每条错误都提到班级」——
   只错几行的话多半真是别的班的人，不该提示改班名 */
const classMismatchAll = computed(() => {
  const p = preview.value
  if (!p || !p.rows || !p.rows.length) return false
  if (p.stats.importable > 0) return false
  const errs = p.rows.filter((r) => r.action === 'error')
  if (errs.length !== p.rows.length) return false
  return errs.every(
    (r) => r.errors && r.errors.length && r.errors.every((m) => m.indexOf('班级') > -1)
  )
})

function goClassSetting() {
  /* 置位：改完班名回来要自动重解析，否则老师还得重新选一次文件 */
  reparseOnShow = true
  /* 把候选班名一起带过去。老师选「手动改」多半是想顺便调年级或开学日期，
     但班名本身没理由让他重打一遍 —— 设置页会把它做成一个「点一下就填进去」的提示。
     多个候选时不带，免得替他做了选择 */
  const one = classNames.value.length === 1 ? classNames.value[0] : ''
  const url = one
    ? `/pages/teacher/class/setting?suggest=${encodeURIComponent(one)}`
    : '/pages/teacher/class/setting'
  uni.navigateTo({ url })
}

/* 名单里出现的、和本班对不上的班名。后端已经按「归一化后不同」筛过一遍，
   所以这里不用再比，直接拿来当候选 */
const classNames = computed(() =>
  preview.value && preview.value.otherClassNames ? preview.value.otherClassNames : []
)

/**
 * 一键把本班改名成名单里写的那个。
 *
 * <p>为什么值得单独做一个按钮：班名长起来是「2028届软通动力订单班」这种，
 * 让老师照着屏幕抄一遍，错一个字的结果就是「改完还是对不上」，
 * 而系统此时只会再说一遍「班级不是本班」—— 比不提供这个功能更让人恼火。
 *
 * <p>为什么仍然要弹一次确认：改班名会影响首页标题、导出表头、
 * 以及以后每一次名单导入的校验基准。这是个「一步错、步步错」的操作，
 * 值得多一次点击。
 */
async function renameTo(name) {
  if (renaming.value) return
  const from = preview.value.className

  const ok = await new Promise((resolve) => {
    uni.showModal({
      title: '确认改班级名',
      content: `「${from}」→「${name}」\n\n改完会自动重新解析这份名单。`,
      confirmText: '确认修改',
      confirmColor: '#C5342B',
      success: (res) => resolve(res.confirm),
      fail: () => resolve(false)
    })
  })
  if (!ok) return

  renaming.value = true
  try {
    /* 只传 name：后端是「传什么改什么」，年级和开学日期不会被碰。
       这正是我们把 PUT /class 改成部分更新的原因 */
    await updateClassInfo({ name })
    uni.showToast({ title: '班名已改', icon: 'success' })
    /* 立刻重解析，让老师看到「现在能导了」——
       如果只是改完就停在那儿，他会怀疑到底成没成 */
    if (lastPick) await doPreview(lastPick)
  } catch (e) {
    /* request.js 已经 toast 过具体原因（比如「班级名称最多 32 个字」） */
  } finally {
    renaming.value = false
  }
}

async function onChoose() {
  const pick = await chooseFile()
  if (!pick) return

  lastPick = pick
  fileName.value = pick.name
  await doPreview(pick)
}

/* 解析并出预览。抽成函数是因为它有两个调用方：
   选了新文件，和从班级设置页改完名回来重解析 */
async function doPreview(pick) {
  result.value = null
  loading.value = true
  try {
    preview.value = await previewImport(pick)
    /* 默认「跳过」而不是「更新」：更新会覆盖库里的资料，
       万一这份名单是旧的，覆盖完就找不回来了。默认选破坏性小的那个 */
    mode.value = 'skip'
  } catch (e) {
    /* api 层已经 toast 过了，这里只需要回到初始态让老师重新选 */
    preview.value = null
  } finally {
    loading.value = false
  }
}

async function onCommit() {
  if (!willWrite.value || loading.value) return

  /* 只提交能导的行。错误行即使传上去也会被后端判失败，
     但提前过滤掉能让结果里的「失败 0」名副其实 */
  const rows = preview.value.rows.filter((r) => r.action !== 'error')
  if (!rows.length) {
    uni.showToast({ title: '没有可导入的行', icon: 'none' })
    return
  }

  loading.value = true
  try {
    result.value = await commitImport({ mode: mode.value, rows })
  } catch (e) {
    /* 失败就留在预览页，老师可以直接再点一次 */
  } finally {
    loading.value = false
  }
}

function backToList() {
  /* 学生列表是 tabBar 页，只能用 switchTab。
     用 navigateTo 会静默失败 —— 按钮点了没反应，最难查 */
  uni.switchTab({
    url: '/pages/teacher/student/index',
    fail: () => uni.navigateBack()
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 20rpx 24rpx 0;
  box-sizing: border-box;
}

/* ---------- 通用卡片 ---------- */
.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 28rpx 26rpx;
  margin-bottom: 18rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.sec-title {
  font-size: 27rpx;
  font-weight: 500;
  color: #2B2622;
  display: block;
  margin-bottom: 18rpx;
}

.hint {
  font-size: 21rpx;
  color: #9A9186;
  line-height: 1.6;
  display: block;
  margin-top: 14rpx;
}

/* ---------- 初始态 ---------- */
.intro-title {
  font-size: 30rpx;
  font-weight: 500;
  color: #2B2622;
  display: block;
  margin-bottom: 18rpx;
}

.intro-list {
  display: flex;
  flex-direction: column;
}

.intro-item {
  font-size: 23rpx;
  color: #7A6F63;
  line-height: 1.9;
}

.pick {
  margin-top: 28rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 60rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 2rpx dashed #E3D5C2;
}

.pick-pressed {
  background: #FDF9F4;
}

.pick-icon {
  font-size: 60rpx;
  color: #C5342B;
  font-weight: 200;
  line-height: 1;
}

.pick-text {
  font-size: 28rpx;
  color: #2B2622;
  font-weight: 500;
  margin-top: 18rpx;
}

.pick-tip {
  font-size: 21rpx;
  color: #9A9186;
  margin-top: 10rpx;
}

/* ---------- 解析中 ---------- */
.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 90rpx 0;
}

.spinner {
  width: 52rpx;
  height: 52rpx;
  border: 5rpx solid #F0E4D4;
  border-top-color: #C5342B;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 26rpx;
  color: #2B2622;
  margin-top: 24rpx;
}

.loading-tip {
  font-size: 21rpx;
  color: #9A9186;
  margin-top: 8rpx;
}

/* ---------- 文件信息 ---------- */
.file {
  display: flex;
  align-items: center;
}

.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 27rpx;
  font-weight: 500;
  color: #2B2622;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  font-size: 21rpx;
  color: #9A9186;
  margin-top: 6rpx;
  display: block;
}

.file-change {
  font-size: 23rpx;
  color: #C5342B;
  margin-left: 18rpx;
  flex-shrink: 0;
}

/* ---------- 统计 ---------- */
.stats {
  display: flex;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-num {
  font-size: 40rpx;
  font-weight: 600;
  line-height: 1.2;
}

.c-create {
  color: #2F7A4F;
}

.c-dup {
  color: #B07B22;
}

.c-err {
  color: #C5342B;
}

.c-gray {
  color: #9A9186;
}

.stat-label {
  font-size: 20rpx;
  color: #9A9186;
  margin-top: 6rpx;
}

.warn {
  margin-top: 20rpx;
  background: #FDF6E9;
  border-radius: 16rpx;
  padding: 16rpx 18rpx;
}

.warn-text {
  font-size: 21rpx;
  color: #8A6A28;
  line-height: 1.6;
}

/* ---------- 全班班级对不上 ---------- */
/* 用红色边框而不是填满红色：内容是解释，不是警报，
   填满会让人以为整个页面都坏了 */
.bad-class {
  border: 2rpx solid #F0C9C4;
  background: #FEF7F6;
}

.bad-title {
  display: block;
  font-size: 27rpx;
  font-weight: 500;
  color: #C5342B;
  margin-bottom: 14rpx;
}

.bad-text {
  display: block;
  font-size: 22rpx;
  color: #7A6F63;
  line-height: 1.7;
  margin-bottom: 10rpx;
}

/* 名单里写的班名要能一眼看到，用深色 + 半粗 */
.bad-names {
  color: #2B2622;
  font-weight: 500;
}

/* ---------- 一键改名 ---------- */
.rename {
  margin-top: 20rpx;
}

.rename-label {
  display: block;
  font-size: 21rpx;
  color: #9A9186;
  margin-bottom: 12rpx;
}

/* 主按钮：整条宽度，和下面那个次要的文字链接拉开层次 */
.rename-btn {
  height: 76rpx;
  border-radius: 22rpx;
  background: #C5342B;
  color: #FFFFFF;
  font-size: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10rpx;
  /* 班名可能很长，允许换行而不是撑破卡片 */
  padding: 0 20rpx;
  text-align: center;
  line-height: 1.3;
}

.rename-btn.busy {
  opacity: 0.6;
}

/* 次要出口：走设置页手动改。用纯文字，不抢主按钮的注意力 */
.bad-alt {
  display: block;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: #8A8178;
  text-align: center;
  text-decoration: underline;
}

/* ---------- 表头映射 ---------- */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.chip {
  display: flex;
  align-items: center;
  background: #EEF5EE;
  border-radius: 12rpx;
  padding: 8rpx 14rpx;
}

.chip-h {
  font-size: 21rpx;
  color: #2F7A4F;
}

.chip-f {
  font-size: 19rpx;
  color: #5B8A6E;
  margin-left: 8rpx;
  padding-left: 8rpx;
  border-left: 1rpx solid #CFE2D2;
}

.unmapped {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
}

.unmapped-label {
  font-size: 21rpx;
  color: #9A9186;
}

.unmapped-text {
  font-size: 21rpx;
  color: #B4A99B;
  flex: 1;
}

/* ---------- 行明细 ---------- */
.row-item {
  display: flex;
  padding: 16rpx 0;
  border-top: 1rpx solid #F5EDE2;
}

.row-no {
  font-size: 21rpx;
  color: #B4A99B;
  width: 120rpx;
  flex-shrink: 0;
  padding-top: 2rpx;
}

.row-body {
  flex: 1;
  min-width: 0;
}

.row-name {
  font-size: 24rpx;
  color: #2B2622;
  display: block;
}

.row-reason {
  font-size: 21rpx;
  color: #C5342B;
  line-height: 1.5;
  margin-top: 4rpx;
  display: block;
}

/* ---------- 策略选择 ---------- */
.modes {
  display: flex;
  gap: 14rpx;
  margin-bottom: 8rpx;
}

.mode {
  flex: 1;
  border: 2rpx solid #F0E4D4;
  border-radius: 20rpx;
  padding: 18rpx;
  background: #FDFBF8;
}

.mode.on {
  border-color: #C5342B;
  background: #FEF6F4;
}

.mode-head {
  display: flex;
  align-items: center;
}

.dot {
  width: 26rpx;
  height: 26rpx;
  border-radius: 50%;
  border: 2rpx solid #D8CBB8;
  margin-right: 10rpx;
  flex-shrink: 0;
  box-sizing: border-box;
}

.dot.on {
  border: 8rpx solid #C5342B;
}

.mode-name {
  font-size: 25rpx;
  color: #2B2622;
  font-weight: 500;
}

.mode-desc {
  font-size: 20rpx;
  color: #9A9186;
  line-height: 1.5;
  margin-top: 8rpx;
  display: block;
}

/* ---------- 底部按钮 ---------- */
.bottom-space {
  height: 150rpx;
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: var(--window-bottom, 0px);
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(247, 239, 228, 0.96);
  box-sizing: border-box;
}

.bar-btn {
  height: 92rpx;
  border-radius: 26rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 29rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 24rpx rgba(197, 52, 43, 0.25);
}

.bar-btn.off {
  background: #D9CEC0;
  box-shadow: none;
}

/* ---------- 结果 ---------- */
.done {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 26rpx;
}

.done-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #EEF5EE;
  color: #2F7A4F;
  font-size: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.done-title {
  font-size: 30rpx;
  font-weight: 500;
  color: #2B2622;
  margin-top: 22rpx;
}

.done-nums {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.done-num {
  font-size: 24rpx;
  color: #7A6F63;
}

.done-num.bad {
  color: #C5342B;
}

.done-sep {
  font-size: 22rpx;
  color: #D3C7B8;
  margin: 0 12rpx;
}
</style>
