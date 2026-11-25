# 学科班级容器 · 设计方案 v0.2

> 状态：**已定稿并实现**（教师端）。学生端按需求暂不做。
> 关联文档：[api-contract.md](api-contract.md)、[engineering-notes.md](engineering-notes.md)
> 范围：教师端「学科班级容器」= 学科 + 班级/课程 的成员群组容器（类微信群）

---

## 0. v0.2 定稿（与 v0.1 冲突处，以本节为准）

v0.1 是待确认稿。需求方确认后有三处实质变更，**下面这些内容覆盖后文对应段落**。

### 0.1 三条变更

| # | v0.1 | v0.2 定稿 | 影响 |
|---|---|---|---|
| 1 | 学生端同步改造 | **只做教师端**；`/api/container/*` 写成角色无关的通用接口，学生端上线直接复用 | 少 4 个页面，接口不变 |
| 2 | 管理员（班委）可禁言 + 可移除成员 | **班委不能踢人**；班委的能力是"禁言普通成员 + 收录精华 + 看邀请码" | 移除接口从"群主/班委"收紧为"仅群主" |
| 3 | 移除成员 = 软删，不能再加入 | 移除时**由老师选择是否同时拉黑**：不拉黑→可凭码再进来；拉黑→3003，直到群主解除 | 新增黑名单表 + 页面 + 3 个接口 |

对应关系：**群主 = 老师，管理员（班委）= 班长/学习委员这类协助角色**。
这也是"班委能发精华、不能踢人"的由来 —— 整理资料是协助，清退同学是权力。

### 0.1.1 v0.3 体验返工（只改界面与两个入参，不动表结构）

试用后提的三点，已全部落地：

| # | 原来 | 现在 | 改动位置 |
|---|---|---|---|
| 1 | 叫「我的学科容器」，字面 jargon，第一次用不知道是干什么的 | 全站文案改为 **课程群组 / 群组**；首页入口副标题写清「每门课一个群，学生凭邀请码加入」 | `pages.json` 标题、首页入口卡、7 个页面文案 |
| 2 | 「学科」必填 + 「容器名称」另起一个字段，预设只有高中九科 | **合并成一个「名称」输入框**，自由文本，提示里直接给「高一物理 / 高等数学（上）/ 考研英语冲刺」三个跨学段例子 | `ContainerCreateDTO`（字段名不变）、`create.vue` |
| 3a | 有效期只能选 7 天 / 30 天 / 某天（换算成天数，实际落在 23:59:59） | 自定义时给 **日期 + 时间两个选择器**，创建接口新增 `inviteExpireAt`（精确到秒） | `ContainerCreateDTO.inviteExpireAt`、`create.vue`、`invite.vue` |
| 3b | 密码开关点了没反应，输入框永远不出现 | 修 `e.detail.checked` → `e.detail.value`（uni-app 的 switch 事件字段是 `value`），并加「显示/隐藏」 | `create.vue`、`invite.vue` |
| 4 | 关联班级是右对齐的一行文字，像只读信息，点不到 | 改成 **整行可选块**（92rpx 高、边框、✓ 勾选态） | `create.vue` |

> 3b 是个真 bug，不是"没做"：`<switch>` 的 change 事件在 uni-app 里是 `event.detail.value`，
> 写成 `checked` 拿到的是 `undefined`，于是 `v-if="usePassword"` 永远为假 —— 开关视觉上还会弹回原状。
> 同一处错误在 `create.vue` 和 `invite.vue` 各有一份，都改了。

### 0.2 最终数据模型（7 张表，全部新增，既有 8 张零改动）

以 `database/migrations/04_subject_container.sql`（前 6 张）+ `05_container_message.sql`（第 7 张）为准：

| 表 | 作用 |
|---|---|
| `t_subject_container` | 容器主体（学科、名称、关联班级、群主、邀请码、有效期、密码哈希、预留 session_id） |
| `t_container_member` | 成员（role 1群主/2班委/3成员，status 1在群/2被移除/3已退出，禁言四字段） |
| `t_container_blacklist` | 黑名单（`released_at` 为 NULL 表示仍在黑名单；解除不删行） |
| `t_container_highlight` | 精华（`message_id` 预留给聊天，本期 `content` 为手写要点） |
| `t_container_join_attempt` | 密码失败计数（落库，不用内存） |
| `t_container_op_log` | 敏感操作日志（`action_type` 而非 `action`，避开关键字疑虑） |
| `t_container_message` | 聊天消息（`id` 兼作排序与轮询游标；`type` 1文本 2图片 3视频 4文件；媒体只存 `/files/` 相对路径，文件本体在磁盘上） |

> 第 7 张表的媒体列由 `06_container_message_media.sql` 补上（`media_url` / `file_name` / `file_size` / `duration`）。

### 0.3 最终接口（25 个：教师端管理 19 + 加入链路 3 + 聊天 3）

| 方法 | 路径 | 最低权限 |
|---|---|---|
| GET | `/api/teacher/container/list` | 老师 |
| POST | `/api/teacher/container` | 老师 |
| GET | `/api/teacher/container/{id}` | 成员 |
| PUT | `/api/teacher/container/{id}` | 群主 |
| POST | `/api/teacher/container/{id}/dissolve` | 群主 |
| GET | `/api/teacher/container/{id}/members` | 成员 |
| PUT | `/api/teacher/container/{id}/members/{userId}/role` | 群主 |
| PUT | `/api/teacher/container/{id}/members/{userId}/mute` | 群主 / 班委（仅普通成员） |
| DELETE | `/api/teacher/container/{id}/members/{userId}?blacklist=&reason=` | **仅群主** |
| GET | `/api/teacher/container/{id}/blacklist` | 群主 |
| DELETE | `/api/teacher/container/{id}/blacklist/{userId}` | 群主 |
| GET | `/api/teacher/container/{id}/highlights` | 成员 |
| POST | `/api/teacher/container/{id}/highlights` | 群主 / 班委 |
| DELETE | `/api/teacher/container/{id}/highlights/{highlightId}` | 群主 / 班委（班委仅自己的） |
| GET | `/api/teacher/container/{id}/invite` | 群主 / 班委 |
| PUT | `/api/teacher/container/{id}/invite/expire` | 群主 |
| POST | `/api/teacher/container/{id}/invite/regenerate` | 群主 |
| PUT | `/api/teacher/container/{id}/join-password` | 群主 |
| GET | `/api/teacher/container/{id}/logs` | 群主 |
| GET | `/api/container/preview?code=` | 登录 |
| POST | `/api/container/join` | 登录 |
| POST | `/api/container/{id}/leave` | 成员 |
| GET | `/api/container/{id}/messages?afterId=&beforeId=&size=` | 成员 |
| POST | `/api/container/{id}/messages` | 成员（未禁言） |
| POST | `/api/container/{id}/upload?kind=image\|video\|file` | 成员 |

（教师端管理 19 个，加入链路 3 个，聊天 3 个；总计 25 个。）

### 0.4 最终页面

`pages/teacher/container/` 下 7 个：`index`（我的群组）、`chat`（**群聊，主入口**）、`create`（新建/编辑）、
`detail`（群组详情）、`members`（成员）、`highlight`（精华）、`invite`（邀请码）、`blacklist`（黑名单）；
另有 `pages/container/join`（用邀请码加入，教师端/学生端共用）。
组件只加了 `components/role-tag`。

