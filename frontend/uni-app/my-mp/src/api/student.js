/* 学生模块接口 */
import { request } from './request'
import { mockStudentList } from '@/mock/students'

/**
 * 后端接口已经写好了，改成 false 走真接口。
 *
 * 如果页面弹「网络异常，请检查后端是否启动」，说明：
 *   1. 后端没启动，或
 *   2. 后端启动时报错（看 IDEA 控制台）
 * 改回 true 就能退回假数据，页面代码一行都不用动。
 */
const USE_MOCK = false

/**
 * 学生列表
 * 注意：这个接口不接收 classId —— 老师带哪个班由后端从 token 反查，
 * 前端传 classId 就能看别的班，那是水平越权。
 *
 * @param {Object} [params]
 * @param {string} [params.keyword] 姓名或学号模糊匹配
 * @param {number} [params.status]  1=在读（默认） 0=已离班 -1=全部
 * @param {Object} [opts]
 * @param {boolean} [opts.silent] 失败时不弹 toast（页面自己会画错误块）
 * @returns {Promise<{total:number, list:Array}>}
 */
export function getStudentList(params = {}, opts = {}) {
  if (USE_MOCK) {
    /* 故意加 300ms 延迟：不加的话 loading 态一闪而过，
       等真接口接上才发现「加载中」那块根本没写好 */
    return new Promise((resolve) => {
      setTimeout(() => resolve(mockStudentList(params)), 300)
    })
  }
  return request({ url: '/teacher/student/list', data: params, silent: opts.silent })
}

/**
 * 学生详情
 *
 * 一次拿全：基本信息 + 座位 + 请假统计 + 最近一次成绩。
 * 不是「详情 + 座位 + 成绩 + 请假」四个接口 —— 详情页只服务一个人，
 * 发 4 个请求任何一环失败整页就空着，弱网下还慢 4 个来回。
 *
 * @param {number} id 学生 ID
 * @returns {Promise<Object>} 见 api-contract.md §6.2
 *   seat / leave / latestScore 三块都可能为 null（没排座 / 没请过假 / 没考过试）
 */
export function getStudentDetail(id) {
  if (USE_MOCK) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const found = mockStudentList({ status: -1 }).list.find((s) => s.id === Number(id))
        if (!found) {
          reject(new Error('学生不存在'))
          return
        }
        /* 假数据里没有座位 / 请假 / 成绩，三块给 null，
           正好把「没有这些数据」的分支也测了 */
        resolve({ ...found, createdAt: null, seat: null, leave: null, latestScore: null })
      }, 300)
    })
  }
  return request({ url: `/teacher/student/${id}` })
}

/**
 * 新增学生
 *
 * @param {Object} data { name, gender, birthday, studentNo }
 *   name 必填；gender 0/1/2；birthday 'yyyy-MM-dd'；studentNo 同班内不能重复
 * @returns {Promise<{id:number}>} 新学生的 id
 */
export function addStudent(data) {
  if (USE_MOCK) {
    return new Promise((resolve) => setTimeout(() => resolve({ id: 999 }), 300))
  }
  return request({ url: '/teacher/student', method: 'POST', data })
}

/**
 * 编辑学生（全量提交）
 *
 * 注意「全量」：没传的字段会被清空。
 * 比如要把生日删掉，就是传 birthday: null，而不是不传这个字段 ——
 * 两者在这里是一回事，但语义上「传 null」更明确。
 *
 * @param {number} id
 * @param {Object} data 与新增完全一致
 */
export function updateStudent(id, data) {
  if (USE_MOCK) {
    return new Promise((resolve) => setTimeout(resolve, 300))
  }
  return request({ url: `/teacher/student/${id}`, method: 'PUT', data })
}

/**
 * 移出班级（软删除，不真删）
 *
 * 后端只是把 status 改成 0，成绩和请假记录都还在，
 * 名单里会出现在「离班」筛选下。幂等：重复调不报错。
 *
 * @param {number} id
 */
export function removeStudent(id) {
  if (USE_MOCK) {
    return new Promise((resolve) => setTimeout(resolve, 300))
  }
  return request({ url: `/teacher/student/${id}`, method: 'DELETE' })
}
