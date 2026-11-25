/* 乐迪对话的本地存档
 *
 * 单独拎出来是为了能测。这段逻辑有 4 个分支（过期、换班、坏数据、条数上限），
 * 埋在页面里就只能靠"手动点一遍"，而这几个分支手动点很难覆盖全。
 *
 * 这里不引 Vue，参数和返回值都是普通对象 / 数组，
 * 所以可以在 Node 里塞一个假的 uni 直接跑（见本轮测试脚本）。
 */

export const STORE_KEY = 'aidi_chat_v1'

/** 最多存多少条。不设上限的话，一个学期能攒几千条，storage 有配额 */
export const KEEP = 40

/** 多久算过期。三天前的"某某成绩怎么样"再端上来只会让人觉得乱 */
export const TTL = 7 * 24 * 3600 * 1000

/**
 * 存档。
 *
 * 只留 role / content / doneText 三个字段，**故意不存 action**：
 * 确认卡不能跨会话复活 —— 三天前那张「群发消息」的卡如果还能点，
 * 等于留了个定时炸弹。已经执行过的结果在 doneText 里，历史照样接得上。
 *
 * @param {number} classId  这段对话属于哪个班
 * @param {Array}  messages 页面上的 list
 * @param {number} [now]    注入当前时间，测试用
 * @returns {boolean} 是否写成功（失败不抛，不能因为存档失败影响聊天）
 */
export function saveChat(classId, messages, now = Date.now()) {
  try {
    const msgs = (messages || [])
      .filter((m) => m && !m.failed && m.content)
      .slice(-KEEP)
      .map((m) => ({
        role: m.role,
        content: m.content,
        doneText: m.doneText || ''
      }))
    uni.setStorageSync(STORE_KEY, {
      classId: Number(classId) || 0,
      at: now,
      msgs
    })
    return true
  } catch (e) {
    return false
  }
}

/**
 * 读档。
 *
 * @param {number} classId  当前班级
 * @param {number} [now]    注入当前时间，测试用
 * @returns {Array|null} 恢复不出来就返回 null（调用方据此显示空状态）
 */
export function loadChat(classId, now = Date.now()) {
  try {
    const raw = uni.getStorageSync(STORE_KEY)
    if (!raw || !Array.isArray(raw.msgs) || !raw.msgs.length) {
      return null
    }
    if (!raw.at || now - raw.at > TTL) {
      uni.removeStorageSync(STORE_KEY)
      return null
    }
    /* 班对不上就别端上来。把二班的对话显示在一班的界面上，
       是用户最难自己解释清楚的一类错误 */
    if (!raw.classId || raw.classId !== (Number(classId) || 0)) {
      return null
    }
    return raw.msgs.map((m) => ({
      role: m.role,
      content: m.content,
      doneText: m.doneText || ''
    }))
  } catch (e) {
    /* 存档坏了就当没有，不要因为一段旧对话把页面卡死 */
    try {
      uni.removeStorageSync(STORE_KEY)
    } catch (e2) {
      /* 连删都删不掉就算了 */
    }
    return null
  }
}

/** 清档。换班、点「新对话」时用 */
export function dropChat() {
  try {
    uni.removeStorageSync(STORE_KEY)
  } catch (e) {
    /* 删不掉也不影响当前这一屏 */
  }
}