> **页面动线是「列表 → 聊天 → 详情」，不是「列表 → 详情」。**
> 点进一个群先看到聊天，跟微信一致；群设置（成员/邀请码/精华/黑名单）收在右上角「详情」里。
> 创建成功、加入成功都 `redirectTo` 到聊天页（建完再返回创建页没有意义）。
> 聊天页用 `navigationStyle: custom`：原生导航栏放不下「详情」这个入口。
> **聊天页不显示邀请码** —— 它是有有效期的加入凭证，过期即作废，只在「详情 → 邀请码」里看。

### 0.5 实测结果（副本库，非正式库）

在 `class_assistant_ct`（从正式库 dump 出来的副本）上跑完 **148 条**接口断言，全部通过。覆盖：

- **创建/列表**：邀请码 10 位、字符集、两次不重复；关键词/班级/有效期三种筛选；非法 tab。
- **详情与越权**：非成员 3002、不存在 1004、非数字 id 1001。
- **邀请码**：改有效期（天数/截止时间/永久）、0 天被拒、过期时间在过去被拒、重置后旧码 1008。
- **加入**：码归一化（小写+空格）、未填密码、连错 4 次 1007 且剩余次数递减、第 5 次 1005 锁定、
  锁定期内正确密码也被拒、失败计数确实落库、解锁后成功、重复加入 1006、黑名单 3003、解除后可再生效。
- **班委边界**：班委不能设班委/不能踢人/不能看黑名单/不能看日志/不能解散（均 3002），
  但能禁言、能收录精华、能看邀请码；班委之间不能互相禁言。
- **禁言**：超 30 天拒绝、永久用 `9999-12-31` 哨兵、解除时四个字段被 `set(null)` 清空、被禁言仍能看详情。
- **移除与黑名单**：不拉黑可重新加入、拉黑后 3003、解除是留痕（`released_at`）不是删行、二次拉黑走 upsert 不撞唯一键、
  300 字超长原因被截断而不是 500。
- **精华**：班委可收录、不能取消别人的、群主可取消任意一条、重复取消 1004。
- **解散**：非群主 3002、名称不一致 1001、解散后详情 1004 / 邀请码 1008 / 列表消失 / `status=0` 软删。
- **安全**：密码落库为 `pbkdf2$sha256$210000$...`，库里和启动日志中都搜不到明文；既有 `/api/auth/me`、`/api/teacher/class` 未回归。

聊天接口另跑 **45 条**断言，全部通过：

- **发送**：`createdAt` 非空且格式为 `yyyy-MM-dd HH:mm:ss`（**不是 null** —— 靠 `DEFAULT CURRENT_TIMESTAMP` 时实体字段是 null）、
  `mine=true`、`senderRole` 正确、群昵称优先于真实姓名、内容自动 trim、`type=1`。
- **校验**：空串/全空格 1001、501 字被拦、500 字刚好通过。
- **拉取**：按 `id` 升序、首条是群里最早的（证明「取最新 N 条」没退化成「取最老 N 条」）、自己的 `mine=true` 别人的 `false`。
- **游标**：`afterId` 只返回更新的且仍升序、`beforeId` 只返回更旧的、两者同时传时 `afterId` 优先、
  `size=999` 被钳到 50 不报错、`size=0` 走默认、`afterId=999999` 返回空数组而不是 null。
- **越权**：非成员拉取/发送均 3002；被移除后拉取/发送也 3002（**不能靠翻历史继续看**）。
- **禁言**：禁言后发送 3005、但拉取仍正常（禁言只拦发言）、详情里 `myMuted=true`；解禁后可发、`myMuted=false`。
- **人数**：`memberCount` 只算在群的人（移除后从 3 变 2）。

合计 **193 条**（148 + 45）。

附件与媒体消息另跑 **35 条**断言，全部通过：

- **上传**：图片上传返回 `/files/` 开头的 url、原始文件名、字节数；`kind=image` 传 pdf → 1001；
  `kind=file` 传 exe → 1001（白名单拦可执行文件）；非成员上传 → 3002。
- **静态访问**：**不带 token** 直接 GET 那个 url 返回 200 且内容是完整 PNG
  （证明 `/files/**` 没被 JWT 拦截器挡住 —— 小程序 `<image>` 带不上 Authorization 头）。
- **媒体消息**：type=2 发送成功且 `content` 是空串不是 null；缺 `mediaUrl` → 1001；
  **外链 `https://evil.example.com/a.png` → 1001**；**`/files/../../etc/passwd` → 1001**；
  文件消息缺 `fileName` → 1001；视频消息 `duration` 正常回传；未知 type → 1001。
- **回归**：文本消息 type 仍默认 1、`mediaUrl` 为 null、空文本仍 1001；
  列表按 id 升序、图片带 mediaUrl、文件带 fileName、视频带 duration。
- **禁言**：被禁言发图片消息同样 3005。

合计 **228 条**（148 + 45 + 35）。

### 0.5.1 执行顺序（务必按这个来）

```bash
# 1) 先在副本库上验证（真库零风险）
mysqldump -uroot -p --set-gtid-purged=OFF --default-character-set=utf8mb4 class_assistant > seed.sql
mysql -uroot -p -e "CREATE DATABASE class_assistant_ct DEFAULT CHARACTER SET utf8mb4"
mysql -uroot -p --default-character-set=utf8mb4 class_assistant_ct < seed.sql
mysql -uroot -p --default-character-set=utf8mb4 class_assistant_ct < database/migrations/04_subject_container.sql
mysql -uroot -p --default-character-set=utf8mb4 class_assistant_ct < database/migrations/05_container_message.sql

# 2) 确认无误后，再对真库执行（脚本只 CREATE，不动既有 8 张表）
mysql -uroot -p --default-character-set=utf8mb4 class_assistant < database/migrations/04_subject_container.sql
mysql -uroot -p --default-character-set=utf8mb4 class_assistant < database/migrations/05_container_message.sql
```

> 注意库名必须写在命令行参数里，脚本内**没有** `USE` 语句。

### 0.6 已知限制（写清楚，避免被当成 bug）

1. **聊天没有 WebSocket**，靠 3 秒轮询 `afterId` 增量。项目里没有长连接基础设施，
   引一个 WebSocket 服务端要多一套连接管理 + 心跳 + 重连，收益不抵成本。一次增量只有几百字节。
2. **没有未读数 / 已读回执 / @提醒**：群列表上不会出现红点。要做需要一张 `t_container_read`（每人每群一个 `last_read_id`）。
3. **不能撤回**：`t_container_message.status` 的 `0=已撤回` 只是预留，本期没有接口也没有入口。
4. **附件存在本机磁盘，不是对象存储**：`app.upload.dir` 默认 `./uploads`。
   单机够用，**多实例部署会出问题**（A 传的文件 B 读不到），到时候要么挂共享盘，要么换 OSS。
   上线前记得把 `UPLOAD_DIR` 指到不会被清理、且随备份走的位置。
5. **图片按原图存、视频不转码**：没有缩略图和压缩。手机拍的 4000px 照片直接进群，
   流量和存储都不省；单文件上限 图片 10MB / 视频 30MB / 文件 20MB。
6. **文件接口只放行文档类**：pdf/doc/xls/ppt/txt/md/csv/zip/rar/7z。
   exe/bat/sh/js/html 一律拒 —— 群里发可执行文件是最常见的钓鱼手法。
7. **精华仍是手写内容**：`t_container_highlight.message_id` 恒为 NULL。
   聊天已上线、也能发图，但"长按某条消息收藏成精华"这个交互没做。
