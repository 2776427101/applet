# 班级助手 · 接口契约 v0.8

> **这份文档是什么**：前后端之间的"合同"。字段名、类型、口径**以本文为准**。任何一方要改，先改这里，再改代码。
>
> **谁写谁读**：这份文档由设计方维护，你照着它写代码。**不需要背，写哪个模块翻哪一节**。

---

## 阅读指南（先看这 30 行，别急着往下翻）

| 你在做什么 | 读哪一节 | 用时 |
|---|---|---|
| 第一次接触这份文档 | §0 通用约定、§1 错误码、§2 数据字典 | 20 分钟，一次看懂，后面全靠它 |
| 想知道整个项目到底有多少接口 | §3 接口全景 | 5 分钟，当目录用 |
| 要写某个模块的代码 | §4 ~ §9 里找到对应小节，**只看那一节** | 10 分钟 / 个 |
| 要写前端的 `api/xxx.js` | 每节末尾的「前端怎么用」 | 2 分钟 |

> **不要从第一页读到最后一页。** 契约是手册，不是小说。现在读 §3 就够了，其余用到再翻。

### 一个接口长什么样（模板说明）

每个接口都用同一个模板，6 个块：

| 块 | 内容 | 对应你代码里的什么 |
|---|---|---|
| 标题 | `GET /api/teacher/student/list` | Controller 上的 `@GetMapping("/list")` |
| 一句话 | 这个接口干嘛用的 | 方法名 + 注释 |
| 请求参数 | Query 或 Body 的字段表 | 方法参数 / DTO 类 |
| 响应示例 | 真实会返回的 JSON | VO 类 |
| 字段说明 | 每个字段的类型和**口径** | Service 里的查询与计算 |
| 错误情况 | 什么场景返回什么 code | 抛异常 / `Result.error(...)` |

看懂一个，剩下 100 个都长一样。**建议先看 §4.2「当前用户」**，它是最短的完整例子。

---

## 0. 通用约定

| 项 | 约定 |
|---|---|
| 开发地址 | `http://127.0.0.1:8080/api` |
| 生产地址 | `https://你的域名/api` |
| 编码 | UTF-8，`Content-Type: application/json` |
| 日期格式 | `yyyy-MM-dd`，例：`2026-10-19` |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`，例：`2026-10-19 08:00:00` |
| 时区 | 固定 `Asia/Shanghai`（**不要用 UTC**，否则早上 8 点前"今日"统计全错） |
| 认证 | `Authorization: Bearer <token>` |
| 时间字段 | 一律字符串，不用时间戳（小程序端好读） |
| 字段命名 | **接口里全用 camelCase**（`studentNo`）；数据库里全用 snake_case（`student_no`）。转换在 VO 层做 |
| 空值 | 没有值就给 `null`，**不要给 `""` 或 `"undefined"`** |

### ⚠️ HTTP 方法白名单：只用这 6 个

微信小程序的 `wx.request`（`uni.request` 底层就是它）**不支持 `PATCH`**。官方文档里 `method` 的可选值只有：

```
OPTIONS  GET  HEAD  POST  PUT  DELETE  TRACE  CONNECT
```

没有 `PATCH`。在开发者工具里可能"看起来能跑"，但换安卓真机、或换基础库版本就会静默失败（请求被降级成 GET，body 丢失，后端收到的是空对象，还不报错——这种 bug 极难查）。

**所以本契约只用 `GET / POST / PUT / DELETE` 四个。**

| 要表达的意思 | 用什么 | 例子 |
|---|---|---|
| 查 | `GET` | `GET /teacher/todo/list` |
| 新建 | `POST` | `POST /teacher/todo` |
| 改整个资源 | `PUT` | `PUT /teacher/student/{id}` |
| 改某个子资源 | `PUT` | `PUT /teacher/todo/{id}/done`（把 `done` 这个子资源整个换成新值） |
| 删 | `DELETE` | `DELETE /teacher/todo/{id}` |

> 关于「只改一个字段，是不是该用 PATCH」：语义上 PATCH 更贴，但**平台不支持就是不能用**。把路径写成 `/todo/{id}/done` 之后，`PUT` 反而是更准确的——请求体 `{"done":1}` 是这个子资源的**完整**表示，不是局部补丁，正好是 PUT 的定义。

### 三条铁律（违反了会出事）

**① 教师端接口一律不接收 `classId` 参数。**

前端传什么 `classId` 都不可信——学生端用户能改请求，传个 `classId=2` 就能看别人班的数据（这叫**水平越权**）。

正确做法：从 token 里拿 `userId` → 查 `t_class.head_teacher_id = userId` → 拿到他真正带的班。

```java
// Service 里固定这么写，所有教师端接口都一样
Long classId = classService.getMyClassId(currentUserId());
if (classId == null) { /* 没绑班，按各接口约定返回空数据 */ }
```

**② 所有列表查询必须带 `class_id` 条件。** 漏了就是越权。

**③ 枚举一律传数字，不传中文。** 中文文案由前端映射，这样以后改文案不用动后端。

### 统一响应体

所有接口都返回这个结构，**HTTP 状态码和 code 同时表达语义**：

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

- `code = 0` 表示业务成功，前端只取 `data`
- `code != 0` 表示失败，`message` 可直接展示给用户
- 失败时 `data` 为 `null`

### 分页约定

只有**会无限增长**的列表才分页（请假记录、成绩、班级日记）。班级人数有天然上限（几十人），学生列表**不分页**。

请求：`?page=1&size=20`（page 从 1 开始，size 默认 20，最大 100）

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": { "list": [], "total": 47, "page": 1, "size": 20 }
}
```

---

## 1. 错误码

| code | HTTP | 含义 | 前端处理建议 |
|---|---|---|---|
| 0 | 200 | 成功 | 取 `data` |
| 1001 | 400 | 参数校验失败 | 直接 toast `message` |
| 1002 | 401 | 未登录 / token 失效 | 清本地 token，`reLaunch` 回入口页 |
| 1003 | 403 | 无权访问该班级 | toast 后返回上一页 |
| 1004 | 404 | 资源不存在 | toast |
| 1005 | 429 | 请求过于频繁 | 提示稍后再试 |
| 1006 | 409 | 数据冲突（如学号重复） | toast `message`，让用户改 |
| 1007 | 400 | 加入密码不正确 | toast `message`（带剩余尝试次数） |
| 1008 | 400 | 邀请码无效 / 已过期 / 容器已解散 | toast `message` |
| 2001 | 400 | 微信 code 无效或已被使用 | 重新调用 `wx.login` 再试一次 |
| 2002 | 502 | 微信服务调用失败 | 提示"微信服务异常，请稍后再试" |
| 3001 | 403 | 当前用户不是老师，无权访问教师端 | 提示并回入口页 |
| 3002 | 403 | 该数据不属于你的班级 / 你不是该学科容器的成员或无权操作 | toast 后返回上一页 |
| 3003 | 403 | 你已被移出该学科容器（在黑名单里），不能再次加入 | toast `message` |
| 3005 | 403 | 你已被禁言，暂时不能发言 | toast `message`（本期无聊天，暂未触发） |
| 5000 | 500 | 服务器内部错误 | 提示"服务开小差了"，同时上报日志 |

> 前端只需要对 **1002** 做特殊处理（跳登录），其余统一 toast 即可。

---

## 2. 数据字典（枚举值）

| 字段 | 值 | 含义 |
|---|---|---|
| `user.role` | 1 / 2 | 老师 / 学生 |
| `user.status` | 1 / 0 | 正常 / 禁用 |
| `student.gender` | 0 / 1 / 2 | 未知 / 男 / 女 |
| `student.status` | 1 / 0 | 在读 / 已离班 |
| `leave.type` | 1 / 2 / 3 | 病假 / 事假 / 其他 |
| `leave.status` | 0 / 1 / 2 / 3 | 待审批 / 已通过 / 已驳回 / 已销假 |
| `todo.done` | 0 / 1 | 未完成 / 已完成 |
| `exam.type` | 1 / 2 / 3 / 4 | 周测 / 月考 / 期中 / 期末 |

**数据库里存数字，接口里也传数字**，中文文案由前端映射（这样以后改文案不用动后端）。

前端映射建议（放在 `src/utils/dict.js`）：

```js
export const GENDER = { 0: '未知', 1: '男', 2: '女' }
export const LEAVE_TYPE = { 1: '病假', 2: '事假', 3: '其他' }
export const LEAVE_STATUS = { 0: '待审批', 1: '已通过', 2: '已驳回', 3: '已销假' }
```

---

## 3. 接口全景

> 这是整个项目的**目录**。截图里 29 个模块，加上底部 5 个 tab，全部在这里。
>
> **为什么有的写得很细、有的只有一行？**
> 因为**没有表结构的接口详情是猜的**。P0 这 6 个模块的表已经建好（或本轮建），字段能定死；P1/P2 的表还没建，现在写字段表，等你做到那里必然要推翻重写。
> 做法是：做到哪个模块，先建表，我再把那一行展开成详情。**展开一个模块约 30 分钟，不拖进度。**

### P0 · 主线（现在做，本轮完成）

| # | 模块 | 接口 | 方法 | 路径 | 状态 |
|---|---|---|---|---|---|
| 1 | 首页 | 聚合数据 | GET | `/api/teacher/home/overview` | ✅ 已实现 |
| 2 | 待办事项 | 列表 | GET | `/api/teacher/todo/list` | 🔨 本轮 |
| 3 | 待办事项 | 新增 | POST | `/api/teacher/todo` | 🔨 本轮 |
| 4 | 待办事项 | 完成 / 取消完成 | PUT | `/api/teacher/todo/{id}/done` | 🔨 本轮 |
| 5 | 待办事项 | 删除 | DELETE | `/api/teacher/todo/{id}` | 🔨 本轮 |
| 6 | 学生信息 | 列表 | GET | `/api/teacher/student/list` | 🔨 本轮 |
| 7 | 学生信息 | 详情 | GET | `/api/teacher/student/{id}` | ✅ |
| 8 | 学生信息 | 新增 | POST | `/api/teacher/student` | ✅ |
| 9 | 学生信息 | 编辑 | PUT | `/api/teacher/student/{id}` | ✅ |
| 10 | 学生信息 | 移出班级 | DELETE | `/api/teacher/student/{id}` | ✅ |
| 10.1 | 学生信息 | 名单导入 · 预览 | POST | `/api/teacher/student/import/preview` | ✅ |
| 10.2 | 学生信息 | 名单导入 · 确认 | POST | `/api/teacher/student/import/commit` | ✅ |
| 10.3 | 班级设置 | 读班级信息 | GET | `/api/teacher/class` | ✅ |
| 10.4 | 班级设置 | 改班级信息 | PUT | `/api/teacher/class` | ✅ |
| 11 | 成绩分析 | 考试列表 | GET | `/api/teacher/exam/list` | 🔨 本轮 |
| 12 | 成绩分析 | 新建考试 | POST | `/api/teacher/exam` | 🔨 本轮 |
| 13 | 成绩分析 | 某次考试的成绩 | GET | `/api/teacher/score/list` | 🔨 本轮 |
| 14 | 成绩分析 | 批量录入成绩 | POST | `/api/teacher/score/batch` | 🔨 本轮 |
| 15 | 成绩分析 | 成绩统计 | GET | `/api/teacher/score/stats` | 🔨 本轮 |
| 16 | 座位表 | 读座位图 | GET | `/api/teacher/seat/chart` | ✅ |
| 17 | 座位表 | 保存座位图 | PUT | `/api/teacher/seat/chart` | ✅ |
| 18 | 请假登记 | 列表 | GET | `/api/teacher/leave/list` | 🔨 本轮 |
| 19 | 请假登记 | 审批 | PUT | `/api/teacher/leave/{id}/audit` | 🔨 本轮 |
| 20 | 请假登记 | 代学生请假 | POST | `/api/teacher/leave` | 🔨 本轮 |

### P1 · 高频（第二批做）

| # | 模块 | 方法 | 路径 | 作用 |
|---|---|---|---|---|
| 1 | 家长通讯录 | GET | `/api/teacher/parent/list` | 学生家长姓名 + 电话，支持按学生搜 |
| 2 | 晨读助手 | GET | `/api/teacher/reading/today` | 今日晨读内容（课文 / 单词表） |
| 3 | 晨读助手 | POST | `/api/teacher/reading/record` | 记录某学生晨读完成情况 |
| 4 | 随机抽签 | GET | `/api/teacher/random/pick?count=1` | 随机抽 N 个学生，可排除已抽过的 |
| 5 | 违纪登记 | GET | `/api/teacher/violation/list` | 违纪记录分页列表 |
| 6 | 违纪登记 | POST | `/api/teacher/violation` | 记一条违纪（学生 / 类型 / 说明 / 扣分） |
| 7 | 课程表 | GET | `/api/teacher/timetable` | 整张课程表（周一到周日 × 节次） |
| 8 | 课程表 | PUT | `/api/teacher/timetable` | 整表覆盖保存 |
| 9 | 荣誉统计 | GET | `/api/teacher/honor/list` | 荣誉记录（学生 / 奖项 / 级别 / 日期） |
| 10 | 荣誉统计 | POST | `/api/teacher/honor` | 新增一条荣誉 |
| 11 | 班干部 | GET | `/api/teacher/cadre/list` | 班干部名单（学生 / 职务） |
| 12 | 班干部 | PUT | `/api/teacher/cadre` | 整份名单覆盖保存 |
| 13 | 值日安排 | GET | `/api/teacher/duty` | 值日表（周次 × 小组 / 学生） |
| 14 | 值日安排 | PUT | `/api/teacher/duty` | 整表覆盖保存 |
| 15 | 班会记录 | GET | `/api/teacher/meeting/list` | 班会记录分页列表 |
| 16 | 班会记录 | POST | `/api/teacher/meeting` | 新增一次班会记录 |
| 17 | 班级日记 | GET | `/api/teacher/diary/list` | 班级日记分页列表 |
| 18 | 班级日记 | POST | `/api/teacher/diary` | 写一篇班级日记 |

### P2 · 低频 / 视情况（先不做）

| # | 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|---|
| 1 | 教师通讯录 | GET | `/api/teacher/colleague/list` | 同校老师，需要 `t_school` 表，暂缓 |
| 2 | 宿舍管理 | GET / PUT | `/api/teacher/dorm` | 宿舍 + 床位分配 |
| 3 | 语音播报 | — | — | **纯前端 TTS，不需要接口** |
| 4 | 小组对抗 | GET / POST | `/api/teacher/group/list` `/api/teacher/group/score` | 分组 + 积分 |
| 5 | 贫困生 | GET / POST | `/api/teacher/poverty/list` `/api/teacher/poverty` | 涉及敏感信息，权限要单独设计 |
| 6 | 家访记录 | GET / POST | `/api/teacher/visit/list` `/api/teacher/visit` | 家访时间 / 对象 / 内容 |
| 7 | 谈心谈话 | GET / POST | `/api/teacher/talk/list` `/api/teacher/talk` | 谈话对象 / 主题 / 记录 |
| 8 | 期末评语 | GET / POST | `/api/teacher/comment/list` `/api/teacher/comment` | 按学生写评语，学期维度 |
| 9 | 目标院校 | GET / POST | `/api/teacher/target/list` `/api/teacher/target` | 学生志愿 / 目标分 |
| 10 | 教研导航 | — | — | **外链配置，前端写死数组即可** |
| 11 | 在线编辑器 | POST | `/api/common/upload` | 只需一个文件上传，编辑器本身用前端库 |
| 12 | 班主任成长 | — | — | 内容聚合，先外链 |
| 13 | 安全教育 | — | — | 内容聚合，先外链 |
| 14 | 数据备份 | POST | `/api/teacher/backup` | 导出本班全部数据为 Excel / JSON |

### 学生端（预留，本轮不做）

| # | 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|---|
| 1 | 登录 | POST | `/api/auth/wx-login` | **复用教师端同一个接口**，靠 `role` 区分 |
| 2 | 我的班级 | GET | `/api/student/my/class` | 学生看自己班的信息 |
| 3 | 我的请假 | GET | `/api/student/leave/list` | 只看自己的 |
| 4 | 提交请假 | POST | `/api/student/leave` | 学生自己发起请假 |

> 学生端和学生 tab 是两回事：**学生 tab 是老师看学生名单**（`/api/teacher/student/*`），**学生端是学生自己登录看自己的东西**（`/api/student/*`）。别混。

### 学科班级容器（v0.9 新增，✅ 已实现）

教师端多了一组「按学科组织的成员容器」，一个老师可以有多个（和"一个老师一个行政班"不冲突）。

| 模块 | 接口 | 路径前缀 |
|---|---|---|
| 容器 | 列表 / 创建 / 详情 / 改信息 / 解散 | `/api/teacher/container` |
| 成员 | 列表 / 设撤班委 / 禁言 / 移除（可拉黑） | `/api/teacher/container/{id}/members` |
| 黑名单 | 列表 / 解除（仅群主） | `/api/teacher/container/{id}/blacklist` |
| 精华 | 列表 / 收录 / 取消 | `/api/teacher/container/{id}/highlights` |
| 邀请码 | 查看 / 改有效期 / 重新生成 / 加入密码 / 操作日志 | `/api/teacher/container/{id}/invite` 等 |
| 加入 | 预览 / 加入 / 退出（角色无关，学生端复用） | `/api/container` |

**详情见 §9.5**。本期不含聊天：容器只预留了 `session_id`，禁言判定落在 `assertCanSpeak()`。

---

## 4. 通用接口

### 4.1 微信登录

`POST /api/auth/wx-login`

**请求**

```json
{ "code": "081Kd5Ga1AbcDe0XyZ..." }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | 是 | `wx.login()` 返回的临时凭证，5 分钟有效、只能用一次 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 604800,
    "needBindClass": false,
    "user": {
      "id": 1,
      "name": "李光",
      "role": 1,
      "avatar": null,
      "classId": 1,
      "className": "628班"
    }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| token | string | JWT，有效期 7 天 |
| expiresIn | number | 有效期（秒），604800 = 7 天 |
| needBindClass | boolean | `true` 表示还没加入班级，前端应引导到"加入班级"页 |
| user.id | number | 用户 ID |
| user.name | string | 姓名，未设置时为 `""` |
| user.role | number | 1 老师 / 2 学生 |
| user.avatar | string | 头像 URL，可能为 `null` |
| user.classId | number | 所属班级 ID，未加入时为 `null` |
| user.className | string | 班级名，未加入时为 `null` |

**后端流程（关键，别搞错）**

```
1. 收到 code
2. 后端用 appid + AppSecret 调微信：
   GET https://api.weixin.qq.com/sns/jscode2session
       ?appid=xxx&secret=xxx&js_code=<code>&grant_type=authorization_code
3. 拿到 openid（失败返回 2001 / 2002）
4. 按 openid 查 t_user：
   - 查到 → 更新 last_login_at
   - 没查到 → 新建一条（role 默认 1，name 为空）
5. 签发 JWT（payload 里放 userId、role），返回
```

> ⚠️ **AppSecret 只在后端**。前端任何地方出现 secret 都等于泄露。

**错误**

| 场景 | code |
|---|---|
| code 为空 / 格式不对 | 1001 |
| 微信返回 code 无效 | 2001 |
| 调微信超时 / 5xx | 2002 |

---

### 4.2 当前用户

`GET /api/auth/me`

用于小程序冷启动时校验 token 是否还有效。**这是本文档最短的完整接口，建议先读它。**

**请求**：无参数，只带 `Authorization` 头。

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "李光",
    "role": 1,
    "avatar": null,
    "classId": 1,
    "className": "628班"
  }
}
```

**错误**

| 场景 | code |
|---|---|
| 没带 token / 过期 / 签名不对 | 1002 |

---

## 5. 首页 & 待办

### 5.1 教师首页聚合数据

`GET /api/teacher/home/overview`

**一次返回首页所有数据**，不要拆成 4 个接口（小程序并发有限，拆了首屏会一块块闪）。

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "classInfo": {
      "id": 1,
      "name": "628班",
      "grade": "高一",
      "teacherName": "李光",
      "teacherTitle": "班主任"
    },
    "stats": {
      "leaveToday": 1,
      "birthdayToday": 0,
      "studentCount": 47,
      "todoUndone": 0
    },
    "term": {
      "weekNo": 8,
      "termStartDate": "2026-08-31"
    }
  }
}
```

| 字段 | 类型 | 口径（**写 SQL 前必须对齐**） |
|---|---|---|
| classInfo.id | number | 当前老师带的班 |
| classInfo.name | string | 班级名 |
| classInfo.grade | string | 年级 |
| classInfo.teacherName | string | 班主任姓名（取自 `t_user.name`） |
| classInfo.teacherTitle | string | 固定 `班主任`，以后可能扩展成"副班主任" |
| stats.leaveToday | number | **今天开始**且状态=已通过(1) 的请假**记录数** |
| stats.birthdayToday | number | 生日"月+日"=今天，且 status=1 的学生数 |
| stats.studentCount | number | 本班 status=1 的学生数 |
| stats.todoUndone | number | 当前老师的 `done=0` 待办数 |
| term.weekNo | number | `(今天 - termStartDate) / 7 + 1`，**后端算** |
| term.termStartDate | string | 学期第一周的周一 |
| ~~quote~~ | — | **已删除**（v0.29）。首页那个位置换成了通知框，见 §5.1.1 |

**口径举例（避免歧义）**

- 一条请假 10-18 到 10-20，今天是 10-19 —— 按"今天开始"算**不计入**。如果产品要的是"今天处于请假中"，SQL 要改成 `start_at <= 今天23:59 AND end_at >= 今天00:00`，**两种口径数字会不一样，现在就要定**。
- 今天是周日、学期第 8 周的判断：以 `termStartDate` 所在周为第 1 周。

**错误**

| 场景 | code |
|---|---|
| 没带 token / 过期 | 1002 |
| role 不是老师 | 3001 |
| 老师还没绑定班级 | `data` 里 `classInfo` 为 `null`，其余字段给默认值（0 / 空字符串） |

---

### 5.1.1 首页通知 `GET /api/teacher/home/notices`

第 39 个接口。首页金句框换成的通知框，以及「通知」页都用它。

```
GET /api/teacher/home/notices?classId=1&limit=20
```

| 参数 | 必填 | 说明 |
|---|---|---|
| classId | 否 | 看哪个班。**不传 = 我的所有班（v0.44 改了语义）**。**不要传 0**，0 会被当成"查 id 为 0 的班"回 1004 |
| limit | 否 | 最多几条，默认 20，上限 100 |

> **v0.44 破坏性变更。** 以前 `classId` 不传 = **我的第一个班**，现在 = **我的所有班**。
> 原因是这条语义本身就不成立：老师的通知天然跨班，二班学生请假不该要求老师先切到二班。
> 依赖"不传就是第一个班"的调用方会突然收到更多数据。需要单班就显式传 `classId`。
>
> 合并后的列表靠新增的 `classId` / `className` 保持可读，前端在班级数 > 1 时加班级前缀。
> 排序固定为 `atMillis` 倒序，同一毫秒再按 `classId` 升序 —— 没有第二个比较键时，
> 两个班同秒提交的请假每次刷新顺序都会变。

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 3,
    "list": [
      {
        "kind": "leave",
        "title": "王小明 提交了请假申请",
        "desc": "病假 · 11-05 12:38 至 11-06 10:38",
        "atText": "3 分钟前",
        "atMillis": 1793846280000,
        "studentId": 121,
        "leaveId": 5,
        "todoId": null,
        "classId": 1,
        "className": "24计算机软通1班"
      },
      { "kind": "todo", "title": "待办：交回执", "desc": "今天到期", "atText": "刚刚", "todoId": 3, "classId": 2, "className": "24软通班2班" },
      { "kind": "birthday", "title": "今天是 张小华 的生日", "desc": "可以去群里说声生日快乐", "atText": "今天", "studentId": 122, "classId": 1, "className": "24计算机软通1班" }
    ]
  }
}
```

