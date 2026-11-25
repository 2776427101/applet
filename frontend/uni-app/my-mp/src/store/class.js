/* 全局班级状态
 *
 * 项目里没有 Pinia / Vuex，也不想为"一个整数 + 一个列表"引入状态库。
 * Vue 3 的 ref 本身就是全局单例（模块只求值一次），页面 import 进来共享同一个值。
 *
 * 为什么要全局：首页、通讯录、成绩、座位、请假五个 tab 都要知道"现在看的是哪个班"。
 * 各自存一份的话，老师在家首页切到二班，切到成绩 tab 还是显示一班的数据 ——
 * 这种"切了没生效"的 bug 比没做切换更让人困惑。
 *
 * 持久化：存 storage。老师中途去回微信再回来，小程序可能已经重启，
 * 不存的话会跳回第一个班。
 */
import { ref, computed } from 'vue'
import { listClasses } from '@/api/class'

const KEY = 'currentClassId'

/** 我的班级列表。空数组 = 还没加载过（或真的没有班） */
export const classes = ref([])

/** 当前选中的班级 id。0 表示还没确定 */
export const currentClassId = ref(Number(uni.getStorageSync(KEY)) || 0)

/** 列表是否已经成功加载过一次，避免每个 tab 都重复请求 */
const loaded = ref(false)

/** 当前班级对象。找不到时回落到第一个班，再没有就是 null */
export const currentClass = computed(() => {
  const list = classes.value
  if (!list.length) return null
  return list.find((c) => c.id === currentClassId.value) || list[0]
})

/** 班级名，模板里直接用，省得每处都写 currentClass?.name */
export const currentClassName = computed(() => currentClass.value?.name || '未选择班级')

/**
 * 拉取我的班级列表。
 *
 * @param {boolean} force true = 强制重新拉（建完班、改完班名后要传）
 * @returns {Promise<Array>} 班级列表
 */
export async function loadClasses(force = false) {
  if (loaded.value && !force) return classes.value
  const rows = await listClasses({ silent: true })
  classes.value = Array.isArray(rows) ? rows : []
  // 存着的班级可能已经不在了（归档 / 换了账号），回落到第一个
  if (!classes.value.some((c) => c.id === currentClassId.value)) {
    setCurrentClass(classes.value.length ? classes.value[0].id : 0)
  }
  loaded.value = true
  return classes.value
}

/**
 * 切换当前班级。
 *
 * 只改状态和 storage，不发请求 —— 刷新数据是各个页面自己的事
 * （首页要重拉 overview，成绩要重拉考试列表，需求不一样）。
 */
export function setCurrentClass(id) {
  const n = Number(id) || 0
  currentClassId.value = n
  uni.setStorageSync(KEY, n)
}

/**
 * 拿到当前班级 id；没加载过就先加载。
 * 页面里 load 数据前调一下，省得每个页面都写一遍 ensure 逻辑。
 */
export async function ensureClassId() {
  if (!classes.value.length) {
    await loadClasses()
  }
  return currentClassId.value
}

/** 退出登录 / 换账号时清掉，避免把上个账号的班级带过来 */
export function resetClassState() {
  classes.value = []
  currentClassId.value = 0
  loaded.value = false
  uni.removeStorageSync(KEY)
}