8. **不支持转让群主**：群主要么继续带，要么解散。
9. **黑名单解除后不会自动回群**：本人需要重新扫码加入（这是有意的，见 §4.3）。
10. 学生端未做，因此"学生扫码加入"目前只能用第二个老师账号验证（后端链路已完整）。

---

## 1. 现状理解（以代码为准，不以描述为准）

### 1.1 技术栈与分层（已核对源码）

| 层 | 现状 | 证据 |
|---|---|---|
| 后端 | Spring Boot **4.1.1** + Java 21 + MyBatis-Plus **3.5.17**（`mybatis-plus-spring-boot4-starter`） | `pom.xml` |
| 鉴权 | JWT（jjwt 0.12.6），`AuthInterceptor` 只校验 token 有效性并塞 `request.userId`，**不校验角色** | `interceptor/AuthInterceptor.java` |
| 角色校验 | 每个教师端 Service 自己调 `classService.requireTeacher(userId)`（非 1 → 3001） | `service/ClassService.java` |
| 统一返回 | `Result{code,message,data}`，`code=0` 成功；异常由 `GlobalExceptionHandler` 转 `BizException(code,msg)` | `common/*` |
| 前端 | uni-app 3 + Vue 3 `<script setup>`，无 Pinia/Vuex，页面自己 `onShow` 拉数据 | `pages.json`、`pages/teacher/index/index.vue` |
| 请求封装 | `api/request.js` 统一注入 `Bearer token`、按 `body.code` 判断、1002 清 token 回入口页 | `api/request.js` |
| 数据库 | MySQL 8.0 / utf8mb4 / **不建外键**，完整性靠 Service；8 张表 | `database/01_schema.sql` |
| 迁移 | `database/migrations/03_alter_student_fields.sql`（纯增量、可重复执行有报错提示） | 同目录 |
| 分页 | **没装分页插件**，手写 `last("... LIMIT off,size")`，且必须 `selectCount` 在 `last` 之前 | `engineering-notes.md §2` |

### 1.2 与本次需求直接相关的既有模型

| 模型 | 现状 | 对本需求的意义 |
|---|---|---|
| `t_user` | 老师/学生共用，`role` 1=老师 2=学生；**登录时新用户一律写 role=1** | 唯一登录主体。学生账号体系实际不存在 |
| `t_class` | 行政班；`head_teacher_id`；`invite_code VARCHAR(16)` 唯一键；**无有效期、无密码** | 行政班≠学科容器，语义不同 |
| `t_student` | 花名册（姓名/学号/宿舍…），`class_id` + `status` 软删 | **不是账号**，无 openid，不能登录 |
| `t_exam` / `t_score` | 科目是**自由文本**：`t_exam.subjects` 逗号分隔、`t_score.subject VARCHAR(16)` | **没有学科字典表** |
| 聊天/会话 | **不存在**。schema 8 张表无 chat/session，api-contract 无任何 IM 接口 | 本期不做 IM |
| 学生端 | api-contract §3 明确「预留，本轮不做」；入口页学生端按钮只 toast「开发中」 | 学生扫码加入无落地页面 |

### 1.3 三条会影响设计的既有事实

1. **`myClass()` 用 `selectOne`**（`service/ClassService.java:57`）。它被 10 个 ServiceImpl 依赖。
   一旦某个 `head_teacher_id` 对应两行 `t_class`，MyBatis-Plus 抛 `TooManyResultsException` → 首页/学生/成绩/座位/请假/待办/导入全挂。
   **结论：容器必须独立建表，绝不能通过"给老师多插几条 t_class"来实现多班。**
2. **契约铁律①：教师端接口一律不接收 `classId`**，班级只能从 token 反查。
   容器接口天然需要 `{id}`（要管理多个），所以必须引入**独立的容器级权限校验**（`ContainerAuthService`），
   这是本项目第一次出现"带 {id} 的教师端写接口"，越权测试要按 engineering-notes §6 的清单逐条过。
3. **DTO 字段列表 = 权限边界**。创建/修改容器的 DTO 不能包含 `ownerId`、`inviteCode`、`status`、`joinPasswordHash`。

---

## 2. 待确认问题与必要假设

### 2.1 冲突点（doc / 代码 与你的描述不一致，已按 doc + 代码处理）

| # | 冲突 | 处理 |
|---|---|---|
| C1 | 你说"一个老师可以管理多个学科班级"；doc 与代码是"一个老师一个班"（`selectOne`） | 容器独立建模，**不动 `t_class`**；"多个"只体现在容器，行政班仍是一个 |
| C2 | 你说"学生通过邀请码加入"；代码里没有学生账号，登录自动建 role=1 | 见 A1：本期成员身份只认 `t_user.id`，学生端页面不建 |
| C3 | 你说"如现有系统有学科字典，应支持选择"；实际无字典表，科目是 `t_exam.subjects` 自由文本 | 学科名自由文本（1–32 字），前端给"本班历史考试科目 + 常用科目"候选，**不新建字典表** |
| C4 | 你说"若已有聊天模块则复用"；实际没有 | 本期不做 IM，只落 `session_id` 预留字段 + 禁言校验钩子 |
| C5 | 现有 `t_class.invite_code`（6 位、无有效期/密码）与容器邀请码 | 两套独立，不复用字段、不复用生成逻辑；容器码 10 位、有有效期/密码 |

### 2.2 待确认问题 + 我的默认处理（可直接否决）

| # | 问题 | 默认方案 | 影响面 |
|---|---|---|---|
| Q1 | "移除班级"是移除成员 / 移出容器 / 解散？ | 拆成两个动作：**移除成员**（软删成员行，容器还在；可勾选同时拉黑）、**解散容器**（`status=0`，全员不可访问，成员关系留档） | 接口、UI 文案 |
| Q2 | 禁言是全局还是仅聊天？时长规则？ | 仅"发言"（本期无聊天 → 只记录 + 预留 `assertCanSpeak`）；可查看不可发言；时长 10 分钟/1 小时/1 天/7 天/永久/解除 | 无聊天时是"只写不读"的字段 |
| Q3 | 管理员能做什么？能设管理员吗？ | **已按需求方确认改为**：班委可禁言普通成员、收录精华、查看邀请码；**不可踢人**、不可改配置、不可设管理员、不可动其他班委和群主、不可解散 | 权限矩阵 §4.2 |
| Q4 | 邀请码有效期形式？过期能否重新启用？ | 统一存**截止时间** `invite_expire_at`（NULL=永久），前端给 7 天/30 天/永久/自定义；**过期后改截止时间即可重新启用**（码不变） | 一个字段解决三种形态 |
| Q5 | 邀请码字符集？ | `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`（32 字符，**排除 0/O/1/I**，全大写），10 位 ≈ 1.1×10¹⁵ 组合；输入时自动转大写 | 校验、UI |
| Q6 | 密码规则 / 错误次数 / 改后旧密码是否立即失效 | 6–20 位、不含空格；PBKDF2-HMAC-SHA256（21 万轮 + 16 字节随机盐）；**同一用户同一容器连续错 5 次锁 15 分钟**；改密码 = 换哈希 → 旧密码**立即失效** | 新表 `t_container_join_attempt` |
| Q7 | 本期是否含聊天 | **不含**。只做容器/成员/权限/邀请码 | 工作量最大的一项裁剪 |
| Q8 | 与现有班级/学科/教师/学生/会话如何关联 | 容器 `class_id` 可空关联 `t_class`；`owner_id`/`user_id` 关联 `t_user`；`session_id` 预留；`t_student` 不关联（无账号） | — |
| Q9 | 是否改造学生端 | 不改造。`/api/container/*` 写成**角色无关**的通用接口，学生端上线直接复用 | 前端只做教师端 |