| kind | 数据来源 | 排序 |
|---|---|---|
| `leave` | `t_leave_request` status=0（待审批） | createdAt 倒序 |
| `todo` | `t_todo` done=0 且 `due_at <= 今天 23:59` | dueAt 升序（越早到期越急） |
| `birthday` | `t_student` 月日=今天且 status=1 | 姓名 |

**三条设计约束**

1. **不落库、不记已读。** 通知是每次现算的：假批完、待办做完，那条通知自己就消失。建 `t_notice` 表就要维护"已读"，而"已读"是需要人点一下才成立的状态，最后一定和真实业务脱节。
2. **`total` 是真实条数，`list` 最多 limit 条。** 首页那个框只显示第一条 + 「N 条」，N 用 total。
3. **`atText` 由服务端拼。** 未来的时间（待办还没到期）不能算"多久之前"，服务端统一处理，避免前端各写一套。

**注意**：`leave` 这一类现在会真的出现了 —— 学生端（v0.39 起）能自己提交请假申请（status=0），
老师端首页和通知页都能收到。v0.43 之前这里写的是"学生端还没有账号，这条分支是为将来预留的"。

---

### 5.2 待办列表

`GET /api/teacher/todo/list`

**Query 参数**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| done | number | 否 | 不传=全部 | 0=未完成，1=已完成 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 2,
    "list": [
      { "id": 1, "title": "收齐国庆实践作业", "done": 0, "dueAt": "2026-10-25 18:00:00" },
      { "id": 2, "title": "整理期中考场", "done": 1, "dueAt": null }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| total | number | 符合条件的总条数 |
| list[].id | number | 待办 ID |
| list[].title | string | 待办内容 |
| list[].done | number | 0 未完成 / 1 已完成 |
| list[].dueAt | string | 截止时间，可为 `null` |

**排序**：`done ASC, due_at IS NULL, due_at ASC, created_at DESC`
（未完成的排前面；有截止时间的按时间早的在前；没有截止时间的排最后）

> ⚠️ 查询条件必须同时带 `teacher_id = 当前用户` 和 `class_id = 我的班`。少了 `teacher_id`，会看到别的老师的待办。

**错误**

| 场景 | code |
|---|---|
| 没带 token | 1002 |
| role 不是老师 | 3001 |

---

### 5.3 新增待办

`POST /api/teacher/todo`

**请求体**

```json
{ "title": "收齐国庆实践作业", "dueAt": "2026-10-25 18:00:00" }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| title | string | 是 | 内容，1~128 字 |
| dueAt | string | 否 | 截止时间，不传则为 `null` |

**响应**

```json
{ "code": 0, "message": "ok", "data": { "id": 3 } }
```

**错误**

| 场景 | code |
|---|---|
| title 为空 / 超过 128 字 | 1001 |
| 没绑班（不知道该挂到哪个班） | 1001，message「请先绑定班级」 |

---

### 5.4 完成 / 取消完成

`PUT /api/teacher/todo/{id}/done`

**为什么用 PUT 不用 PATCH**：小程序不支持 `PATCH`（见 §0 方法白名单）。路径已经落到 `done` 这个子资源上，`{"done":1}` 就是这个子资源的完整新值，`PUT` 的语义正好对。

**请求体**

```json
{ "done": 1 }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| done | number | 是 | 1=标记完成，0=取消完成 |

**响应**

```json
{ "code": 0, "message": "ok", "data": null }
```

**错误**

| 场景 | code |
|---|---|
| id 不存在 | 1004 |
| 这条待办不属于当前老师 | 3002 |

---

### 5.5 删除待办

`DELETE /api/teacher/todo/{id}`

**响应**

```json
{ "code": 0, "message": "ok", "data": null }
```

**错误**

| 场景 | code |
|---|---|
| id 不存在 | 1004 |
| 不属于当前老师 | 3002 |

> 待办是**物理删除**（直接 `DELETE`）。理由：待办是一次性任务，删了就删了，没有历史价值，也没有别的表引用它。
> 对比：学生是**软删除**（`status=0`），因为请假、成绩记录都还指向他。

**前端怎么用**

```js
// src/api/todo.js
import request from './request'

export const getTodoList = (params) => request({ url: '/teacher/todo/list', data: params })
export const addTodo      = (data)   => request({ url: '/teacher/todo', method: 'POST', data })
export const setTodoDone  = (id, done) => request({ url: `/teacher/todo/${id}/done`, method: 'PUT', data: { done } })
export const delTodo      = (id)     => request({ url: `/teacher/todo/${id}`, method: 'DELETE' })
```

---

## 6. 学生信息（学生 tab）

> 这是第一个完整的 CRUD 模块，后面成绩、座位、请假都照这个套路来。**建议这一节读两遍。**

### 6.1 学生列表

`GET /api/teacher/student/list`

**Query 参数**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| keyword | string | 否 | 空 | 同时模糊匹配 `name` 和 `student_no` |
| status | number | 否 | 1 | 1=在读，0=已离班，**-1=全部** |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 47,
    "list": [
      { "id": 1, "name": "王小明", "gender": 1, "birthday": "2009-03-12", "studentNo": "2026001", "status": 1 },
      { "id": 2, "name": "李思远", "gender": 2, "birthday": null,           "studentNo": null,      "status": 1 }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| total | number | 符合条件的总人数 |
| list[].id | number | 学生 ID（后续所有接口用它） |
| list[].name | string | 姓名 |
| list[].gender | number | 0 未知 / 1 男 / 2 女 |
| list[].birthday | string | `yyyy-MM-dd`，**可能为 `null`** |
| list[].studentNo | string | 学号，**可能为 `null`** |
| list[].status | number | 1 在读 / 0 已离班 |

**口径说明（三个坑）**

1. **不接收 `classId`**。老师带哪个班，后端从 token 反查。
2. **`student_no` 可以是 NULL**。所以排序必须写成 `ORDER BY student_no IS NULL, student_no`——否则 MySQL 会把 NULL 排在最前面，名单一开头全是没填学号的人。
3. **不分页**。一个班几十人，一次返回完，前端做本地过滤更快。

**错误**

| 场景 | code |
|---|---|
| 没带 token | 1002 |
| role 不是老师 | 3001 |
| 老师没绑班 | 返回 `{ "total": 0, "list": [] }`，**不报错**（首页能进，学生页空着，这是正常状态） |

---

### 6.2 学生详情

`GET /api/teacher/student/{id}`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 7,
    "name": "学生07",
    "gender": 2,
    "birthday": "2009-05-03",
    "studentNo": "2026007",
    "status": 1,
    "createdAt": "2026-10-22 08:48:42",

    "dormitory": "3栋502",
    "phone": "13800000001",
    "wechat": "zhangrt",
    "familyPhone1": "13900000001",
    "familyPhone2": null,
    "trainingDirection": "软件开发",
    "nation": "汉族",
    "politicalStatus": "共青团员",
    "partyActivist": "是",

    "seat": { "row": 1, "col": 7 },

    "leave": { "total": 2, "pending": 0, "lastDate": "2026-10-20", "lastType": 1 },

    "latestScore": {
      "examId": 2,
      "examName": "期中考试",
      "examDate": "2026-10-20",
      "subjects": [
        { "subject": "语文", "score": 107.1, "fullScore": 150 },
        { "subject": "数学", "score": null,  "fullScore": 150 },
        { "subject": "英语", "score": 102.9, "fullScore": 150 }
      ],
      "total": 210.0,
      "rank": 47,
      "classSize": 47
    }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| id / name / gender / birthday / studentNo / status | — | 与列表项完全一致 |
| createdAt | string | 入班时间，`yyyy-MM-dd HH:mm:ss` |
| dormitory | string \| null | 宿舍，如「3栋502」 |
| phone | string \| null | 学生本人手机号。**字符串，不是数字** |
| wechat | string \| null | 微信号 |
| familyPhone1 / familyPhone2 | string \| null | 家人电话 |
| trainingDirection | string \| null | 培训方向，如「软件开发」 |
| nation | string \| null | 民族，如「汉族」 |
| politicalStatus | string \| null | 政治面貌，如「共青团员」 |
| partyActivist | string \| null | 入党积极分子，**存原文**（「是」「否」「已递交申请」都可能） |
| seat | object \| null | `{row, col}`，**null = 还没排座**（不是 `{row:0,col:0}`） |
| leave | object \| null | **null = 一次都没请过假** |
| leave.total | number | 已通过 + 已销假的次数，**不含已驳回**（被驳回的没缺课） |
| leave.pending | number | 待审批次数 |
| leave.lastDate | string \| null | 最近一次请假的开始日期（跳过被驳回的） |
| leave.lastType | number | 1=病假 2=事假 3=其他 |
| latestScore | object \| null | **null = 这个人没有最近一次考试的成绩记录**。两种情况：班里还没录过成绩，或者那次考试时他还没在班里（转学生） |
| latestScore.subjects | array | **按考试设定的科目顺序**排，缺考也占一行（`score: null`） |
| latestScore.total | number \| null | 已录科目的总分，缺考不计入 |
| latestScore.rank | number \| null | 班内排名，同分并列；全班都缺考时为 `null` |
| latestScore.classSize | number | 分母，取成绩表行数（转学走的人不该算进分母） |

**为什么详情要多带三块，而不是让前端调三个接口**

详情页只服务一个人。让前端自己拼要发 4 个请求（详情 + 座位图 + 成绩列表 + 请假列表），
每个都可能失败，任何一个挂了整页就空着；弱网下还多 3 个来回。
一次查完更稳，也更快。

**为什么用 List 装各科而不是 Map**

`subjects` 要按考试设定的科目顺序画条形图。Map 的顺序由 JSON 序列化决定，靠不住。

**「缺考」和「没参加那次考试」必须分开**

| 数据库里的情况 | 含义 | 接口返回 |
|---|---|---|
| 有 `t_score` 行，`score` 是 `null` | 缺考 | `latestScore` 正常返回，那一科 `score: null` |
| 一行 `t_score` 都没有 | 那次考试时他不在班里（转学生） | `latestScore: null` |

不加这个判断的话，刚转来的新生详情页会显示「语文缺考 / 数学缺考 / 英语缺考」——
但他不是缺考，是那次考试还没他。这两件事对老师的意义完全不同：缺考要去问原因，没参加什么都不用管。

实现就是一次 `SELECT COUNT(*) FROM t_score WHERE exam_id=? AND student_id=?`。

**错误**

| 场景 | code |
|---|---|
| id 不存在 / 是 0 或负数 | 1004 |
| id 不是数字（`/student/abc`） | 1001 |
| 这个学生不在你的班 | 3002 |
| 当前用户不是老师 | 3001 |

> ⚠️ 查详情必须加 `AND class_id = 我的班`。只按 id 查，改个 URL 就能看别的班的学生。
>
> ⚠️ **1004 和 3002 不能合并**。合成一个「查不到」，前端就没法区分「这孩子被删了」和「你点错班了」，
> 排查越权问题时也看不出到底是哪种。

---

### 6.3 新增学生

`POST /api/teacher/student`

**请求体**

```json
{
  "name": "王小明",
  "gender": 1,
  "birthday": "2009-03-12",
  "studentNo": "2026001",

  "dormitory": "3栋502",
  "phone": "13800000001",
  "wechat": "wangxm",
  "familyPhone1": "13900000001",
  "familyPhone2": null,
  "trainingDirection": "软件开发",
  "nation": "汉族",
  "politicalStatus": "共青团员",
  "partyActivist": "是"
}
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| name | string | 是 | 1~32 字，去首尾空格后不能为空 |
| gender | number | 否 | 只能是 0/1/2，不传默认 0 |
| birthday | string | 否 | `yyyy-MM-dd`，不能是未来日期 |
| studentNo | string | 否 | ≤32 字；**同班内不能重复** |
| dormitory | string | 否 | ≤32 字 |
| phone | string | 否 | ≤20 字，**不做格式校验** |
| wechat | string | 否 | ≤64 字 |
| familyPhone1 / familyPhone2 | string | 否 | ≤20 字 |
| trainingDirection | string | 否 | ≤64 字 |
| nation | string | 否 | ≤32 字 |
| politicalStatus | string | 否 | ≤32 字 |
| partyActivist | string | 否 | ≤32 字 |

> **为什么 phone 不校验手机号格式**：家长电话可能是座机（0731-88888888）、可能带 +86、可能压根不是手机。
> 格式校验太严只会让老师「明明有号码却存不进去」。长度够防住脏数据就行。
>
> **为什么 partyActivist 存字符串而不是 0/1**：名单里可能写「是」「否」「已递交申请」，
> 统一成布尔会丢掉后半句。

**响应**

```json
{ "code": 0, "message": "ok", "data": { "id": 48 } }
```

**错误**

| 场景 | code |
|---|---|
| name 为空 | 1001 |
| gender 不在 0/1/2 | 1001 |
| birthday 格式错 / 是未来 | 1001 |
| 学号在本班已存在 | 1006，message「学号 2026001 已被 王小明 使用」 |

> **学号重复为什么要给 1006 而不是 1001**：1001 是"你填的格式不对"，用户自己能改；1006 是"和已有数据冲突"，需要用户去核对。前端可以给不同的提示样式。

**学号唯一性的三个细节**

| 细节 | 做法 | 为什么 |
|---|---|---|
| 范围 | 同班内，**含已离班的学生** | 学号是终身的。离班学生的成绩、请假还挂着这个号，给新生复用会让人对不上历史数据 |
| 空值 | `""` 和 `"   "` 一律存成 `NULL` | 否则两个没填学号的学生会因为都是空字符串而互相冲突 |
| 错误信息 | 「学号 T9001 已被 王小明 使用」 | 带上占用者姓名，老师不用再翻名单 |

**DTO 里没有 `classId` / `status`**

前端传 `{"classId": 2}` 想把人塞进别的班是无效的 —— Jackson 会忽略未知字段，学生永远落在 `token` 对应的那个班。
`status` 也一样，新增的人一律 `1`（在读）。**DTO 的字段列表就是权限边界。**

---

### 6.4 编辑学生

`PUT /api/teacher/student/{id}`

**请求体**：与新增完全一致（**全量提交**，前端把整个对象传回来，不要只传改动的字段）。

```json
{
  "name": "王小明", "gender": 1, "birthday": "2009-03-12", "studentNo": "2026001",
  "dormitory": "3栋502", "phone": "13800000001", "wechat": "wangxm",
  "familyPhone1": "13900000001", "familyPhone2": null,
  "trainingDirection": "软件开发", "nation": "汉族",
  "politicalStatus": "共青团员", "partyActivist": "是"
}
```

> **9 个扩展字段一个都不能漏。** 全量提交意味着「没传 = 清空」。
> 前端 `edit.vue` 用一个 `EXTRA_FIELDS` 数组统一遍历，就是为了避免
> 「编辑页只改了姓名，保存后手机号没了」这种漏字段的 bug。

**响应**

```json
{ "code": 0, "message": "ok", "data": null }
```

**错误**

| 场景 | code |
|---|---|
| id 不存在 | 1004 |
| 学生不在你的班 | 3002 |
| 学号被同班其他人占用 | 1006 |

> **为什么 PUT 要全量而不是局部**：局部更新会让"清空生日"这种操作无法表达（传 `null` 到底是"不改"还是"清空"？）。全量提交没这个歧义。前端编辑页本来就有完整数据，传全量不费事。

**实现上有个坑：不能用 `updateById`**

MyBatis-Plus 的 `updateById` 走的是实体的非空字段策略，**`null` 字段会被跳过**。
老师把生日清空时，生日会保持旧值 —— 和"全量提交"的语义直接冲突。

必须用 `LambdaUpdateWrapper.set()`，它会无条件写入，包括 `null`：

```java
studentMapper.update(null, new LambdaUpdateWrapper<Student>()
        .eq(Student::getId, studentId)
        .eq(Student::getClassId, classId)   // 兜底，防将来有人漏掉上面的校验
        .set(Student::getName, name)
        .set(Student::getBirthday, dto.getBirthday())   // null 也会写进去
        .set(Student::getStudentNo, no));
```

**前端怎么传空值**：生日清空后传 `birthday: null`，学号清空后传 `studentNo: null`。
不传这个字段和传 `null` 在后端是一回事，但传 `null` 语义更明确，也方便日志排查。

---

### 6.5 移出班级（软删除）

`DELETE /api/teacher/student/{id}`

**实际做的事**：`UPDATE t_student SET status = 0 WHERE id = ? AND class_id = ?`

**不物理删除的理由**：请假记录、成绩记录都还指向这个学生。真删了，历史数据就成了孤儿，以后查"上学期的请假"会查出 `student_name = null`。

**响应**

```json
{ "code": 0, "message": "ok", "data": null }
```

**错误**

| 场景 | code |
|---|---|
| id 不存在 | 1004 |
| 学生不在你的班 | 3002 |

**幂等**：已经离班的再调一次，返回成功而不是报错。老师网络卡了一下点两次，第二次不该弹「操作失败」。

**顺手清座位**：移出时把 `t_seat` 里这个人占的格子置空（`student_id = NULL`）。

座位读接口本来就会把离班学生的格子当空位返回，所以不清也「看起来」是对的 ——
但数据库里还留着他占着 3 排 5 列，下次有人查库会以为数据错了。
这是第二个写操作，所以 `remove()` 上加了 `@Transactional`。

**前端怎么用**

```js
// src/api/student.js
import request from './request'

export const getStudentList = (params) => request({ url: '/teacher/student/list', data: params })
export const getStudent      = (id)     => request({ url: `/teacher/student/${id}` })
export const addStudent      = (data)   => request({ url: '/teacher/student', method: 'POST', data })
export const updateStudent   = (id, data) => request({ url: `/teacher/student/${id}`, method: 'PUT', data })
export const removeStudent   = (id)     => request({ url: `/teacher/student/${id}`, method: 'DELETE' })
```

---

### 6.6 名单导入 · 预览

`POST /api/teacher/student/import/preview`

`multipart/form-data`，字段名固定为 `file`。**不写库**，只返回「会发生什么」。

**请求**

```
Content-Type: multipart/form-data
file=@名单.xlsx
```

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sheetName": "软通动力",
    "headerRowNo": 2,
    "dataRows": 16,
    "blankRows": 0,

    "columns": [
      { "column": 0, "header": "序号",   "field": null,         "fieldLabel": null,     "mapped": false },
      { "column": 1, "header": "姓名",   "field": "NAME",       "fieldLabel": "姓名",   "mapped": true },
      { "column": 2, "header": "学号",   "field": "STUDENT_NO", "fieldLabel": "学号",   "mapped": true },
      { "column": 13, "header": "班级",  "field": "CLASS_NAME", "fieldLabel": "班级",   "mapped": true }
    ],
    "unmappedHeaders": ["序号", "班主任"],

    "className": "628班",
    "otherClassNames": ["629班"],

    "stats": {
      "importable": 12,
      "create": 11,
      "duplicate": 1,
      "error": 4,
      "noStudentNo": 1
    },

    "rows": [
      {
        "excelRowNo": 3,
        "name": "张软通", "studentNo": "2028001", "gender": 1, "birthday": null,
        "dormitory": "3栋502", "phone": "13800000001", "wechat": "zhangrt",
        "familyPhone1": "13900000001", "familyPhone2": null,
        "trainingDirection": "软件开发", "nation": "汉族",
        "politicalStatus": "共青团员", "partyActivist": "是",
        "action": "create", "reason": null, "errors": null
      },
      {
        "excelRowNo": 5, "name": "王测试", "studentNo": "2026001", "gender": 1,
        "action": "duplicate",
        "reason": "学号 2026001 已存在（学生01）",
        "errors": null
      },
      {
        "excelRowNo": 8, "name": "孙运维", "studentNo": "2028001", "gender": 1,
        "action": "error",
        "reason": "学号 2028001 和第 3 行重复",
        "errors": ["学号 2028001 和第 3 行重复"]
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| sheetName | string | 读的是哪个工作表 |
| headerRowNo | number | 表头在 **Excel 第几行**（不是数组下标） |
| dataRows | number | 数据行数，不含表头和空行 |
| columns | array | 每一列一个，`mapped=false` 表示没认出来 |
| unmappedHeaders | array | 没认出来的列名，只是提示，不拦截导入 |
| className | string | **本班的班名**（库里存的那个）。见下方「班级对不上」 |
| otherClassNames | array | 名单「班级」列里出现过的、和本班对不上的名字，去重后最多 5 个。只用于提示，不参与校验 |
| stats.importable | number | = create + duplicate |
| stats.create | number | 会新增的行数 |
| stats.duplicate | number | 学号已存在、等老师决定的行数 |
| stats.error | number | 有问题、怎么都导不进去的行数 |
| stats.noStudentNo | number | 名单里没学号的行数。**这些人无法判断是否重复，会全部新增** |
| rows[].excelRowNo | number | Excel 行号，报错时用它定位 |
| rows[].action | string | `create` / `duplicate` / `error` |
| rows[].reason | string \| null | 一句话说明，`duplicate` 时带占用者姓名 |
| rows[].errors | array \| null | 这一行的**所有**问题，不是只报第一个 |

**表头自动识别怎么做的**

拿前 10 行逐个当「表头候选」，每命中一个已知字段名加一分，**分最高且包含「姓名」**的那一行就是表头行。

- 精确匹配优先（整串相等），其次才是包含匹配
- 包含匹配**跳过「姓名」**：「家长姓名」「班主任姓名」都含「姓名」，放进来会把家长名字当学生名字
- 别名按长度降序试：「家人电话1」必须先于「家人电话」被匹配

**错误**

| 场景 | code | message |
|---|---|---|
| 没传文件 / 空文件 | 1001 | 请选择要导入的文件 |
| 超过 5MB | 1001 | 文件超过 5MB… |
| `.xls` | 1001 | 暂不支持老版 .xls，请另存为 .xlsx |
| `.csv` | 1001 | 暂不支持 .csv，请另存为 .xlsx |
| 把 .xls 改名成 .xlsx | 1001 | 这不是有效的 xlsx 文件… |
| 前 10 行找不到「姓名」列 | 1001 | 没找到「姓名」列… |
| 没绑定班级 | 1001 | 你还没有绑定班级，不能导入学生 |
| 未登录 | 1002 | 请先登录 |

**班级对不上：整张表被拦时怎么提示**

名单里有「班级」列时，每一行都要和本班班名比对（`TextNormalize.sameClass`，
`628班` / `628 班` / `628（1）班` 都算同一个班）。对不上的行判 `error`，不进库。

这是**故意拦**的：真实名单常常是年级大表，69 个人跨 9 个班，
全导进一个班之后再一条条删比拦住贵得多。

但「整张表都被拦」和「混进几个人」是两回事。前者的原因往往是
**库里的班名起错了**（比如注册时随手填的测试名），老师不知道能改，
就会卡在这里。所以：

- `className` 告诉前端「本班叫什么」
- `otherClassNames` 告诉前端「名单里写的是什么」

前端据此显示一张卡：「整张表都不是「628班」的学生，名单里写的是 629班」
+ 一个「去改班级名」按钮（跳班级设置页，见 §6.8）。

> **没有做「忽略班级列」开关**。看似方便，实则危险：
> 打开它就能把 9 个班的人一次导进 1 个班，而且导完没有任何提示。
> 真需要这样做的场景（比如把拆开的表合并）应该在 Excel 里解决，不是在导入页放一个开关。

---

### 6.7 名单导入 · 确认

`POST /api/teacher/student/import/commit`

**请求体**

```json
{
  "mode": "skip",
  "rows": [ /* 就是 6.6 返回的 rows，原样回传 */ ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| mode | string | 是 | `skip`=学号已存在的跳过；`update`=用名单覆盖 |
| rows | array | 是 | 预览返回的 rows，**前端不做任何加工** |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "created": 11,
    "updated": 0,
    "skipped": 1,
    "failed": 0,
    "failures": []
  }
}
```

`failures` 里每条是 `{ excelRowNo, name, reason }`，只列失败的 —— 69 行成功不需要看 69 条。

**`mode` 的语义**

| mode | 学号已存在时 | 什么时候用 |
|---|---|---|
| `skip` | 库里那几个人一个字都不动 | 名单是补充材料，不想动已有数据 |
| `update` | 覆盖姓名、性别、生日、9 个扩展字段 | 名单是最新版本，要以它为准 |

`update` **不覆盖 `status`**：名单里没有「在读/离班」这一列，一个已离班的学生被重新导进来不该自动复学。
`update` 也不覆盖 `classId`：导入的目标班永远是 token 对应的班。

**三条实现约定**

| 约定 | 原因 |
|---|---|
| **commit 重新校验每一行**，前端传的 `action` 一概不看 | 预览到提交之间可能隔了几分钟，别人可能刚加了同学号的学生；前端传的数据本来就不可信 |
| **单行失败不回滚整批**（没加 `@Transactional`） | 导入是「尽量多成」，不是「全成或全不成」。69 行里 1 行撞唯一键，让另外 68 行白导，老师得重来 |
| **同一份 rows 里学号重复会被拦下** | 否则第二条 insert 会撞唯一键，报出来的错误跟「学号已存在」长得一样，看不出是自己表里重复 |

**错误**

| 场景 | code |
|---|---|
| rows 为空 | 1001「没有可导入的数据」 |
| mode 不是 skip/update | 1001「重复学生只能选 skip 或 update」 |
| 没绑定班级 | 1001 |
| 未登录 | 1002 |

> **已知限制**：名单里没学号的行，导两次会重复插入。
> 因为没有任何可靠字段能判断「这个人是新同学还是老同学」——
> 靠姓名猜，同名同姓太常见。所以 `stats.noStudentNo` 会把这个数字显眼地告诉老师。

---

```js
// src/api/studentImport.js
import { previewImport, commitImport } from '@/api/studentImport'

// previewImport 走的是 uni.uploadFile（不是 request），因为要传文件
// commitImport 走 request，普通的 JSON 提交
```

---

### 6.8 班级设置（读 / 改）

> **为什么单独一节**：班级名不是装饰。它同时是首页顶部的标题、
> 名单导入时校验「这行是不是本班」的依据、导出表格的表头。
> 名字起错了（比如注册时随手写的「628班」），导入会被整表拦下，
> 而老师在小程序里**找不到任何地方能改**。这一节补的就是这个入口。

#### 6.8.1 读班级信息

`GET /api/teacher/class`

**路径里没有 `{id}`**，这是刻意的。班级 id 只能从 token 里的 `userId` 反查 ——
一旦允许前端传 id，就得在每个方法里校验「这个班是不是你的」。

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "name": "628班",
    "grade": "高一",
    "termStartDate": "2026-09-03",
    "inviteCode": "628628",
    "studentCount": 47
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 班级 id。前端不用它做任何请求，只用来判断「有没有绑班」 |
| name | string | 班级名，如「628班」「2028届软通1班」 |
| grade | string | 年级，如「高一」。**可能是空串**，注册时没填就是空 |
| termStartDate | string | 本学期第一周的周一，`yyyy-MM-dd`。首页「第几周」按它算 |
| inviteCode | string | 学生加入班级用的邀请码。**只读** |
| studentCount | number | 在读学生数（`status=1`）。设置页显示「本班 47 人」 |

> `studentCount` 存在的理由：改班名是低频但影响面很大的操作。
> 显示「本班 47 人」能让老师确认「我改的是哪个班」，防手滑。

#### 6.8.2 改班级信息

`PUT /api/teacher/class`

**全量提交**，只认三个字段。

**请求**

```json
{
  "name": "2028届软通1班",
  "grade": "高一",
  "termStartDate": "2026-09-03"
}
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| name | string | ✅ | `@NotBlank`，`@Size(max=32)` |
| grade | string | ❌ | `@Size(max=16)`，传空串等于清空 |
| termStartDate | string | ✅ | `@NotNull`，`yyyy-MM-dd`。数据库里是 NOT NULL |

**响应**：`{ "code": 0, "message": "ok", "data": null }`

**错误**

| 场景 | code | message |
|---|---|---|
| 班名空 / 全空格 | 1001 | 班级名称不能为空 |
| 班名超 32 字 | 1001 | 班级名称最多 32 个字 |
| 没传开学日期 | 1001 | 请选择开学日期 |
| 没绑定班级 | 1001 | 你还没有绑定班级 |
| 非老师 | 3001 | 当前用户不是老师，无权访问 |
| 未登录 | 1002 | 请先登录 |

**三个实现约定**

1. **DTO 的字段列表就是权限边界**。`ClassSaveDTO` 里没有 `inviteCode` 和 `status`，
   前端传了也会被 Jackson 忽略（已实测：夹带 `"inviteCode":"HACKED"` 后库里仍是原值）。
   原因：改邀请码会让已经发出去的码全部失效；归档班级是危险操作。
   这两件事要做也得是独立接口 + 二次确认，不能混在「改班名」的表单里。

2. **用 `LambdaUpdateWrapper.set()` 显式列字段，不用 `updateById`**。
   后者会跳过 null —— 老师把年级清空（传空串）时能生效，但如果哪天
   允许传 null，`updateById` 会静默不更新，看起来像「保存了但没变」。
   这里 `grade == null` 统一转成 `""`。

3. **不加 `@Transactional`**。单表单行 UPDATE 本身就是一个原子操作。

**前端惯例：这个入口放在哪**

小程序里改「班级/空间」这类归属信息的通行做法是两个入口指向同一页：

- **首页顶部点班级名** —— 老师的直觉是「我要改这个名字，就点这个名字」
- **首页右上角「设置」** —— 找设置的人会去点齿轮

只做一个都会有人找不到。`pages/teacher/class/setting.vue` 同时服务这两个入口。

---

## 7. 成绩分析（成绩 tab）

> ⚠️ **本节依赖两张还没建的表**，建表语句见 §12 附录 A。做这一节之前先执行 DDL。

### 7.1 数据怎么存（先理解这个，接口才看得懂）

成绩是**两次考试成绩 × N 个学生 × M 个科目**的三维数据。存法有两种，本项目选第二种：

| 方案 | 结构 | 优缺点 |
|---|---|---|
| ❌ 宽表 | 一行 = 一个学生，列是 `语文/数学/英语...` | 加科目要改表结构，不同考试科目还不一样，放弃 |
| ✅ 窄表 | 一行 = 一个学生的一科成绩 | 加科目只是多几行数据，表结构永不变 |

所以有 **两张表**：

```
t_exam  （一次考试一条：月考一、期中…）
   └── t_score （一行 = 某学生某科的成绩）
```

一次月考，47 个学生 × 3 科 = 141 行 `t_score`。查询时按 `student_id` 聚合回来，前端看到的就是"一个学生一行"。

### 7.2 考试列表

`GET /api/teacher/exam/list`

**Query 参数**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| page | number | 否 | 1 | 页码 |
| size | number | 否 | 20 | 每页条数 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 3,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 2,
        "name": "第一次月考",
        "type": 2,
        "examDate": "2026-10-10",
        "subjects": ["语文", "数学", "英语"],
        "scoreCount": 141
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| list[].id | number | 考试 ID |
| list[].name | string | 考试名称 |
| list[].type | number | 1 周测 / 2 月考 / 3 期中 / 4 期末 |
| list[].examDate | string | 考试日期 |
| list[].subjects | string[] | 本次考的科目，按录入顺序 |
| list[].scoreCount | number | 已录入的成绩条数，`0` 表示还没录 |

**排序**：`exam_date DESC`（最近的考试排最前）

> `subjects` 为什么要存下来：不同考试科目不一样（月考考 6 科，周测只考数学），前端表头要按这个数组动态生成，不能写死。

---

### 7.3 新建考试

`POST /api/teacher/exam`

**请求体**

```json
{
  "name": "第一次月考",
  "type": 2,
  "examDate": "2026-10-10",
  "subjects": ["语文", "数学", "英语"],
  "fullScore": { "语文": 150, "数学": 150, "英语": 150 }
}
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| name | string | 是 | 1~64 字 |
| type | number | 否 | 1/2/3/4，默认 2 |
| examDate | string | 是 | `yyyy-MM-dd` |
| subjects | string[] | 是 | 至少 1 个，每个 ≤16 字，**不能重复** |
| fullScore | object | 否 | 键必须是 subjects 里的科目，值 >0。**不传或漏传的科目按 100** |

> `fullScore` 是 v0.3 加的。原本打算复用 `t_score.full_score`，但那样"还没录成绩"时满分就是未知的，及格线算不出来。改成建考试时一次定好，存进 `t_exam.full_score`（JSON 字符串），录入成绩时再抄到每一行 `t_score.full_score`。

**响应**

```json
{ "code": 0, "message": "ok", "data": { "id": 3 } }
```

**错误**

| 场景 | code |
|---|---|
| subjects 为空 / 有重复项 | 1001 |
| 同班同名考试已存在 | 1006 |

**前端页面：`pages/teacher/score/exam-edit.vue`**

入口有两处，都指向同一页：成绩页右上角「＋ 新建」、以及一场考试都没有时的底部主按钮。

> 底部那个按钮原来在没考试时只 toast「先建一次考试」—— 等于把一条死路做成了按钮。
> 现在它直接变成「新建考试」。

三个实现细节：

1. **`fullScore` 只提交真的填了的科目**。留空的不放进 map，后端按 100 处理。
   「老师没设」和「老师设成 100」以后要区分时，还能分得开。
   实测：只填语文 150 → 库里存 `{"语文":150}`，前端显示总分上限 `150+100+100=450`。
2. **建完要通知成绩页**。成绩页会「保持上次选中的考试」，不通知的话
   老师刚建完看到的还是上一场，会以为没建成。用 `uni.$emit('exam:created', id)`。
3. **科目用「预置芯片 + 自定义」**，都进同一个 `selected` 数组，取消时点一下就掉。
   自定义的单独存一份，取消勾选后仍留在选片区里（不然加错了就找不回来）。

---

### 7.4 某次考试的成绩

`GET /api/teacher/score/list?examId=2`

**Query 参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| examId | number | 是 | 考试 ID |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "exam": {
      "id": 2,
      "name": "第一次月考",
      "type": 2,
      "examDate": "2026-10-10",
      "subjects": ["语文", "数学", "英语"],
      "fullScore": { "语文": 150, "数学": 150, "英语": 150 }
    },
    "list": [
      {
        "studentId": 1,
        "studentName": "王小明",
        "studentNo": "2026001",
        "scores": { "语文": 128, "数学": 141, "英语": 136 },
        "total": 405,
        "rank": 1
      },
      {
        "studentId": 2,
        "studentName": "李思远",
        "studentNo": null,
        "scores": { "语文": 110, "数学": null, "英语": 98 },
        "total": 208,
        "rank": 2
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| exam.fullScore | object | 各科满分，键是科目名。未设置时统一 100 |
| list[].scores | object | **键是科目名，值是分数**。没考的科目为 `null`，不是 0 |
| list[].total | number | 已录科目的总分，`null` 的科目不计入 |
| list[].rank | number | 按 `total` 降序排名，**后端算**；同分并列（1、1、3） |

**为什么 `scores` 用对象而不是数组**：

```json
{ "语文": 128, "数学": 141 }      // ✅ 前端直接 scores['语文']，表头一变也不用改代码
[{ "subject": "语文", "score": 128 }]  // ❌ 前端要 find() 一遍才能取值
```

> ⚠️ `null` 和 `0` 必须区分：`null` 是"缺考"，`0` 是"考了但 0 分"。总分计算、及格率统计都依赖这个区别。

**错误**

| 场景 | code |
|---|---|
| examId 不传 | 1001 |
| 考试不存在 | 1004 |
| 考试不属于你的班 | 3002 |

---

### 7.5 批量录入成绩

`POST /api/teacher/score/batch`

> **一次提交整张表，不要一个学生一个请求。** 47 个学生就是 47 次请求，小程序会卡死。

**请求体**

```json
{
  "examId": 2,
  "rows": [
    { "studentId": 1, "scores": { "语文": 128, "数学": 141, "英语": 136 } },
    { "studentId": 2, "scores": { "语文": 110, "数学": null, "英语": 98 } }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| examId | number | 是 | 考试 ID |
| rows | array | 是 | 至少 1 行，最多 200 行（一个班的上限） |
| rows[].studentId | number | 是 | 必须是本班学生 |
| rows[].scores | object | 是 | 键必须是本次考试的 `subjects` 里的科目；值 `null` 表示缺考 |

**响应**

```json
{ "code": 0, "message": "ok", "data": { "saved": 141 } }
```

`saved` = 实际写入 / 更新的成绩条数。

**错误**

| 场景 | code |
|---|---|
| 行数超过 200 | 1001 |
| 有学生的 `studentId` 不在本班 | 3002 |
| 有科目名不在本次考试的 `subjects` 里 | 1001，message 指出是哪个科目 |
| 分数为负 / 超过满分 | 1001 |

**后端实现要点**

- **幂等**：同一个 `(examId, studentId, subject)` 已存在就 `UPDATE`，不存在就 `INSERT`。用 `INSERT ... ON DUPLICATE KEY UPDATE` 一句搞定（依赖 `uk_exam_student_subject` 唯一键）。
- **事务**：整个 `rows` 在一个 `@Transactional` 里，要么全成要么全滚。不能出现"前 20 个学生存了，第 21 个报错"。
- **先校验再写**：把所有 `studentId` 一次性查出来核对班级归属，别在循环里逐个查（N+1 查询）。

---

### 7.6 成绩统计

`GET /api/teacher/score/stats?examId=2`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "examId": 2,
    "gradeRank": 3,
    "gradeClassCount": 12,
    "subjects": [
      {
        "subject": "语文",
        "avg": 112.4,
        "max": 138,
        "min": 86,
        "passRate": 0.89,
        "fullScore": 150
      }
    ],
    "totalAvg": 335.2,
    "totalMax": 405,
    "totalMin": 240
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| gradeRank | number | 本班**平均总分**在同年级班级中的名次。**算不出来时为 `null`** |
| gradeClassCount | number | 同年级参与统计的班级数，`null` 时前端显示「未统计」 |
| subjects[].avg | number | 平均分，保留 1 位小数 |
| subjects[].passRate | number | 及格率，`0~1` 的小数（前端 `×100` 显示成百分比），及格线 = 满分的 60% |
| totalAvg / totalMax / totalMin | number | 总分的三个统计值 |

**年级排名怎么算（口径必须统一）**

同年级 = `t_class.grade` 相同的所有班级。按**班级平均总分**排名，不是按学生个人。

> ⚠️ **不能按 `exam_id` 跨班对齐。** `t_exam` 是**按班**存的：628 班的「期中考试」是 id=2，
> 629 班的「期中考试」是另一个 id。拿 `examId` 去查永远只查到本班，年级排名恒等于 1。
> 必须用「考试名 + 考试日期」这两个跨班一致的字段来匹配。

```sql
-- 先算每个学生的总分，再算每个班的平均总分
SELECT t.class_id AS classId, AVG(t.stu_total) AS avgTotal
FROM (
    SELECT sc.class_id, sc.student_id, SUM(sc.score) AS stu_total
    FROM t_score sc
    JOIN t_exam  e ON e.id = sc.exam_id
    WHERE e.name      = ?          -- 本场考试的 name
      AND e.exam_date = ?          -- 本场考试的 exam_date
      AND e.class_id IN (SELECT id FROM t_class WHERE grade = ?)   -- 同年级
      AND sc.score IS NOT NULL
    GROUP BY sc.class_id, sc.student_id
) t
GROUP BY t.class_id
ORDER BY avgTotal DESC
```

拿到结果后，本班在这个降序列表里的位置 + 1 就是名次。
列表长度 = `gradeClassCount`。

**什么情况下返回 `null`**

| 情况 | 返回 |
|---|---|
| 同年级只有本班一个班在系统里 | `gradeRank = null` |
| 有其他班，但这次考试他们还没录成绩 | `gradeRank = null`（不能拿 0 分当分母） |
| 本班自己还没录成绩 | `gradeRank = null` |

> ⚠️ **不要返回 1**。只有一个班时排第 1 是假象，老师会误以为「我们年级第一」。返回 `null`，前端显示「未统计」，比撒谎强。

**前端怎么显示**

```js
// gradeRank 为 null 时显示「未统计」，不要显示「第 null 名」
const rankText = computed(() => {
  const d = stats.value
  if (!d || d.gradeRank == null) return '未统计'
  return `年级第 ${d.gradeRank}`
})
```

> `passRate` 用小数不用百分数（`0.89` 而不是 `89`），因为小数能直接参与计算和画图。**前端负责 `(0.89 * 100).toFixed(0) + '%'`。**

**错误**：同 §7.4。

**前端怎么用**

```js
// src/api/score.js
import request from './request'

export const getExamList  = (params)   => request({ url: '/teacher/exam/list', data: params })
export const addExam      = (data)     => request({ url: '/teacher/exam', method: 'POST', data })
export const getScoreList = (examId)   => request({ url: '/teacher/score/list', data: { examId } })
export const saveScores   = (data)     => request({ url: '/teacher/score/batch', method: 'POST', data })
export const getScoreStats= (examId)   => request({ url: '/teacher/score/stats', data: { examId } })
```

---

## 8. 座位表（座位 tab）

### 8.1 座位图怎么存

座位是**二维网格**：`第几排 × 第几列 → 坐的是谁`。

存法：`t_seat` 一行 = **一个格子**（不是一个人）。空座位也有一行，只是 `student_id` 为 `NULL`。

```
t_seat: (class_id, row_no, col_no, student_id)
        1班      3       5       王小明
        1班      6       8       NULL      ← 最后一格没人
```

> **为什么空座位也要存**：如果只存有人的格子，`rows`/`cols` 就只能靠 `max(row_no)`/`max(col_no)` 反推。最后一排整排没人时，排数会凭空少一排 —— 老师明明设了 6 排，保存完再打开变成 5 排。存满格子就没这个问题：有几行数据就是几排几列。代价是 6×8 的班多存十几行，一年下来也就几百行。

### 8.2 读座位图

`GET /api/teacher/seat/chart`

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "rows": 6,
    "cols": 8,
    "seats": [
      { "row": 1, "col": 1, "studentId": 1,  "studentName": "王小明", "gender": 1 },
      { "row": 1, "col": 2, "studentId": 2,  "studentName": "李思远", "gender": 2 },
      { "row": 6, "col": 8, "studentId": null, "studentName": null,  "gender": null }
    ],
    "unseated": [
      { "studentId": 48, "studentName": "转学生", "gender": 1 }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| rows | number | 排数（纵向），默认 6 |
| cols | number | 列数（横向），默认 8 |
| seats | array | **rows × cols 个格子，空位也在里面**，前端按 `row`/`col` 定位 |
| seats[].row | number | 第几排，**从 1 开始**（不是 0，前端别减 1） |
| seats[].col | number | 第几列，从 1 开始 |
| seats[].studentId | number | 学生 ID，空座位为 `null` |
| seats[].studentName | string | 姓名，空座位为 `null` |
| seats[].gender | number | 性别，空座位为 `null`（前端用它给座位卡上色） |
| unseated | array | 本班在读但还没排座的学生（座位表下方的「未排座」条） |

**为什么 `rows`/`cols` 也要返回**：教室布局每个班不一样，不能前端写死 6×8。后端按 `max(row_no)` / `max(col_no)` 推算，因为空格子也存了行，这个最大值就是老师上次设的布局。

**为什么多返回 `unseated`**：前端要显示「还有 3 人未排座」，还要让老师点一下把人放进空位。如果让前端再调一次学生列表自己算差集，页面会出现「座位画好了、未排座人数还是 0」的半截状态，也白白多一次请求。

> 座位指向的学生如果已离班（`status=0`）或已删除，这一格按**空位**返回 —— 离班学生的名字不能一直挂在座位上。


**错误**

| 场景 | code |
|---|---|
| 没带 token | 1002 |
| role 不是老师 | 3001 |
| 没绑班 | `{ "rows": 6, "cols": 8, "seats": [] }` |

---

### 8.3 保存座位图

`PUT /api/teacher/seat/chart`

**整图覆盖保存**——前端拖完座位，把整张图传回来，后端先清空本班座位再全量写入。

**请求体**

```json
{
  "rows": 6,
  "cols": 8,
  "seats": [
    { "row": 1, "col": 1, "studentId": 1 },
    { "row": 1, "col": 2, "studentId": 2 },
    { "row": 2, "col": 1, "studentId": null }
  ]
}
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| rows | number | 是 | 1~20 |
| cols | number | 是 | 1~20 |
| seats | array | 是 | 可以传空数组（清空座位） |
| seats[].row / col | number | 是 | 必须在 `1~rows` / `1~cols` 范围内 |
| seats[].studentId | number | 是 | 可以是 `null`（表示这个位置空着） |

> **`seats` 可以只传有人的格子**：后端会按 `rows × cols` 建一张全空的图，再把传上来的填进去，最后连空格子一起落库。所以前端传 47 条还是 48 条都行，行列数不会丢。传空数组 = 清空座位（格子还在，只是都没人）。

**响应**

```json
{ "code": 0, "message": "ok", "data": { "saved": 47 } }
```

`saved` 是**已排座的学生人数**，不是写入的行数（写入的行数是 `rows × cols`，含空格子）。前端拿它显示「已保存 · 47 人」。

**错误**

| 场景 | code |
|---|---|
| 没绑班 | 1001 |
| rows/cols 缺失或超范围 | 1001 |
| `seats` 字段缺失（`null`） | 1001 |
| 座位 `row` / `col` 缺失或越界 | 1001，message 指出是第几排第几列 |
| 同一个 `(row, col)` 出现两次 | 1001 |
| 同一个学生被排到两个座位 | 1001，message 指出是谁 |
| 有 `studentId` 不在本班（或不存在） | 3002 |
| 有 `studentId` 已离班（`status=0`） | 1001 |

**后端实现要点**

- **整表覆盖**要放在一个事务里：`DELETE FROM t_seat WHERE class_id = ?` → 批量 `INSERT`。
- 删之前先校验（同一个学生不能坐两个位置），校验失败直接抛异常，别删了才发现数据有问题。
- 事务隔离级别用默认的 `REPEATABLE READ` 就行，不要动。

> **为什么不提供"移动一个座位"的接口**：拖拽场景下前端本来就有整张图，整表覆盖最简单、最不容易出错。做单点移动会引入"两个人交换座位"的中间态问题。

**前端怎么用**

```js
// src/api/seat.js
import request from './request'

export const getSeatChart  = ()       => request({ url: '/teacher/seat/chart' })
export const saveSeatChart = (data)   => request({ url: '/teacher/seat/chart', method: 'PUT', data })
```

---

## 9. 请假登记（请假 tab）

### 9.1 请假列表

`GET /api/teacher/leave/list`

**Query 参数**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| status | number | 否 | -1 | -1=全部，0 待审批，1 已通过，2 已驳回，3 已销假 |
| page | number | 否 | 1 | 页码 |
| size | number | 否 | 20 | 每页条数 |

**响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 12,
    "page": 1,
    "size": 20,
    "list": [
      {
        "id": 5,
        "studentId": 1,
        "studentName": "王小明",
        "type": 1,
        "startAt": "2026-10-19 08:00:00",
        "endAt": "2026-10-20 18:00:00",
        "reason": "发烧 38.5 度，医院开了证明",
        "status": 0,
        "createdAt": "2026-10-19 07:12:30"
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| list[].id | number | 请假记录 ID |
| list[].studentName | string | **JOIN `t_student` 拿到的姓名**，前端不用再查一次 |
| list[].type | number | 1 病假 / 2 事假 / 3 其他 |
| list[].startAt / endAt | string | 请假起止时间（含时分秒） |
| list[].reason | string | 原因，可为 `null` |
| list[].status | number | 0 待审批 / 1 已通过 / 2 已驳回 / 3 已销假 |
| list[].createdAt | string | 提交时间 |

**排序**：`status = 0 优先，然后 created_at DESC`
（待审批的排最前——老师打开页面第一眼要看到"还有几条要我批"）

**为什么这里要分页、学生列表不用**：请假记录一个学期累积几百条，不分页会越查越慢。学生名单是固定的几十人。

**错误**

| 场景 | code |
|---|---|
| 没带 token | 1002 |
| role 不是老师 | 3001 |
| 没绑班 | `{ "total": 0, "list": [], "page": 1, "size": 20 }` |

---

### 9.2 审批请假

`PUT /api/teacher/leave/{id}/audit`

**请求体**

```json
{ "status": 1, "remark": "已电话确认家长，同意" }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| status | number | 是 | **只能传 1（通过）或 2（驳回）**，传别的返回 1001 |
| remark | string | 否 | 审批备注，≤255 字 |

**响应**

```json
{ "code": 0, "message": "ok", "data": null }
```

**错误**

| 场景 | code |
|---|---|
| status 不是 1 或 2 | 1001 |
| 记录不存在 | 1004 |
| 记录不属于你的班 | 3002 |
| 当前状态不是"待审批"（0） | 1001，message「该请假已处理过」 |

> **状态机要卡死**：只能 `0 → 1` 或 `0 → 2`。已经通过的不许再驳回，否则数据会乱。
> 销假（`1 → 3`）是另一个动作，本轮不做，以后加 `PUT /{id}/cancel`。

---

### 9.3 代学生请假

`POST /api/teacher/leave`

> 场景：家长微信跟老师请假，老师在后台直接录一条。**这条记录默认 `status = 1`（已通过）**，因为老师录的就是已经批准的。

**请求体**

```json
{
  "studentId": 1,
  "type": 1,
  "startAt": "2026-10-19 08:00:00",
  "endAt": "2026-10-20 18:00:00",
  "reason": "发烧，家长已电话告知"
}
```

| 字段 | 类型 | 必填 | 校验 |
|---|---|---|---|
| studentId | number | 是 | 必须是本班学生 |
| type | number | 是 | 1/2/3 |
| startAt | string | 是 | `yyyy-MM-dd HH:mm:ss` |
| endAt | string | 是 | 必须 **晚于** `startAt` |
| reason | string | 否 | ≤255 字 |

**响应**

```json
{ "code": 0, "message": "ok", "data": { "id": 13 } }
```

**错误**

| 场景 | code |
|---|---|
| endAt 早于或等于 startAt | 1001，message「结束时间必须晚于开始时间」 |
| studentId 不在本班 | 3002 |
| type 不是 1/2/3 | 1001 |

**前端怎么用**

```js
// src/api/leave.js
import request from './request'

export const getLeaveList  = (params)        => request({ url: '/teacher/leave/list', data: params })
export const auditLeave    = (id, data)      => request({ url: `/teacher/leave/${id}/audit`, method: 'PUT', data })
export const addLeave      = (data)          => request({ url: '/teacher/leave', method: 'POST', data })
```

---

## 9.5 学科班级容器（教师端）

> 完整设计（含为什么这么建模、测试清单）见 [subject-container-design.md](subject-container-design.md)。
> 本节只写接口契约，保证前后端字段对得上。

### 9.5.1 这是什么

容器 = **学科 × 班级/课程** 的成员群组，类微信群。和行政班是两个维度：

| 概念 | 表 | 说明 |
|---|---|---|
| 行政班 | `t_class` | 一个老师一个班（`myClass()` 用 `selectOne` 反查，**不要往这张表里塞多个班**） |
| 学科容器 | `t_subject_container` | 一个老师可以有多个；`class_id` 可空（跨班选修） |
| 成员 | `t_container_member` | `role` 1=群主(老师) 2=班委 3=成员 |
| 黑名单 | `t_container_blacklist` | 只有群主可见；`released_at IS NULL` 表示仍在黑名单 |
| 精华 | `t_container_highlight` | 本期 `message_id` 恒为 NULL，内容是手写的 |
| 聊天消息 | `t_container_message` | 一个群一条消息流；`id` 兼作排序与轮询游标；图片/视频/文件只存相对路径 |

**两套 role 不要混**：`t_user.role` 是全局身份（1老师 2学生）；容器里的 `role` 是"你在某个群里的身份"，和全局身份无关。

### 9.5.2 接口总表

| # | 方法 | 路径 | 最低权限 |
|---|---|---|---|
| 1 | GET | `/api/teacher/container/list?tab=created\|managed\|joined` | 老师 |
| 2 | POST | `/api/teacher/container` | 老师 |
| 3 | GET | `/api/teacher/container/{id}` | 成员 |
| 4 | PUT | `/api/teacher/container/{id}` | 群主 |
| 5 | POST | `/api/teacher/container/{id}/dissolve` | 群主 |
| 6 | GET | `/api/teacher/container/{id}/members` | 成员 |
| 7 | PUT | `/api/teacher/container/{id}/members/{userId}/role` | 群主 |
| 8 | PUT | `/api/teacher/container/{id}/members/{userId}/mute` | 群主 / 班委 |
| 9 | DELETE | `/api/teacher/container/{id}/members/{userId}?blacklist=&reason=` | **仅群主** |
| 10 | GET | `/api/teacher/container/{id}/blacklist` | 群主 |
| 11 | DELETE | `/api/teacher/container/{id}/blacklist/{userId}` | 群主 |
| 12 | GET | `/api/teacher/container/{id}/highlights` | 成员 |
| 13 | POST | `/api/teacher/container/{id}/highlights` | 群主 / 班委 |
| 14 | DELETE | `/api/teacher/container/{id}/highlights/{highlightId}` | 群主 / 班委（班委仅自己的） |
| 15 | GET | `/api/teacher/container/{id}/invite` | 群主 / 班委 |
| 16 | PUT | `/api/teacher/container/{id}/invite/expire` | 群主 |
| 17 | POST | `/api/teacher/container/{id}/invite/regenerate` | 群主 |
| 18 | PUT | `/api/teacher/container/{id}/join-password` | 群主 |
| 19 | GET | `/api/teacher/container/{id}/logs?page=&size=` | 群主 |
| 20 | GET | `/api/container/preview?code=` | 登录 |
| 21 | POST | `/api/container/join` | 登录 |
| 22 | POST | `/api/container/{id}/leave` | 成员 |
| 23 | GET | `/api/container/{id}/messages?afterId=&beforeId=&size=` | 成员 |
| 24 | POST | `/api/container/{id}/messages` | 成员（未禁言） |
| 25 | POST | `/api/container/{id}/upload?kind=image\|video\|file` | 成员 |

> 20~25 不挂 `/teacher` 前缀：加入链路和聊天都是**角色无关**的，学生端上线后直接复用。

### 9.5.3 创建 `POST /api/teacher/container`

```json
{ "subjectName": "高等数学（上）", "name": "高等数学（上）", "classId": 1,
  "inviteExpireAt": "2026-12-31 20:30:00", "joinPassword": "math2026" }
```

| 字段 | 必填 | 校验 | 说明 |
|---|---|---|---|
| subjectName | ✅ | 1-32 字，trim 后非空 | 群组名称。**与 name 是同一个名字**（见下），字段名沿用旧契约 |
| name | ❌ | ≤64 字 | 与 subjectName 同值；不传时后端按「学科名 · 班级名」兜底（兼容老调用方） |
| classId | ❌ | 必须是当前老师自己带的班，否则 3002 | 传不存在的 id → 1004 |
| inviteExpireAt | ❌ | `yyyy-MM-dd HH:mm:ss`，不能早于现在 | **精确到秒**，优先级高于 inviteExpireDays |
| inviteExpireDays | ❌ | 1-3650 | 不传 = 永久 |
| joinPassword | ❌ | 6-20 位 | 不传 = 无密码；只存 PBKDF2 哈希 |

> **v0.10 变更：`subjectName` 与 `name` 合并为一个用户自定义名称。**
> 原先「学科」必填、「容器名称」另起一个字段，导致大学课程（高等数学（上））、
> 考研课（考研英语冲刺）这类名字只能硬塞进「学科」，还得把同一句话写两遍。
> 现在前端只有一个输入框，两个字段传同一个值；展示层在两者相同时只显示一行。
> 数据库列 `subject_name` / `name` 不变，老数据照常可读。

> **v0.10 新增：`inviteExpireAt`。** 老师常说「本周五晚 8 点前有效」，
> 只给天数前端得自己换算，跨零点容易差一天。现在前端传日期+时间，后端只校验「不能早于现在」。
> 与 `inviteExpireDays` 同时传时取 `inviteExpireAt`。

响应 `{ "id": 12, "inviteCode": "K7M2QX9B4T" }`。邀请码 10 位、全大写、字符集
`23456789ABCDEFGHJKLMNPQRSTUVWXYZ`（排除 0/O/1/I），全局唯一由 `uk_invite_code` + 撞键重试保证。

### 9.5.4 加入 `POST /api/container/join`

```json
{ "code": "K7M2QX9B4T", "password": "math2026" }
```

**校验顺序（顺序即安全设计，不要随意调整）**：

| 步 | 条件 | 结果 |
|---|---|---|
| 1 | 码 normalize 后不是 10 位合法字符 | 1001 |
| 2 | 码查不到 / 容器 `status=0` | 1008 |
| 3 | `invite_expire_at < now` | 1008「邀请码已过期」 |
| 4 | 在黑名单（`released_at IS NULL`） | 3003 |
| 5 | 已是在群成员 | 1006（幂等，前端当成功处理） |
| 6 | 有密码且已锁定（连错 5 次锁 15 分钟） | 1005 |
| 7 | 有密码且密码错 | 1007，message 带剩余次数 |
| 8 | 通过 | 插入成员行，`role=3`；曾退出/被移除（未拉黑）则恢复原行 |

响应 `{ "containerId": 12, "name": "数学 · 628班", "role": 3 }`。

### 9.5.5 移除成员 `DELETE /{id}/members/{userId}?blacklist=true&reason=...`

**参数走 query 不走 body**：DELETE 带 body 在各端行为不一致（H5 会发、小程序有时会拼进 URL），
一旦被吞掉，`blacklist` 会静默变成 false —— 老师以为拉黑了，人还能进来。

| 场景 | 结果 |
|---|---|
| 班委调用 | 3002（班委不能踢人） |
| 目标不在群 / 目标是群主 / 目标是自己 | 1004 / 1001 / 1001 |
| `blacklist=false` 或不传 | 成员 `status=2`；**此人可以凭码再进来** |
| `blacklist=true` | 同时写黑名单；此人再加入 → 3003，直到群主解除 |

> **移除 ≠ 解散**。移除只影响一个人，容器还在；解散让所有人无法访问，且不可恢复。

### 9.5.6 权限矩阵

| 操作 | 群主 | 班委 | 成员 |
|---|---|---|---|
| 看详情 / 成员列表 / 精华 | ✅ | ✅ | ✅ |
| 看邀请码 | ✅ | ✅ | ❌ |
| 改容器信息 / 有效期 / 重置码 / 设密码 | ✅ | ❌ | ❌ |
| 设/撤班委 | ✅ | ❌ | ❌ |
| 禁言 / 解禁 | ✅（除自己） | ✅（仅普通成员） | ❌ |
| 移除成员 | ✅ | ❌ | ❌ |
| 收录 / 取消精华 | ✅（可取消任意） | ✅（仅自己的） | ❌ |
| 黑名单查看 / 解除 | ✅ | ❌ | ❌ |
| 解散容器 | ✅ | ❌ | ❌ |
| 主动退出 | ❌ 1001 | ✅ | ✅ |

`GET /{id}` 返回的 `permissions` 只用于按钮显隐，**后端每个写接口都会独立校验**。

### 9.5.8 聊天消息 `GET|POST /api/container/{id}/messages`

**为什么没有会话表**：一个群只有一个聊天。`t_subject_container.session_id` 那个预留字段
就让它空着 —— 再拆一层「会话」除了多一次 join 没有任何收益。

**为什么用自增 `id` 当游标而不是 `created_at`**：同一秒内连发几条，时间排序的结果是随机的，
`id` 天然单调。前端轮询只传 `afterId=最后一条的 id`，增量返回，不用重传整页。

#### 拉取 `GET`

| 参数 | 说明 |
|---|---|
| `afterId` | 拉比它**新**的（轮询增量）。传了它就忽略 `beforeId` |
| `beforeId` | 拉比它**旧**的（上滑看历史） |
| `size` | 默认 30，上限 50（超过按 50 算，小于 1 按默认算） |

三个参数都不传 = 最近 30 条。**返回一律按 `id` 升序**（取最新 N 条是内部倒序查完再翻回来，
否则拿到的是最老的 N 条）。

```json
[{
  "id": 128, "senderId": 1, "senderName": "李光", "senderAvatar": null,
  "senderRole": 1, "content": "今晚作业第 3 题不用做", "type": 1,
  "createdAt": "2026-10-29 16:20:00", "mine": false
}]
```

| 字段 | 说明 |
|---|---|
| `senderName` | 群昵称 > 真实姓名 > 「未设置姓名」（登录自动建的账号 `name` 是空串） |
| `senderRole` | 1 群主 / 2 班委 / 3 成员，前端据此加小标签 |
| `mine` | **后端算好**。小程序端没有全局用户态，让页面自己比 `userId` 得多读一次存储 |
| `type` | 本期恒为 1（文本）；预留图片/文件 |
| `status` | 1 正常 / 0 已撤回。**列表只返回 1** |

#### 发送 `POST`

```json
{ "content": "今晚作业第 3 题不用做" }
```

| 场景 | 结果 |
|---|---|
| 不是成员（被移除 / 已退出） | 3002 |
| 已解散 | 1004 |
| 被禁言（`mute_until > now`） | **3005**，前端把输入框置灰而不是反复弹 toast |
| 空串 / 全空格 | 1001 |
| 超过 500 字 | 1001（`@Size` 拦下，返回校验消息） |

响应是**完整的一条 `MessageVO`**（`mine=true`），前端直接 push 进列表，不用等下一轮轮询。

> `created_at` 由后端显式 `setCreatedAt(now)` 写入。只靠数据库 `DEFAULT CURRENT_TIMESTAMP`
> 的话列有值、实体字段仍是 `null`，返回给前端就成了 `createdAt: null`，气泡上的时间不显示。

#### 三条硬规则

1. **拉消息前必须 `requireMember`** —— 被移出的人不能靠翻历史继续看群里的内容
2. **发消息前必须 `assertCanSpeak`** —— 禁言要真的拦得住，不能只在成员列表里挂个标签
3. **没有 WebSocket**，前端 3 秒轮询一次；一次增量只有几百字节，退出页面（`onHide`）必须停掉

#### 图片 / 视频 / 文件（`type` = 2 / 3 / 4）

| type | 含义 | content | mediaUrl | fileName | fileSize | duration |
|---|---|---|---|---|---|---|
| 1 | 文本 | 正文，必填 ≤500 | — | — | — | — |
| 2 | 图片 | 空串 | ✅ | 可选 | 可选 | — |
| 3 | 视频 | 空串 | ✅ | ✅ | ✅ | 秒，可空 |
| 4 | 文件 | 空串 | ✅ | ✅ 必填 | ✅ | — |

发图片消息：

```json
{ "type": 2, "mediaUrl": "/files/container/1/202610/8f3c....png" }
```

发文件消息：

```json
{ "type": 4, "mediaUrl": "/files/container/1/202610/a91d....pdf",
  "fileName": "第三章讲义.pdf", "fileSize": 102400 }
```

**三条校验（顺序即安全设计）**：

| 情况 | 结果 |
|---|---|
| `type` 不是 1~4 | 1001 |
| 图片/视频/文件没有 `mediaUrl` | 1001 |
| `mediaUrl` 不是以 `/files/` 开头，或含 `..` / 反斜杠 | **1001** |
| 文件消息没有 `fileName` | 1001 |
| 被禁言 | 3005（和文本一样） |

> **为什么必须卡 `/files/` 前缀**：不卡的话客户端可以传任意 URL（外部图床、带追踪参数的短链），
> 群聊就变成能挂外链的地方；而前端会把它当图片直接加载，等于让所有人的小程序
> 去访问一个陌生人给的地址。只认本服务上传接口返回的路径，这条链才是闭环的。

> **媒体消息的 `content` 存空串，不存 null**：列是 `NOT NULL`，存 null 插不进去；
> 统一空串之后前端渲染也不用为 null 单独分支。

### 9.5.9 上传附件 `POST /api/container/{id}/upload`

`multipart/form-data`，表单字段名固定 `file`；query 参数 `kind` 决定校验规则。

| kind | 扩展名白名单 | 大小上限 |
|---|---|---|
| `image`（默认） | jpg / jpeg / png / gif / webp / bmp | 10 MB |
| `video` | mp4 / mov / m4v / 3gp | 30 MB |
| `file` | pdf / doc(x) / xls(x) / ppt(x) / txt / md / csv / zip / rar / 7z | 20 MB |

响应 `{ "url": "/files/container/1/202610/xxx.png", "name": "截图.png", "size": 102400 }`。

**上传和发消息是两步**：先传拿到 `url`，再带着 `url` 发一条 `type=2/3/4` 的消息。
合成一步的话，用户选了图又取消发送，文件已经落盘却没被任何消息引用，没法回收。

**三条实现约定**：

1. **落盘名一律用 UUID**，原始名只用来取扩展名和展示。用原始名会带来 `../` 穿越和 `.jsp` 这类可执行后缀问题
2. **校验的是最终落盘的扩展名**，按 kind 分开白名单 —— 图片接口不能传 `.exe` 再骗过去
3. **附件走 `/files/**` 而不是 `/api/**`**：小程序里 `<image src>` / `<video src>` 由原生组件发请求，
   **带不上 `Authorization` 头**。放 `/api` 下会被 `AuthInterceptor` 拦成 401，图片永远显示不出来。
   附件靠"路径里的 UUID 猜不到"保护，不是靠 token

> 上线前记得把 `app.upload.dir`（环境变量 `UPLOAD_DIR`）指到一个**不会被清理、且随备份走**的位置。
> 默认值是相对路径 `./uploads`，跟着启动目录走。

### 9.5.10 前端怎么用

```js
// src/api/container.js
export const listContainers = (params) => request({ url: '/teacher/container/list', data: params })
export const createContainer = (data)  => request({ url: '/teacher/container', method: 'POST', data })
export const getContainer    = (id)    => request({ url: `/teacher/container/${id}` })
// 移除成员：参数必须拼在 URL 上，原因见 §9.5.5
export const removeMember = (id, userId, blacklist, reason) => request({
  url: `/teacher/container/${id}/members/${userId}?blacklist=${blacklist}${reason ? '&reason=' + encodeURIComponent(reason) : ''}`,
  method: 'DELETE'
})
// 聊天：三种用法共用同一个 GET，见 §9.5.8
export const listMessages = (id, params) => request({ url: `/container/${id}/messages`, data: params })
export const sendMessage  = (id, payload) => request({ url: `/container/${id}/messages`, method: 'POST',
  data: typeof payload === 'string' ? { content: payload } : payload })
// 上传：uni.uploadFile 不走 request 封装，但地址和 token 必须共用同一份
export function uploadToContainer(id, filePath, kind = 'image') {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/container/${id}/upload?kind=${kind}`,
      filePath, name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      success: (res) => { /* JSON.parse(res.data) 后按 code 判成功 */ },
      fail: reject
    })
  })
}
// 后端只存相对路径，域名在这里补；BASE_URL 结尾是 /api，要切掉
export const fileUrl = (p) => BASE_URL.replace(/\/api\/?$/, '') + p
```

**右下角是「发送」还是「＋」，由输入框有没有字决定**：

| 状态 | 显示 | 行为 |
|---|---|---|
| 输入框为空 | ＋ | 展开工具面板（照片/视频、文件）；展开时图标转 45° 变叉号 |
| 输入框有字 | 发送 | 发文本 |
| 被禁言 | 灰掉的 ＋ | 点了没反应，placeholder 提示"你已被禁言" |

> 以前空着也显示「发送」，点了什么都不发生 —— 用户会以为按钮坏了。
> 空状态下的按钮应该**换个功能**，而不是原地变成死按钮。

**三种气泡的渲染方式不一样**：

| type | 渲染 | 点击 |
|---|---|---|
| 1 文本 | 气泡 | — |
| 2 图片 | `<image mode="widthFix">` | `uni.previewImage`，可左右滑同一批图片 |
| 3 视频 | 深色卡片 + 播放三角 | `uni.previewMedia` 全屏播 |
| 4 文件 | 角标 + 文件名 + 大小 | `downloadFile` → `openDocument` |

> **视频不在气泡里嵌 `<video>`**：它是原生组件，会盖在同层普通元素上面，
> 工具面板和输入栏都可能被它挡住点不到。全屏播放既躲开这个坑，也更接近微信。

**页面动线是「列表 → 聊天 → 详情」**，不是「列表 → 详情」：

| 入口 | 落到 |
|---|---|
| 列表卡片点击 | `chat?id=` |
| 创建成功后 | `redirectTo` 到 `chat?id=`（**不是** `navigateTo` —— 建完再返回创建页没有意义） |
| 加入成功后 | `redirectTo` 到 `chat?id=` |
| 聊天页左上角 | `navigateBack`；栈里只有自己时 `reLaunch` 回列表 |
| 聊天页右上角「详情」 | `navigateTo` 到 `detail?id=` |

聊天页是**自定义导航**（`navigationStyle: custom`）：原生导航栏放不下「详情」这个入口，
微信自己也是在右上角放「···」。返回箭头用两条边框拼（`border-left` + `border-bottom` + `rotate(45deg)`），
比文字箭头锐利，也不依赖字体是否带这个字形。

> **聊天页不展示邀请码。** 邀请码是**有有效期的加入凭证**，过期就作废，
> 摆在聊天页底下会被当成"群号/永久有效"，而且一个已经过期的码放在那里只会误导人。
> 看邀请码、改有效期、重置码统一走「详情 → 邀请码」。
> 聊天页空状态只做一件事：引导发第一条消息（整块可点，点了聚焦输入框）。

---

## 9.6 多班级（v0.13）

**背景**：v0.12 之前系统默认"一个老师一个班"。`ClassService.myClass()` 用
`selectOne(head_teacher_id)` 查班 —— 老师带第二个班的那天，所有教师端接口会一起抛
`TooManyResultsException`（"Expected one result but found 2"），报错信息和业务毫无关系。

### 9.6.1 三条全局约定

| 约定 | 说明 |
|---|---|
| `classId` 是**可选** query 参数 | 不传 = "我的第一个班"。只为兼容还没改造的调用方；前端页面一律显式传 |
| 传了必须校验归属 | 不是自己的班 → **3002**（不是 1004）。1004 会让前端以为"这个班被删了" |
| 班级不存在/已归档 → **1004** | 和 3002 分开，前端才能给出不同提示 |

`classId` 走 query 而不是路径 `/class/{id}`：它表达的是"在哪个班上下文中操作"，
不是"操作哪个资源"。路径里的 id 会被当成资源主键，语义不对。

### 9.6.2 新增接口（4 个，总计 29 个）

| # | 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|---|
| 26 | GET | `/api/teacher/class/list` | 老师 | 我的班级 + 在读人数 + 班级群 |
| 27 | POST | `/api/teacher/class` | 老师 | 新建班级，**自动建班级群**（班主任=群主） |
| 28 | POST | `/api/teacher/class/{classId}/group` | 老师 | 补建班级群（幂等，给历史数据兜底） |
| 29 | DELETE | `/api/teacher/class/{classId}` | 老师 | 删除班级（**归档**，连带归档班级群） |

`GET /api/teacher/class/list` 每项返回：

```json
{
  "id": 1, "name": "24计算机软通1班", "grade": "高一",
  "termStartDate": "2026-09-10", "inviteCode": "953738D26C",
  "studentCount": 57,
  "containerId": 2, "containerName": "24计算机软通1班", "groupMemberCount": 1
}
```

`containerId` 为 null 表示这个班还没有群，前端要显示"点这里创建"并调 28。

### 9.6.3 受影响的既有接口

全部新增**可选** `classId`，不传时行为和改造前一致（落到第一个班）：

| 接口 | 说明 |
|---|---|
| `GET /api/teacher/home/overview` | 首页四个统计、周次、班级名都跟着 classId 走；`todoUndone` 也按班统计 |
| `GET /api/teacher/student/list` | 按班查名单 |
| `POST /api/teacher/student` | 新增学生落到 classId 指定的班 |
| `GET /api/teacher/exam/list`、`POST /api/teacher/exam` | 考试挂在班下 |
| `GET /api/teacher/seat/chart`、`PUT /api/teacher/seat/chart` | 座位按班 |
| `GET /api/teacher/leave/list`、`POST /api/teacher/leave` | 请假按班 |
| `GET /api/teacher/todo/list`、`POST /api/teacher/todo` | 待办按班 |
| `POST /api/teacher/student/import/preview`、`/commit` | 导入到指定班 |
| `GET` / `PUT /api/teacher/class` | 读写指定班的设置 |

**按 id 定位的接口不需要 classId**（它们自己会校验归属）：

- `GET/PUT/DELETE /api/teacher/student/{id}` → 学生所在班的班主任是不是我
- `GET /api/teacher/score/list|stats?examId=`、`POST /score/batch` → 考试所属班是不是我的
- `PUT /api/teacher/leave/{id}/audit` → 请假记录所属班是不是我的
- 成绩导入 → 由 `examId` 推班级

> **成绩不可能写错班**：成绩表没有"选班级"这一步，它跟着 `examId` 走，
> 而 `examId` 的归属在服务端校验。前端就算传错 examId，后端也是 3002，不会写进去。

### 9.6.4 改名与删除（v0.14）

通讯录每张班级卡片右上角有 `⋯`，收两个操作：**重命名班级** / **删除班级**。

#### 重命名 `PUT /api/teacher/class?classId=2`

只传 `{"name":"24软通2班"}` 即可，年级和开学日期不动（「传什么改什么」）。

两条**新增的**服务端行为：

| 行为 | 说明 |
|---|---|
| 重名检查（排除自己） | 改成别的班已用的名字 → `1006`。原来只有 `POST /class` 查重，`PUT` 不查，改完会出现两个同名班，选择器里两行一模一样 |
| 班级群名同步 | 班名改了，`t_subject_container.name` / `subject_name` 一起改。班级群是系统自动建的、一个班只有一个，不该有独立名字；不同步的话通讯录卡片上会同时出现新旧两个班名 |

改成自己当前的名字不算重名（幂等），不会报错。

#### 删除 `DELETE /api/teacher/class/{classId}`

**这里的 classId 走路径，和其它接口不一样**：别的接口里 classId 是"在哪个班上下文中操作"，所以放 query；这个接口删的**就是班级本身**，它是资源主键。

**删除 = 归档（`status=0`），不是物理删除。**

| 对象 | 处理 |
|---|---|
| `t_class` | `status=0` |
| `t_subject_container`（该班的班级群） | `status=0`，从「课程群组」列表消失 |
| `t_student` / `t_exam` / `t_score` / 座位 / 请假 / 待办 / 聊天记录 | **保留不动** |

为什么不做物理删除：一个班的数据散在七八张表里。级联删要么写一长串、漏一张表就是脏数据；要么留下永远查不到的孤儿行，还会让"新建同名班级"读到旧数据。归档只动两个字段，而所有查询本来就带 `status=1`，天然隔离。

删除后：

- `GET /class/list` 不再返回这个班
- `GET /class?classId=`、`/student/list?classId=`、`/exam/list?classId=`、`/home/overview?classId=` → `1004 班级不存在或已归档`
- `GET /student/{id}`（该班的学生）→ `3002`。`ownsStudent` 现在会一起判班级 `status`，否则归档后拿旧 studentId 还能读到、甚至改到一个"已经删掉的班"的学生
- **允许删到最后一个班**。老师带完一届要清空是正常需求，首页和通讯录都有「还没有班级」的空状态
- 归档后可以**重新建同名班级**（查重只查 `status=1`）

### 9.6.5 数据模型

**没有新增表。** 班级群复用 `t_subject_container.class_id`：

| 表 | 变化 |
|---|---|
| `t_class` | 无变化（`head_teacher_id` 本来就允许多行指向同一个老师） |
| `t_subject_container` | 无变化；`class_id` 非空 = 这是某个班的班级群 |
| `t_container_member` | 无变化；建班时插一行 `role=1`（群主） |

`database/migrations/07_class_group.sql` 负责给**已有**班级补群（幂等，可重复执行）。
邀请码在 SQL 里用 `MD5` 前 10 位并把 `0/1` 换成 `2/3` ——
`InviteCodeGenerator` 的字符集排除了 `0/O/1/I`，直接用 MD5 会生成"查得到但加不进去"的码。

**学生不会进群**：`t_student` 没有 `user_id`/`openid`，学生还没有账号。
所以"学生归入班级"体现在 `t_student.class_id`（通讯录分组），
而不是 `t_container_member`。这不是 bug，是账号体系还没做。

### 9.6.6 前端

| 变化 | 说明 |
|---|---|
| `src/store/class.js`（新） | 全局当前班级（`classes` / `currentClassId` / `currentClass`），持久化到 storage |
| tabBar | 「学生」→「通讯录」（`pages/teacher/contact/index`） |
| `pages/teacher/contact/index`（新） | 按班分组：班级群（进群聊）+ 班级联系人（进名单）；支持新建班级；卡片右上角 `⋯` → 跳「班级信息」页（带 `classId`） |
| `pages/teacher/class/setting`（改） | 成为统一的「班级信息」页：`?classId=` 指定看哪个班（不带则当前班）；可改名称/年级/开学日期，只读展示邀请码与人数，底部有「删除班级」 |
| `pages/teacher/container/detail`（改） | 班级群的详情里新增「所属班级」入口，直接跳到该班的「班级信息」 |
| `pages/teacher/class/create`（新） | 建班表单，建完自动切到新班 |
| 首页 | 班级栏点击弹底部选择器（自定义，不用 `showActionSheet`：那个最多 6 项） |
| 成绩页 | 顶部加班级选择条，切班时清空选中的考试再重拉 |
| 座位/请假/待办 | 拉数据时带上 `currentClassId`，不再依赖后端兜底 |

---

## 9.7 接龙与抽签（v0.15）

### 9.7.1 三条前提

| # | 前提 | 后果 |
|---|---|---|
| 1 | 群里只有老师有账号，学生不在 `t_container_member` 里 | 接龙必须支持**代填**；抽签不依赖成员参与，所以它能直接用 |
| 2 | 接龙的「已满」「已截止」是**算出来的**，不是存库的状态 | 存了就得有定时任务去翻状态，翻漏了卡片就永远显示"进行中" |
| 3 | 卡片状态不存消息表 | 每次 `GET /messages` 批量重算，卡片上的数字永远是当下的 |

**为什么不发"参与消息"**：30 个人接龙就是 30 条系统消息，会把真正的聊天挤没。卡片自己更新人数，不产生新消息。

### 9.7.2 接口总表（9 个）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/container/{id}/relay` | 发起接龙，同时发一条 `type=5` 卡片消息 |
| GET | `/api/container/{id}/relay/{relayId}` | 详情：状态 + 名单 + 代填候选 + 我的填写 |
| POST | `/api/container/{id}/relay/{relayId}/entry` | 自助接龙（重复提交幂等） |
| POST | `/api/container/{id}/relay/{relayId}/entry/batch` | 代填补录（整批一个事务） |
| PUT | `/api/container/{id}/relay/{relayId}/entry/{entryId}` | 改一条记录 |
| POST | `/api/container/{id}/relay/{relayId}/close` | 结束接龙 |
| POST | `/api/container/{id}/lottery` | 发起抽签，同时发一条 `type=6` 卡片消息 |
| POST | `/api/container/{id}/lottery/{lotteryId}/draw` | 开奖 |
| GET | `/api/container/{id}/lottery/{lotteryId}` | 结果详情 |

