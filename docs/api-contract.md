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
| 2001 | 400 | 微信 code 无效或已被使用 | 重新调用 `wx.login` 再试一次 |
| 2002 | 502 | 微信服务调用失败 | 提示"微信服务异常，请稍后再试" |
| 3001 | 403 | 当前用户不是老师，无权访问教师端 | 提示并回入口页 |
| 3002 | 403 | 该数据不属于你的班级 | toast 后返回上一页 |
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
    },
    "quote": "耐心是教育的底色。"
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
| quote | string | 每日金句，先写死常量，后续可换表 |

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