### 2.3 假设（集中列出，均可调整）

| # | 假设 | 影响范围 | 后续可调整点 |
|---|---|---|---|
| A1 | **成员身份 = `t_user.id`**。加入接口对任何已登录用户开放，容器内角色（群主/管理员/成员）与全局 `t_user.role`（老师/学生）**完全无关** | 学生端未上线时，"学生加入"链路只能后端验证（用第二个老师账号模拟） | 学生端上线后无需改后端，只需加页面；若要绑花名册，再加 `member.student_id` 可空列 |
| A2 | 容器**独立建表**，不复用 `t_class` 行 | 新增 4 张表；现有 8 张表零改动 | 若将来要"行政班即默认容器"，加一次数据迁移脚本即可 |
| A3 | 群主**不可退出、不可被移除**；本期**不支持转让群主** | 少一个接口 | 后续加 `PUT /{id}/owner` |
| A4 | ~~被移除不能自助加入~~ → **v0.2 改为**：被移除但**没拉黑**可以凭码再进来（成员行从 2 恢复成 1）；**拉黑了**才拒绝（3003），直到群主解除 | 加入链路多一条黑名单分支 + 一张黑名单表 | 若想改成"移除即封禁"，把 `remove()` 里的 blacklist 默认值改成 true 即可 |
| A5 | 解散为软删（`status=0`），容器与成员行保留 | 数据只增不删 | 需要彻底清理时写离线脚本 |
| A6 | 一个老师容器数量**不设上限**（本期） | 无 | 需要时加 `COUNT` 校验 + 1006 |
| A7 | 成员列表不分页（与"班级人数有天然上限"一致），一屏滚动加载 | 复用 `StudentListVO` 风格 | 容器人数上千时再分页 |

---

## 3. 数据模型 / 索引 / 迁移

### 3.1 设计要点

- 容器 = **学科 × 班级/课程** 的成员容器，与行政班是**两个维度**，用 `class_id` 弱关联（可空，支持跨班选修）。
- 成员表用 `UNIQUE(container_id, user_id)`：既是"不能重复加入"的约束，也是**并发加入的兜底**（撞键即视为已加入）。
- 角色与状态分离：`role`(1群主/2管理员/3成员) 与 `status`(1在群/2被移除/3已退出) 互不干扰，避免"管理员被移除后角色还在"的歧义。
- 禁言用单字段 `mute_until`（NULL=未禁言，`9999-12-31 23:59:59`=永久），**不额外加 `is_permanent`**：两个字段会出现"永久但又有到期时间"的矛盾态。
- 不做 `member_count` 冗余计数：容器数量是几十级，列表用一次 `GROUP BY` 即可，避免计数漂移。

### 3.2 DDL（`database/migrations/04_subject_container.sql`）

```sql
-- =============================================================
-- 班级助手 · 增量脚本 04：学科班级容器
-- 执行时机：已跑过 01_schema.sql 的库；只执行一次
-- 只 CREATE，不改任何既有表 —— 现有 8 张表零影响
-- =============================================================
-- 执行方式：mysql -uroot -p class_assistant < 04_subject_container.sql
-- 脚本里**不写 USE**：写死了库名，`mysql 副本库 < 04_xxx.sql` 会静默把表建到真库
-- （踩过，见 engineering-notes §9.6）

-- 1. 学科容器主体
CREATE TABLE `t_subject_container` (
  `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_name`       VARCHAR(32)     NOT NULL                COMMENT '学科名称，必填，如 数学',
  `name`               VARCHAR(64)     NOT NULL                COMMENT '容器显示名，默认「学科名 · 班级名」，可改',
  `class_id`           BIGINT UNSIGNED DEFAULT NULL            COMMENT '关联行政班 t_class.id，可空（跨班选修）',
  `owner_id`           BIGINT UNSIGNED NOT NULL                COMMENT '群主 t_user.id',
  `invite_code`        CHAR(10)        NOT NULL                COMMENT '10 位邀请码，全局唯一，大写不含 0O1I',
  `invite_expire_at`   DATETIME        DEFAULT NULL            COMMENT '邀请码截止时间，NULL=永久有效',
  `join_password_hash` VARCHAR(255)    DEFAULT NULL            COMMENT '加入密码 PBKDF2 哈希，NULL=无密码',
  `join_password_set_at` DATETIME      DEFAULT NULL            COMMENT '密码最近设置时间（仅展示用）',
  `session_id`         BIGINT UNSIGNED DEFAULT NULL            COMMENT '预留：聊天会话 ID，本期不建 IM',
  `status`             TINYINT         NOT NULL DEFAULT 1      COMMENT '1=正常 0=已解散',
  `dissolved_at`       DATETIME        DEFAULT NULL            COMMENT '解散时间',
  `dissolved_by`       BIGINT UNSIGNED DEFAULT NULL            COMMENT '解散操作人',
  `created_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`),
  KEY `idx_owner_status` (`owner_id`, `status`),
  KEY `idx_class` (`class_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学科班级容器';

-- 2. 容器成员（群主/管理员/成员 + 禁言）
CREATE TABLE `t_container_member` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `container_id` BIGINT UNSIGNED NOT NULL                COMMENT '容器',
  `user_id`      BIGINT UNSIGNED NOT NULL                COMMENT '成员 t_user.id',
  `role`         TINYINT         NOT NULL DEFAULT 3      COMMENT '1=群主 2=管理员 3=成员',
  `status`       TINYINT         NOT NULL DEFAULT 1      COMMENT '1=在群 2=被移除 3=已退出',
  `nickname`     VARCHAR(32)     DEFAULT NULL            COMMENT '群内昵称，空则用 t_user.name',
  `join_source`  TINYINT         NOT NULL DEFAULT 1      COMMENT '1=邀请码 2=创建者 3=管理员添加',
  `joined_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `mute_until`   DATETIME        DEFAULT NULL            COMMENT '禁言到期；NULL=未禁言；9999-12-31 23:59:59=永久',
  `mute_reason`  VARCHAR(100)    DEFAULT NULL            COMMENT '禁言原因',
  `mute_by`      BIGINT UNSIGNED DEFAULT NULL            COMMENT '禁言操作人',
  `mute_at`      DATETIME        DEFAULT NULL            COMMENT '禁言操作时间',
  `removed_by`   BIGINT UNSIGNED DEFAULT NULL            COMMENT '移除操作人',
  `removed_at`   DATETIME        DEFAULT NULL            COMMENT '移除时间',
  `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_container_user` (`container_id`, `user_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_container_role` (`container_id`, `role`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '容器成员';

-- 3. 加入密码错误计数（防爆破；不用内存计数：重启丢失、多实例不一致）
CREATE TABLE `t_container_join_attempt` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `container_id` BIGINT UNSIGNED NOT NULL,
  `user_id`      BIGINT UNSIGNED NOT NULL,
  `fail_count`   INT             NOT NULL DEFAULT 0  COMMENT '连续失败次数',
  `locked_until` DATETIME        DEFAULT NULL        COMMENT '锁定到期时间',
  `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_container_user` (`container_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '加入密码尝试记录';