**路径不带 `/teacher`**：学生端上线后要复用同一批接口，放进 teacher 命名空间会让"学生能不能调"变成需要解释的问题。权限全在 `ContainerAuthService.requireMember` 里。

**"我是谁"永远从 token 取**，没有 `userId` 参数。

### 9.7.3 发起接龙 `POST /api/container/{id}/relay`

```json
{
  "title": "春游报名",
  "description": "周六 8:00 校门口集合",
  "entryFormat": 1,
  "formatMin": null,
  "formatMax": null,
  "options": ["参加", "不参加"],
  "maxCount": 30,
  "deadline": "2026-11-08 18:00:00",
  "allowSelfEdit": true
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | ≤ 30 字 |
| `entryFormat` | 否 | 1 序号（默认）/ 2 数字 / 3 手机号 / 4 短文本 / 5 单选 |
| `formatMin` `formatMax` | 否 | 仅格式 2 有意义 |
| `options` | 否 | 仅格式 5 有意义，2-10 个。用数组不用逗号串——选项里出现逗号很正常 |
| `maxCount` | 否 | `null` = 不限。有值时卡片显示进度条 |
| `deadline` | 否 | `yyyy-MM-dd HH:mm:ss`，`null` = 不限 |
| `allowSelfEdit` | 否 | 默认 `true` |

**为什么只有这五种格式**：「选两门课」的真实语义是**两个接龙**，不是一个多选接龙——合并起来人数就算不清了。组合字段（姓名 + 手机号）留到 v2 用字段模板做。

### 9.7.4 接龙状态

**库里只存 3 个值**，另外 2 个是出参里算出来的：

| 值 | 含义 | 存库 |
|---|---|---|
| 1 | 进行中 | ✅ |
| 2 | 已截止 | ❌ 算：`deadline < now()` |
| 3 | 已满 | ❌ 算：`count >= maxCount` |
| 4 | 已结束 | ✅ 发起人手动结束 |
| 5 | 已取消 | ✅ |

**「已满」和「已截止」必须是两个状态**：看到"已满"会想"能不能挤进去"，看到"已截止"不会。文案分开写。

### 9.7.5 自助接龙 `POST .../entry`

```json
{ "value": "" }
```

- 格式 1（序号）传空串，**一键完成，不进页面**——报名类接龙 90% 是"我就是要去"
- 格式 2-5 传填写内容，服务端按格式校验
- **重复提交不报错**，返回已有记录。报错会让用户以为没成功，继续点
- 抢最后一个名额时对 `t_relay` 加 `SELECT ... FOR UPDATE` 行锁，否则两个人都读到 29/30 然后都插进去

### 9.7.6 代填 `POST .../entry/batch`

```json
{ "entries": [{ "studentId": 12, "value": "" }, { "studentId": 13, "value": "" }] }
```

- 只有发起人 / 群主 / 班委能调
- **整批一个事务**：中途有一条重复或超员，全部回滚。部分成功比全失败更难解释——老师没法知道到底哪几条进去了
- 重复时报 `1006 王小明 已经在第 1 位了`，指名道姓说清楚，不悄悄跳过

### 9.7.7 发起抽签 `POST /api/container/{id}/lottery`

```json
{ "kind": 1, "source": 1, "drawCount": 3, "dedupe": true, "publicResult": true }
```

| 字段 | 说明 |
|---|---|
| `kind` | 1 随机点名 / 2 随机分组 |
| `source` | 1 当前班学生（默认）/ 2 手动勾选（配 `studentIds`） |
| `drawCount` | 点名模式，1-50 |
| `groupCount` | 分组模式，2-20 |
| `dedupe` | 已抽中的人不再进下一轮，默认 `true` |
| `publicResult` | `false` 时非发起人拿到空结果，**判断在服务端** |

**没有「中奖概率」和「内定名单」，这是有意的。** 抽签要的是"每个人机会一样"；一旦能设权重，它就从抽签变成了定向工具，被学生发现就是信任问题。真要做，做成规则（"优先抽最近没被点到的"）而不是手动权重——规则可解释。

**名单在发布时快照**（`t_lottery.pool_snapshot`）。现查名单会让"学生转班后历史结果对不上"，同一张卡两次刷新还可能抽出不同的人。

### 9.7.8 开奖 `POST .../draw`

- 只有发起人 / 群主 / 班委能点
- 两人同时点：行锁 + `version` 版本号，后到的那次拿到**同一个结果**，不会各抽一批
- 随机种子 `seed` 存库，用 `new Random(seed ^ round * 0x9E3779B97F4A7C15L)`。有人质疑"是不是内定"时把种子和名单给他，他能自己复现出同一批人——这比"我们真的是随机的"这句话有用得多
- 分组余数分给**前 N 组**，不是全丢在最后一组（`57 / 4` → 15,14,14,14）

### 9.7.9 消息卡片聚合

`GET /messages` 的每条消息新增 `refId`，`type=5` 带 `relayCard`，`type=6` 带 `lotteryCard`：

```json
{
  "id": 9,
  "type": 5,
  "refId": 1,
  "content": "[接龙] 春游报名",
  "relayCard": {
    "id": 1, "title": "春游报名", "status": 3, "entryCount": 3, "maxCount": 3,
    "entryFormat": 1, "deadlineText": "11-08 18:00",
    "joined": true, "myEntryId": 1, "mine": true
  }
}
```

**批量聚合，不是一条一查**：先把这一页的 `refId` 去重收集，再一次查完，否则一页 30 条消息就是 30 次查询。

`deadlineText` 由后端格式化（如 `11-08 18:00`）。不让前端做，是因为 iOS 上 `new Date('2026-11-08 18:00:00')` 返回 `Invalid Date`——这个坑在小程序里很难查。

### 9.7.10 新增错误码

| 码 | 含义 | 前端处理 |
|---|---|---|
| 4101 | 接龙已经满员 | 重新拉卡片，状态会变成「已满员」 |
| 4102 | 接龙已经截止 | 同上 |
| 4103 | 接龙已结束 | 同上 |
| 4105 | 抽签名单已经抽完了 | 重新拉卡片，显示「还剩 0 人」 |

### 9.7.11 前端

| 文件 | 说明 |
|---|---|
| `src/api/relay.js`（新） | 6 个函数 |
| `src/api/lottery.js`（新） | 3 个函数 |
| `pages/teacher/container/relay-create`（新） | 主题 / 说明 / 5 种格式 / 上限 / 截止 |
| `pages/teacher/container/relay-detail`（新） | 名单 + 我的填写 + 代填面板 |
| `pages/teacher/container/lottery-create`（新） | 点名 / 分组 + 名单来源 + 人数 / 组数 |
| `pages/teacher/container/lottery-result`（新） | 点名按轮次倒序 / 分组两列 + 复制结果 |
| `pages/teacher/container/chat`（改） | ＋ 面板改 4 列宫格；渲染 type 5 / 6 卡片；卡片内一键接龙与开奖 |

**「＋」面板的四个入口**：照片/视频、文件、接龙、抽签。4 列宫格（每项 25%），**位置永久固定**，不做分类 Tab、不做拖拽排序、不做「最近使用」自适应排序；只有 ＋ 号上的新功能红点（打开面板即消除）。

**卡片状态与按钮**

| 状态 | 标签 | 按钮 |
|---|---|---|
| 进行中 · 未参与 | 接龙 · 进行中（主色） | 我要接龙（实心） |
| 进行中 · 已参与 | 接龙 · 已参与（绿 `#3B6D11`） | 修改我的填写（描边） |
| 已满 / 已截止 / 已结束 | 灰 | 查看名单 / 查看结果（描边灰） |
| 已取消 | 灰，整卡置灰 | 无按钮 |

