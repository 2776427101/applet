<template>
  <view class="page">
    <view class="card">
      <view class="field">
        <text class="label">班级名称</text>
        <input
          class="input"
          v-model="name"
          type="text"
          :maxlength="32"
          placeholder="如 24级软通1班"
          placeholder-style="color:#C6BAA9;font-size:28rpx"
        />
      </view>

      <view class="field">
        <text class="label">年级</text>
        <input
          class="input"
          v-model="grade"
          type="text"
          :maxlength="16"
          placeholder="如 大一 / 高一（可不填）"
          placeholder-style="color:#C6BAA9;font-size:28rpx"
        />
      </view>

      <!-- picker 要用它包住触发区域，不能只写一个空的 picker 靠 v-if 去"打开"：
           它没有"open()"这种命令式用法，必须自己就是可点的那个元素 -->
      <picker mode="date" :value="termStartDate || today" @change="onDateChange">
        <view class="field">
          <text class="label">开学日期</text>
          <view class="picker-row">
            <text class="picker-val" :class="{ empty: !termStartDate }">
              {{ termStartDate || '用于计算首页「第几周」' }}
            </text>
            <text class="chev">›</text>
          </view>
        </view>
      </picker>
    </view>

    <view class="tip">
      建班时会自动建好这个班的班级群，之后可以在通讯录里直接进群。
    </view>

    <view class="btn" :class="{ busy: saving }" hover-class="btn-pressed" @click="submit">
      {{ saving ? '创建中…' : '创建班级' }}
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { createClass } from '@/api/class'
import { loadClasses, setCurrentClass } from '@/store/class'

const name = ref('')
const grade = ref('')
const termStartDate = ref('')
const saving = ref(false)

const pad = (n) => String(n).padStart(2, '0')
const d = new Date()
const today = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

function onDateChange(e) {
  termStartDate.value = e.detail.value
}

async function submit() {
  if (saving.value) return
  const n = name.value.trim()
  if (!n) {
    uni.showToast({ title: '请填写班级名称', icon: 'none' })
    return
  }

  saving.value = true
  try {
    const res = await createClass({
      name: n,
      grade: grade.value.trim(),
      /* 开学日期不填就让后端用今天：t_class.term_start_date 是 NOT NULL，
         传空字符串会被当成非法日期 */
      termStartDate: termStartDate.value || undefined
    })

    /* 新建的班直接切成当前班：老师建完班下一步一定是往这个班里加人 */
    await loadClasses(true)
    setCurrentClass(res.id)

    uni.showToast({ title: '已创建', icon: 'success' })
    setTimeout(() => {
      /* 从首页弹层进来时，返回首页就能看到新班；
         从通讯录进来时，返回通讯录列表里也会多一张卡 */
      uni.navigateBack()
    }, 600)
  } catch (e) {
    /* 1006（同名班级）/ 1001（校验）都由 request.js toast 过了 */
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F7EFE4;
  padding: 28rpx 24rpx 60rpx;
  box-sizing: border-box;
}

.card {
  background: #FFFFFF;
  border-radius: 28rpx;
  padding: 8rpx 26rpx;
  box-shadow: 0 6rpx 20rpx rgba(160, 120, 80, 0.05);
}

.field {
  padding: 26rpx 0;
  border-bottom: 1rpx solid #F5EDE2;
}

.field:last-child {
  border-bottom: none;
}

.label {
  display: block;
  font-size: 23rpx;
  color: #9A9186;
  margin-bottom: 12rpx;
}

.input {
  height: 72rpx;
  font-size: 30rpx;
  color: #2B2622;
}

.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72rpx;
}

.picker-val {
  font-size: 30rpx;
  color: #2B2622;
}

.picker-val.empty {
  font-size: 26rpx;
  color: #C6BAA9;
}

.chev {
  font-size: 30rpx;
  color: #C6BAA9;
}

.tip {
  margin: 22rpx 8rpx 0;
  font-size: 21rpx;
  line-height: 1.7;
  color: #A2937F;
}

.btn {
  margin-top: 44rpx;
  height: 92rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #E8594F, #C5342B);
  color: #FFFFFF;
  font-size: 29rpx;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 26rpx rgba(197, 52, 43, 0.26);
}

.btn.busy {
  opacity: 0.6;
}

.btn-pressed {
  opacity: 0.85;
}
</style>