-- 4. 敏感操作审计（设置管理员/禁言/移除/改配置/重置码/改密码/解散）
CREATE TABLE `t_container_op_log` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `container_id`   BIGINT UNSIGNED NOT NULL,
  `operator_id`    BIGINT UNSIGNED NOT NULL                COMMENT '操作人',
  `target_user_id` BIGINT UNSIGNED DEFAULT NULL            COMMENT '被操作人（改配置类为 NULL）',
  `action`         VARCHAR(32)     NOT NULL                COMMENT 'SET_ADMIN/UNSET_ADMIN/MUTE/UNMUTE/REMOVE/UPDATE/DISSOLVE/REGEN_CODE/SET_PASSWORD/SET_EXPIRE',
  `detail`         VARCHAR(255)    DEFAULT NULL            COMMENT '人可读摘要，如「禁言 60 分钟：上课说话」',
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_container_created` (`container_id`, `created_at`),
  KEY `idx_target` (`target_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '容器敏感操作日志';
```

### 3.3 索引与约束的理由

| 约束 | 理由 |
|---|---|
| `uk_invite_code` | 邀请码**全局唯一**由数据库保证，而不是"先查再插"（并发下必撞） |
| `uk_container_user` | 同一人不能重复加入；并发点两次"加入"时第二次撞键 → 返回"你已在该容器中"，不会插两条 |
| `idx_owner_status` | 「我创建的」列表主查询 |
| `idx_user_status` | 「我加入的/我管理的」列表主查询（按 user_id 反查） |
| `idx_container_role` | 成员列表按角色筛选 |
| `idx_container_created` | 操作日志倒序分页 |

### 3.4 邀请码唯一性的并发保护（关键）

```
for (int i = 0; i < 5; i++) {
    String code = InviteCodeGenerator.next();      // SecureRandom，32 字符集，10 位
    try { containerMapper.insert(entity); return; }
    catch (DuplicateKeyException e) {              // 只有 uk_invite_code 可能撞
        if (i == 4) throw new BizException(1006, "邀请码生成失败，请重试");
    }
}
```

- 不先 `selectOne` 判重：判重与插入之间有窗口，靠唯一键 + 重试才是正确做法。
- 创建容器是 **insert container + insert owner member** 两步 → `@Transactional`。
- **重新生成邀请码**同样用"UPDATE + 撞键重试"，且 `WHERE id=? AND owner_id=?`（双条件兜底越权）。

### 3.5 迁移方案

| 步骤 | 操作 | 回滚 |
|---|---|---|
| 1 | 备份：`mysqldump --set-gtid-purged=OFF class_assistant > dump_before_04.sql` | — |
| 2 | 先在副本库 `class_assistant_test` 跑 `04_subject_container.sql` | — |
| 3 | 正式库执行同一脚本（纯 `CREATE TABLE`，不触碰既有表） | `DROP TABLE t_container_op_log, t_container_join_attempt, t_container_member, t_subject_container;` |
| 4 | 可选种子：给 `seed_openid_teacher` 建一个「数学 · 628班」容器，方便联调 | 删除该行即可 |

> 脚本幂等性：重复执行会报 `ERROR 1050 Table already exists`，属正常，不需要 `IF NOT EXISTS`（与 `03_` 的风格一致，避免"以为是新表其实结构没更新"）。

---

## 4. 接口清单与权限矩阵

### 4.1 接口清单

前缀：教师端 `/api/teacher/container`；通用（教师/学生共用）`/api/container`。
**只使用 GET / POST / PUT / DELETE**（`wx.request` 不支持 PATCH，见契约 §0）。

| # | 方法 | 路径 | 作用 | 最低权限 |
|---|---|---|---|---|
| 1 | GET | `/api/teacher/container/list` | 我的容器列表，`tab=created\|managed\|joined`，支持 `keyword`/`classId`/`inviteStatus` | 登录 + 老师 |
| 2 | POST | `/api/teacher/container` | 创建容器（自动生成邀请码、写入群主成员行） | 登录 + 老师 |
| 3 | GET | `/api/teacher/container/{id}` | 容器详情（含 `myRole`、`permissions`） | 成员 |
| 4 | PUT | `/api/teacher/container/{id}` | 改 `name`/`subjectName`/`classId` | 群主 |
| 5 | POST | `/api/teacher/container/{id}/dissolve` | 解散（body 带 `confirmName` 二次确认） | 群主 |
| 6 | GET | `/api/teacher/container/{id}/members` | 成员列表，支持 `role`/`keyword` | 成员 |
| 7 | PUT | `/api/teacher/container/{id}/members/{userId}/role` | 设为管理员 / 取消管理员 | 群主 |
| 8 | PUT | `/api/teacher/container/{id}/members/{userId}/mute` | 禁言 / 解除禁言 | 群主、管理员 |
| 9 | DELETE | `/api/teacher/container/{id}/members/{userId}` | 移除成员 | 群主、管理员 |
| 10 | GET | `/api/teacher/container/{id}/invite` | 查看邀请码 + 有效期 + 是否有密码 | 群主、管理员 |
| 11 | PUT | `/api/teacher/container/{id}/invite/expire` | 改有效期（`expireAt` 或 `days` 或 `null`=永久） | 群主 |
| 12 | POST | `/api/teacher/container/{id}/invite/regenerate` | 重新生成邀请码（旧码立即失效） | 群主 |
| 13 | PUT | `/api/teacher/container/{id}/join-password` | 设置 / 修改 / 关闭加入密码 | 群主 |
| 14 | GET | `/api/teacher/container/{id}/logs` | 操作日志（倒序，`page`/`size`） | 群主 |
| 15 | GET | `/api/container/preview?code=XXXX` | 加入前预览（名称/成员数/是否需要密码/是否已是成员） | 登录 |
| 16 | POST | `/api/container/join` | 用邀请码加入 | 登录 |
| 17 | POST | `/api/container/{id}/leave` | 主动退出（群主不可） | 成员 |

**未做（明确写入文档，避免误解）**：聊天消息收发、群公告、文件、@提醒、转让群主、成员邀请成员。

### 4.2 权限矩阵

| 操作 | 群主(1) | 管理员(2) | 成员(3) | 非成员 |
|---|---|---|---|---|
| 查看容器详情/成员列表 | ✅ | ✅ | ✅ | ❌ 3002 |
| 查看邀请码 | ✅ | ✅ | ❌ 3002 | ❌ |
| 改容器信息（名称/学科/班级） | ✅ | ❌ 3002 | ❌ | ❌ |
| 改邀请码有效期 / 重新生成 | ✅ | ❌ | ❌ | ❌ |
| 设置/关闭加入密码 | ✅ | ❌ | ❌ | ❌ |
| 设置/取消管理员 | ✅ | ❌ | ❌ | ❌ |
| 禁言/解禁 | ✅ | ✅（仅普通成员） | ❌ | ❌ |
| 移除成员 | ✅ | ✅（仅普通成员） | ❌ | ❌ |
| 解散容器 | ✅ | ❌ | ❌ | ❌ |
| 主动退出 | ❌ 1001（需先解散） | ✅ | ✅ | — |
| 发言（本期无聊天） | 校验钩子预留 | 同左 | 被禁言则拒 | — |
| 查看操作日志 | ✅ | ❌ | ❌ | ❌ |

**管理员的三条硬边界**（后端逐条判断，前端只做置灰）：
1. 不能操作 `role=1`（群主）的成员；
2. 不能操作 `role=2`（其他管理员）的成员 —— 只有群主能撤管理员；
3. 不能操作自己。

### 4.3 关键接口契约

#### 4.3.1 创建 `POST /api/teacher/container`

```json
{ "subjectName": "数学", "name": "数学 · 628班", "classId": 1,
  "inviteExpireDays": 30, "joinPassword": "math2026" }