「已参与」必须变绿并改文案——回来看到"我要接龙"会怀疑自己到底报没报。

---

## 9.8 乐迪（AI 小助手）

老师用自然语言问班级的事，乐迪先查库再回答；也能替老师动手改数据。

**所有写操作都是两段式**：模型只提议 → 前端弹一张确认卡 → 老师点了才落库。
查错了只是看到一个错数字，改错了是真的把数据改了，而审批请假、群发消息都没有撤销按钮。

### 9.8.1 问一句 `POST /api/teacher/aidi/chat`

```json
{
  "classId": 1,
  "messages": [
    { "role": "user", "content": "王小明的成绩怎么样" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| classId | long | 否 | 问哪个班；不传 = 我的第一个班。**传 0 会 1004**，前端 `classId > 0` 才拼 |
| messages | array | 是 | 对话历史，最后一条必须是 `user` |

`messages[].role` 只接受 `user` / `assistant`。`system` 会被丢弃——否则前端就能改提示词。

返回（只读问答）：

```json
{
  "code": 0,
  "data": {
    "reply": "王小明这次期中考试总分 383，班内第 8 名，比班平均高 32 分。\n· 数学 135\n· 英语 128\n· 语文 120",
    "toolCalls": 1,
    "action": null
  }
}
```

返回（要动手时，多一张卡）：

```json
{
  "code": 0,
  "data": {
    "reply": "我要给王小明录语文 125 分，确认卡已经弹出来了。",
    "toolCalls": 0,
    "action": {
      "type": "save_scores",
      "title": "录入成绩",
      "summary": "王小明 · 期中考试：语文 125",
      "confirmText": "确认录入",
      "danger": false,
      "params": { "_studentId": 121, "_examId": 2, "scores": [{ "subject": "语文", "score": 125 }] }
    }
  }
}
```

| 字段 | 说明 |
|---|---|
| reply | 纯文本，可能带 `\n`。前端用 `white-space: pre-wrap` 渲染，不做 Markdown 解析 |
| toolCalls | 这一轮查了几次库，前端不显示，排查用 |
| action | 待确认的动作，没有就是 `null`。**一次最多一个**——两张卡一起弹，老师很容易点错那张 |

`action` 内部字段：

| 字段 | 说明 |
|---|---|
| type | 动作类型，见 9.8.3 |
| title | 卡片标题，如「录入成绩」「通过请假」 |
| summary | 一句话说清要改什么（学生名、时间、分数都在这里），由后端拼好 |
| confirmText | 确认按钮文案，如「确认录入」。前端不猜语义，直接用 |
| danger | `true` = 不可撤销（审批请假、群发消息）。前端描红边并标「不可撤销」 |
| params | 执行时**原样回传**。`_` 开头的是后端解析出的 id，前端不要读、不要改 |

**对话不落库。** 历史由前端每次带上来（后端只取最近 8 条），页面关掉就结束。
不建表 = 不用清理、不用管"换手机看不到历史"、也不会出现"AI 记住了错误信息"。
**稳定事实是另一回事**，那些要落库，见 9.8.5。

### 9.8.2 读工具（7 个，模型自己决定调哪个）

| 工具 | 参数 | 返回 |
|---|---|---|
| `class_overview` | — | 在读人数、男女、最近一次考试、待审请假数、未完成待办数 |
| `list_students` | `keyword` 可选 | 姓名 / 学号 / 性别，最多 60 条 |
| `student_scores` | `studentName` | 最近 5 次考试的各科分数、总分、班内排名、班级平均 |
| `exam_summary` | `examName` 可选 | 某次考试的平均总分、最高分、各科平均、**各科最高分**、前 5 名 |
| `list_exams` | — | 最近几次考试的名称 / 日期 / 科目。录成绩前必须先查，确认是哪次考试 |
| `leave_records` | `status` 可选 | 最近 10 条请假记录 |
| `todo_items` | `done` 可选 | 最近 10 条待办 |

**班 id 永远由后端注入，模型碰不到。** 模型只负责「查什么」，不负责「查哪个班」——
这是权限边界，不能指望提示词守住。

工具内部出错返回 `{"error":"..."}` 而不是抛异常：抛出去整轮对话就废了，
而模型看着错误信息完全可以跟老师说「这个查不到」。

### 9.8.3 写工具（7 个）

参数一律用「人能说出来的东西」（姓名、考试名），不用 id——模型编 id 是常事，
后端负责把姓名解析成真实的 id。

| type | 参数 | 卡片 | danger |
|---|---|---|---|
| `create_todo` | `title`、`dueDate` 可选 | 新增待办 | false |
| `complete_todo` | `title`（关键词，用来定位） | 完成待办 | false |
| `audit_leave` | `studentName`、`decision`(approve/reject)、`remark` 可选 | 通过请假 / 驳回请假 | **true** |
| `send_group_message` | `content`（≤500 字） | 群发消息 | **true** |
| `create_relay` | `title`、`description` 可选、`deadline` 可选 | 发起接龙 | false |
| `create_lottery` | `kind`(roll/group)、`count` | 随机点名 / 随机分组 | false |
| `save_scores` | `studentName`、`examName` 可选、`scores[{subject,score}]` | 录入成绩 | false |

模型调用写工具时**不写库**，只走 `AidiActions.preview()` 生成卡片。校验不过
（找不到学生、分数超范围、有两条待审请假）不抛异常，而是作为工具结果
`{"error":"..."}` 回给模型，模型会转述并追问。

`save_scores` 直接复用 `scoreService.batchSave`——科目合法性、分数范围、考试归属
这些规则只写一遍，不因为多了个入口就多一套判断。

### 9.8.4 执行 `POST /api/teacher/aidi/act`

```json
{
  "classId": 1,
  "action": {
    "type": "complete_todo",
    "params": { "_todoId": 4, "title": "收家长回执" }
  }
}
```

返回：

```json
{ "code": 0, "data": { "reply": "已把「收家长回执」标为完成", "toolCalls": 0, "action": null } }
```

| 字段 | 说明 |
|---|---|
| classId | 同 chat。不传 = 第一个班 |
| action.type | 必须是 9.8.3 里的 7 种之一 |
| action.params | 把上一轮 `reply.action.params` **原样**发回来 |

四条硬规则：

1. **这一步不经过模型。** 参数已经解析好了，执行是一次确定的写操作，模型不再有发言权。
2. **后端重新校验归属。** 前端传来的 `_todoId` / `_leaveId` / `_studentId` / `_examId`
   全部按 `classId` 再查一遍，查不到就报错。前端能改 params，所以不能信。
3. **不检查 API Key。** 余额用光的那天，已经弹出来的确认卡也应该能点完。
4. **失败返回可读的错误码 + 消息**（3002「这个学生不在你的班里」、1004「这条待办已经找不到了」），
   前端把这句话显示在卡片下方，卡片保留可再点一次。

### 9.8.5 长期记忆（事实层）

对话不落库，但**稳定事实**要记。两者不是一回事：

| | 存在哪 | 例子 | 为什么 |
|---|---|---|---|
| 对话 | 前端 storage（最近 40 条，7 天） | 「他哪科最弱」里的「他」 | 关掉就结束，不该变成长期资产 |
| 事实 | `t_aidi_memo` | 「周三下午没课」 | 每次都要用，重新交代一遍很蠢 |

表结构（`database/migrations/11_aidi_memo.sql`）：

| 列 | 说明 |
|---|---|
| user_id | 谁的记忆 |
| class_id | 属于哪个班；**0 = 跨班通用** |
| content | 一条事实，≤80 字（后端硬卡） |
| status | 1=有效 0=已忘掉（软删，忘掉后再记会复活原来那行） |

唯一键 `(user_id, class_id, content)`：同一句话记两遍没有意义。

**记忆不走确认卡，直接写。** 确认卡是为"改错了没撤销按钮"设计的——审批请假、
群发消息发出去就收不回。记忆只影响助手自己的行为，不碰班级业务数据，
一句话就能忘掉。界线是：**改业务数据要确认，改助手自己的笔记不用。**

| 工具 | 参数 | 行为 |
|---|---|---|
| `remember` | `content`、`scope`(class/all，默认 class) | 直接写库；重复则提示「已经记过了」 |
| `forget` | `content`（关键词） | 命中 1 条就忘掉；**命中多条会列出来反问，绝不自己选** |

**记忆是拼进提示词，不是让模型查。** 每轮 `AidiMemoService.promptBlock()` 把
本班 + 通用的记忆（最多 30 条）拼在系统提示词里，模型不用调工具就能"想起来"。
如果做成 `recall` 工具，模型得先意识到自己该查——它往往意识不到，
表现就是"明明记过却说不知道"。

记忆上限 30 条/人，超了拒绝并让老师先清理。**不自动淘汰旧的**：
悄悄丢掉一条老师以为还记着的规矩，比直接说"记满了"糟得多。

### 9.8.6 新增错误码

| 码 | 含义 | 前端处理 |
|---|---|---|
| 5001 | 后端没配 DeepSeek API Key | 气泡里显示这句话，别重试 |
| 5002 | Key 无效 / 余额不足 | 同上 |
| 5003 | DeepSeek 挂了 / 超时 / 连不上 | 显示「重试」 |
| 5004 | 被限流 | 显示「重试」 |

### 9.8.7 配置

```yaml
app:
  deepseek:
    api-key: ${DEEPSEEK_API_KEY:}          # 没配不会启动失败，问乐迪才 5001
    base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
    model: ${DEEPSEEK_MODEL:deepseek-chat}
    timeout-seconds: ${DEEPSEEK_TIMEOUT:60}
