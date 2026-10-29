<template>
  <view class="page">
    <!-- 编辑模式下要先把旧数据拉回来 -->
    <view v-if="loading" class="state">
      <text class="state-text">正在加载…</text>
    </view>

    <view v-else-if="loadErr" class="state">
      <text class="state-title">{{ loadErr }}</text>
      <view class="state-btn" @click="loadOld">重试</view>
    </view>

    <template v-else>
      <!-- ============ 姓名 ============ -->
      <view class="card">
        <view class="row">
          <text class="label">姓名<text class="req">*</text></text>
          <input
            class="input"
            v-model="form.name"
            type="text"
            maxlength="32"
            placeholder="请输入学生姓名"
            placeholder-style="color:#B4A99B;font-size:26rpx"
            :focus="!isEdit"
          />
        </view>
      </view>

      <!-- ============ 性别 ============ -->
      <view class="card">
        <view class="row row-col">
          <text class="label">性别</text>
          <view class="seg">
            <view
              class="seg-item"
              v-for="g in GENDERS"
              :key="g.value"
              :class="{ on: form.gender === g.value }"
              @click="form.gender = g.value"
            >
              {{ g.label }}
            </view>
          </view>
        </view>
      </view>

      <!-- ============ 学号 ============ -->
      <view class="card">
        <view class="row">
          <text class="label">学号</text>
          <input
            class="input"
            v-model="form.studentNo"
            type="text"
            maxlength="32"
            placeholder="选填"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <text class="hint">同班内不能重复，留空也能保存</text>
      </view>

      <!-- ============ 生日 ============ -->
      <view class="card">
        <view class="row">
          <text class="label">生日</text>
          <picker
            class="picker-wrap"
            mode="date"
            :value="form.birthday || today"
            :end="today"
            @change="onBirthday"
          >
            <view class="picker-value" :class="{ empty: !form.birthday }">
              {{ form.birthday || '选填' }}
            </view>
          </picker>
          <!-- picker 选不出「空」，要清空只能另给一个按钮 -->
          <text v-if="form.birthday" class="clear" @click="form.birthday = ''">×</text>
        </view>
        <text class="hint">用来算年龄，也可以不填</text>
      </view>

      <!-- ============ 联系方式 ============ -->
      <view class="card">
        <text class="group-title">联系方式</text>
        <view class="row">
          <text class="label">手机号</text>
          <input
            class="input"
            v-model="form.phone"
            type="text"
            maxlength="20"
            placeholder="选填"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <view class="row line">
          <text class="label">微信号</text>
          <input
            class="input"
            v-model="form.wechat"
            type="text"
            maxlength="64"
            placeholder="选填"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <view class="row line">
          <text class="label">家人电话1</text>
          <input
            class="input"
            v-model="form.familyPhone1"
            type="text"
            maxlength="20"
            placeholder="选填"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <view class="row line">
          <text class="label">家人电话2</text>
          <input
            class="input"
            v-model="form.familyPhone2"
            type="text"
            maxlength="20"
            placeholder="选填"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
      </view>

      <!-- ============ 住宿与其他 ============ -->
      <view class="card">
        <text class="group-title">住宿与其他</text>
        <view class="row">
          <text class="label">宿舍</text>
          <input
            class="input"
            v-model="form.dormitory"
            type="text"
            maxlength="32"
            placeholder="如 3栋502"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <view class="row line">
          <text class="label">培训方向</text>
          <input
            class="input"
            v-model="form.trainingDirection"
            type="text"
            maxlength="64"
            placeholder="如 软件开发"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <view class="row line">
          <text class="label">民族</text>
          <input
            class="input"
            v-model="form.nation"
            type="text"
            maxlength="32"
            placeholder="如 汉族"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <view class="row line">
          <text class="label">政治面貌</text>
          <input
            class="input"
            v-model="form.politicalStatus"
            type="text"
            maxlength="32"
            placeholder="如 共青团员"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
        <view class="row line">
          <text class="label">积极分子</text>
          <input
            class="input"
            v-model="form.partyActivist"
            type="text"
            maxlength="32"
            placeholder="如 是 / 否"
            placeholder-style="color:#B4A99B;font-size:26rpx"
          />
        </view>
      </view>

      <!-- ============ 保存 ============ -->
      <view class="save" :class="{ busy: saving }" @click="onSave">
        {{ saving ? '保存中…' : (isEdit ? '保存修改' : '添加学生') }}
      </view>

      <!-- 编辑模式下才有的危险操作 -->
      <view v-if="isEdit" class="danger" @click="onRemove">移出班级</view>
    </template>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getStudentDetail, addStudent, updateStudent, removeStudent } from '@/api/student'

/* 三个性别选项。value 和后端约定一致：0=未知 1=男 2=女 */
const GENDERS = [
  { value: 0, label: '未知' },
  { value: 1, label: '男' },
  { value: 2, label: '女' }
]

/* 日期选择器的上限：不能选未来 */
const today = new Date().toISOString().slice(0, 10)

const isEdit = ref(false)
const loading = ref(false)
const saving = ref(false)
const loadErr = ref('')

/* 学号用字符串不是数字：学号可能带前导零（"0201"），用 number 会被吃掉 */
const form = reactive({
  name: '',
  gender: 0,
  studentNo: '',
  birthday: '',
  /* 9 个扩展字段全部是字符串，空串表示「没填」，
     提交时会转成 null（见 onSave 里的 nullify） */
  dormitory: '',
  phone: '',
  wechat: '',
  familyPhone1: '',
  familyPhone2: '',
  trainingDirection: '',
  nation: '',
  politicalStatus: '',
  partyActivist: ''
})