```

| 字段 | 必填 | 校验 |
|---|---|---|
| subjectName | ✅ | `@NotBlank` `@Size(max=32)`，trim 后非空 |
| name | ❌ | `@Size(max=64)`；空则后端生成 `subjectName + " · " + 班级名/「未关联班级」` |
| classId | ❌ | 必须存在且 `head_teacher_id = 当前老师`（否则 3002，防止把容器挂到别人班） |
| inviteExpireDays | ❌ | 1–3650；不传 = 永久（`invite_expire_at = NULL`） |
| joinPassword | ❌ | 6–20 位、无空格；不传 = 无密码 |

响应 `{ "id": 12, "inviteCode": "K7M2QX9B4T" }`。
**错误**：学科名空 1001；班级不属于你 3002；非老师 3001；邀请码生成失败 1006。

> `ownerId` / `inviteCode` / `status` **不在 DTO 里** → 前端夹带也被 Jackson 丢弃（与 `ClassSaveDTO` 同一手法）。

#### 4.3.2 加入 `POST /api/container/join`

```json
{ "code": "K7M2QX9B4T", "password": "math2026" }
```

校验顺序（顺序本身就是安全设计）：

| 步 | 校验 | 失败码 |
|---|---|---|
| 1 | 码格式（10 位、字符集内），先 `trim().toUpperCase()` | 1001 |
| 2 | 按码查容器；查不到 / `status=0` | 1008「邀请码无效或容器已解散」 |
| 3 | `invite_expire_at != NULL && < now()` | 1008「邀请码已过期」 |
| 4 | 已是在群成员（`status=1`） | 1006「你已在该容器中」（幂等，前端当成功处理） |
| 5 | `status=2`（被移除） | 3003「你已被移出该容器，请联系老师」 |
| 6 | `status=3`（曾退出）→ 允许重新加入，走后续校验后 `UPDATE status=1` | — |
| 7 | 有密码：查 `locked_until`，未锁定再校验 PBKDF2；错 → `fail_count++`，达 5 次锁 15 分钟 | 1005（锁定）/ 1007（密码错，message 带剩余次数） |
| 8 | 插入或恢复成员行（`role=3`、`join_source=1`） | 撞 `uk_container_user` → 1006 |

响应：`{ "containerId": 12, "name": "数学 · 628班", "role": 3 }`。
**密码正确后清零 `fail_count`；密码错误不写任何日志到业务表，日志里也不出现密码明文（只记 userId+containerId）。**

#### 4.3.3 禁言 `PUT /{id}/members/{userId}/mute`

```json
{ "muteMinutes": 60, "reason": "上课讲话" }
```

- `muteMinutes > 0` → `mute_until = now + n 分钟`
- `muteMinutes = -1` → 永久（`9999-12-31 23:59:59`）
- `muteMinutes = 0` 或 `null` → 解除禁言（`mute_until/mute_reason/mute_by/mute_at` 全置 NULL）
- `reason` 必填（≤100 字），解除时可空

#### 4.3.4 重新生成邀请码 `POST /{id}/invite/regenerate`

响应 `{ "inviteCode": "B9X4TN2KQW" }`。
**旧码立即失效**（`uk_invite_code` 上换值，旧码查不到任何行）。
前端必须弹二次确认：「已发出的旧邀请码会立刻失效，已加入的成员不受影响」。

#### 4.3.5 改有效期 `PUT /{id}/invite/expire`

三种入参，**优先级 days > expireAt > 永久**：

```json
{ "days": 7 }                              // 从现在起 7 天
{ "expireAt": "2026-12-31 23:59:59" }      // 指定截止时间
{ }                                        // 永久有效（NULL）
```

改有效期**不动邀请码本身** → 过期后改一个未来时间即可"重新启用"，码不变（回答 Q4）。

#### 4.3.6 列表 `GET /list?tab=created`

```json
{ "code": 0, "message": "ok", "data": {
  "total": 3,
  "list": [{
    "id": 12, "name": "数学 · 628班", "subjectName": "数学",
    "className": "628班", "myRole": 1, "memberCount": 47,
    "inviteCode": "K7M2QX9B4T", "inviteExpireAt": "2026-11-28 11:47:21",
    "inviteExpired": false, "hasPassword": true,
    "lastActiveAt": null, "createdAt": "2026-10-29 11:47:21"
  }]
}}
```

- `tab`：`created`=我创建的（`owner_id=me`）、`managed`=我管理的（`role=2`）、`joined`=我加入的（`role=3`）；默认 `created`。
  三角色互斥，一个容器只出现在一个 tab。
- `memberCount` 用一次 `SELECT container_id, COUNT(*) ... WHERE container_id IN (...) AND status=1 GROUP BY container_id`，**不做 N+1，也不加冗余计数列**。
- `inviteStatus` 筛选：`1`=有效（NULL 或 > now）、`2`=已过期、`3`=永久。

#### 4.3.7 详情 `GET /{id}`

在列表字段基础上增加：

```json
{ "permissions": { "canEdit": true, "canManageMember": true, "canManageAdmin": true,
                   "canMute": true, "canDissolve": true, "canViewInvite": true,
                   "canRegenerateCode": true, "canSetPassword": true },
  "ownerName": "李光", "myMuted": false, "myMuteUntil": null, "sessionId": null }