```

**key 只能待在后端。** 写进小程序等于公开：反编译包就能拿到，还会跟着代码进版本库。

### 9.8.8 前端

| 文件 | 说明 |
|---|---|
| `src/api/aidi.js`（新） | `askAidi()` 超时 60 秒；`confirmAidiAction()` 超时 20 秒 |
| `pages/teacher/aidi/index`（改） | 聊天页：气泡、思考中三点、三个示例问题、失败重试、**动作确认卡**、**本地存档** |
| `src/api/request.js`（改） | 支持 `timeout` 参数，默认仍是 10 秒 |

**本地存档**：`aidi_chat_v1`，最近 40 条、7 天有效、按班级隔离。
三个刻意的取舍：

- **不存 `action`。** 确认卡不能跨会话复活——三天前那张「群发消息」的卡如果还能点，
  等于留了个定时炸弹。已执行的结果存在 `doneText` 里，历史照样接得上。
- **班对不上就不恢复。** `currentClassId` 是同步从 storage 读的，冷启动时也已经是
  上次那个班，所以能直接比。恢复二班的对话到一班的界面上，是最难解释的错误。
- **有对话时顶部出现一条细栏**：左边写清这是哪个班的对话，右边给「新对话」。
  存档之后如果没有重开的出口，旧对话会一直赖着删不掉。

确认卡的状态机（全部是页面内的临时状态，不落库）：

| 状态 | 表现 |
|---|---|
| 待确认 | 两个按钮：取消 / `confirmText` |
| 执行中 | 按钮变浅、文案「执行中…」，防连点 |
| 已执行 | 按钮换成后端返回的那句话（「已录入 王小明 的 期中考试：语文 125」） |
| 已取消 | 「已取消，没有改动任何数据」，不发请求——没落库就没有要撤销的东西 |
| 失败 | 红字显示原因，**按钮保留**，可以再点一次 |

点取消不发请求；已执行的结果会并进那条 assistant 消息带进下一轮历史，
否则老师接着问「刚才那个录好了吗」，模型完全不知道自己做过什么。

---

## 9.9 学生端与师生私信（v0.33）

学生端第一次真正落地：学生用**学号 + 姓名**进入，能收、能回班主任私信。
在此之前整个小程序只有教师端，`t_student` 只是一份花名册，没有任何人能登录成学生。

> v0.41 起，**登录不再要求先被班主任导入名单**：花名册里查不到也给一张 token，
> 只是这张 token 没有班级，只能用来凭邀请码入班。见 §9.9.2。

### 9.9.1 学生不是 t_user

| | 老师 | 学生 |
|---|---|---|
| 存在哪 | `t_user`（有 openid、角色、单位） | `t_student`（花名册里的一行） |
| 怎么登录 | 微信授权换 openid | 学号 + 姓名，多班时选班；不在名单里则先拿"未入班"token |
| token | `role=1`，subject = userId | `role=2`，subject = studentId，另带 classId；未入班时 subject=0 且无 classId |

**为什么不把学生塞进 t_user**：t_user 是"能登录的老师"。塞进去要处理
"这个 user 其实不是老师"、登录时要先建账号、学生转班时账号算谁的。
学生一辈子不该有密码，也不该有 openid —— 他换手机就该能重新用学号进来。

### 9.9.2 学生登录 `POST /api/auth/student-login`

> **v0.38 改版：邀请码不再参与登录。** 学生可能同时上好几门课（每门课一个班），
> 邀请码只能指向"一个班"，用它当登录闸门等于逼学生为每个班记一个码。
>
> **v0.41 改版：不再要求先被导入花名册。** 原来登录就是"在 `t_student` 里按学号找人"，
> 找不到直接报错 —— 等于只有班主任导过名单的人才能用学生端。转专业的、插班的、
> 名单还没更新的，全被挡在门外，而他们看到的只是一句「没找到学号」。
> 现在查得到就进班；查不到也给一张 token，只是这张 token **没有班级**，只能用来入班。

```json
{ "studentNo": "202401001", "name": "王小明", "classId": 1 }
```

| 字段 | 必填 | 说明 |
|---|---|---|
| studentNo | 是 | 完整学号，跨班唯一（学号重复时按班拆开让学生选） |
| name | **是（v0.41 起）** | 花名册里有这个人时**必须一致**；没有时它就是要写进新建花名册行的名字 |
| classId | 否 | 该学号在多个班时指定进哪个班；不传且有多班则返回选班列表 |
| inviteCode | 否 | **已不参与登录**，仅为旧客户端保留字段，传了也忽略 |

**① 正常进班**（直接签发 token）：

```json
{
  "code": 0,
  "data": {
    "token": "...",
    "expiresIn": 604800,
    "student": { "id": 121, "name": "王小明", "studentNo": "202401001",
                 "classId": 1, "className": "24计算机软通1班",
                 "teacherName": "李光", "unread": 0 }
  }
}
```

**② 学号在多个班**（**不发 token**，让前端先选班）：

```json
{
  "code": 0,
  "data": {
    "token": null,
    "needClass": true,
    "classes": [
      { "classId": 1, "className": "24计算机软通1班", "teacherName": "李光" },
      { "classId": 3, "className": "24计算机软通2班", "teacherName": "王敏" }
    ]
  }
}
```

前端拿到 `needClass` 后弹一个底部选择器，选完带着 `classId` 再调一次同一个接口。

**为什么不在多班时默认进第一个**：进错班的学生看到的是别人的课表、别人的座位，
而且不会报错 —— 他会以为数据错了，不会想到是班错了。让他在两个班名里点一下，
成本一次点击，收益是"永远进对"。

**`token` 为 `null` 时前端绝不能存。** 存了就会带着空 token 去调业务接口，
后端返回 401，学生看到的是「登录失败」而不是「请选班级」。

**③ 还没进任何班（v0.41）**：

```json
{
  "code": 0,
  "data": {
    "token": "...",
    "expiresIn": 604800,
    "needJoin": true,
    "student": { "name": "测试新生", "studentNo": "202499001",
                 "classId": null, "className": "", "unread": 0 }
  }
}
```

这张 token 的 `subject` 是 `0`（一个永远不存在的 id），**没有 `classId` claim**。
它只能调两个接口：`POST /api/student/join`（入班）和 `GET /api/student/me`（我是谁）；
其余 `/api/student/**` 一律返回 **1009**「你还没加入班级，先填班主任给的邀请码」。

**为什么在拦截器挡、不在业务方法里判**：业务方法签名都是 `(classId, studentId)`，
`classId` 为 null 时会先撞上 `requireStudent` 的 1002 —— 而 1002 在前端等于
"登录失效，踢回入口页"。学生明明登录成功了却被踢出去，是最让人困惑的一种失败。
挡在拦截器里，前端能拿到一句准确的话（`request.js` 只对 1002 做清 token 跳转，1009 不跳）。

前端拿到 `needJoin` 直接 `reLaunch` 到 `pages/student/join/index?first=1`，**不要往首页走**。

失败一律 1004 + 可读文案：「学号和姓名对不上。姓名要和班主任名单上的一致，差一个字也进不去」。

**姓名为什么从选填改成必填**：登录不再依赖花名册之后，名单里查不到的人只剩"学号 + 姓名"
两样东西，姓名是唯一能写进新建花名册行的内容 —— 没有它，班主任的名单里会出现一个
只有学号、没有名字的人，谁也不知道那是谁。顺带也把"输错学号登进别人账号"这道闸关紧了。

**姓名比较会去掉所有空白**（含全角空格）：名单里是「王小明」，学生在手机上打成
「黄 旭铭」不该被判成"不是同一个人"。空白从来不带信息，只带手误。

### 9.9.3 老师发私信

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/teacher/message` | **我的私信会话列表**（通讯录顶部那一区），最多 20 个学生，按最近说话时间倒序 |
| GET | `/api/teacher/message/{studentId}` | 拉对话：`{ title(学生名), subtitle(学号), messages, unread }` |
| POST | `/api/teacher/message/{studentId}` | `{ "content": "..." }`，≤500 字 |
| POST | `/api/teacher/message/{studentId}/read` | 把学生发来的标为已读，返回标记条数 |

会话列表一行：

```json
{
  "studentId": 143,
  "name": "刘思远",
  "studentNo": "202401004",
  "lastContent": "老师我收到了",
  "lastTime": "11-18 16:01",
  "lastSenderRole": 2,
  "unread": 1
}
```

- **挂在根路径**，不是 `/list`：下面有 `/{studentId}`，任何字面量段都会被当成 studentId 试一次
- **跨全部班级**：老师带两个班时两类学生在同一个列表里，按时间排，不按班分组
- **已离班的学生不出现**：历史消息还在，但发不出去，列出来会让人以为能联系上
- `unread` 只数**学生发来且未读**的；`lastSenderRole` 给前端拼「我：」前缀

**老师侧不收 classId**：先按 studentId 查学生，再用 `resolveClass` 确认这个班是我的。
前端多传一个 classId 只会多一个越权的口子。

### 9.9.4 学生端

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/student/me` | 我是谁 + 未读数 |
| GET | `/api/student/message` | 和班主任的对话：`{ title(班主任名), subtitle(班级名), messages, unread }` |
| POST | `/api/student/message` | 回复，`{ "content": "..." }` |
| POST | `/api/student/message/read` | 标为已读 |

**学生侧所有接口都不收 classId / studentId**，两个值全从 token 里取（拦截器塞进 request）。

消息结构：

```json
{ "id": 12, "senderRole": 1, "content": "明天下午三点家长会",
  "time": "11-18 15:40", "read": false }
```

`senderRole`：1=老师发的，2=学生发的。前端据此决定气泡靠左还是靠右。

### 9.9.4b 加入班级 `POST /api/student/join`（v0.40，v0.41 支持自助入班）

两种人都调这个接口：

- **已经在某个班**、想进**另一个班**（学生可能同时上几门课，一门课一个班）；
- **还没进任何班**（v0.41 的未入班 token，subject=0，没有 classId）。

```json
// 请求
{ "inviteCode": "5XXZ74YSCB" }

// 响应
{ "token": "eyJ...", "expiresIn": 604800,
  "student": { "id": 121, "name": "王小明", "studentNo": "202401001",
               "classId": 2, "className": "24软通班2班",
               "teacherName": "李光", "unread": 0 },
  "alreadyIn": false }
```

**返回的 token 里 classId 已经换成目标班**，前端存下来之后所有学生接口都按新班走。
「加入群聊」就是这件事 —— 一个班一个群，进了班就在群里（见 §9.10.9）。

| 情况 | 返回 |
|---|---|
| 邀请码不存在 / 班已归档 | 1004「邀请码不对，找班主任要一下」 |
| 该班花名册里有这个学号，姓名一致 | 绑定到那一行，发新 token |
| 该班花名册里**没有**这个学号 | **新建一行**（`status=1` 在读），发新 token |
| 该班花名册里有这个学号，但**姓名对不上** | 1004「「24软通班2班」里的学号 202401001 叫「王小明」，和你填的姓名对不上」 |
| 这个学号在该班是**离班**状态（`status=0`） | 1004「你之前在这个班，现在不在名单里了，找班主任把你恢复」——**不新建重复行** |
| 本来就在这个班 | `alreadyIn=true`，**照样给一张可用 token** |
| 邀请码大小写不一致 | 先按原样查，查不到再转大写查一次，所以 `5xxz74yscb` 也能进 |

**为什么 `alreadyIn` 也要给 token**：返回 null 让前端判断的话，总会有人把 null 存进
storage，然后拿着空 token 去调接口，看到的是「登录失败」而不是「你已经在这个班了」。

**v0.41 为什么改了"不自助报名"这条规矩。** v0.40 的写法是：花名册里没有这个学号就报错，
让他找班主任。理由是成绩、请假、座位全挂在 `(classId, studentId)` 上，学生自己造一行，
等于往老师的名单里插一条没人知道的学籍。

但这条规矩的代价是**名单没更新的人永远进不来** —— 而现在登录本身已经放开了，
再卡住入班，等于让人登录完看到一堵墙。所以改成允许新建，用三条约束把风险压住：

1. **必须持有邀请码** —— 老师发码这个动作本身就是授权；
2. **学号在本班已有在读行时，姓名必须一致**才绑定 —— 挡住"拿别人学号入班"；
3. **学号在本班是离班状态时拒绝**，提示找班主任恢复，**不新建重复行** ——
   否则老师的名单里会出现两个同号的人。

新建的行 `status` 直接给 1（在读），**不是"待审核"**：老师端的名单页只认 1（在读）和
0（离班），塞一个 2 进去，学生入了班但班主任在任何列表里都看不到他 —— 比不建还糟。
真要加审批，得先给老师端做"待确认"那一屏，那是另一端的事。

> 副作用（已知）：学生自助建的行和老师导入的行长得一模一样，老师端看不出哪条是自己建的。
> 想区分的话，加一列 `self_joined` 并在名单里打个标即可，本次没做。

### 9.9.5 鉴权：按路径分权

`AuthInterceptor` 不只是"解析出 id 就放行"，而是**按路径双向卡死**：

| 路径前缀 | 要求 | 不满足时 |
|---|---|---|
| `/api/teacher/**` | role=1 | 3001「这是教师端的功能」 |
| `/api/student/**` | role=2 | 1002「请先用学号登录学生端」 |
| `/api/student/**` 且 token **没有 classId**（未入班） | 只许 `/api/student/join`、`/api/student/me` | 1009「你还没加入班级，先填班主任给的邀请码」 |
| 其余 `/api/**`（如 `/api/auth/me`） | role=1 | 3001 |

未入班的白名单是**白名单不是黑名单**：将来新增学生接口时默认"进不去"，
而不是"忘了拦"。这一条很要紧 —— 黑名单模式下，每加一个接口就多一个漏权限的机会。

**为什么必须这样**：教师接口到处在用 `request.getAttribute("userId")`。
如果学生 token 也往 userId 里塞学生 id，学生就能以"用户 121"的身份调教师接口 ——
而 `t_user` 里恰好也可能存在 id=121 的老师。这类越权最难发现，因为接口全"正常返回"。

放行清单只有两个：`/api/auth/wx-login`、`/api/auth/student-login`。

### 9.9.6 数据表 `t_direct_message`

见 `database/migrations/12_direct_message.sql`。

| 列 | 说明 |
|---|---|
| class_id / student_id | 线程键。一对一没有群名、成员、公告，不单独建会话表 |
| teacher_id | 班主任，留档用 |
| sender_role | 1=老师 2=学生 |
| content | ≤500 字（业务层卡） |
| read_at | 对方读的时间；null=未读 |

未读数的定义是"**对方发给我的、还没读的**"：老师侧数 `sender_role=2`，学生侧数 `sender_role=1`。
`read_at` 只有一个，够用 —— 一条消息的收件人只有一方。

### 9.9.7 前端

| 文件 | 说明 |
|---|---|
| `api/student-portal.js`（新） | 学生登录、`/student/*` 四个接口、退出 |
| `api/message.js`（新） | 老师侧四个接口（含会话列表） |
| `components/direct-chat/`（新） | 两边共用的聊天界面，只负责画，接口由页面注入 |
| `pages/student/login`（新） | 学号 + 姓名（v0.41 起姓名必填）；多班时弹底部选择器；`needJoin` 直接跳入班页 |
| `pages/student/home`（新） | 我的：姓名/学号/班级 + 私信入口（未读红点） |
| `pages/student/message`（新） | 与班主任对话（`mineRole=2`） |
| `pages/teacher/student/message`（新） | 老师侧对话（`mineRole=1`），`?id=` 传学生 id |
| `pages/teacher/student/detail`（改） | 底部操作栏加「私信」，和「编辑资料」并排 |
| `pages/teacher/contact/index`（改） | 顶部新增「私信」区：最近聊过的学生 + 最后一句 + 未读红点 |
| `pages/index/index`（改） | 「学生端」卡片从"即将开放"改成真入口；`enterTeacher` 会检查 `userRole`，避免拿学生 token 去调教师接口 |

**入口页要存 `userRole`**：一台设备只该有一个身份。`enterTeacher` 发现本地是
学生 token 时不会硬闯，而是重新走一次老师登录 —— 否则会卡在"进不去也不说为什么"。

**导航栏标题 = 对方姓名**，`pages.json` 里的「私信」只是占位：从通讯录连着点进
好几个学生时，标题一直写「私信」就分不清在跟谁说话。页内那条副标题只留
「学号 xxx」（学生侧是班级名），不再重复一遍名字。

**没聊过时不显示空状态插画**：一句「还没跟这位学生说过话」加一句隐私说明，
对新会话没有任何帮助，反而把输入框推得更远。空白聊天页是 IM 的正常形态。

---

## 9.10 学生端业务接口（v0.35）

§9.9 只解决了「学生能登录、能和班主任说话」。这一节是**学生端真正的主体**：
请假、成绩、座位、通知、资料。

### 9.10.0 一条贯穿全部的约定

**所有接口都不收 `classId` / `studentId`。** 两个值只来自 token：

```
token(role=2, sub=studentId, classId=xxx)
  → AuthInterceptor 校验 role=2，塞进 request
  → Controller 读 request.getAttribute(...)
  → Service 再查一次库确认「这个学生现在确实在这个班」
```

最后那次查库不是多余的：token 里的 classId 是**登录那一刻**的快照，学生换班之后
旧 token 还带着旧班级。只信 token 就会让转班的学生看到原班的数据。

共 **17 个接口**（含 1 个详情），学生端接口总数 21 个，全站 67 个。
（v0.38 追加 2 个：`GET /api/student/classmates`、`POST /api/student/aidi/chat`；
v0.39 下架 `classmates`、新增班级群 3 个，所以总数不变）

### 9.10.1 首页 `GET /api/student/home`

一次返回上半屏所有数据，和老师端 `GET /api/teacher/home/overview` 同构。

```json
{
  "code": 0,
  "data": {
    "classInfo": { "id": 1, "name": "24计算机软通1班", "grade": "大一",
                   "teacherName": "李光", "teacherTitle": "班主任" },
    "stats": { "leavePending": 0, "leaveThisMonth": 1, "unread": 0, "examCount": 2 },
    "term": { "weekNo": 12, "termStartDate": "2026-09-01" },
    "latestExam": { "examId": 2, "name": "期中考试", "examDate": "2026-11-10",
                    "total": 388.9, "fullTotal": 450, "rank": 5, "classSize": 47 },
    "seat": { "rowNo": 3, "colNo": 2 }
  }
}
```

| 字段 | 口径 |
|---|---|
| `stats.leavePending` | 我提交了、还没批的条数 |
| `stats.leaveThisMonth` | 本月**开始**过的请假条数（含已驳回） |
| `stats.examCount` | 我有成绩的考试次数（不是全班考过几次） |
| `latestExam` | 只在我有成绩的考试里找最近一次；没有就是 `null` |
| `latestExam.rank` | 同分并列（1、1、3）；无成绩为 `null` |
| `seat` | 没排座是 `null` |

**`latestExam` 为什么不取「班级最近一次考试」**：班里刚考完、还没录我的分时，
首页会显示一个空分数，学生以为考砸了。取「我最近一次有分的」才对。

### 9.10.2 请假

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/student/leave` | 我的全部请假，提交时间倒序，上限 100 |
| GET | `/api/student/leave/{id}` | 单条详情；不是自己的 → 1004 |
| POST | `/api/student/leave` | `{type, startAt, endAt, reason}`，落库状态固定 0 |
| POST | `/api/student/leave/{id}/cancel` | 撤销（仅 status=0），**真删行** |
| POST | `/api/student/leave/{id}/report` | 销假（仅 status=1），status → 3 |

单条返回：

```json
{ "id": 7, "type": 1, "startAt": "2026-11-24 08:00:00", "endAt": "2026-11-25 18:00:00",
  "reason": "发烧", "status": 0, "auditRemark": null,
  "createdAt": "2026-11-19 15:02:11", "updatedAt": "2026-11-19 15:02:11",
  "canCancel": true, "canReport": false }
```

**`canCancel` / `canReport` 由后端算，前端不写 `status === 0`。** 前端自己判断的话，
规则一改两边就不同步，而且这种不同步只会以「按钮能点、点了报错」的形式暴露。

**撤销为什么是真删行而不是加 `status=4`**：只有待审批能撤，这时老师还没看过，
删掉不丢任何审批信息；而加 4 的话老师端的状态文案表里没有 4，状态列会变空白 ——
那就得改老师端代码，而这次明确不动老师端。以后要做「撤回留痕」，正确做法是
加 4 **并同时改两端文案**，不是在这里硬塞。

**提交时后端拦的几件事**：类型必须在 1~3；`endAt > startAt`；跨日历日 ≤ 30 天；
原因 ≤ 255 字；同一 `(studentId, startAt, endAt)` 已有待审批/已通过 → 1003。
最后一条是防「网络慢点两次」，不拦的话老师会看到两条一模一样的申请。

### 9.10.3 成绩 `GET /api/student/score`

老师端是「一场考试 × 全班学生」的横表，学生端必须**转置**成「一个学生 × 历次考试」。

```json
{
  "list": [
    { "examId": 2, "name": "期中考试", "type": 2, "examDate": "2026-11-10",
      "subjects": ["语文", "数学", "英语"],
      "fullScore": { "语文": 150, "数学": 150, "英语": 150 },
      "scores": { "语文": 128.5, "数学": 131, "英语": 129.4 },
      "total": 388.9, "fullTotal": 450,
      "rank": 5, "classSize": 47, "classAvg": 352.6 }
  ],
  "summary": { "examCount": 2, "bestRank": 3 }
}
```

**排名后端算，前端不碰。** 前端要算就得拿到全班每个人的总分 —— 那等于把全班成绩
发给每个学生，「只看自己」就成了空话。学生只拿到**自己的名次和班级平均**。

`classSize` 是**有成绩的人数**，不是全班人数：转学生没参加过的考试不该把他算进分母。

`total` 为一科都没录时是 `null`（不是 0）；缺考科目在 `scores` 里是 `null`。
0 分和「没录」混在一起，平均分和排名都会被拉低。

### 9.10.4 座位 `GET /api/student/seat`

```json
{ "rowNo": 3, "colNo": 2, "rows": 7, "cols": 8, "seated": true }
```

**只给自己的格子，不给整张座位图。** `rows` / `cols` 只用来画「第 3 排 / 共 7 排」
这种相对位置。整张图意味着把全班每个人坐哪都发给每个学生 —— 那是老师的工作台。

### 9.10.5 通知 `GET /api/student/notice?limit=20`

和老师端一样是**算出来的，不是一张表**（没有 `t_notice`）。老师端算的是
「要你处理的事」，学生端算的是「已经有结果、要告诉你的事」：

| kind | 来源 | 说明 |
|---|---|---|
| `leave` | `t_leave_request` status ∈ {1,2,3} | 每条审批结果一条，按 `updated_at` 倒序 |
| `message` | `t_direct_message` 未读 | **只出一条汇总**，不逐条列 |

私信逐条列的话，老师连发 10 句就会把请假结果全顶下去。

```json
{ "total": 2, "list": [
  { "kind": "leave", "title": "你的请假被驳回", "status": 2,
    "desc": "其他 · 10-23 13:00 → 10-23 17:30 · 比赛通知没交上来，先驳回",
    "atText": "10-24", "atMillis": 1761..., "leaveId": 4 },
  { "kind": "message", "title": "班主任给你发了 1 条消息",
    "desc": "明天记得带实训手册", "atText": "昨天 16:01", "atMillis": 1761... }
] }
```

### 9.10.6 资料

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/student/profile` | 学生本人该看见的字段 |
| PUT | `/api/student/profile` | 只认 `phone` / `wechat` / `dormitory` |

**只给三个可改字段，是白名单不是黑名单。** 姓名、学号、班级、生日是学籍信息，
学生改了就和对不上名册，而且「改名字」没有审批流程兜着。要改只能找班主任。

**出参只有 12 个字段**：`id` / `name` / `studentNo` / `gender` / `birthday` /
`className` / `teacherName` / `dormitory` / `phone` / `wechat` / `nation` /
`politicalStatus`。

> v0.39 去掉了 `familyPhone1` 和 `trainingDirection`。
> 前者是**家长的电话**，学生看自己的档案不需要它，而"能看到"和"能拿到"是两件事；
> 后者是教务口径（培养方向），学生看了也改不了。老师端的学生详情照旧有这两项 ——
> 那是他填表、联系家长用的。

**`null` = 不改，空串 = 清空。** 这两个必须能区分：前端只改手机号时不会带
`dormitory`，把 `null` 也当成清空的话，学生每改一次电话宿舍就没了。

> ⚠️ 实现上必须用 `LambdaUpdateWrapper.set(...)`，**不能 `updateById(实体)`**。
> MyBatis-Plus 的 `updateById` 默认忽略 null 字段（NOT_NULL 策略），
> 「清空手机号」这个操作根本写不进库 —— 前端显示已清空，刷新一下又回来了。
> 这是实测时发现的，不是理论问题。

### 9.10.7 前端

| 文件 | 说明 |
|---|---|
| `api/student-data.js`（新） | 上面 15 个接口 |
| `utils/leave-fmt.js`（新） | 请假类型/状态/时长/时间格式化，三处页面共用 |
| `utils/student-aidi-store.js`（新，v0.39） | 乐迪对话本地存档，按学号隔离 |
| `components/student-tab-bar/`（新） | 学生端自定义底栏，逐项对齐原生 tabBar 尺寸 |
| `components/direct-chat/`（改，v0.39） | 加 `group` / `emptyText` 两个可选参数，默认行为不变 |
| `pages/student/home`（重写） | 顶部身份卡 + 四宫格 + 通知条 + 常用四格 |
| `pages/student/contact/index`（v0.38，v0.39 重写） | 班主任（私信入口）+ 班级群入口 |
| `pages/student/group/index`（新，v0.39；v0.42 重写） | 群聊：`?id=` 指定群，3 秒轮询 + afterId 增量；支持文本/图片/文件/视频，卡片降级显示标题 |
| `pages/student/group/join`（新，v0.42） | 填群邀请码加入别的群 |
| `pages/student/aidi/index`（新，v0.38） | 乐迪对话（只读自己的数据，对话存本地） |
| `pages/student/leave/index`（新） | 待审批 / 已通过 / 已结束 三段 |
| `pages/student/leave/apply`（新） | 申请表单（类型 / 起止 / 原因 / 实时时长） |
| `pages/student/leave/detail`（新） | 状态横幅 + 信息卡 + 进度条 + 撤销/销假 |
| `pages/student/score/index`（新） | 汇总 + 每场考试一张卡（总分条 + 各科） |
| `pages/student/seat/index`（新） | 座位号大字 + 位置示意图（只有自己的格子是红的） |
| `pages/student/notice/index`（新） | 请假结果 + 私信汇总 |
| `pages/student/profile/index`（新） | 身份卡 / 联系方式（可改）/ 学籍信息（只读）/ 退出登录 |
| `pages/student/profile/edit`（新） | 手机 / 微信 / 宿舍 |

**学生首页不用自定义导航栏。** 老师端首页是 tabBar 页才用 `custom`；
学生首页是从入口页进来的，用 `custom` 会把返回键一起干掉，
学生只能靠「退出登录」回入口 —— 那太绕了。

**学生端底栏是自定义组件 `components/student-tab-bar/`，不是 tabBar。**
小程序只有一个 tabBar，老师端已占满。组件参数逐项对齐原生底栏，
五个条目**和老师端一一对应**：首页 / 通讯录 / 乐迪 / 请假 / 我的。

| 底栏条目 | 学生端落点 | 为什么在这儿 |
|---|---|---|
| 首页 | `pages/student/home` | — |
| 通讯录 | `pages/student/contact/index` | 班主任私信和同学名单都在这儿，和老师端「通讯录」同位置 |
| 乐迪 | `pages/student/aidi/index` | 和老师端「乐迪」同位置，只是工具集换成只读的 |
| 请假 | `pages/student/leave/index` | 学生唯一需要"发起"的动作，必须一眼看得见 |
| 我的 | `pages/student/profile/index` | 资料 + 退出登录 |

**成绩和座位不进底栏。** 首页「常用」四格里已经有这两个入口，底栏再放一遍是重复；
底栏五个位置应该留给"每天都要点"的东西，成绩和座位是"考完试/开学时点一次"。

**退出登录在「我的」最下面，不在首页。** 首页是每天看数据的地方，
放一个"走人"的按钮只会被误点；「我的」才是账号相关操作的归处。

切栏目用 `redirectTo`，首页快捷入口对同样五个页面也必须用 `redirectTo`，
否则栈里会出现两个首页。

### 9.10.8 通讯录

学生端通讯录 = **一个班主任 + 我能在的那些群 + 两个加入入口**，没有同学名单。

| 区域 | 数据来源 |
|---|---|
| 班主任 | §9.9.4 `GET /api/student/me`（名字、班级、未读数） |
| 群列表（可多个） | §9.10.9 `GET /api/student/group/list` |
| 加入群聊 | §9.10.9 `POST /api/student/group/join`（群邀请码） |
| 加入其他班级 | §9.9.4b `POST /api/student/join`（班级邀请码，换班） |

> **v0.39 下架了 `GET /api/student/classmates`。**
> 第一版把花名册摊在通讯录里，用户一句话点破了问题：
> 「这些同学都没加好友，怎么出现在通讯录里了，而且都点不了」。
>
> 这个系统**没有好友关系**。通讯录在用户心里是"我能联系上的人"，
> 而花名册是"这个班有哪些人"—— 后者属于班级管理，不是社交。
> 更糟的是名单里的每一行看起来都可点（卡片行、有头像），
> 点下去什么都不会发生 —— 那是在承诺一个不存在的能力。
>
> 所以现在：想找人说悄悄话 → 班主任那一栏的「私信」；想跟全班说话 → 群聊。
> 同学之间没有点对点的通道，就不该出现在"通讯录"这个语义下面。

**「加入群聊」和「加入其他班级」是两个动作，不能合并：**
前者只是在 `t_student_container` 里记一笔"我能看这个群"，身份和班级都不变；
后者换的是 token 里的 classId，成绩、请假、座位、群聊全部跟着换。
放在一起会让人以为"加个群就把班也换了"。

### 9.10.9 群聊（v0.42 改为接入老师端的群）

> **v0.42 重写。** v0.39 这里是一个独立的"班级群"（表 `t_class_message`），
> 结果是：老师在「24计算机软通1班」群里发了 22 条消息，学生打开"班级群"是一间空屋子；
> 老师把群邀请码发给学生，学生输了提示"无效"。**因为那本来就是两份数据，只是名字撞了。**
> 现在学生端看到的就是老师端建的那个群（`t_subject_container`），一份消息、一个邀请码。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/student/group/list` | 我能看到的群：本班的（自动）+ 用码加入的 |
| GET | `/api/student/group/{id}` | 单个群的信息：群名、班级、人数、群主、能不能退 |
| GET | `/api/student/group/{id}/messages?afterId=&size=` | 消息列表。带 `afterId` 是增量，不带是最近 N 条 |
| POST | `/api/student/group/{id}/messages` | 文本 `{ "content": "..." }`；媒体 `{ "type": 2, "mediaUrl": "/files/..." }` |
| POST | `/api/student/group/{id}/upload?kind=image` | 上传附件（v0.43）。`kind` = image / video / file |
| POST | `/api/student/group/{id}/leave` | 退出用邀请码加入的群（v0.43）。本班的群返回 1001 |
| POST | `/api/student/group/join` | `{ "inviteCode": "953738D26C" }` 加入别的群 |

`{id}` 是 `t_subject_container.id`，**不是 classId**：一个班可能有好几个群
（班级群、课程群），用 classId 表示不了。

```json
// GET /api/student/group/list
[ { "id": 2, "name": "24计算机软通1班", "subjectName": "24计算机软通1班",
    "className": "24计算机软通1班", "ownerName": "李光",
    "memberCount": 59, "teacherCount": 1, "studentCount": 58,
    "lastContent": "我是陈志强", "lastTime": "11-24 11:01",
    "classGroup": true, "canLeave": false } ]

// GET /api/student/group/2/messages
[ { "id": 29, "senderType": 2, "senderName": "王小明", "content": "大家好",
    "type": 1, "time": "11-24 09:20", "createdAt": "2026-11-24 09:20:31", "mine": true } ]
```

| 字段 | 口径 |
|---|---|
| `senderType` | 1=老师 2=学生。**不是** `senderRole`（那个是群主/班委/成员） |
| `senderName` | 学生消息是**发送那一刻**的快照；老师消息由后端回查 `t_user` |
| `mine` | 后端算好，并且**连 senderType 一起比** |
| `memberCount` | 老师成员 + 本班学生 + 用码加入的其他学生。老师端那个 `memberCount` 只算老师 |
| `teacherCount` / `studentCount` | 上面那个总数的拆解，群信息页分两栏显示 |
| `classGroup` | true=本班的群（自动可见）；false=用邀请码加入的 |
| `canLeave` | `!classGroup`。本班的群退不掉 —— 在不在群里由 `class_id` 派生 |
| `createdAt` | 原始 `yyyy-MM-dd HH:mm:ss`（v0.43）。`time` 只有「月-日 时:分」，做不了"间隔 5 分钟才插时间" |
| `lastContent` | 媒体消息的 `content` 是空串，这里补成 `[图片]` / `[视频]` / `[文件]`，否则列表上的"最后一条"是空白 |

**媒体消息的字段和老师端 `MessageSendDTO` 完全一致**（`type` / `mediaUrl` / `fileName` /
`fileSize` / `duration`），`content` 上没有 `@NotBlank`，按 `type` 分支校验。
`mediaUrl` 只认 `/files/` 前缀 —— 不校验的话谁都能往群里挂一个外部链接。

**上传走 `/api/student/group/{id}/upload`，不复用 `/api/container/{id}/upload`**：
后者第一步是 `requireMember`，而学生不在 `t_container_member` 里，永远过不去。
落盘、扩展名白名单、大小上限共用同一份 `ContainerFileServiceImpl`，只是权限判断换成
"这个群我看不看得见"（`requireVisible`）。

**可见性只有两条，覆盖了"进群"的全部情况：**

1. **本班的群自动可见** —— `container.class_id` 等于我的 classId。花名册里有你，
   你就在群里，**不需要邀请码**。这是"一个班一个群"的落点。
2. **用邀请码加入别的群** —— 记在 `t_student_container`。跨班旁听、
   `class_id` 为空的公共群（如高数群）都走这条。

看不到的群一律 **3002**「你不在这个群里，找班主任要邀请码」。列表里能看到、
点进去说没权限，是最容易让人以为"小程序坏了"的不一致，所以 `list()` 和
`requireVisible()` 用同一套判断。

**为什么学生不写进 `t_container_member`：** 那张表的 `user_id` 指向 `t_user`，
学生不是 `t_user`（见 §9.9.1）。硬塞进去，老师端的成员管理（禁言、设班委、移除）
会把学生当老师处理；而且两套自增 id 都有 121 号，会静默串成同一个人。
学生靠"班级"这一层关系天然在群里，不需要成员行。

**消息表就是 `t_container_message`**，加了 `sender_role`（1 老师 / 2 学生）
和 `sender_name`（学生姓名快照）。老师端的 `MessageVO` 因此也多了 `senderType`：
`mine` 必须连角色一起比，否则 `studentId=1` 发的消息会显示成老师 1 自己发的。

**学生只能看文本/图片/文件/视频和卡片的标题**，接龙名单和抽签结果不开放 ——
那是老师的工作台。设了加入密码的群，学生端暂时进不去，会明确提示找班主任去掉密码。

**老师端的班级群人数（v0.43）**：`GET /api/teacher/class/list` 新增 `groupTotalCount`
（老师 + 学生），通讯录「班级群」那一行改用它。原来显示 `groupMemberCount` = 1，
学生端同一个群显示 59 —— 老师会以为"群里没人"。

**`GET /api/teacher/container/list` 的 `className` 修了一个错位（v0.43）**：
`classNames` 的 key 是 classId，取值时却用了 `c.getId()`（容器 id），
结果每个群都显示成隔壁班的名字（高等数学 → 24计算机软通1班，一班 → 二班，二班 → null）。
老师照着列表找"一班的群"，看到的是二班，以为群没了。

### 9.10.10 乐迪 `POST /api/student/aidi/chat`

学生端乐迪是**另一套实现**（`StudentAidiService`），不是老师端 `AidiServiceImpl` 加权限判断。
共用一个类就得在每个工具里判断"这个人是老师还是学生"，漏一个就是越权。

```json
{ "messages": [ { "role": "user", "content": "我上次考多少" } ] }
```

返回：

```json
{ "code": 0, "data": { "reply": "上次是期中考试，总分 388.9（满分 450），班级第 5。",
                       "toolCalls": 1 } }
```

**只有 4 个只读工具，且都不收"查谁"：**

| 工具 | 数据来源 |
|---|---|
| `my_leave` | 我的请假记录 |
| `my_score` | 我的历次成绩 + 名次 |
| `my_seat` | 我的座位 |
| `my_info` | 我的班级 / 学号 / 班主任 |

**没有 `action`，学生端乐迪不能改任何东西。** 老师端的两段式确认卡（§9.8.4）
在这里整条链路都不存在 —— 不是隐藏按钮，是后端压根没有写接口。

**`classId` / `studentId` 由后端注入，请求体里没有这两个字段。** 和 §9.10.0 同一条规矩。

系统提示词里写死三件事：只查自己的数据、必须调工具不许凭印象答、回答 3~5 行不用 markdown。
实测「张三的数学考多少」会回「这个我看不到，只能查你自己的成绩。」

**一次问答最多 2 轮工具调用（v0.39 从 3 改成 2）。** 耗时几乎全在"串行等模型"上，
每多一轮就多一次完整 HTTP 往返。2 轮够用：第 1 轮决定查什么，第 2 轮拿到数据后回答；
第 2 轮还在要工具说明它没查明白，这时候关掉工具让它用已有数据说话，比再放一轮更快也更准。

**日志里有一行耗时**（`学生乐迪：1740ms，2 轮，1 次工具`）。用户说"慢"的时候，
先看这行：是上游慢（单轮就 5 秒以上），还是我们绕了太多轮（轮数 ≥3）。

实测（本机，2026-11-24）：简单问题 0.8~1.4 秒，查一次数据 1.7~2.1 秒，
查两次数据 1.9~2.6 秒。**这个量级已经是两跳 LLM 调用的正常值**，
再快只能上流式输出（`stream: true` + 小程序 `enableChunked`），那是另一个工程。

前端那边有三件事让"慢"变得可以接受：提问后立刻出现「正在查…」气泡；
3 秒后换成「还在查，马上好…」（证明它还活着）；**对话存本地**，
切到别的页面再回来还在（见下）。

**对话存档（v0.39）**：`utils/student-aidi-store.js`，key 是 `student_aidi_chat_v1`，
按**学号**隔离，最多 40 条，7 天过期。和老师端的 `aidi_chat_v1` 是两个 key ——
共用一个的话，同一台设备上老师一进乐迪，学生的对话就没了。
不存 `loading` 气泡（恢复出来会永远转圈），也不存失败气泡（会变成一条假历史）。

---

## 10. 前端 api 文件规划

按模块一个文件，**不要全塞进一个 `api.js`**（做到 10 个模块时会变成 500 行，找函数靠 Ctrl+F）。

```
src/api/
├── request.js      # 请求封装（已有）
├── auth.js         # 登录、当前用户（已有）
├── home.js         # 首页聚合（已有）
├── todo.js         # 待办        ← 本轮新增
├── student.js      # 学生        ← 本轮新增
├── score.js        # 成绩        ← 本轮新增
├── seat.js         # 座位        ← 本轮新增
└── leave.js        # 请假        ← 本轮新增
```

**命名规则**（每个文件都一样，形成肌肉记忆）：

| 前缀 | 方法 | 例子 |
|---|---|---|
| `get` | 查询 | `getStudentList`、`getSeatChart` |
| `add` | 新增 | `addStudent`、`addTodo` |
| `update` | 修改 | `updateStudent` |
| `del` / `remove` | 删除 | `delTodo`、`removeStudent` |
| 动词 | 特殊动作 | `auditLeave`、`saveSeatChart` |

---

## 11. 前端接入说明

1. 首页当前是**写死的 mock 数据**，位置在 `src/pages/teacher/index/index.vue` 的 `classInfo` / `stats` / `quote` / `weekNo`。
2. 接口就绪后，把这几段换成一次请求：

```js
const res = await request({ url: '/teacher/home/overview' })
classInfo.value = res.classInfo
stats.value = [
  { value: res.stats.leaveToday,    label: '今日请假', color: '#C9A227' },
  { value: res.stats.birthdayToday || '—', label: '今日生日', color: '#8C3B34' },
  { value: res.stats.studentCount,  label: '学生',     color: '#A81E28' },
  { value: res.stats.todoUndone,    label: '待办未完成', color: '#8A6A2E' }
]
```

3. **模板一行都不用改** —— 前提是现在 mock 的字段名和本文一致。所以写页面时字段名就要按这里来。
4. 日期、时间、问候语、周几：**前端本地算，不走接口**（第 8 周除外，那个后端算）。
5. 请求封装在 `src/api/request.js`，统一处理：
   - 拼 `baseURL`
   - 注入 `Authorization`
   - 收到 `1002` 时清 token 并 `reLaunch` 到 `/pages/index/index`
   - 非 0 的 code 统一 toast
   - **网络层失败也 reject 一个 `Error`**，`e.message` 是「网络异常，请检查后端是否启动」。
     原来 reject 的是 `uni.request` 的原始对象，只有 `errMsg: "request:fail ..."`，
     页面拿 `e.message` 永远是 undefined，想显示原因就只能显示「加载失败」四个字

6. **`silent` 选项：页面自己画错误块时，别再弹 toast**

   每个列表接口都接受第二个参数 `{ silent: true }`：

   ```js
   const res = await getStudentList({ status: -1 }, { silent: true })
   ```

   不加这个开关的话，断网时会同时出现一个 toast 和一个页面级错误块，
   说的是同一句话，看起来像出了两个错。

   **什么时候传**：页面上有「加载失败 + 重试」块的列表加载。
   **什么时候别传**：保存、删除、审批这类**动作**——动作失败了用户必须立刻知道，
   而且页面上没有对应的错误块。

7. **加载失败 ≠ 没有数据**

   两者在界面上必须能区分，这是 §11.8 的约定，也是 `components/load-error/load-error.vue`
   存在的理由：

   | 情况 | 显示 |
   |---|---|
   | 请求中 | 骨架屏 / 「正在加载…」 |
   | 请求失败，且手里没有旧数据 | 错误块 + 重试按钮 |
   | 请求成功，但确实是 0 条 | 空状态（「这个班还没有学生」） |
   | 请求失败，但手里有旧数据 | 继续显示旧数据，不盖错误块 |

   最后一条容易被忽略：刷新失败时把已有内容整块换成错误页，
   等于告诉用户「你刚才看到的数据也没了」。

8. **错误块里不要出现编造的数字**

   最典型的错误是：断网时顶部还显示「0 人在读 / 全部 0 / 男生 0」，
   或者「0 项待处理」。这些 0 不是事实，是「没拿到」被当成了「是 0」。
   做法是：拿不到数据时，统计位显示 `—`，或者整块藏掉。

---

## 12. 附录

### 附录 A · 已建表 DDL（留档）

> ✅ **`t_exam` / `t_score` / `t_seat` 都已经建好了**，同步写进了 `sql/01_schema.sql` 第 6、7、8 段。
> 这里留档，方便重装库时对照；重装库请直接跑 `sql/01_schema.sql`，不要只跑这一段。

```sql
USE `class_assistant`;

-- -------------------------------------------------------------
-- 6. 考试表：一次考试一条
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_exam` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  `name`        VARCHAR(64)     NOT NULL                COMMENT '考试名称，如 第一次月考',
  `type`        TINYINT         NOT NULL DEFAULT 2      COMMENT '1=周测 2=月考 3=期中 4=期末',
  `exam_date`   DATE            NOT NULL                COMMENT '考试日期',
  `subjects`    VARCHAR(255)    NOT NULL                COMMENT '科目，逗号分隔，如 语文,数学,英语',
  `full_score`  VARCHAR(255)    DEFAULT NULL            COMMENT '各科满分，JSON，如 {"语文":150}；NULL=全部 100',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_name` (`class_id`, `name`),
  KEY `idx_class_date` (`class_id`, `exam_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '考试';

-- -------------------------------------------------------------
-- 7. 成绩表：一行 = 某学生某科的成绩
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_score` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属班级（冗余，便于按班统计）',
  `exam_id`     BIGINT UNSIGNED NOT NULL                COMMENT '考试',
  `student_id`  BIGINT UNSIGNED NOT NULL                COMMENT '学生',
  `subject`     VARCHAR(16)     NOT NULL                COMMENT '科目名',
  `score`       DECIMAL(6,1)    DEFAULT NULL            COMMENT '分数，NULL=缺考',
  `full_score`  DECIMAL(6,1)    NOT NULL DEFAULT 100    COMMENT '满分',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_student_subject` (`exam_id`, `student_id`, `subject`),
  KEY `idx_class_exam` (`class_id`, `exam_id`),
  KEY `idx_student` (`student_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩';

-- -------------------------------------------------------------
-- 8. 座位表：一行 = 一个格子（坐没坐人都存）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_seat` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_id`    BIGINT UNSIGNED NOT NULL                COMMENT '所属班级',
  `row_no`      TINYINT         NOT NULL                COMMENT '第几排，从 1 开始',
  `col_no`      TINYINT         NOT NULL                COMMENT '第几列，从 1 开始',
  `student_id`  BIGINT UNSIGNED DEFAULT NULL            COMMENT '学生，NULL=空座位',
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_row_col` (`class_id`, `row_no`, `col_no`),
  KEY `idx_class_student` (`class_id`, `student_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '座位';
```

**为什么 `t_seat.student_id` 不建唯一键**：MySQL 的唯一键允许多个 `NULL`，所以建了也能跑。但"一个学生坐两个位置"这种错，唯一键只会甩一句 `Duplicate entry`，Service 层校验才能说出「「王小明」被排到了两个座位」。错误信息给不给得出，取决于校验放在哪一层。

**为什么 `t_score.score` 用 `DECIMAL(6,1)` 而不是 `INT`**：有些科目满分 150，会有 128.5 这样的半分。`DECIMAL(6,1)` 表示最多 6 位、1 位小数（最大 99999.9）。用 `FLOAT` 会出现 `0.1 + 0.2 = 0.30000000000000004` 这种精度问题。

**为什么 `uk_exam_student_subject` 要建唯一键**：批量录入靠 `INSERT ... ON DUPLICATE KEY UPDATE` 实现幂等，没有这个唯一键就变成重复插入。

### 附录 B · 实现顺序建议

做每个模块都按这个顺序，**不要跳步**（跳了必返工）：

```
1. 建表 / 加字段        → 执行 DDL，Navicat 里看一眼字段对不对
2. Entity              → 和表字段一一对应
3. VO / DTO            → 接口的出入参，字段名按本文档
4. Mapper              → 只写接口方法，复杂 SQL 写在 XML 或注解
5. Service + Impl      → 业务逻辑，班级归属校验在这里做
6. Controller          → 只做参数接收 + 调 Service + 包 Result
7. Apifox 测试         → 6 条用例：正常 / 空参 / 越权 / 不存在 / 重复 / 边界
8. 前端 api/xxx.js     → 照「前端怎么用」抄
9. 前端页面            → 接数据、空状态、加载态、错误态
```

**Service 层必须做的三件事**（每个模块都一样）：

```java
// ① 拿当前用户
Long userId = UserContext.getUserId();

// ② 反查班级（不信任前端传的 classId）
Long classId = classService.getMyClassId(userId);
if (classId == null) return 空数据;   // 或抛 1001「请先绑定班级」

// ③ 所有查询 / 更新都带 class_id 条件
studentMapper.selectById(id, classId);   // ← 注意第二个参数
```

### 附录 C · 术语速查

| 术语 | 意思 |
|---|---|
| 契约 / 接口契约 | 前后端约定的接口清单和字段定义，就是本文档 |
| DTO | Data Transfer Object，**接收**前端传进来的参数（如 `StudentCreateDto`） |
| VO | View Object，**返回**给前端的数据（如 `StudentVO`） |
| Entity | 和数据库表一一对应的类 |
| 水平越权 | 同角色的用户 A 看到了用户 B 的数据（改 `classId` 就能看别的班） |
| 幂等 | 同一个请求发多次，结果和发一次一样（批量录入靠唯一键实现） |
| 软删除 | 不真删，把 `status` 改成 0，数据还在 |
| N+1 查询 | 循环里查数据库，100 条数据查 101 次，性能杀手 |

---

## 13. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v0.1 | 2026-10-19 | 初版：登录、当前用户、首页聚合 |
| v0.2 | 2026-10-23 | 补全 29 个模块的接口全景；新增待办、学生、成绩、座位、请假 5 个模块的完整契约；新增错误码 1006 / 3002；新增数据字典 `exam.type`；新增待建表 DDL（`t_exam` / `t_score` / `t_seat`）；新增实现顺序与术语速查 |
| v0.3 | 2026-10-27 | 座位模块定稿：§8.1 改为「空座位也存一行」（否则 rows/cols 会缩水）；§8.2 响应新增 `unseated`；§8.3 明确 `seats` 可只传有人的格子、`saved` 是已排座人数；错误表补齐 3002 / 离班校验；附录 A 标记 `t_seat` 已建 |
| v0.3 | 2026-10-27 | 成绩模块落地：`POST /exam` 新增可选 `fullScore`，`t_exam` 加 `full_score` 列和 `uk_class_name` 唯一键；**修正年级排名算法**（原来按 `examId` 跨班对齐是错的，改成按「考试名 + 考试日期」）；`t_exam` / `t_score` 已在正式库建好 |
| v0.4 | 2026-10-28 | 学生详情落地：§6.2 从「只有基本字段」扩展为聚合详情，新增 `seat` / `leave` / `latestScore` 三块（都可为 `null`）；错误表补 1001（id 非数字）和 3001（非老师）；`GlobalExceptionHandler` 新增类型转换异常 → 1001 |
| v0.5 | 2026-10-28 | 学生增删改落地：§6.3 补学号唯一性三个细节（含已离班、空值归一、错误信息带姓名）和「DTO 字段即权限边界」；§6.4 补 `updateById` 跳过 null 的坑，改用 `LambdaUpdateWrapper.set()`；§6.5 补幂等与「移出时顺手清座位」；§6.2 区分「缺考」和「没参加那次考试」 |
| v0.6 | 2026-10-28 | 名单导入落地：`t_student` 加 9 个可空字段（宿舍/手机号/微信/家人电话×2/培训方向/民族/政治面貌/入党积极分子），§6.2~§6.4 同步；新增 §6.6 预览、§6.7 确认两个接口；明确「学号一律字符串」「班级名归一化只用于比较」「单行失败不回滚整批」三条约定；`application.yml` 加 multipart 5MB 上限 |
| v0.7 | 2026-10-28 | 新增 §6.8 班级设置（`GET` / `PUT /api/teacher/class`）—— 补上「改班级名」这个缺失的入口，此前班名起错了就只能重建库；§6.6 响应新增 `className` / `otherClassNames`，并说明「整张表被班级拦下」时前端要给「去改班级名」的出口，以及**为什么不做「忽略班级列」开关** |
| v0.8 | 2026-10-28 | 新建考试页落地：§7.3 补前端页面位置、`fullScore` 只提交填过的科目、`uni.$emit('exam:created')` 的用途；§11 新增 `silent` 选项（页面自己画错误块时别再弹 toast）、「加载失败 ≠ 没有数据」四种状态表、「错误块里不要出现编造的数字」；`request.js` 网络失败改为 reject `Error` |
| v0.9 | 2026-10-29 | 新增 §9.5 学科班级容器（22 个接口）：容器 / 成员 / 黑名单 / 精华 / 邀请码 / 加入链路；新增错误码 1007 / 1008 / 3003 / 3005；明确「班委不能踢人」「移除时可选拉黑」「DELETE 参数走 query」；新增 6 张表（见 `database/migrations/04_subject_container.sql`） |
| v0.10 | 2026-10-29 | 体验返工（不改表结构）：①`subjectName` 与 `name` 合并为一个用户自定义名称（大学课程/考研课不再塞不进「学科」），§9.5.3 同步；②创建接口新增 `inviteExpireAt`（`yyyy-MM-dd HH:mm:ss`，精确到秒，优先级高于 `inviteExpireDays`），邀请码有效期从"只能选天"变为"可选到分钟"；③UI 文案「学科容器/容器」统一改为**课程群组/群组**（接口路径与数据库列不变） |
| v0.11 | 2026-10-29 | 群聊上线（24 个接口）：新增 §9.5.8 `GET` / `POST /api/container/{id}/messages`；新增表 `t_container_message`（见 `database/migrations/05_container_message.sql`），**不建会话表**、用自增 `id` 当轮询游标；禁言从此真正生效（`assertCanSpeak` 返回 3005）；页面动线改为「列表 → 聊天 → 详情」，聊天页用自定义导航（左上返回 / 右上详情） |
| v0.12 | 2026-10-30 | 群聊支持图片/视频/文件（25 个接口）：新增 §9.5.9 上传接口 `POST /api/container/{id}/upload`，新增 `t_container_message` 的 `media_url` / `file_name` / `file_size` / `duration` 四列（`06_container_message_media.sql`），`type` 扩为 1文本 2图片 3视频 4文件；`content` 去掉 `@NotBlank` 改为按类型分支校验；**媒体地址只认 `/files/` 前缀**；附件走 `/files/**` 静态映射（不挂 `/api`，否则原生组件带不上 token）；右下角按钮改为「空 → ＋ 工具面板 / 有字 → 发送」；**邀请码不再出现在列表和聊天页，过期时详情页也不展示码本身** |
| v0.13 | 2026-11-02 | **多班级改造（28 个接口）**：`ClassService.myClass()` 由 `selectOne` 改为"班级列表 + 按 classId 解析"，修掉"老师带第二个班时所有教师端接口一起抛 TooManyResultsException"；新增 §9.6（`GET /class/list`、`POST /class`、`POST /class/{id}/group`），建班自动建班级群（群主=班主任）；home / student / exam / seat / leave / todo / 导入 全部支持可选 `classId`（缺省=第一个班，越权 3002）；新增 `database/migrations/07_class_group.sql` 给历史班级补群；前端 tabBar「学生」改为「通讯录」，新增全局班级状态 `store/class.js`、通讯录页、建班页、首页班级选择器、成绩页班级选择 |
| v0.14 | 2026-11-02 | **通讯录支持改名 / 删除班级**：新增 `DELETE /api/teacher/class/{classId}`（第 29 个接口，删除=归档 status=0，连带归档班级群，学生/成绩/座位/请假记录保留）；`PUT /class` 补上重名检查（排除自己，重名 1006）并在改名时同步班级群名（原来不同步，通讯录卡片上会同时出现新旧两个班名）；`ownsStudent` 增加班级 status 判断，归档班级的学生不能再按 id 直接访问；新增 §9.6.4 |
| v0.15 | 2026-11-04 | **接龙与抽签（9 个接口）**：新增 §9.7；`t_container_message` 加 `ref_id` 列，`type` 扩为 5接龙卡 / 6抽签卡；新增 4 张表（`t_relay` / `t_relay_entry` / `t_lottery` / `t_lottery_result`，见 `database/migrations/09_relay_lottery.sql`）；新增错误码 4101 / 4102 / 4103 / 4105；**「已满」「已截止」是算出来的不存库**、**卡片状态不存消息表**、**接龙不产生参与消息**、**抽签名单发布时快照**；`t_lottery_result` 的排序字段叫 `sort_no` 不叫 `rank`（`rank` 是 MySQL 8 窗口函数保留字，MyBatis-Plus 不加反引号会让 SELECT 直接语法错误）；＋ 面板改 4 列宫格 |
| v0.29 | 2026-11-05 | **首页通知（第 39 个接口）**：新增 §5.1.1 `GET /api/teacher/home/notices`，聚合待审批请假 / 今天到期的待办 / 今天生日三类；**不落库、不记已读**，通知跟着业务状态自己消失；`HomeOverviewVO` 删掉 `quote`（金句框改成通知框，字段一起删，不留死字段）；新增前端页面 `pages/teacher/notice/index`；修复「学生」入口带 `classId=0` 导致后端回 1004「班级不存在或已归档」、学生页只剩错误块的问题（`classId` 为 0 时不再拼参数） |
| v0.30 | 2026-11-18 | **乐迪（第 40 个接口）**：新增 §9.8 `POST /api/teacher/aidi/chat`，走 DeepSeek 的 OpenAI 兼容接口 + 6 个只读工具（`class_overview` / `list_students` / `student_scores` / `exam_summary` / `leave_records` / `todo_items`）；**对话不落库**，历史由前端带、后端只取最近 8 条；**班 id 由后端注入**，模型只能决定查什么、不能决定查哪个班；新增错误码 5001~5004；`request.js` 支持 `timeout` 参数（AI 问答单独放宽到 60 秒）；tabBar 新增「乐迪」，占位页改成聊天页 |
| v0.31 | 2026-11-18 | **乐迪会动手了（第 41 个接口）**：新增 §9.8.4 `POST /api/teacher/aidi/act`；`/chat` 响应新增 `action`（待确认的动作）；新增 7 个写工具 `create_todo` / `complete_todo` / `audit_leave` / `send_group_message` / `create_relay` / `create_lottery` / `save_scores`；读工具补 `list_exams`（录成绩前先确认考试名和科目），`exam_summary` 补各科最高分；**两段式确认**——模型只走 `preview()` 生成卡片，落库要等老师点按钮；`act` 不经过模型、不检查 API Key、按 classId 重新校验 `_todoId` / `_leaveId` / `_studentId` / `_examId` 的归属；`save_scores` 复用 `scoreService.batchSave`，不另写一套校验；前端聊天页加确认卡（执行中 / 已执行 / 已取消 / 失败可重试四种状态） |
| v0.32 | 2026-11-18 | **乐迪有长期记忆了**：新增 §9.8.5 事实层记忆 + 表 `t_aidi_memo`（`database/migrations/11_aidi_memo.sql`）；新增 2 个记忆工具 `remember` / `forget`，**不走确认卡**（改的是助手自己的笔记，不碰业务数据）；记忆按 user + class 隔离，`class_id=0` 为跨班通用层，上限 30 条/人；记忆**拼进系统提示词**而不是做成 `recall` 工具（模型意识不到自己该查）；`forget` 命中多条时列出来反问，绝不自己选；前端聊天页加**本地存档**（`aidi_chat_v1`，最近 40 条 / 7 天 / 按班级隔离，不存 `action`，确认卡不跨会话复活）+ 顶部细栏与「新对话」入口 |
| v0.33 | 2026-11-18 | **学生端落地 + 师生私信（+6 个接口，共 48 个）**：新增 §9.9；`POST /api/auth/student-login`（班级邀请码 + 学号，可选姓名核对）签发 **role=2** 的 token，学生不是 t_user；`AuthInterceptor` 改为**按路径分权**（`/api/teacher/**` 要 role=1、`/api/student/**` 要 role=2），学生 token 不再往 `userId` 属性里塞 id；老师侧 `/api/teacher/message/{studentId}` 三个接口（拉对话 / 发 / 标已读），学生侧 `/api/student/me`、`/api/student/message` 三个接口；新增表 `t_direct_message`（`database/migrations/12_direct_message.sql`），线程键 `(class_id, student_id)`，未读用单列 `read_at`；前端新增学生端登录 / 我的 / 对话三页 + 老师侧对话页 + 共用组件 `direct-chat`，学生详情页底部加「私信」，入口页「学生端」从占位改成真入口并引入 `userRole` 防止两种 token 混用 |
| v0.34 | 2026-11-18 | **私信会话列表（第 49 个接口）**：新增 `GET /api/teacher/message`，返回最近聊过的 20 个学生（`lastContent` / `lastTime` / `lastSenderRole` / `unread`），用 `GROUP BY student_id` + `MAX(id)` 聚合，避免"取最近 N 条再在内存去重"导致久未联系的学生从列表消失；通讯录页顶部新增「私信」区；私信页导航栏标题改成对方姓名（`pages.json` 的「私信」只作占位），页内副标题只留学号；删掉新会话的空状态插画与"这里只有你和 X 能看见"提示 |
| v0.35 | 2026-11-19 | **学生端业务接口（+11，共 60 个）**：新增 §9.10；`GET /api/student/home`（首页聚合）、`leave` 5 个（列表/详情/提交/撤销/销假）、`GET /api/student/score`（历次考试 + 各科 + 排名，排名后端算）、`GET /api/student/seat`（只给自己的格子）、`GET /api/student/notice`（请假结果 + 私信汇总，算出来的不是表）、`profile` 2 个（只认手机/微信/宿舍）。全部不收 classId/studentId，只从 token 取；前端新增 9 个页面 + `api/student-data.js` + `utils/leave-fmt.js`，未改任何老师端页面 |
| v0.36 | 2026-11-19 | **学生端有底栏了**：新增 `components/student-tab-bar/`（小程序只有一个 tabBar，老师端已占满，学生端只能自定义）；尺寸逐项对齐原生底栏（100rpx 高 / 48rpx 图标 / 20rpx 字 / `#8A8178`↔`#C5342B`），底部留 `env(safe-area-inset-bottom)`；切栏目用 `redirectTo`，首页快捷入口对 tab 级页面同样走 `redirectTo` 防止栈里出现两个首页；学生首页去掉 `navigationStyle: custom`（它是被 `navigateTo` 进来的，custom 会把返回键一起干掉） |
| v0.37 | 2026-11-19 | **底栏对齐老师端**：学生端五个条目改成 首页 / 通讯录 / 乐迪 / 请假 / 我的，与老师端 tabBar 一一对应；**成绩和座位退出底栏**（首页「常用」四格里已有，底栏该留给每天都点的东西）；首页删掉「班主任私信」长条（挪进通讯录）和「我的通知」长条（那条"暂时没有新通知"的提醒条本身就是入口）；首页删掉「退出学生身份」，改成「我的」页最下面的**退出登录**按钮 |
| v0.38 | 2026-11-19 | **登录不要邀请码了（+2，共 64 个）**：`POST /api/auth/student-login` 改为**只认学号**（可选姓名核对），`inviteCode` 降级为忽略字段 —— 学生可能同时上好几门课，邀请码只能指向一个班，**邀请码从此只用于加入班级群聊**；多班时返回 `needClass=true` + `classes[]` 且**不发 token**，前端弹底部选择器带 `classId` 再登一次（`StudentLoginVO.token` 为 null 时前端绝不能存，否则拿着空 token 去调接口只会看到"登录失败"）；新增 §9.10.8 `GET /api/student/classmates`（`total` 含自己 / `list` 不含自己，只给姓名+学号+性别，不可点——学生之间没有发消息的通道）和 §9.10.9 `POST /api/student/aidi/chat`（`StudentAidiService` 独立实现，4 个只读工具 `my_leave`/`my_score`/`my_seat`/`my_info`，都不收"查谁"，**没有 action 所以改不了任何东西**）；前端新增 `pages/student/contact/index`、`pages/student/aidi/index`，登录页重写 |
| v0.39 | 2026-11-24 | **通讯录回到"能联系的人"，群聊补上（接口总数不变，仍是 64）**：① 资料页删掉「家庭电话」「培养方向」，`StudentProfileVO` 同步去掉 `familyPhone1` / `trainingDirection`（家长电话不该给学生看）；② **下架 `GET /api/student/classmates`** —— 系统没有好友关系，把花名册摊在通讯录里等于承诺一堆点不动的行；③ 新增 §9.10.9 **班级群** 3 个接口（`GET /api/student/group`、`GET/POST /api/student/group/messages`），新表 `t_class_message`（`database/migrations/13_class_message.sql`），用 `sender_role` 区分两套 id、`sender_name` 存快照，**不复用 `t_container_message`**（学生不是 t_user，两套自增会撞号）；④ 乐迪 `MAX_ROUNDS` 3→2 并加耗时日志，前端加「还在查」渐进提示；⑤ 学生乐迪对话改存本地（`student_aidi_chat_v1`，按学号隔离 / 40 条 / 7 天），切页面不再丢 |
| v0.40 | 2026-11-24 | **学生能自己进另一个班了 + 老师能退出登录（+1，共 65 个）**：新增 §9.9.4b `POST /api/student/join` —— 学生凭邀请码加入（切换）另一个班，返回**换了 classId 的新 token**，邀请码大小写不敏感，`alreadyIn` 时照样给可用 token；**花名册里没有该学号就报 1004 让他找班主任，不做自助报名**（成绩/请假/座位都挂在 classId+studentId 上，学生自造一行会让老师的名单出现重名行、成绩挂错人）；前端新增 `pages/student/join/index`，通讯录加「加入其他班级」入口；老师端个人中心补「退出登录」（和学生端「我的」同一套样式，弹窗二次确认） |
| v0.41 | 2026-11-24 | **学生端登录不再要求先被导入名单（接口总数不变，仍是 65 个）**：`POST /api/auth/student-login` 在花名册里查不到学号时不再报 1004，改为签发**未入班 token**（subject=0、无 classId、带 studentNo/name claim），返回 `needJoin=true`；`name` 由选填改为**必填**（它是新建花名册行唯一的姓名来源），比较时去掉所有空白；`AuthInterceptor` 对无 classId 的学生 token 只放行 `/api/student/join` 与 `/api/student/me`，其余返回 **1009**（新增业务码）；`POST /api/student/join` 支持未入班 token，花名册没有该学号时**新建一行 status=1**，有在读行时校验姓名，离班（status=0）时拒绝且不重复建行；前端登录页姓名必填、`needJoin` 直接 reLaunch 到 `pages/student/join/index?first=1`，入班页新增"第一次进来"的欢迎态，入口页对未入班 token 直接进入班页，首页对 1009 兜底跳转 |
| v0.42 | 2026-11-24 | **学生端群聊接入老师端的群（下架 3 个、新增 5 个，学生端 21 个，全站 67 个）**：v0.39 给学生端另建的 `t_class_message` 导致老师端看不到学生发言、老师端的群邀请码对学生无效 —— 两份数据只是名字撞了。现在统一到 `t_subject_container`：`t_container_message` 加 `sender_role`（1 老师 / 2 学生）与 `sender_name` 快照，`MessageVO` 加 `senderType` 且 `mine` 连角色一起比（防 `t_user.id` 与 `t_student.id` 撞号）；新建 `t_student_container` 记录"用码加入非本班群"；`t_class_message` 里已有的 2 条学生消息迁进对应班级容器后删表；学生端新增 `GET /api/student/group/list`、`GET /api/student/group/{id}`、`GET/POST /api/student/group/{id}/messages`、`POST /api/student/group/join`，**本班的群自动可见（不需要邀请码）**，用码可加入别的群，看不到的群 3002；老师端 `ContainerVO`/`ContainerDetailVO` 增加 `studentCount`（学生不在 `t_container_member` 里），群人数改为"老师 + 学生" |
| v0.43 | 2026-11-25 | **学生端群聊对标老师端（+2 个接口，学生端 23 个，全站 69 个）**：① 学生端群聊页重写成老师端那一套 —— 自定义导航（返回 / 群名 + 人数 / 详情）、5 分钟时间分隔、图片/视频/文件气泡、右下角「空 → ＋ 面板 / 有字 → 发送」，＋ 面板给「照片 / 视频」「文件」两项（接龙、抽签是老师发起的功能，不给入口）；新增 `pages/student/group/detail`（群信息：群名 / 班级 / 人数 / 群主，拆成「老师 N 人」「学生 M 人」两栏）；② 新增 `POST /api/student/group/{id}/upload`（学生不在 `t_container_member` 里，老师那条 `/api/container/{id}/upload` 的 `requireMember` 会把他挡住，所以另开一条，落盘逻辑共用 `ContainerFileServiceImpl`）和 `POST /api/student/group/{id}/leave`（只退用码加入的群，本班的群 1001）；`StudentGroupSendDTO` 补 `type` / `mediaUrl` / `fileName` / `fileSize` / `duration`，和老师端 `MessageSendDTO` 一一对应；③ `StudentGroupVO` 补 `className` / `ownerName` / `teacherCount` / `studentCount` / `canLeave`，`StudentMessageVO` 补 `createdAt`（`time` 只有「月-日 时:分」，做不了时间分隔）；`lastContent` 对媒体消息补 `[图片]/[视频]/[文件]`；④ **修 bug：`GET /api/teacher/container/list` 的 `className` 错位** —— `classNames` 的 key 是 classId，取值用了容器 id，每个群都显示成隔壁班的名字，老师以为"一班的群没了"；⑤ `ClassVO` 新增 `groupTotalCount`（老师 + 学生），老师端通讯录「班级群」改用它，两端人数从此一致（原来老师看 1 人、学生看 59 人） |
| v0.44 | 2026-11-25 | **通知跨班 + 两端首页红点（接口总数不变，仍是 69 个，但有一处破坏性语义变更）**：① **`GET /api/teacher/home/notices` 的 `classId` 不传 = 我的所有班**（原：我的第一个班）。老师必须先切班才能看到别的班的学生请假，这条语义本身就不成立；请假结果、接龙截止、未读私信三条查询全部 `.eq(classId)` → `.in(classIds)`。**调用方若依赖旧语义会突然收到更多数据**，要单班必须显式传 `classId`；② `HomeNoticeVO` 新增 `classId` / `className`（合并后列表要能看出是哪个班），排序固定 `atMillis` 倒序 + `classId` 升序（不加第二比较键时同秒多条每次刷新顺序都会变）；③ 老师端首页 + 通知页改为跨班加载，班级数 > 1 时每条加班级前缀，生日跳转用 `n.classId`；④ 两端首页通知条加红点：本地 `student_notice_seen_at` / `teacher_notice_seen_at` 与 `notices[0].atMillis` 比较（`total` 是历史累计，拿它当红点会永远亮着；通知不落库也就没有服务端已读态，见 §9.49）；⑤ 学生端乐迪修「一直正在查」—— 占位气泡从 `ref([])` 里拆成独立 `thinking` ref，加 45 秒客户端硬超时、8 秒后「还在查，N 秒了」、失败气泡可重试；⑥ **修 bug：老师端乐迪头像变形** —— 顶栏 `.head` 与机器人内部 `.head` 同名，scoped 只隔离组件不隔离同文件，顶栏的 `padding` / `display:flex` 盖到机器人脑袋上；内部件改名 `rd-head` / `rd-eye` / `rd-mouth` |
