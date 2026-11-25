/* 学生端乐迪对话的本地存档
 *
 * 和老师端 utils/aidi-store.js 是两份，不是一份加参数：
 *   老师按 classId 区分（一个老师带几个班），学生按学号区分（一个学生只有一个人格）。
 *   共用一个 STORE_KEY 更糟 —— 同一台设备上老师端和学生端会互相覆盖，
 *   老师一进乐迪，学生的对话就没了。
 *
 * 这里不引 Vue，参数和返回值都是普通对象 / 数组，可以在 Node 里塞一个假的 uni 直接跑。
 */

export const STORE_KEY = 'student_aidi_chat_v1'

/** 最多存多少条。一个学期能攒几千条，storage 有配额 */
export const KEEP = 40

/** 多久算过期。一周前的"我上次考多少"再端上来只会让人觉得乱 */
export const TTL = 7 * 24 * 3600 * 1000

/**
 * 存档。
 *
 * 只留 role / content 两个字段：失败气泡不存（它是个瞬时状态，
 * 恢复出来会变成一条"没答上来"的假历史），loading 气泡也不存。
 *
 * @param {string} owner    学号，这段对话属于哪个学生
 * @param {Array}  messages 页面上的 list
 * @param {number} [now]    注入当前时间，测试用
 * @returns {boolean} 是否写成功（失败不抛，不能因为存档失败影响聊天）
 */
export function saveChat(owner, messages, now = Date.now()) {
  try {
    const msgs = (messages || [])
      .filter((m) => m && !m.failed && !m.loading && m.content)
      .slice(-KEEP)
      .map((m) => ({ role: m.role, content: m.content }))
    uni.setStorageSync(STORE_KEY, {
      owner: String(owner || ''),
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
 * @param {string} owner  当前学号
 * @param {number} [now]  注入当前时间，测试用
 * @returns {Array|null} 恢复不出来就返回 null（调用方据此显示空状态）
 */
export function loadChat(owner, now = Date.now()) {
  try {
    const raw = uni.getStorageSync(STORE_KEY)
    if (!raw || !Array.isArray(raw.msgs) || !raw.msgs.length) {
      return null
    }
    if (!raw.at || now - raw.at > TTL) {
      uni.removeStorageSync(STORE_KEY)
      return null
    }
    /* 学号对不上就别端上来。把别人的对话显示在自己界面上，
       是用户最难自己解释清楚的一类错误（一台设备换人登录就会遇到） */
    if (!raw.owner || raw.owner !== String(owner || '')) {
      return null
    }
    return raw.msgs.map((m) => ({ role: m.role, content: m.content }))
  } catch (e) {
    try {
      uni.removeStorageSync(STORE_KEY)
    } catch (e2) {
      /* 连删都删不掉就算了 */
    }
    return null
  }
}

/** 清档。「新对话」和退出登录时用 */
export function dropChat() {
  try {
    uni.removeStorageSync(STORE_KEY)
  } catch (e) {
    /* 删不掉也不影响当前这一屏 */
  }
}