```

`permissions` 只用于**按钮显隐**，每个写接口仍独立校验 —— 前端隐藏不是安全边界。

### 4.4 新增错误码（需同步 `GlobalExceptionHandler.statusOf` 与契约 §1）

| code | HTTP | 含义 | 说明 |
|---|---|---|---|
| 1007 | 400 | 加入密码不正确 | message 带剩余尝试次数 |
| 1008 | 400 | 邀请码无效 / 已过期 / 容器已解散 | 三种情况合并，避免泄露"码存在但过期" |
| 3003 | 403 | 你已被移出该容器，不能再次加入 | 与 3002（无权操作）区分，前端文案不同 |

复用：1001 参数错、1004 容器不存在、1005 尝试过于频繁、1006 数据冲突（已是成员/码生成失败）、3001 非老师、3002 无权操作该容器。

---

## 5. 页面 / 组件 / 路由 / 交互

### 5.1 页面（全部 `navigateTo`，不进 tabBar）

| 路径 | 作用 | 关键交互 |
|---|---|---|
| `pages/teacher/container/index` | 我的学科容器 | 顶部三段 tab（我创建的/我管理的/我加入的）+ 搜索 + 邀请码状态筛选；卡片显示学科、班级、人数、码状态；右下角悬浮「＋ 新建」 |
| `pages/teacher/container/create` | 创建 / 编辑 | 学科名（必填，带候选 chip）、容器名（自动带出可改）、关联班级（picker，可选「不关联」）、有效期（7天/30天/永久/自定义）、加入密码（开关 + 输入） |
| `pages/teacher/container/detail` | 容器详情 | 头部信息卡 + 两个 tab（成员 / 设置）；成员行右侧「…」出操作面板（设管理员/禁言/移除） |
| `pages/teacher/container/members` | 成员管理 | 角色筛选（全部/管理员/成员）、搜索、按角色分组；行内展示禁言状态与到期时间 |
| `pages/teacher/container/invite` | 邀请码与加入设置 | 大字邀请码 + 复制 + 有效期 + 密码开关 + 「重新生成」（二次确认） |
| `pages/container/join` | 加入容器（通用） | 输入 10 位码 → 调 `preview` 显示容器名/人数/是否需要密码 → 输密码 → 加入成功跳详情 |

> `pages/container/join` 放在 `pages/container/` 而不是 `pages/teacher/`：它是**角色无关**的入口，学生端上线后同一页面直接复用（对应 A1）。

### 5.2 组件

| 组件 | 用途 |
|---|---|
| `components/role-tag/role-tag.vue` | 群主/管理员/成员 三色标签，列表与详情共用 |
| `components/member-row/member-row.vue` | 头像 + 名称 + 角色标签 + 禁言状态 + 右侧操作槽 |

只抽这两个：其余（卡片、空态、错误块）复用现有 `load-error` 与页面内样式，不新增抽象。

### 5.3 路由与入口

1. `pages.json` 追加 6 个页面（**不放进 tabBar**，tabBar 已有 5 个，小程序上限 5 个）。
2. 首页 `pages/teacher/index/index.vue`：在「金句」与「快捷操作」之间插入一张入口卡
   「📚 我的学科容器 · 3 个」，点击进 `container/index`。
   **不改动现有 `quicks` 四宫格**（2×2 布局改动会破坏视觉平衡，且回归面变大）。
3. 创建成功后 `redirectTo` 详情页，并 toast 提示邀请码已生成、可复制。

### 5.4 四种页面状态（遵循 engineering-notes §7）

| 状态 | 表现 |
|---|---|
| 加载中 | 骨架卡片（3 张占位，结构与真实卡片一致） |
| 失败且无旧数据 | `load-error` 块 + 重试 |
| 成功但 0 条 | 区分「还没建过容器」（带新建按钮）与「搜不到」（带清空筛选） |
| 失败但有旧数据 | 继续显示旧数据，**不盖错误块**，顶部一条浅色提示 |

### 5.5 交互细则（防手滑）

- 解散容器：必须输入容器名确认（`confirmName` 后端二次校验），文案写明「成员关系保留但全部无法访问」。
- 重新生成邀请码：二次确认 + 结果页大字展示新码，避免"点完就忘"。
- 移除成员：弹窗显示「移除后该成员不能再凭邀请码自助加入」，与"解散"文案严格区分（Q1）。
- 禁言：预设时长 chip（10分钟/1小时/1天/7天/永久/解除），原因必填。
- 密码输入框 `password` 类型，**不写入 storage、不回填**；离开页面即丢弃。
- 邀请码输入框自动转大写、去除空格。

### 5.6 UI 规范（沿用现有视觉）

| 项 | 取值（来自现有页面） |
|---|---|
| 页面底色 | `#F7EFE4` |
| 卡片 | `#FFFFFF`，`border-radius: 28rpx`，`box-shadow: 0 6rpx 20rpx rgba(160,120,80,.05)` |
| 主色 | `#C5342B`，按钮渐变 `linear-gradient(135deg,#E8594F,#C5342B)` |
| 正文/次要/提示 | `#2B2622` / `#9A9186` / `#B4A99B` |
| 标签底色 | 群主 `#FDEDEB`、管理员 `#FBF3DC`、成员 `#F0EDE8` |
| 邀请码 | `font-family: Menlo, Consolas, monospace; letter-spacing: 2rpx` |

---

## 6. 改动文件清单

### 6.1 数据库

| 文件 | 改动 | 原因 |
|---|---|---|
| `database/migrations/04_subject_container.sql` | **新增** | 4 张新表 + 索引；不触碰既有表 |

### 6.2 后端（`backend/class_assistant_server/src/main/java/com/banxing/classassistant/`）

| 文件 | 类型 | 原因 |
|---|---|---|
| `entity/SubjectContainer.java` | 新增 | 映射 `t_subject_container` |
| `entity/ContainerMember.java` | 新增 | 映射 `t_container_member` |
| `entity/ContainerJoinAttempt.java` | 新增 | 映射 `t_container_join_attempt` |
| `entity/ContainerOpLog.java` | 新增 | 映射 `t_container_op_log` |
| `mapper/SubjectContainerMapper.java` | 新增 | `BaseMapper`，另加 `countMembersByContainerIds`（`@Select`，避免 N+1） |
| `mapper/ContainerMemberMapper.java` | 新增 | `BaseMapper` |
| `mapper/ContainerJoinAttemptMapper.java` | 新增 | `BaseMapper` |
| `mapper/ContainerOpLogMapper.java` | 新增 | `BaseMapper` |
| `util/InviteCodeGenerator.java` | 新增 | `SecureRandom` + 32 字符集，排除 0/O/1/I |
| `util/PasswordHasher.java` | 新增 | PBKDF2-HMAC-SHA256，`pbkdf2$sha256$210000$salt$hash`，`MessageDigest.isEqual` 定时比较 |
| `service/ContainerAuthService.java` | 新增 | **容器级权限唯一入口**：`requireMember/requireAdmin/requireOwner`、`permissionsOf`、`assertCanSpeak`（预留） |
| `service/ContainerService.java` + `service/impl/ContainerServiceImpl.java` | 新增 | 业务：创建/列表/详情/改信息/解散/成员/邀请码/密码/日志 |
| `service/ContainerJoinService.java` + `impl/ContainerJoinServiceImpl.java` | 新增 | 加入链路（预览/加入/退出），与教师端管理分离，便于学生端复用 |
| `controller/ContainerController.java` | 新增 | `/api/teacher/container/**` |
| `controller/ContainerJoinController.java` | 新增 | `/api/container/**` |
| `dto/`（约 14 个） | 新增 | `ContainerCreateDTO`/`ContainerUpdateDTO`/`ContainerListVO`/`ContainerVO`/`ContainerDetailVO`/`ContainerPermissionsVO`/`MemberVO`/`MemberListVO`/`MemberRoleDTO`/`MemberMuteDTO`/`InviteVO`/`InviteExpireDTO`/`JoinPasswordDTO`/`JoinDTO`/`JoinPreviewVO`/`DissolveDTO`/`ContainerLogVO` |
| `common/GlobalExceptionHandler.java` | **改 1 处** | `statusOf()` 增加 `1007,1008 -> 400`、`3003 -> 403`；不改其他逻辑 |
| `config/WebConfig.java` | **不改** | `/api/**` 已被拦截，`/api/container/**` 自动需要登录 |

**不改**：`AuthInterceptor`、`ClassService`、`ClassController`、以及学生/成绩/座位/请假/待办/导入的全部代码。

### 6.3 前端（`frontend/uni-app/my-mp/src/`）

| 文件 | 类型 | 原因 |
|---|---|---|
| `api/container.js` | 新增 | 17 个接口封装，风格对齐 `api/class.js`（JSDoc + 说明为什么这么传） |
| `pages/teacher/container/index.vue` | 新增 | 我的学科容器列表 |
| `pages/teacher/container/create.vue` | 新增 | 创建/编辑 |
| `pages/teacher/container/detail.vue` | 新增 | 详情（成员 + 设置） |
| `pages/teacher/container/members.vue` | 新增 | 成员管理 |
| `pages/teacher/container/invite.vue` | 新增 | 邀请码与加入设置 |
| `pages/container/join.vue` | 新增 | 加入容器（通用） |
| `components/role-tag/role-tag.vue` | 新增 | 角色标签 |
| `components/member-row/member-row.vue` | 新增 | 成员行 |
| `pages.json` | **改** | 追加 6 个页面（不动 tabBar） |
| `pages/teacher/index/index.vue` | **改 1 处** | 插入「我的学科容器」入口卡（约 20 行模板 + 1 个跳转函数） |

