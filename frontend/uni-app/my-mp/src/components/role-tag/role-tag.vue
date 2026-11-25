<template>
  <text class="tag" :class="'role-' + role">{{ label }}</text>
</template>

<script setup>
/* 容器内的角色标签。群主=老师，班委=管理员，成员=普通学生
 *
 * 注意这套 role 和 t_user.role（1=老师 2=学生）完全无关，
 * 不要拿登录返回的 userInfo.role 往这里传 —— 那是全局身份，
 * 这里是「你在某个容器里的身份」。
 */
import { computed } from 'vue'

const props = defineProps({
  role: { type: Number, default: 3 }
})

const LABEL = { 1: '群主', 2: '班委', 3: '成员' }
const label = computed(() => LABEL[props.role] || '成员')
</script>

<style lang="scss" scoped>
.tag {
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  line-height: 1.5;
  flex-shrink: 0;
}

/* 群主用主色，班委用暖黄，成员用中性灰 —— 一眼能看出层级 */
.role-1 {
  background: #FDEDEB;
  color: #C5342B;
}

.role-2 {
  background: #FBF3DC;
  color: #8A6A2E;
}

.role-3 {
  background: #F0EDE8;
  color: #8A8178;
}
</style>