/* 9 个扩展字段的字段名，loadOld 和 onSave 都要遍历它。
   写成常量是为了将来加字段只改一处 —— 漏一个字段的后果是
   「编辑页保存一次，那个字段就被清空了」，很难发现 */
const EXTRA_FIELDS = [
  'dormitory',
  'phone',
  'wechat',
  'familyPhone1',
  'familyPhone2',
  'trainingDirection',
  'nation',
  'politicalStatus',
  'partyActivist'
]

let sid = null

onLoad((opt) => {
  sid = opt && opt.id ? opt.id : null
  isEdit.value = !!sid
  /* 导航栏标题跟着模式变：新增页写「添加学生」，编辑页写「编辑资料」 */
  uni.setNavigationBarTitle({ title: isEdit.value ? '编辑资料' : '添加学生' })
  if (isEdit.value) loadOld()
})

async function loadOld() {
  loading.value = true
  loadErr.value = ''
  try {
    const d = await getStudentDetail(sid)
    form.name = d.name || ''
    /* gender 可能是 null（老数据），兜底成 0，否则三个胶囊一个都不高亮 */
    form.gender = d.gender == null ? 0 : d.gender
    form.studentNo = d.studentNo || ''
    form.birthday = d.birthday || ''
    /* 扩展字段可能是 null（老数据、导入时这列没填），统一转成空串给 input */
    EXTRA_FIELDS.forEach((k) => {
      form[k] = d[k] || ''
    })
  } catch (e) {
    loadErr.value = e && e.message ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function onBirthday(e) {
  form.birthday = e.detail.value
}

/* ---------- 保存 ---------- */

async function onSave() {
  if (saving.value) return

  const name = (form.name || '').trim()
  if (!name) {
    uni.showToast({ title: '请填姓名', icon: 'none' })
    return
  }

  /* 学号：空白一律当没填，传 null。
     传 "" 的话后端会存成空字符串，两个空学号之间还会互相冲突 */
  const no = (form.studentNo || '').trim()
  const payload = {
    name,
    gender: form.gender,
    /* 生日同理：清空后传 null，后端才会真的把这一列置空 */
    birthday: form.birthday || null,
    studentNo: no ? no : null
  }

  /* 9 个扩展字段必须全部带上。
     PUT 是全量提交 —— 少传一个字段，后端就会把它 set 成 null。
     「编辑页只改了姓名，保存后手机号没了」这种 bug 十有八九是漏了这里 */
  EXTRA_FIELDS.forEach((k) => {
    const v = (form[k] || '').trim()
    payload[k] = v ? v : null
  })

  saving.value = true
  try {
    if (isEdit.value) {
      await updateStudent(sid, payload)
    } else {
      await addStudent(payload)
    }
    uni.showToast({ title: isEdit.value ? '已保存' : '已添加', icon: 'success' })

    /* 停 600ms 再返回：toast 还没消失就跳走，用户看不到「已保存」，
       会怀疑到底存没存 */
    setTimeout(() => {
      /* 新增和编辑都是返回上一页：
         新增 → 回列表（onShow 会自动刷新，新学生就在里面）
         编辑 → 回详情（详情页的 onShow 也会重新拉） */
      uni.navigateBack()
    }, 600)
  } catch (e) {
    /* request.js 已经 toast 过具体原因（比如「学号 T9001 已被 王小明 使用」），
       这里不用再弹一次 */
  } finally {
    saving.value = false
  }
}

/* ---------- 移出班级 ---------- */

function onRemove() {
  uni.showModal({
    title: '移出班级',
    content: `确定把「${form.name}」移出班级吗？\n成绩和请假记录会保留，之后能在「离班」里找到。`,
    confirmText: '移出',
    confirmColor: '#C5342B',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await removeStudent(sid)
        uni.showToast({ title: '已移出', icon: 'success' })
        /* 直接回列表，不要回详情页 —— 人都离班了还停在详情页很奇怪 */
        setTimeout(() => {
          uni.navigateBack({ delta: 2 })
        }, 600)
      } catch (e) {
        /* api 层已 toast */
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
  padding: 8rpx 28rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.row {
  display: flex;
  align-items: center;
  min-height: 100rpx;
}

/* 卡片内第二行起加一条分隔线。
   第一行不加，否则标题和内容之间会多出一条线，看着像断开了 */
.row.line {
  border-top: 1rpx solid #F5EDE2;
}

/* 分组标题：联系方式 / 住宿与其他 */
.group-title {
  display: block;
  padding: 26rpx 0 4rpx;
  font-size: 23rpx;
  color: #9A9186;
  letter-spacing: 1rpx;
}

/* 性别那一行要竖着排：标签在上、胶囊在下 */
.row-col {
  flex-direction: column;
  align-items: flex-start;
  padding: 22rpx 0;
}

.label {
  /* 150 而不是 120：「家人电话1」五个字按 27rpx 算要 135rpx，
     120 放不下会折成两行，看着像排版坏了 */
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

.hint {
  display: block;
  padding: 0 0 20rpx 150rpx;
  font-size: 21rpx;
  color: #B4A99B;
}

/* ---------- 性别胶囊 ---------- */
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
  transition: background 0.15s;
}

.seg-item.on {
  background: #C5342B;
  color: #FFFFFF;
  font-weight: 500;
}

/* ---------- 生日 ---------- */
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

.clear {
  font-size: 34rpx;
  color: #B4A99B;
  padding: 0 6rpx;
  line-height: 1;
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
.danger {
  margin-top: 32rpx;
  height: 88rpx;
  border-radius: 28rpx;
  background: #FFFFFF;
  color: #C5342B;
  font-size: 27rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}
</style>