### 6.4 文档

| 文件 | 改动 |
|---|---|
| `docs/api-contract.md` | 新增 §9.5「学科容器」接口章节 + §1 错误码追加 3 行 + §3 接口全景追加一行 |
| `docs/engineering-notes.md` | 追加 3 条决策：容器独立建表的原因、邀请码靠唯一键+重试、禁言用单字段哨兵值 |

### 6.5 回归风险点（改动会波及的地方）

| 改动 | 风险 | 规避 |
|---|---|---|
| `GlobalExceptionHandler.statusOf` | 所有接口的 HTTP 状态码映射 | 只新增 case，不改既有 case；用 `api-test.http` 抽查 5 个既有接口 |
| `pages.json` | 整包编译；页面路径写错会导致整包白屏 | 只追加数组项；改完必须验证 `dist/dev/mp-weixin/pages.json` 与产物时间戳 |
| 首页 `index.vue` | 首页是 tab 页，最容易被改坏 | 只加一个卡片块，不动 `quicks`/`stats`/计时器逻辑 |
| 新表 DDL | 若误写 `DROP`/`ALTER` 既有表 | 脚本只含 `CREATE TABLE`，review 时逐行确认无 `ALTER`/`DROP` |

---

## 7. 代码交付计划（待确认后按此顺序提交）

1. **P1 数据层**：`04_subject_container.sql` + 4 个 entity + 4 个 mapper + 2 个 util（含单元测试友好的纯函数）。
2. **P2 权限层**：`ContainerAuthService`（含 `permissionsOf` 与 `assertCanSpeak` 预留）+ 错误码映射。
3. **P3 管理接口**：创建/列表/详情/改信息/解散。
4. **P4 成员与邀请码**：成员列表/设管理员/禁言/移除、邀请码查看/改期/重置、密码设置。
5. **P5 加入链路**：preview / join / leave + 失败计数与锁定。
6. **P6 前端**：`api/container.js` → 列表页 → 创建页 → 详情/成员/邀请码 → 加入页 → 首页入口。
7. **P7 文档与回归**：契约与工程笔记更新，跑 §8 验收清单。

---

## 8. 测试用例与验收清单（执行阶段展开为脚本）

### 8.1 功能

| # | 用例 | 预期 |
|---|---|---|
| T1 | 创建容器不填有效期 | 生成 10 位码，`inviteExpireAt = null`，`memberCount = 1`（群主） |
| T2 | 创建容器设 1 天有效期 | 次日加入返回 1008「邀请码已过期」 |
| T3 | 不设密码加入 | 直接成功，`role=3` |
| T4 | 设密码后错误密码加入 | 1007，message 含剩余次数；第 5 次后 1005 锁定 15 分钟 |
| T5 | 正确密码加入 | 成功，`fail_count` 清零 |
| T6 | 同一人重复加入 | 1006「你已在该容器中」，不产生第二行 |
| T7 | 被移除后再用邀请码加入 | 3003 |
| T8 | 重新生成邀请码 | 旧码 1008，新码可加入；已加入成员不受影响 |
| T9 | 过期后改 `expireAt` 为未来 | 同一码恢复可用 |
| T10 | 群主设 A 为管理员 | A 出现在 `managed` tab，`role=2` |
| T11 | 管理员禁言成员 60 分钟 | `mute_until ≈ now+60m`，`mute_by` 为管理员 id，日志有记录 |
| T12 | 管理员尝试禁言另一管理员 / 群主 / 自己 | 3002 |
| T13 | 管理员尝试改容器名 / 重置码 / 解散 | 3002 |
| T14 | 群主移除成员 | 成员 `status=2`，列表默认不显示（筛选"已移除"可见） |
| T15 | 群主解散 | 容器 `status=0`；成员访问详情 3002/1004；列表不再出现 |
| T16 | 群主主动退出 | 1001「群主不能退出，请先解散或转让」 |

### 8.2 越权（按 engineering-notes §6 五条清单逐条过）

| # | 用例 | 预期 |
|---|---|---|
| S1 | 老师 B 调 `GET /{A的容器id}` | 3002 |
| S2 | 老师 B 调 `PUT /{A的容器id}` | 3002 |
| S3 | 老师 B 调 `DELETE /{A的容器id}/members/{x}` | 3002 |
| S4 | 普通成员调 `PUT /{id}/members/{x}/role` | 3002 |
| S5 | 请求体夹带 `ownerId`/`inviteCode`/`status` | 被忽略，库里值不变 |
| S6 | 创建时传别人的 `classId` | 3002 |
| S7 | `t_user.role` 改成 2 后调教师端容器接口 | 3001 |
| S8 | 无 token 调任意容器接口 | 1002 |

### 8.3 安全

| # | 检查 | 方法 |
|---|---|---|
| X1 | 密码不落明文 | `SELECT join_password_hash FROM t_subject_container` → 形如 `pbkdf2$sha256$210000$...` |
| X2 | 日志无密码 | grep 启动日志与 `t_container_op_log.detail`，不得出现明文 |
| X3 | 前端不存密码 | 检查 `uni.getStorageInfoSync()` 无密码键 |
| X4 | 邀请码不可预测 | 连续生成 1000 个，检查无重复、字符分布均匀、无 0/O/1/I |
| X5 | 并发加入 | 同一用户 20 并发 join → 仅 1 行成员记录 |

### 8.4 回归（现有功能）

| # | 用例 | 预期 |
|---|---|---|
| R1 | 首页 overview | 四个数字、周次、班级名正常 |
| R2 | 学生列表/详情/新增/编辑/移出 | 与改动前一致 |
| R3 | 成绩列表/录入/统计 | 一致 |
| R4 | 座位读/存 | 一致 |
| R5 | 请假列表/审批/代请假 | 一致 |
| R6 | 待办增删改查 | 一致 |
| R7 | 班级设置读/改 | 一致；夹带 `inviteCode` 仍被忽略 |
| R8 | 名单导入预览/确认 | 一致 |
| R9 | 错误码抽查 | 1002/1004/3001/3002/5000 的 HTTP 状态与改动前一致 |

---

## 附：实现约定（避免踩已有坑）

1. `LocalDateTime` 出参一律 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，**不写 timezone**；入参同理。
2. 更新用 `LambdaUpdateWrapper.set()` 显式列字段 —— `updateById` 会跳过 null（清空禁言/清空密码必须靠 `set(null)`）。
3. `LambdaQueryWrapper` 的 `OR` 必须包在 `.and(w -> ...)` 里，否则关键词搜索会越过容器边界（最隐蔽的越权）。
4. 时间统一服务器本地时间（Asia/Shanghai），有效期比较用 `LocalDateTime.now()`，不用 UTC。
5. `wx.request` 不用 PATCH；所有"改子资源"用 PUT。
6. 前端解析后端时间字符串先 `.replace(/-/g, '/')`（iOS 坑）。
7. 新增 DTO 的 Jackson 注解 import 沿用现有写法 `com.fasterxml.jackson.annotation.*`（注解模块在 Jackson 3 中未改包名，与工程笔记里 `tools.jackson.*` 的说明不冲突）。
