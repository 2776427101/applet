# 工程笔记与设计决策

这份文档回答的不是「怎么用」，而是**「为什么这么做」**和**「哪些做法看着对、实际会坏」**。

内容从开发过程的日志里提炼，剔除了真实数据和个人信息，只保留接手本项目时需要知道的结论。
如果你要改某个模块，先在这里搜一下关键词，很多坑已经踩过了。

---

## 1. 数据建模决策

这些决定一旦改动，会牵连接口契约和前端展示，**改动前请先读完理由**。

| 决策 | 理由 | 不这么做会怎样 |
|---|---|---|
| 学生删除是**软删**（`status=0`） | 请假、成绩记录还引用这个学生 | 物理删除后历史记录悬空，详情页查不到人 |
| 待办是**物理删除** | 无外部引用 | — |
| 成绩用**窄表**（一行 = 某学生某科） | 科目数量可变，宽表加科目要改表结构 | 加一门课就得写 DDL |
| `t_score` 建 `uk_exam_student_subject` 唯一键 | 支撑 `INSERT ... ON DUPLICATE KEY UPDATE` 幂等批量录入 | 重复提交会插重复行，或者要写「先查再判」的慢逻辑 |
| 成绩接口的 `scores` 返回**对象**（键是科目名）而非数组 | 前端直接 `scores['语文']` | 前端每次都要 `find(s => s.subject === '语文')` |
| **`null` ≠ `0`** | `null` 是缺考，`0` 是考了但 0 分 | 缺考被算进平均分分母，班级均分被拉低；家长以为孩子考了 0 分 |
| 座位**一行 = 一个格子**，空座位也存 `student_id = NULL` | 只存有人的格子时，`rows/cols` 只能靠 `max()` 反推 | 最后一排或最后一列整排没人时，排数凭空少一截。**满座的班测一辈子都测不出来** |
| `t_seat.student_id` **故意不建唯一键** | 由 Service 校验，能报「「张三」被排到了两个座位」 | 数据库唯一键只会说 `Duplicate entry`，不知道是谁 |
| 座位**整表覆盖保存**，不做单点移动 | 前端手里本来就有整张图；单点移动要处理交换的中间态 | 交换座位时中途崩溃会留下两个人叠在一个格子 |
| 年级排名**不能按 `examId` 跨班对齐** | `t_exam` 按班存，每班考试 id 不同 | 按 examId 查永远只查到本班，排名恒为第 1 |
| 排名按「**考试名 + 考试日期**」匹配，并用 `t_class.grade` 限定同年级 | 跨班唯一能对齐的业务键就是这两个 | 把不同年级的班算进同一个排名 |
| 算不出来的排名**返回 `null`，不返回 1** | 只有一个班时排第 1 是假象 | 前端显示「年级第 1」，实际根本没比过 |
| `classSize` 取**成绩表行数**，不是学生表人数 | 转学走的人不该进分母 | 排名算出来偏低 |
| `leave.total` **不含已驳回**（status=2） | 被驳回的没缺课 | 学生档案上显示请了假，但那次申请被驳了 |
| 请假状态机**卡死**：只能 `0→1` 或 `0→2` | 审批是终态操作 | 已通过的再被驳回，历史记录自相矛盾 |
| 分页只给**会无限增长**的列表（请假、成绩、考试） | 一个班 50 人，学生列表分页反而增加操作成本 | — |
| `fullScore` 建考试时就定 | 否则「还没录成绩」时及格线算不出来 | 及格线只能写死 60，150 分制的科目全错 |

---

## 2. Spring Boot 4 + MyBatis-Plus

### 依赖与配置

- MyBatis-Plus 必须用 **`mybatis-plus-spring-boot4-starter`**。
  用 Boot 3 的或普通的 starter，启动直接报 `No qualifying bean`。
- Spring Boot 4 用的是 **Jackson 3**，包名是 `tools.jackson.*`，不是 `com.fasterxml.jackson.*`。
  从网上抄的 `@JsonFormat` 示例通常 import 是错的。
- **分页插件没上**。3.5.9 之后 `PaginationInnerInterceptor` 被拆到 `mybatis-plus-jsqlparser` 独立依赖，
  离线仓库里没有。现在改用手写
  `last("ORDER BY ... LIMIT " + offset + "," + size)`，offset/size 都是钳制过的 `int`，拼不出非数字内容。
  **注意顺序**：必须先 `selectCount(qw)` 再 `qw.last(...)`，反了会生成 `COUNT(*) ... LIMIT` 语法错误。

### 五个真会咬人的点

1. **`updateById` 会跳过 `null` 字段**（实体非空字段策略）。
   接口语义是「全量提交」时，老师清空生日 → 旧值还在。
   **必须用 `LambdaUpdateWrapper.set()`** 显式写 null。这是本项目最容易踩的一个坑。

2. **`@JsonFormat` 进出两个方向都要**。
   `LocalDateTime` 默认输出 ISO 格式 `2026-10-25T18:00:00`，前端按契约传空格格式会抛
   `InvalidFormatException` → 被兜底接住变成 5000「服务器开小差」。
   另外 `LocalDateTime` 上**不要写 `timezone`**，不生效。

3. **`GlobalExceptionHandler` 的 `@ExceptionHandler(Exception.class)` 会吞掉 `NoResourceFoundException`**。
   路径写错会返回 `200 + 5000` 而不是 404，排查时误导性极强。需要单独 handler。
   同理 `MethodArgumentTypeMismatchException`（`/student/abc`）应映射成 1001 ——
   那是调用方 URL 拼错，不是服务器坏了。

4. **`LambdaQueryWrapper` 里的 `OR` 必须包在 `.and(w -> ...)` 里**。
   直接链式 `.eq(classId).and(name like).or(no like)` 会生成
   `class_id=1 AND name LIKE ? OR student_no LIKE ?` —— OR 后面的条件**脱离班级限制**，
   能搜出别的班的学生。**这是最隐蔽的越权漏洞**。

5. **批量 insert 的 null 参数必须带 `jdbcType`**。
   `student_id` 为空时不写 `jdbcType=BIGINT` 会报「无效的列类型」。

### 权限与边界

- **DTO 的字段列表就是前端的权限边界**。绝不用 `@RequestBody Entity` 直接接参数，
  否则前端能传 `teacherId` / `classId` / `done` 改别人的数据（Mass Assignment）。
  班级设置接口的 `ClassSaveDTO` 故意不含 `inviteCode` / `status`，实测夹带 `"inviteCode":"HACKED"` 会被忽略。
- **路径不带 `{id}` 时不可能水平越权**。`GET/PUT /api/teacher/class` 的班 id 只从 token 反查。
- 错误码语义要分清：
  - `1004` = id 不存在
  - `3001` = 不是老师
  - `3002` = 存在但不归你的班
  混用会让前端以为「这条被删了」从而从列表移除该行。

### 事务

`@Transactional` 用来保证一致性，**不用来兜正确性**。
座位保存是「先校验全部、再 `DELETE` + 批量 `INSERT`」，
不是「先删了再看校验过不过」—— 别把正确性押在回滚上。

名单导入的 commit **故意不加 `@Transactional`**：单行失败不该回滚整批，
失败行由返回结果告诉用户哪几行错了。

---

## 3. 微信小程序 / uni-app

### 只能在真机上发现的

- **`wx.request` 的 `method` 白名单里没有 `PATCH`**（只有 OPTIONS/GET/HEAD/POST/PUT/DELETE/TRACE/CONNECT）。
  开发者工具里可能「看起来能跑」，换安卓真机或换基础库会**静默降级成 GET**，body 丢失且不报错。
  本项目所有 `PATCH` 已全改 `PUT`。

- **iOS 上 `new Date('2026-10-25 18:00:00')` 是 `Invalid Date`**。
  必须 `.replace(/-/g, '/')` 再构造。凡是解析后端返回的时间字符串，都要过这一步。

- **`uni.uploadFile` 在 H5 传 `File` 对象时 `success` 回调不触发**。
  请求发出去了、后端也 200 了，但回调不来，页面永远停在「正在解析」。
  解法：H5 走 `fetch` + `FormData`，小程序才走 `uni.uploadFile`（条件编译）。

### 布局与交互

- **`placeholder-class` 在 `scoped` 样式下匹配不到**小程序内部的 placeholder 元素。
  用内联 `placeholder-style`。
- **`position: fixed; bottom: 0` 会被 H5 的 tabBar 盖住**（小程序 tabBar 是原生的，H5 是 DOM）。
  弹层和底部工具条都要 `bottom: var(--window-bottom, 0px)`。
- **表头 `position: sticky` 不要放进 `scroll-view`**，小程序里有兼容问题。
- **tabBar 页面之间跳转必须 `switchTab`**，`navigateTo` 会静默失败。
  页面多了以后建议收口成一个 `go(path)` 函数，用数组判断目标是不是 tab 页，
  否则每加一个非 tab 页都要改 N 处点击事件。
- 小程序限制：**单个分包 / 主包 ≤ 2MB，所有分包总和 ≤ 30MB，tabBar 页面必须在主包内**。

### 数据录入

- **学号/编号一律用字符串**，不要用 number —— 前导零会被吃掉。
- **空值传 `null`，不传 `""`**。`""` 和 `"   "` 要在后端归一化成 `NULL`，
  否则两个空学号会互相撞唯一键。
- **`<picker mode="date">` 选不出「空」**，旁边要另给一个清除按钮。
- **请假时长不能按 `ms/86400000` 算**。10-27 08:00 → 10-29 18:00 会得出「2.4 天」，
  没人看得懂。按日历日算 `(d1-d0)/86400000 + 1 = 3 天`，同一天才退回小时数。

### 成绩录入的三条输入规则

这套规则是为了区分「没录」和「录了 0 分」：

| 用户操作 | 提交值 | 含义 |
|---|---|---|
| 格子留空 | `null` | 缺考 |
| 填 `0` | `0` | 考了，0 分 |
| 整行没碰过 | **不提交** | 只补录 5 个人时，不会把另外 42 人写成缺考 |
| 碰过但又清空 | 提交 `null` | 能真正删掉某个学生的分数 |

「碰过」用 `touched` 标记单独记，不能靠「值是否为空」判断。

---

## 4. 最容易浪费时间的坑：`dist/dev` 和 `dist/build` 是两个目录

**这个坑在开发中踩了三次**，每次都表现为「我改了代码，小程序里没变」。

| 命令 | 输出目录 | 用途 |
|---|---|---|
| `npm run dev:mp-weixin` | `dist/dev/mp-weixin` | 日常开发（watch 模式） |
| `npm run build:mp-weixin` | `dist/build/mp-weixin` | 打包上传 |
| `npm run dev:h5` | `dist/dev/h5` | 浏览器里调布局 |

微信开发者工具**只读你导入时选的那个目录**。如果工具导入的是 `dev/`，
你跑 `build` 改的东西它一辈子看不到。

**DevTools 的「编译」按钮只重读 `dist`，不会编译 `src`。**

### 自查纪律

改完前端后，必须确认目标目录里有对应产物**且时间戳是刚才**：

```bash
ls -la dist/dev/mp-weixin/pages/teacher/<页面>/
grep -oE "关键词1|关键词2" dist/dev/mp-weixin/pages/teacher/<页面>/index.js
```

只看 `dist/build/` 不算验证。

### 4.1 报「未找到 xxx.wxml」＝ 产物被写了一半

DevTools 弹：

> Error: app.json: 未找到 ["pages"][14] 对应的 pages/teacher/container/index.wxml 文件

**不要先去怀疑页面路径写错了**。先 `ls` 那个目录：

```bash
ls dist/dev/mp-weixin/pages/teacher/container/
# 只有 *.json，没有 *.js / *.wxml / *.wxss  ← 就是这个原因
```

uni-app 编译一个页面是分步写的：**先**把 `app.json` 的 `pages` 数组和页面 `.json` 占位写进去，
**再**输出 `.wxml/.js/.wxss`。如果 `dev` 进程在这两步之间被 Ctrl+C、被关窗口、或者两个 watcher
同时写同一个 `dist`，就会留下「`app.json` 里登记了、模板文件不存在」的半成品。

判断依据是**文件类型不全**，而不是文件是否存在：

```bash
# 一条命令查全部页面是否齐全（app.json 登记 27 个，缺 0 个才算好）
for p in $(grep -o '"pages/[^"]*"' dist/dev/mp-weixin/app.json | tr -d '"'); do
  [ -f "dist/dev/mp-weixin/$p.wxml" ] || echo "缺失: $p.wxml"
done
```

修复：重新完整跑一次 `npm run dev:mp-weixin`（等到日志打出 `DONE` 再动 DevTools）。
**别同时开两个 watcher** —— 它们会互相覆盖，正是把产物写成半截的常见原因。

### 视觉验证

H5 无头截图时用 **`--window-size=750,xxx`**（750 视口下 1rpx = 1px，比例才对）。
不要用 375 视口加 `force-device-scale-factor`，rpx 换算会错，页面被拉宽。

调布局优先看编译产物里的 `wxml`/`css`，截图只用来确认「元素在不在」，
不用来判断像素级对齐（rpx 基准随视口变）。

---

## 5. 环境与启动

- **端口被宿主进程占用**：环境里可能存在 `SERVER_PORT` / `SERVER__PORT`，
  Spring 的宽松绑定会把双下划线当 `.`，于是 `server.port` 被静默覆盖成别的值。
  **一律显式传端口**，命令行参数优先级 > 环境变量 > yml：

  ```bash
  DB_PASSWORD=xxx mvn -o spring-boot:run \
    '-Dspring-boot.run.arguments=--server.port=8080'
  ```

  `-Dspring-boot.run.arguments` 必须用**单引号**包住，否则参数里的 `&` 会被 shell 吃掉。

- **Maven 本地仓库可能是自定义路径**（在 Maven 安装目录的 `conf/settings.xml` 里配的），
  不在 `~/.m2/repository`。IDEA 若用内置 Maven 或 wrapper 会指向别的仓库 → 重复下载 + 满屏红线。

- **`mvn -o` 离线编译可以，`spring-boot:run` 不行**：运行期依赖（`spring-boot-buildpack-platform`、
  `httpclient5`、`micrometer-observation` 等）不在本地仓库时启动会失败，需要先联网跑一次。

- **Windows PowerShell 5.1 传 JSON 参数会吃掉双引号**：
  `python -c "..." -d '{"code":""}'` 到达程序变成 `{code:}`。
  要用 `Invoke-RestMethod`（.NET 对象传参，不经过 argv）或改用 Git Bash。

---

## 6. 测试方法

### 永远对着副本库跑

写数据的测试脚本**绝不能对着正式库跑**：

```bash
mysqldump --set-gtid-purged=OFF class_assistant > dump.sql   # 少这个参数导入会报 GTID_PURGED cannot be changed
mysql -e "CREATE DATABASE class_assistant_test"
mysql class_assistant_test < dump.sql
# 后端指向副本，测完 DROP DATABASE
```

测完核对正式库的行数没变。

### 四条经验

1. **测试报错先怀疑测试**。曾经因为脚本的位置参数写错（token 被当成 body），
   3 条「越权应该 3002」全变成 200，看起来像后端有越权漏洞。
   同一批里另一条相关用例是通的 —— **两个结果自相矛盾时，说明是脚本问题**。

2. **断言「应该失败」之前，先确认失败的条件真的成立了**。
   「改成别人的学号」那条用的学号根本不存在，返回 200 是对的。

3. **写库路径和读库路径要分别验**。曾经审批备注写得进库，
   但列表接口的 VO 漏了这个字段，前端永远显示空白 —— 只查数据库有没有值是不够的。

4. **数人头前先切出数组**。响应里 `seats` 和 `unseated` 都含 `studentId`，
   在整个响应里 grep `"studentId":` 会把同一批人算两遍（48 格 + 45 未排座 = 93）。

### 越权测试清单

每个带 `{id}` 的接口都要造「另一个老师 + 另一个班」，验证这五处都返回 3002：

- 列表看不到别班数据
- 详情 / 编辑 / 删除拿不到别班的 id
- 跨班代请假被拒
- 角色被改成非老师 → 全部 3001
- 夹带契约外字段（`teacherId`、`classId`、`inviteCode`）被忽略

---

## 7. 前端状态管理约定

页面有**四种**状态，不能只做两种：

| 状态 | 表现 |
|---|---|
| 加载中 | 骨架屏（结构和真实列表一致，数据回来不跳） |
| 失败**且无**旧数据 | 错误块（图标 + 原因 + 重试按钮） |
| 成功但 0 条 | 空状态（区分「搜不到」和「本来就没有」） |
| 失败**但有**旧数据 | 继续显示旧数据，**不盖错误块** |

最后一条最容易漏。

**错误状态下不要出现编造的数字**。学生页断网时顶部还写「0 人在读」，
和下面的「加载失败」自相矛盾 —— 整块应该藏掉，或显示 `—`。

**座位页断网时要藏掉整条工具条**：随机排座会按 0 人排、保存会把空表写回去，
这是会丢数据的操作。

---

## 8. 开发顺序上的教训

- **契约先写、按契约实现**。真实项目里这是后端负责人/架构师的活，前端照着实现。
  一次性把 29 个模块的字段全写完是浪费时间 —— 表还没建，写出来的字段是猜的，
  做到第 8 个模块全要推翻。**只展开表已经建好的模块**。
- **mock 开关必须在收尾时主动关掉**。交付 mock 接口后不检查 `USE_MOCK`，
  用户会以为假数据是真的。切换后可以 grep 编译产物确认 mock 分支被 dead-code 消除。
- **技术债按「三次法则」还**：同一个私有方法抄到第 3 份时抽公共 Service，
  但**先测通再重构**，别和功能混着改。

---

## 9. 学科班级容器（2026-10-29）

这一节记的是做「学科容器」时踩到 / 差点踩到的坑，都是**测出来才发现**的。

### 9.1 事务会把"失败计数"一起回滚

加入接口原来写了 `@Transactional`，密码错误时流程是「失败次数 +1 → 抛 1007」。
抛异常让整个事务回滚，**刚写的失败次数也一起没了** —— 数据库里永远是 0 次。

表现极具迷惑性：连错 5 次，每次都提示「还可尝试 4 次」，锁定机制看起来写了、实际完全失效。
只有真的连点 5 次才会发现。

**结论**：计数类写入不能和"会抛异常的校验"放在同一个事务里。
现在 `ContainerJoinServiceImpl.join()` **故意不加** `@Transactional` —— 它只有两条单语句写，
各自原子，不需要事务；加了反而把计数吃掉。

### 9.2 DELETE 不要带 body

`DELETE /members/{id}` 需要传「是否同时拉黑」。原本想用 body，
但 DELETE 带 body 在各端行为不一致：H5 的 XHR 会发，小程序的 `wx.request` 有时会拼进 URL，
被吞掉之后 `blacklist` 静默变成 false —— **老师以为拉黑了，人还能进来**，这是最危险的失败方式。

**结论**：删除类接口的参数一律走 query（`?blacklist=true&reason=...`）。
接口签名里的 `@RequestBody(required=false)` 看起来更"优雅"，但它不可靠。

### 9.3 唯一键 + 撞键重试，不要"先查再插"

邀请码要全局唯一。`selectOne` 判重再 `insert` 在并发下有窗口，第二个请求会 500。
正确写法是：循环 `insert`，捕获 `DuplicateKeyException` 重试（最多 5 次）。
32^10 的组合下重试几乎不会发生，但**发生一次就值回票价**。

同理，成员表的 `uk_container_user` 不是"防重复"的装饰，是并发加入的唯一防线：
两个请求同时点「加入」，第二个撞键后返回「你已在该容器中」，而不是插出两行。

### 9.4 禁言只用一个到期时间字段

`mute_until`：NULL = 未禁言，`9999-12-31 23:59:59` = 永久。
不另加 `is_permanent`：两个字段一定会出现「永久禁言但又有到期时间」的矛盾数据，
到时候没人说得清以哪个为准。

解除禁言时四个字段（`mute_until` / `mute_reason` / `mute_by` / `mute_at`）要一起 `set(null)`，
**`updateById` 清不掉 null**（§2 第 1 条），页面会显示"已解除"而库里还留着到期时间。

### 9.5 容器不能塞进 `t_class`

`ClassService.myClass()` 用 `selectOne(head_teacher_id)` 反查班级，被 10 个 ServiceImpl 依赖。
一旦某个老师对应两行 `t_class`，MyBatis-Plus 抛 `TooManyResultsException`，
首页/学生/成绩/座位/请假/待办/导入**全部挂掉**。

**结论**：容器必须独立建表（`t_subject_container`），行政班维持"一个老师一个班"不变。
如果哪天要放开，先改 `myClass()` 的返回类型和 10 个调用方，那是另一个工程。

### 9.6 迁移脚本里不要写 `USE <库名>`

`04_subject_container.sql` 最初第一行是 `USE class_assistant;`，看起来只是"指明在哪个库执行"，
实际是个静默陷阱：`mysql class_assistant_ct < 04_xxx.sql` 里的 `class_assistant_ct` **会被 `USE` 覆盖**，
6 张表全部建到正式库里，而副本库里一张都没有 —— 命令行不报错，`SHOW TABLES` 还"看起来成功了"
（脚本末尾那条核对 `SELECT` 也写死了 `TABLE_SCHEMA = 'class_assistant'`，所以它同样在说谎）。

发现方式：服务连副本库启动后，建容器报 `Table 'class_assistant_ct.t_subject_container' doesn't exist`，
但 `SHOW TABLES` 里明明有 —— 用 `information_schema.tables` 按 `table_schema` 分组查才看出来。

**两条规矩**：
1. 增量脚本里**不写 `USE`**，库名一律由命令行给：`mysql -uroot -p class_assistant < 04_xxx.sql`；
2. 脚本末尾的核对查询用 `DATABASE()` 而不是写死库名，这样在副本库上跑也能给出正确结论。

顺带一条：`@Size` 之类的注解只有配上 `@Valid` 才生效。`PUT /join-password` 一开始漏了 `@Valid`，
DTO 上写着"6-20 位"，实际上设一个 1 位密码也能存进去 —— 校验注解是**声明**，不是**行为**。

### 9.7 `<switch>` 的 change 事件是 `e.detail.value`，不是 `checked`

```js
// 错：拿到 undefined，开关看起来点了没反应
usePassword.value = e.detail.checked

// 对
usePassword.value = !!e.detail.value
```

`checked` 是**属性名**（`:checked="..."`），`value` 才是**事件字段**。
写成 `checked` 不会报错、不会警告，表现是：

- 开关点一下又弹回原样（因为 `v-if` 依赖的变量是 `undefined`）；
- 依赖它的输入框永远不出现 —— 用户会以为"这个功能没做"。

这次就是老师反馈「密码开关打开后没有输入密码的地方」，代码里输入框明明写好了，
只是 `v-if="usePassword"` 永远为假。**这类"点了没反应"的 bug 先查事件字段名。**

同源问题：`<picker mode="date">` 的 `e.detail.value` 是 `"2026-12-31"` 字符串，
`<picker mode="time">` 是 `"20:30"`（只有时:分，没有秒），
拼成后端要的 `yyyy-MM-dd HH:mm:ss` 时要自己补 `:00`。

### 9.8 `DEFAULT CURRENT_TIMESTAMP` 填的是列，不是实体字段

```java
// 表里 created_at 有 DEFAULT CURRENT_TIMESTAMP，于是不写它也能插进去
ContainerMessage m = new ContainerMessage();
m.setContent(content);
messageMapper.insert(m);

// 但 m.getCreatedAt() 仍然是 null —— MyBatis 只把参数写下去，不会把库里的默认值读回来。
// 直接拿 m 组装响应，前端收到的就是 createdAt: null，气泡上的时间不显示。
m.setCreatedAt(LocalDateTime.now());   // 显式写，别指望 DEFAULT
```

要么显式赋值，要么插完再 `selectById` 回读。前者便宜得多。
**判断信号**：接口返回 200、数据也进库了，但某个时间字段是 `null`。

### 9.9 `scroll-into-view` 赋同一个值，第二次不触发

```js
// 错：一直是 'anchor-b'，第一条之后的每条新消息都不会滚动
anchor.value = 'anchor-b'

// 对：两个锚点交替，值一变就一定会重新滚
anchorFlip = !anchorFlip
anchor.value = anchorFlip ? 'anchor-a' : 'anchor-b'
```

`scroll-into-view` 是**属性变化才触发**，不是"每次赋值都执行"。
聊天页在消息列表底部放两个空 `view`（`anchor-a` / `anchor-b`）就是为了这个。

### 9.10 聊天页两个必踩的布局坑

```scss
.msgs {
  flex: 1;
  min-height: 0;   /* 不写这行，消息一多就把输入框顶出屏幕 */
}
```

`flex` 子项的 `min-height` 默认是 `auto`（内容高度），不是 `0`。
只写 `flex: 1` 的话它会被内容撑大，`100vh` 的父容器装不下，底部输入栏直接被挤到可视区外。
表现是"输入框不见了"，但 DOM 里它还在。

第二个坑：**轮询定时器必须在 `onHide` / `onUnload` 里 `clearInterval`**。
小程序页面切到后台不销毁，不清理的话会一直在后台每 3 秒打一次接口。
`onShow` 里重新 `startPoll()`（内部先 `stopPoll()`），避免重复叠加定时器。

### 9.11 看到 `1004 接口不存在`，先查路径，别查权限

`GlobalExceptionHandler` 把 `NoResourceFoundException`（Spring 6.2+ 对未匹配路由抛的）
也映射成了 1004。于是"路径写错"和"容器不存在"返回同一个码，只能靠 message 区分：

| message | 真实原因 |
|---|---|
| `接口不存在` | **URL 拼错了**（少一段、多一段、方法不对） |
| `学科容器不存在或已解散` | 路由是对的，id 不对 |

这次自测聊天时，脚本把禁言接口写成了 `/api/container/1/members/3/mute`
（漏了 `/teacher`），报 `1004 接口不存在`，我一度以为是权限问题。
**这类错误改测试脚本，不要改业务代码。**

### 9.12 「点了没反应」有时不是事件丢了，是页面上真的没东西可点

老师反馈聊天页"哪个都点不了没反应"。逐层排查的结果：

| 查什么 | 结果 |
|---|---|
| 编译后的 `bindtap` | 都在（返回/详情/重试/发送/空状态） |
| `hover-class` 的样式 | 在（`.nav-press.data-v-xxx` 确实编译进 wxss） |
| 列表卡片跳转 | 正常（`bindtap` + `goChat` 都在） |
| 后端接口 | 正常（`/api/container/1/messages` 返回 `{"code":0,"data":[]}`） |

**根因是体验问题，不是 bug**：那个群还没有任何消息，整页只有两行文字 + 一张邀请码卡片，
其余全是空白。点空白 → 无反应；点「发送」但输入框为空 → `onSend` 提前 return，也无反应。
**唯一的可点区域只有邀请码卡片**，所以主观感受就是"整个页面点不动"。

修法不是加事件，是**给空白区域一个动作**：

- 空状态 `min-height: 100%` 撑满消息区，整块 `@click` 聚焦输入框；
- 加一个显眼的「点这里开始输入」按钮，让"可点"这件事看得见；
- 发送按钮加 `hover-class`，按下有反馈；
- 输入框外壳也可点（`@click` → `focusInput`）。

顺带两个真 bug：

1. **`focus` 只在值变化时生效**。`focusInput()` 里先置 `false` 再 `nextTick` 置 `true`，
   否则连续点两次第二次不响应 —— 又是一次"点了没反应"。
2. **没有 id 时页面会永远停在「正在加载…」**：`init()` 只在 `onShow` 里调，而它依赖 `id`，
   `id=0` 时直接跳过，`loading` 永远是 `true`，页面上一个能点的东西都没有。
   现在 `onLoad` 里判空并直接置错误态，首次加载也挪到 `onLoad`（它一定早于 `onShow`）。

> 教训：**空页面必须留一个明确的动作**。数据为空的页面天然"点不动"，
> 用户不会认为"这里没内容"，只会认为"这功能坏了"。

### 9.13 `scroll-view` 在 flex 容器里要给 `height: 0`

```scss
.msgs {
  flex: 1;
  height: 0;      /* 关键 */
  min-height: 0;
}
```

`scroll-view` 是组件不是普通 `view`。在 flex 列布局里只写 `flex: 1; min-height: 0`
在部分基础库上会按内容高度撑开，把下面的输入栏顶出可视区。
`height: 0` 让 flex-basis 归零，再由 `flex-grow` 撑满剩余空间，这是最稳的写法。

配合 `.page { height: 100vh; overflow: hidden }` —— 页面自己管滚动，页面级不要再滚一层。

### 9.14 附件不能挂在 `/api` 下：原生组件带不上 token

小程序里 `<image src>` / `<video src>` 由**原生组件**发请求，请求头里**没有 `Authorization`**。
把附件放在 `/api/**` 下，`AuthInterceptor` 会把它拦成 401，表现是：

> 消息发出去了、接口也返回 200，但图片位置一片空白，控制台报 401。

所以附件走独立前缀 `/files/**`（不匹配 `/api/**`，天然不被拦截器管），
再用 `addResourceHandlers` 映射到磁盘目录。安全性靠"路径里是 UUID、猜不到"，
而不是靠 token —— 和公开图床是一个取舍。

```java
Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
registry.addResourceHandler("/files/**").addResourceLocations(dir.toUri().toString());
```

`toUri()` 不能省：手拼 `"file:" + 相对目录` 在 Windows 上会得到带反斜杠的串，
Spring 解析不出来，图片全部 404。

### 9.15 Java 注释里也不能出现「反斜杠 + u」

写 WebConfig 时在 Javadoc 里举例写了 `.\uploads`，编译直接报：

```
非法的 Unicode 逃逸
```

原因：Java 的 Unicode 转义在**词法分析之前**处理，注释里的 `\u` 一样会被扫描。
只要出现反斜杠紧跟 `u`，哪怕在注释里也是编译错误。
**注释里描述 Windows 路径时，别写出那个组合**，改用文字说明。

### 9.16 `<video>` 是原生组件，会盖住同层的一切

气泡里直接嵌 `<video>`，工具面板和输入栏都可能被它挡住点不到（原生组件层级高于普通元素）。
所以视频消息渲染成"深色卡片 + 播放三角"，点击走 `uni.previewMedia` 全屏播放：

- 躲开原生组件的层级问题
- 体验更接近微信（微信也是点开全屏）
- 列表里几十条视频不会同时初始化几十个解码器

### 9.17 空状态下的按钮要「换功能」，不能变死按钮

聊天页右下角原来一直是「发送」，输入框空着时点它什么都不发生 —— 用户的判断是"按钮坏了"。
现在按输入框有没有字切换：

| 状态 | 按钮 | 行为 |
|---|---|---|
| 空 | ＋ | 展开工具面板，图标转 45° 变叉号 |
| 有字 | 发送 | 发文本 |

**空状态下不该留一个点了没反应的按钮。** 要么禁用（视觉上明显灰掉），
要么换成一个当下有意义的动作。

### 9.18 上传和发消息要分两步

`POST /upload` 返回 url，再 `POST /messages` 带 url 发消息。合成一步的坏处：
用户选了图又取消发送，文件已经落盘却没有消息引用它，只能写个"取消时删文件"的补偿逻辑。
分两步之后，"没发出去"就等于没发，不需要回收。

### 9.19 `selectOne` 是单班级假设的埋雷点

多班级改造前，`ClassService.myClass()` 是这么写的：

```java
return classMapper.selectOne(
        new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getHeadTeacherId, userId));
```

单班时一切正常，老师带第二个班的那天，**所有教师端接口会一起挂**：

```
org.apache.ibatis.exceptions.TooManyResultsException:
  Expected one result (or null) to be returned by selectOne(), but found: 2
```

这个报错的字面意思和业务毫无关系，日志里也看不出是哪个班的问题。

改成"列表 + 按 id 解析"：

```java
public List<ClassInfo> myClasses(Long userId) { ... selectList ... }

public ClassInfo resolveClass(Long userId, Long classId) {
    if (classId == null) return myClass(userId);   // 兼容：第一个班
    ClassInfo c = classMapper.selectById(classId);
    if (c == null || c.getStatus() != 1) throw new BizException(1004, "班级不存在或已归档");
    if (!userId.equals(c.getHeadTeacherId())) throw new BizException(3002, "这不是你带的班级");
    return c;
}
```

**教训：凡是 `selectOne`，都要先问一句"将来会不会变成多行"。**
会的话现在就用 `selectList` + 取第一条，改造成本是一行；等到业务铺开再改，
就是十几个 ServiceImpl 一起改。

### 9.20 按 id 定位的接口不要加 classId

给 `student/{id}`、`score?examId=`、`leave/{id}/audit` 这类接口加 `classId` 是**有害**的：

- 调用方要同时保证 id 和 classId 匹配，多了一个出错维度
- 两个参数不一致时，以哪个为准？又是一个要写文档的边界情况

正确做法是**让数据自己说明归属**：

```java
// 学生：看他所在班的班主任是不是我
classService.ownsStudent(userId, s);

// 考试：拿 exam.class_id 去校验
classService.resolveClass(userId, exam.getClassId());
```

这样"选错班写错成绩"在物理上不可能发生 —— 前端就算传了别的班的 examId，
后端也是 3002，不会写进去。

### 9.21 全局状态不一定要 Pinia

`store/class.js` 只有三样东西：班级列表、当前班级 id、当前班级对象。
用一个 Vue `ref` 就够了 —— 模块只求值一次，天然是单例：

```js
export const currentClassId = ref(Number(uni.getStorageSync('currentClassId')) || 0)
export function setCurrentClass(id) {
  currentClassId.value = Number(id) || 0
  uni.setStorageSync('currentClassId', currentClassId.value)
}
```

要持久化是因为小程序会被系统回收：老师去回个微信再回来，进程可能已经重启，
不存 storage 就会跳回第一个班，看起来像"切换没生效"。

**切换班级只改状态，不发请求**：首页要重拉 overview、成绩要重拉考试列表，
每个页面该刷什么不一样，统一在 store 里刷反而要写一堆特判。

### 9.22 `showActionSheet` 最多 6 项

班级选择器一开始想用 `uni.showActionSheet`，两行代码就能用。
但微信端 `itemList` **上限 6 项**，老师带 7 个班时后面的班直接选不到，
而且不会报错，只是"消失"。

改成自定义底部弹层，顺带解决三件事：

- 没有数量上限
- 每项能显示"班级名 + 人数"，选择时信息更全
- 底部可以放「＋ 新建班级」，选不到时下一步该做什么是明确的

### 9.23 页面从 tabBar 移出后，所有指向它的 `switchTab` 都会静默失败

「学生」页换成「通讯录」之后，`pages/teacher/student/index` 不再是 tabBar 页。
但代码里还留着几处 `uni.switchTab({ url: '/pages/teacher/student/index' })`，
微信端会直接报 `can not switch to no-tabBar page` —— 如果外面套了 `fail` 回调，
表现就是"点了没反应"，最难查的那一类。

**`switchTab` 和 `navigateTo` 不是可以互换的两个 API**：

| | 目标在 tabBar 里 | 目标不在 tabBar 里 |
|---|---|---|
| `switchTab` | ✅ | ❌ 报错，页面不跳 |
| `navigateTo` | ❌ 报错 | ✅ |

所以判断标准只有一条：**这个页面在 `pages.json` 的 `tabBar.list` 里吗**。
不要在点击事件里凭印象选，把名单收敛到一处：

```js
const TAB_PAGES = [
  '/pages/teacher/index/index',
  '/pages/teacher/contact/index',
  '/pages/teacher/score/index',
  '/pages/teacher/seat/index',
  '/pages/teacher/leave/index'
]
const go = (to) => TAB_PAGES.indexOf(to) > -1
  ? uni.switchTab({ url: to })
  : uni.navigateTo({ url: to })
```

**改 tabBar 时的检查清单**（这次漏掉了一条，就是因为没做）：

```bash
# 1. 全量搜 switchTab，逐个核对目标是否还在 tabBar 里
grep -rn "switchTab" src/ --include=*.vue --include=*.js
# 2. 编译产物里再核一遍（防止注释/字符串里的假命中，也防漏编译）
grep -o 'switchTab({url:"[^"]*"' dist/build/mp-weixin/pages/**/*.js
# 3. 对照 pages.json 的 tabBar.list 白名单
```

`navigateBack` 是安全的：它不关心目标在不在 tabBar 里。
所以"导入完成返回列表"这类场景，正确写法是 `navigateBack`，
再挂一个 `fail` 兜底到 tabBar 页（万一页面是直接打开的，栈里没有上一页）。

另外，列表页必须有 `onShow(() => load())`，否则 `navigateBack` 回来看到的是旧数据，
老师会以为"导入没生效"又导一遍。

### 9.24 真机「加载失败」，先看包里烧的是哪个地址

真机上一打开就「加载失败」，下面写「网络异常，请检查后端是否启动」。
最容易走错的一步是去怀疑后端 —— 而后端其实是活的，`curl localhost:8080` 好得很。

**这次的真实原因有两层，都不在后端**：

1. `dist/build` 里烧的是 `http://localhost:8080/api`。**手机上的 localhost 是手机自己**，
   不是那台跑后端的电脑 —— 所以必然连不上。
2. `.env.production` 里写的内网 IP `172.25.39.216` 早就失效了：
   热点是 DHCP，重连就换段，电脑现在已经是 `192.168.43.27`。

**记住三件事**：

- `.env.*` 只在**构建那一刻**被读进包里。改完 env 不重新 `build`，
  包里还是旧地址 —— 而且构建过程不会报任何错。
- `dev` 模式和 `build` 模式读不同的 env 文件。`dev` 用 `.env` / `.env.development`，
  `build` 才用 `.env.production`。所以「开发者工具里好、手机上好不了」是正常的，
  别拿 dev 能跑当证据。
- 判断手机能不能连电脑，直接在电脑上 `curl http://<内网IP>:8080/api/...`。
  返回业务 JSON（哪怕是 `401`/`1002`）就说明链路通了；返回 `000` 才是网络不通。

**顺手改掉了一个坏提示**：`request.js` 的 `fail` 分支原来固定写
「网络异常，请检查后端是否启动」，不告诉你它在连谁。
现在把实际地址打出来：

```js
const host = BASE_URL.replace(/\/api\/?$/, '')
const msg = `连不上 ${host}，请检查后端是否启动、地址是否变了`
```

这样一眼就能看出「它连的 172.x，可我电脑现在是 192.168.x」。
**失败提示里要带上"它试图访问的目标"**，否则用户只能在两个地方瞎猜。

### 9.25 「xxx is not a function」而代码明明是对的：查「编译热重载」

真机上（或开发者工具模拟器里）报 `api_class.listClasses is not a function`。
翻遍源码和编译产物都是对的：

- `api/class.js` 确实 `exports.listClasses`
- `store/class.js` 确实 `const api_class = require("../api/class.js")`
- 全包 391 处 `require` + 成员调用校验，无缺失
- 依赖图 `page → store → api/class → api/request → vendor` 是干净 DAG，无循环
- 所有文件同一次编译，时间戳一致

**那这个"新 store + 旧 api"的组合是从哪来的？**

微信开发者工具的 `project.private.config.json` 里默认开着：

```json
"compileHotReLoad": true,
"ignoreDevUnusedFiles": true
```

`compileHotReLoad`（编译热重载）在改动后**只重载当前页面，不重置 `require` 缓存**。
于是会出现一个磁盘上根本不存在、只有运行时才有的组合：
新的 `store/class.js` 被重新求值，而它 `require` 的 `api/class.js` 还是内存里的旧实例。

这类 bug 的特征是：**报错信息和你看到的代码互相矛盾**。
遇到时不要继续读代码，先怀疑"跑的不是这份代码"。

**处置**：

1. 关掉 `compileHotReLoad`（详情 → 本地设置 → 取消「编译热重载」）
2. 「清缓存」→「全部清除」
3. 重新「编译」；真机还要**重新点「预览」生成新二维码**，旧码里封的是旧代码

**加了一个构建时间戳**（`vite.config.js` 的 `define` + `App.vue` 的 `onLaunch` 打印）：

```js
// vite.config.js
const BUILD_STAMP = new Date().toLocaleString('zh-CN', { hour12: false })
define: { __BUILD_STAMP__: JSON.stringify(BUILD_STAMP) }

// App.vue
const BUILD_STAMP = typeof __BUILD_STAMP__ !== 'undefined' ? __BUILD_STAMP__ : 'unknown'
console.log(`[班级助手] 构建于 ${BUILD_STAMP}`)
```

用 `typeof` 判断而不是直接引用，是因为万一某个入口没走 `define`，
`ReferenceError` 会把整个小程序启动打挂 —— 一个调试标记不值得冒这个险。

以后遇到"改了没生效"，**先看这行日志的时间对不对**，比清十次缓存都快。

### 9.26 「删除班级」做成归档，不是物理删除

老师要求通讯录里能删班级。第一反应是 `DELETE FROM t_class WHERE id=?`，但一个班的数据散在
`t_class` / `t_student` / `t_exam` / `t_score` / `t_seat` / `t_leave` / `t_todo` /
`t_subject_container` / `t_container_message` 里。物理删除只有两条路，都不好：

- **级联删**：写一长串 DELETE，漏一张表就是孤儿数据，而且删掉就真找不回来了
- **只删主表**：留下永远查不到的孤儿行，更糟的是「新建同名班级」会读到旧数据

**归档只动两个字段**：

```java
classMapper.update(null, new LambdaUpdateWrapper<ClassInfo>()
        .eq(ClassInfo::getId, id).set(ClassInfo::getStatus, 0));
containerMapper.update(null, new LambdaUpdateWrapper<SubjectContainer>()
        .eq(SubjectContainer::getClassId, id).eq(SubjectContainer::getStatus, 1)
        .set(SubjectContainer::getStatus, 0));
```

能这么简单，是因为**所有查询本来就带 `status=1`** —— 隔离是免费的。
反过来说：如果哪天新写一个查询忘了带 `status`，归档的班就会重新冒出来。
这是归档方案唯一的代价，写新查询时要记住。

**三个必须一起处理的连带点**（这次都补上了）：

1. **班级群也要归档**。否则它会留在「课程群组」列表里，点进去是个已经没有班级的群，还能发消息。
2. **`ownsStudent` 要判班级 status**。原来只比 `head_teacher_id`，归档后拿旧 `studentId`
   直接调 `/student/{id}` 还能读到、甚至改到一个"已经删掉的班"的学生。
3. **允许删到最后一个班**。老师带完一届要清空是正常需求，别为了"至少留一个"去挡；
   首页和通讯录都有「还没有班级」的空状态，删空不会崩。

归档后可以**重新建同名班级**（查重只查 `status=1`），这一点也是归档方案顺带带来的。

### 9.27 改名不查重 = 静默产生两个同名班

`POST /class` 建班时查了重名（1006），但 `PUT /class` 改名时没查。
于是把「一班」改成「二班」，两个班同名，首页选择器和通讯录里出现两张一模一样的卡，
老师根本分不清点的是哪个。

**比建班重名更难发现**：建班时一眼就报错，改名却会静默成功。

修法很简单，关键是**排除自己**：

```java
if (renamed) {
    Long dup = classMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getHeadTeacherId, userId)
            .eq(ClassInfo::getName, name)
            .eq(ClassInfo::getStatus, 1)
            .ne(ClassInfo::getId, clazz.getId()));   // 不排除自己的话，改成原名都会报重名
    ...
}
```

**顺带一个一致性问题**：班名改了，班级群名也得改。
原来的注释写的是「群也可以有自己的名字，所以不同步」，但通讯录里班级群就是班级的入口 ——
卡片上同时出现「24级软通1班」和「24级软通2班」两个名字，老师只会以为点错了班。
班级群是系统自动建的、一个班只有一个，它不该有独立名字。
`t_subject_container` 的 `name` 和 `subject_name` 都要同步（前者是卡片上显示的，后者是群聊列表里显示的）。

### 9.28 低频操作：弹层撑不住的时候就该给一页

「重命名 / 删除班级」第一版做成了 `⋯` → `showActionSheet` → `showModal`（editable 输入框）。
能用，但很快就撑不住了：

- 弹层只装得下两个动作。改年级、改开学日期、看邀请码、看人数，全都放不进去
- 改班名不是"随手改个字段"。它是首页标题、名单导入的校验依据、导出表头，
  需要一个明确的「保存」动作 —— 弹窗里的输入框没有这个语义
- 删除要放在页面底部、和改名隔开。塞进同一个 actionSheet 里，
  手指滑过头就点到了

所以改成：`⋯` → `navigateTo('/pages/teacher/class/setting?classId=')`。

**判断标准**：动作只有 1~2 个、且都不需要解释 → 弹层；
动作超过 2 个、或者任何一个需要说明后果 → 独立页面。

**顺手修的一个隐患**：这个页面原来只认 `currentClassId`。
从通讯录点二班的 `⋯` 进去时，全局当前班可能还是一班，
于是打开的是错的班，改完还以为改的是二班。
现在 `onLoad` 读 `?classId=`，读不到才回落到 `currentClassId` ——
**凡是"从列表点进某个具体条目"的页面，都要能从 query 拿到那个条目的 id，
不能依赖全局选中项。**

### 9.29 群聊右上角其实有「详情」，但没人看见

老师反馈「那个群右上角好像没有群信息」。查下来 `container/chat` 的自定义导航里
一直有 `nav-detail`（红字「详情」），`goDetail` 也正常跳 `container/detail`，
接口对班级群返回的 `classId` / `className` 都对。

不是 bug，是**入口不够显眼**，以及**群信息和班级信息之间没有路**：
群详情里能看到「所属班级：24计算机软通1班」，但那只是一行灰字，点不动。

补了一行可点的「所属班级」卡片，跳 `/pages/teacher/class/setting?classId=`。
这样「群 → 群信息 → 班级信息」是通的，不用退回通讯录再找卡片。

顺带记一下排查方法：**先打接口，再看布局**。
`GET /teacher/container/{id}` 对班级群返回 `classId`/`className` 正常，
就能排除"数据没带过来"；再看 `.nav-detail` 有 `flex-shrink: 0`、
`.nav-name` 有 `max-width + ellipsis`，就能排除"被长群名挤掉"。
两样都排除后，结论只剩"用户没注意到"，那就把它挪到更显眼的地方、或者加一条新路。

### 9.30 设置页的「解释」是成本，不是价值

群信息页（`container/detail`）原来长这样：

- 每行入口下面挂一句说明：「班委可收录」「只有群主可见」「名称 / 关联班级」
- 页面底部一整块「关于权限」讲群主/班委能做什么
- 创建完还弹一个「群组已创建 · 把下面的邀请码发给学生」

这些字都是写代码的人觉得"用户会想知道"，但用户看到的是：
**一屏里有一半是灰字，真正能点的东西反而不显眼。**

改完之后：

| 删掉 | 保留 |
|---|---|
| 「关于权限」整块 | 群名、班级、人数、群主、邀请码 |
| 三个入口的说明副标题 | 右侧的值（`57 人`、`永久有效`、`24计算机软通1班`） |
| 「群组已创建」提示条 | 全部功能入口 |

**区分"说明"和"值"**：右侧的「57 人」「永久有效」是信息，删了用户就不知道点了会看到什么；
「班委可收录」是解释，删了不影响判断。判断标准是——**这句话是不是在描述当前状态**。
是状态就留，是教育用户就删。

### 9.31 一项一张卡：分界靠留白，不靠 1rpx 细线

原来的做法是一张白卡里放 5 行，行之间用 `1rpx solid #F5EDE2` 分隔。
问题：`1rpx` 在真机上几乎看不见，五行糊成一整块白，用户点之前要犹豫一下"这是几行"。

改成**一项一张卡**，卡片之间 16rpx 留白：

```scss
.item {
  display: flex; align-items: center;
  min-height: 108rpx;
  margin-top: 16rpx;
  background: #fff; border-radius: 24rpx;
  overflow: hidden;   /* 不加的话 hover 背景会溢出圆角，变成方块 */
}
.item-pressed { background: #FBF5EC; }
.item-danger  { margin-top: 36rpx; }  /* 危险项单独拉开距离 */
```

两个细节：

- **`overflow: hidden` 必须加**。`hover-class` 换的是 `background`，
  不裁剪的话点击时圆角会变成直角，闪一下很难看。
- **危险项加大 `margin-top`**。不用 `:first-child` 之类的选择器
  （小程序对部分伪类支持不稳），直接给危险项一个更大的上边距；
  而且「退出」和「解散」互斥（群主看解散、非群主看退出），不会同时出现两个。

## 9.32 多班级改造最容易漏的一处：登录里的 `selectOne`

**症状**：`POST /api/auth/wx-login` 返回 `{"code":5000,"message":"服务器开小差了"}`，
日志里只有一句 `TooManyResultsException`，没有任何"班级"字样。表现出来是
**"登录不上"**，很容易被误判成网络/地址问题。

**原因**：`AuthServiceImpl` 里按班主任查班用的是 `selectOne`：

```java
// 错的：单班级时代的写法
ClassInfo clazz = classMapper.selectOne(
        new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getHeadTeacherId, user.getId()));
```

`selectOne` 查到 **2 条**就抛异常，被 `GlobalExceptionHandler` 的 `Exception`
兜底成 5000。一个老师有第二个班的那一刻，登录就废了 —— 而且是**静默**的：
没有任何代码改动、没有报错，只是"今天突然登不上了"。

**改法**：查列表取第一条。

```java
private ClassInfo firstClassOf(Long userId) {
    List<ClassInfo> list = classMapper.selectList(new LambdaQueryWrapper<ClassInfo>()
            .eq(ClassInfo::getHeadTeacherId, userId)
            .eq(ClassInfo::getStatus, 1)        // 归档班不能当默认班
            .orderByAsc(ClassInfo::getId));     // 取最早建的，保证默认班稳定
    return list.isEmpty() ? null : list.get(0);
}
```

两个细节都不是可选的：

- **必须 `orderByAsc(id)`**。不加排序，MySQL 返回顺序不保证，默认班每次登录
  可能变，老师会以为"数据串班了"。
- **必须过滤 `status=1`**。删班是归档（§9.26），归档班留着 `head_teacher_id`，
  不过滤就会把已删的班当默认班。

**排查手法**：这类 bug 不能靠看接口返回，只能看 SQL。把 `log-impl` 设成
`StdOutImpl`（本项目已设），日志里会打出

```
==>  Preparing: SELECT ... FROM t_class WHERE (head_teacher_id = ? AND status = ?) ORDER BY id ASC
<==      Total: 2
```

`Total: 2` 就是证据 —— 原来那条 SQL 没有 `status` 条件也没有 `ORDER BY`，
一样会返回 2 行，然后 `selectOne` 炸掉。

**顺带一条**：`/api/auth/me` 里是同一份代码，所以它和登录会同时坏。
改的时候要一起改，只改一处会得到"能登录但一进首页就 5000"这种更奇怪的现象。

## 9.33 后端地址必须能在运行时改，不能只烧在包里

**问题**：`.env.*` 是**构建时**读取的，地址会被内联成字符串常量写进包里。
只要换一次网络（手机热点 → 宿舍路由 → 教室 Wi-Fi），电脑的局域网 IP 就被
DHCP 重新分配，包里的旧 IP 立刻失效，手机上表现是「连不上 http://x.x.x.x」。
修它要四步：改 `.env` → 重新构建 → 重新点「预览」出二维码 → 手机重新扫码。

对一个「自己用」的小程序来说，这四步的代价远大于收益 —— 换教室就断一次，
断一次就要回到电脑前重来一遍。

**改法**：地址存 storage，运行时优先读 storage，读不到才回落到构建时的默认值。

```js
// utils/api-host.js
const KEY = 'apiBaseUrl'
export function getBaseUrl() {
  return (uni.getStorageSync(KEY) || DEFAULT_BASE_URL).replace(/\/+$/, '')
}
```

三个关键点，少一个就不成立：

1. **`BASE_URL` 不能是模块级常量**。原来 `export const BASE_URL = ...`，
   `container.js` / `upload.js` 直接 `import { BASE_URL }` —— 那是**值拷贝**，
   用户改了地址它们还指着老的。必须改成 `getBaseUrl()` 函数，每次现取。
2. **改地址的入口要能被找到**。放在首页底部版本号旁边（`v1.0.0 · 192.168.43.27`），
   它同时是「现在连的是哪台机器」的显示，不额外占地方。
3. **连不上时自动给一次机会**。`request.js` 的 `fail` 分支原来是 toast，
   用户只能干看着；改成弹「连不上服务器 / 改地址」。这里要加时间戳去重
   （10 秒一次），否则首页并发四五个请求会叠出四五个一样的弹窗。

**输入要宽松**。用户在手机上打 `192.168.1.5` 就该能通，别要求他打全
`http://192.168.1.5:8080/api`。`normalizeBaseUrl` 负责补协议、补 `:8080`、补 `/api`：

| 输入 | 结果 |
|---|---|
| `192.168.43.27` | `http://192.168.43.27:8080/api` |
| `192.168.1.5/api` | `http://192.168.1.5:8080/api` |
| `https://api.example.com` | `https://api.example.com/api`（域名不补端口） |

**补端口只对 IP 补**。域名补成 `:8080` 是错的 —— 线上走 443。
判断标准是 `/^\d{1,3}(\.\d{1,3}){3}$/` 或 `localhost`。

**没有做的取舍**：`showModal` 的 `editable` 只适合「一年用几次」的输入，
所以没为它单独做一页。如果以后要填的东西超过一个（比如同时配超时时间），
再升级成页面。

## 9.34 真机排版和开发者工具不一样：`calc()` 里写 `rpx`

**症状**：首页四个数据卡在开发者工具里是 2×2，在手机上变成**一张一行**，
整个首页被拉长一倍多。样式代码完全没动过，工具里也复现不出来。

**原因**：`.stat` 的宽度是这么写的：

```scss
.stats { display: flex; flex-wrap: wrap; gap: 20rpx; }
.stat  { width: calc(50% - 10rpx); }
```

`calc()` 里混用 `%` 和 `rpx`，**开发者工具能算对，真机上这条 `width` 可能整条被丢掉**。
宽度一没，flex 项就退化成「自己占一行」，四张卡竖着排下来。
`gap` 也不会报错，只是跟着一起失效或生效，很难从现象反推。

**改法**：不依赖 `calc`，两个百分比就够。

```scss
.stats { display: flex; flex-wrap: wrap; justify-content: space-between; }
.stat  { flex: 0 0 48.6%; width: 48.6%; box-sizing: border-box; margin-bottom: 20rpx; }
```

三个要点：

- **`48.6%` 而不是 `50%`**。两个 50% 加上任何间距就超过 100%，一超过就换行。
  留 2.8% 当沟槽，`justify-content: space-between` 负责把它推到两边。
- **`flex-basis` 和 `width` 同时写**。这两条走的是不同的解析路径，
  只要有一条没被丢掉，就还是两列。只写 `width` 就是在赌渲染内核。
- **纵向间距用 `margin-bottom`**，不用 `gap`。`gap` 在旧内核上支持不一，
  而 `margin` 从 1998 年起就没变过。

**排查方法**：真机和工具不一致时，第一件事是去翻**编译产物**的 wxss，
看目标声明还在不在。`grep "width: calc" dist/*/mp-weixin/**/*.wxss`。
注意 dev 包**不压缩、保留注释**，grep 到 `calc` 可能只是命中了注释里的说明文字，
要连上下文一起看。

## 9.35 tabBar 从 5 个减到 4 个：页面「脱离 tabBar」的连带修改

需求：新增「个人中心」，把成绩、座位挪进去，底部只留 首页 / 通讯录 / 请假 / 个人中心。

**看起来只是改 `pages.json`，实际有三处会静默失效**，全部都是同一个原因：
页面从 tabBar 里拿掉之后，`switchTab` 就不能再指向它了。

1. **`switchTab` 的目标**。`uni.switchTab` 只认 tabBar 里注册过的页面，
   指向别处会直接报 `can not switch to no-tabBar page`，点击没反应、也没有弹窗。
   这次改到两处：
   - `score/import.vue` 的返回兜底：`switchTab(score/index)` → `redirectTo(score/index)`
     （用 `redirectTo` 而不是 `navigateTo`：这一页是从成绩页跳过来的，
     再 `navigateTo` 会叠出一个新的成绩页，返回时又回到导入页，绕圈）
   - `student/detail.vue` 的「查看座位」：`switchTab` → `navigateTo`
2. **那份「哪些页面是 tab」的名单**。首页里有个 `TAB_PAGES` 常量，
   `go()` 靠它决定用 `switchTab` 还是 `navigateTo`。名单不同步就会跳错方法。
   新增/移除 tab 页时，**`pages.json` 和这份名单必须一起改**。
3. **页面的返回方式**。tab 页没有返回箭头（它没有上一页），
   变成普通页之后导航栏会自动出现返回箭头，原来靠 tabBar 切换的路径现在要靠 `navigateBack`。

**检查清单**（改 tabBar 后逐条过一遍）：

```bash
grep -rn "uni.switchTab" src/     # 每个目标都必须在 tabBar 名单里
grep -rn "teacher/score/index\|teacher/seat/index" src/   # 找所有入口，确认用的是 navigateTo
```

**新增 tabBar 图标的规格**（`src/static/icons/tab-*.png`）：
81×81、RGBA 8bit、实心填充（不是描边）、灰 `#8A8178` / 选中 `#C5342B`。
这次的「个人中心」用**圆圈里装一个小人**，而不是纯人形 ——
通讯录那个 tab 已经是纯人形了，两个并排会分不清。

## 9.36 「改自己的资料」：`PUT /api/auth/me` 的取舍

个人中心要能改姓名和手机号，于是加了第 30 个接口。几个决定值得记一下。

**路径是 `/auth/me`，不是 `/user/{id}`**。改的永远是「当前登录的这个人」，
userId 从拦截器塞进 request 的属性里取。如果接受前端传 id，
传谁的 id 就能改谁的名字 —— 这类越权不需要黑客，一个手抖的循环就够了。

**DTO 里没有 `role` / `status` / `openid`**。角色决定「能不能改成绩」，
状态决定「能不能登录」。把它们放进表单，等于把权限系统交给用户自己填。
`avatar` 也没放：头像要先上传拿 URL，是「文件 + 表单」两步，
混进来会让这个接口多一个失败分支。

**`phone` 的 null 和空串是两个意思**：

| 传的值 | 含义 |
|---|---|
| 不传 / `null` | 不改（MyBatis-Plus 的 `updateById` 默认忽略 null 字段，正好对上） |
| `""` | 清空手机号 |
| `"138..."` | 改成它 |

校验写成 `^$|^1[3-9]\d{9}$` 而不是 `@NotBlank`：手机号是选填的，
强制必填会把「没填手机号」的老师挡在保存按钮外面。

**`joinedAt` 用 String 而不是 LocalDateTime**。前端只是把它当一行字显示，
传时间戳过去还要在前端再格式化一次，多一处出错的地方。
后端 `createdAt.toLocalDate().toString()` 直接给 `2026-10-29`。

**改完直接返回最新 UserVO**。前端保存后要用它刷新页面，
让它再发一次 GET 只是多一次往返和一个失败点。

## 9.37 「单位」不能塞进「班级」：教师和班级是多对一

个人中心原来那一行写的是「班级」，但这是**教师端**。老师挂的是学校 / 单位，
不是某个班；而且一个老师可以带好几个班（多班级改造之后是常态），
「单位」只有一个。两者不是同一个概念，不能复用 `t_class.name`。

所以给 `t_user` 加了 `unit` 字段（`migrations/08_user_unit.sql`，可空）。

**为什么不用 `t_class` 的某个字段代替**：学生也在 `t_user` 里，
学生没有班级可填，更没有单位。字段挂在人身上，不挂在班上。

**为什么可空而不是 `DEFAULT ''`**：`''` 会让「没填」和「填了空」变成一个意思，
以后想做「完善资料」提醒就没法区分了。前端显示 `user.unit || '未填写'`。

**顺手删掉了「加入时间」**。`created_at` 这种字段属于「系统知道但用户不关心」，
放在个人信息里只是多一行噪音。后端 VO 里的 `joinedAt` 也一起删了 ——
留着没人用的字段，下次读代码的人会以为某处依赖它。

**教训**：往个人信息里加一行之前，先问「这个字段属于人，还是属于班」。
属于班的，去班级信息页；只有属于人的（姓名、手机号、单位）才放这里。

## 9.38 座位页为什么「不能选班级」——它一直没选过

**症状**：老师问「座位为什么不能选班级」。成绩页顶上有一条「切换班级 ▾」，
座位页没有，只能看当前班。

**原因不是这次改坏的，是从来就没有**。座位页从头到尾只写了
`getSeatChart({ classId: currentClassId.value })`，班级由首页的全局选择器决定。
以前座位是 tab 页，老师的路径是「首页切班 → 点座位」，所以没暴露出来；
现在座位挪进个人中心，路径变成「个人中心 → 座位」，中间没有切班的环节，
问题就显形了。

**这暴露的是页面职责问题**：一个页面如果「按班展示数据」，那它自己就该能选班。
依赖别人先选好，等于把一半的操作藏在了上一个页面里。

**改法**：把成绩页那套班级条原样搬到座位页（原生 `picker` + 一条浅色横条），
顺手处理两个座位页特有的问题：

1. **切班前必须问一句**。座位页有 `dirty` 标记（拖过座位还没保存）。
   直接切班会重新拉座位表，未保存的排列静默消失 —— 老师白排一次。
   有 `dirty` 时弹「还没保存 / 丢掉并切换」。
2. **`onShow` 里要先 `await loadClasses()` 再拉座位**。`currentClassId` 的初值
   来自 storage，第一次进小程序时可能是 0，拿 `classId=0` 去请求会直接报
   「班级不存在」。顺序反了就是白屏 + 报错。

**检查清单**：页面从 tabBar 挪到子页面后，要重新想一遍「它依赖哪些全局状态」。
全局状态是「别人替你选好的」，一旦路径变了，那个「别人」就不在链路上了。

## 9.39 一张卡里放两种东西，靠 1rpx 分割线区分是不够的

通讯录原来一个班一张卡：上半「班级群」，下半「班级联系人」，中间一条 1rpx 的线。
两个入口的视觉重量几乎一样，都是「图标 + 两行字 + 箭头」，唯一的分界是那条线 ——
手指按下去之前必须先分辨「我按的是线上方还是下方」。

**改法**：拆成两张卡。班级信息 + 联系人在上面那张，班级群单独一张放下面。

```html
<view class="block" v-for="c in classes" :key="c.id">
  <view class="card">…班级头 + 联系人…</view>
  <view class="card card-group">…班级群…</view>
</view>
```

三个细节：

- **外层必须有 `.block`**。`v-for` 只能落在一个根节点上，两张卡要一起循环，
  就得包一层。间距也从 `.card` 挪到 `.block`（`margin-bottom: 20rpx`），
  否则两张卡之间会多出一份间距。
- **群卡的间距比班与班之间小**。`margin-top: 12rpx` vs 20rpx。
  这个差值承担了「这个群属于上面那个班」的表达 —— 加一行「所属班级：xxx」
  也能说清楚，但那是每个班多一行字，而 8rpx 的差值不占任何空间。
- **`.entry-single { border-top: none }`**。`.entry` 的顶边线是用来分隔
  「同一张卡里的上下两行」的，一张卡只有一行时它就成了卡内顶部一道多余的杠。

**通用判断**：一张卡里如果两行的**交互目的不同**（一个进聊天、一个进名单），
就该是两张卡。同一种东西的多行（比如设置页里的「主题」「字体」「通知」）
才适合用分割线串成一张。

## 9.40 把同类项收成一个区块：两区结构

上一版把每个班的群单独一张卡、贴在班级卡下面，视觉上确实分开了，
但**信息是散的**：想「我一共有几个群、各多少人」得从上往下逐张卡看。

改成两个分区：

```
班级群                    2 个未创建        ← 分区标题
┌────────────────────────────────┐
│ 群 24计算机软通1班    57 人  › │   ← 所有群一张卡，行高 96rpx
│ 群 24软通班          未创建  › │
└────────────────────────────────┘
班级                                      ← 分区标题
┌────────────────────────────────┐
│ 24计算机软通1班  57 人  ⋯      │
│ ─────────────────────────────  │
│ 人 班级联系人  57 位学生    ›  │
└────────────────────────────────┘
```

**为什么群用一张卡串起来，班级却一个班一张卡**：
判断标准是「这一行需要承载多少信息」。群行只需要「点进去聊天」，
一行就够，同一种东西串成一张卡最紧凑，也最容易横向比较（人数对齐）。
班级卡要放班名、年级、人数、管理入口、联系人入口，信息量决定它必须独占一张卡。

**群行压到 96rpx、图标 56rpx**，比班级卡里的入口（112rpx / 72rpx）矮一档。
这个高度差本身就是层级：上面的区块更轻，下面的更重。

**「N 个未创建」写在分区标题右边**，不是写在每一行。
未创建是「需要处理的少数情况」，用主色标在标题处就够了；
每一行再标一次红色，整块会显得很吵。行内仍然保留 `未创建` 三个字，
但只有真正点进去的那一行才需要看到它。

**删掉了页面底部那段说明**（「学生名单在班级联系人里，学生目前还没有账号…」）。
理由和 §9.30 一样：它解释的是「为什么不能做什么」，属于一次性信息。
§9.30 的判断标准是「这句话是在描述当前状态吗」—— 不是，所以删。

## 9.41 首页格子按「多久点一次」排，不按「功能重不重要」排

快捷操作原来是四个：加学生 / 记成绩 / 记请假 / 待办。
老师要求撤掉前两个，只留请假和待办。

**这不是删功能**。撤掉之后两个页面都还在，入口也没断：

| 功能 | 现在从哪进 |
|---|---|
| 加学生 | 通讯录 → 某个班 → 班级联系人 → 名单页右下角 ＋ |
| 记成绩 | 个人中心 → 成绩 |

**为什么该撤**：加学生是一次导入几十个人、之后基本不动的操作；
记成绩是期中期末各一次。它们各占着首页最显眼的四个格子之一，
而老师每天真正要点的只有「今天谁请假」「今天要做什么」。

首页的格子数量是有限的、位置是最贵的，所以排序依据应该是**使用频率**，
不是功能的重要性。重要但低频的东西，放在它自己的模块里就行
（学生归通讯录、成绩归个人中心），不必占用首页。

**改法**：`quicks` 数组删掉两项即可，`.quick-grid` 的两列布局不用动 ——
`flex: 0 0 48.6%` 在 2 项时仍然是一行两列，和上面的数据四宫格对齐。

**保留的**：`.tone-r` / `.tone-g` 两个配色没删。它们属于 r/g/b/y 一套完整调色，
删一半会让剩下的一半看起来像随手写的；恢复时也只需要把数组项加回去。

---

## 9.42 「已满」和「已截止」是算出来的，不存库

接龙有五个状态，`t_relay.status` 只存三个：

| 值 | 含义 | 存不存 |
|---|---|---|
| 1 | 进行中 | 存 |
| 2 | 已截止 | **算**：`deadline < now()` |
| 3 | 已满 | **算**：`count >= maxCount` |
| 4 | 已结束 | 存（发起人手动） |
| 5 | 已取消 | 存 |

**为什么后两个不存**：存了就得有个定时任务去翻状态。翻漏一次，卡片就永远停在
"进行中"，而名单早就满了；改截止时间还得顺手把状态改回去。这中间的每一种漏改
都是一个"看起来能用但其实在骗人"的 bug。

算出来的代价是每次读多两个判断——和定时任务的运维成本比，这个代价几乎为零。

```java
private int effectiveStatus(Relay r, int count) {
    int s = r.getStatus() == null ? Relay.STATUS_ONGOING : r.getStatus();
    if (s == Relay.STATUS_CLOSED || s == Relay.STATUS_CANCELED) return s;
    if (r.getDeadline() != null && r.getDeadline().isBefore(LocalDateTime.now())) return Relay.VIEW_EXPIRED;
    if (r.getMaxCount() != null && count >= r.getMaxCount()) return Relay.VIEW_FULL;
    return Relay.STATUS_ONGOING;
}
```

**「已满」和「已截止」必须是两个状态，不能合并成"不可用"。** 看到"已满"会想
"能不能挤进去"，看到"已截止"不会——用户的心理动作完全不同，文案就得分开。

---

## 9.43 接龙不产生"参与消息"

最直觉的做法是每有人接龙就往群里发一条「张三 参与了接龙」。3 个人还好，
30 个人就是 30 条系统消息，把真正的聊天全挤没了。

**做法**：卡片自己更新人数，不产生任何新消息。卡片本身就是那条消息，
它的内容是每次拉列表时重新算的（见 9.44）。

同理，同一张卡片的状态变化（12 → 13 → 14）也不各发一条，只改卡片上的数字。

---

## 9.44 卡片状态不存消息表，每次批量重算

`t_container_message` 里只有 `type` 和 `ref_id`，**没有** `entry_count` 这类冗余列。

如果存了：`join()` 成功、`updateEntry()` 成功、代填成功，三个地方都要记得同步
消息表里的冗余字段。漏掉任何一处，卡片上的数字就永久错了，而且没有任何报错——
用户只会觉得"这功能不准"。

`ContainerMessageServiceImpl` 在 `list()` 里做批量聚合：

```java
private void attachCards(List<MessageVO> list, Long containerId, Long meId) {
    // 先把这一页的 refId 去重收集，再一次性查完
    List<Long> relayIds = list.stream()
            .filter(v -> v.getType() != null && v.getType() == ContainerMessage.TYPE_RELAY)
            .map(MessageVO::getRefId).filter(Objects::nonNull).distinct().toList();
    ...
    if (relayIds.isEmpty() && lotteryIds.isEmpty()) return;
    Map<Long, RelayCardVO> relays = relayService.cardMap(meId, containerId, relayIds);
    ...
}
```

**必须是批量**：一页 30 条消息，一条一查就是 30 次查询。

**注意循环依赖**：`ContainerMessageServiceImpl` 现在依赖 `RelayService` 和
`LotteryService`，所以这两个 Service 插卡片消息时**必须直接操作
`ContainerMessageMapper`**，不能回头调 `ContainerMessageService` —— 构造器循环依赖，
Spring 启动就报错。

---

## 9.45 `rank` 是 MySQL 8 的保留字，列名不能叫它

抽签结果表最初把排序字段叫 `rank`（第几名）。建表、插入都正常，
只有 SELECT 报错：

```
SQLSyntaxErrorException: ... near 'FROM t_lottery_result WHERE (lottery_id = 1)
ORDER BY round ASC, group_no A'
```

报错位置指向 `FROM`，看起来像表名或 WHERE 写错了，实际是**列名 `rank` 被当成
窗口函数**（`RANK() OVER (...)`）。MyBatis-Plus 生成列清单时不加反引号，
所以 `SELECT id, rank, ...` 直接语法错误。

**改成 `sort_no`。** 同类要避开的保留字还有 `rank` / `row_number` / `dense_rank` /
`groups` / `system` / `window`。命名时宁可加个后缀，也别赌它是不是保留字。

---

## 9.46 「＋」面板从 2 项到 4 项：为什么直接上 4 列

原来面板是 2 项左对齐（`.tool { width: 150rpx; margin-right: 28rpx }`），
加接龙和抽签后变成 4 项。

**直接改成 4 列宫格（每项 `width: 25%` + `flex-wrap: wrap`），不做分类 Tab。**

| 方案 | 为什么不做 |
|---|---|
| 3 列 | 每项 234rpx，图标和文字之间留一大片空白，看着像"没排满" |
| 分类 Tab | 4 个功能分两类，用户要多点一次才能看到第二个分类 |
| 左右滑动 | 移动端最经典的发现性问题：右边的功能永远没人知道 |
| 长按拖拽排序 | 面板是"每次重新打开"的临时界面，拖拽需要教学，收益接近 0 |

**只做一件事：红点在 ＋ 号上，不在面板内的图标上。**
不打开面板就看不到的红点等于没有。点开面板即消除，不是"用过才消除"——
后者会让人为了消红点乱点。

**「记住最近使用」已经删掉。** 原设计是本地存 `chat_last_tool`，把上次用过的
提到第一位。上线后真实反馈是：**"四个功能位置不固定，点谁谁跳到最前面"**。

错在哪：面板是个**肌肉记忆界面**。用户第二、第三次打开时手已经先动了，
他记得"抽签在第四个"。把抽签挪到第一位，等于让他的手指落在"照片"上。
省下的是"横向扫一眼"的成本，付出的是"每次都要重新定位"的成本 ——
而后者发生在每一次打开，前者只发生在第一次。

所以四个入口的位置**永久固定**，不做任何自适应排序。真要提升高频项的
可达性，正确做法是把高频项做成聊天页上的常驻入口（比如输入框旁边的
快捷按钮），而不是在面板里偷偷换位置 —— 换位置解决不了"打开面板"这一步。

**图标全部用 CSS 画**（省四张图片资源），线宽 3rpx、圆头、单色。接龙是
"三行小圆点 + 横线"（名单的样子），抽签是"签筒 + 一支斜出的签"。
**抽签不用骰子**——骰子会被读成"游戏"。

---

## 9.47 卡片宽度的算法：比气泡宽，但没占满屏

气泡的 `.msg-body` 是 `max-width: 74%`，卡片不能用这个值——它是功能性内容，
74% 装不下"已报 12/30 · 11-08 18:00 截止"这一行。

```scss
.msg.card-msg { flex-direction: row; }          /* 自己发的卡片也不靠右 */
.msg.card-msg .msg-body { max-width: none; flex: 1; align-items: stretch; }
```

这样卡片宽度 = 屏幕 − 页面 padding(48rpx) − 头像(76rpx) − 两边间距(36rpx)
≈ **容器宽 − 96rpx**，和设计稿一致。

**`.msg.card-msg` 必须覆盖 `.msg.self`**：两者特异性相同（都是两个类），
靠"写在后面"生效。卡片是给全群看的功能块，自己发的那张靠右对齐会很怪。

**卡片高度硬上限 320rpx**。接龙说明允许写 500 字，不设上限的话一张卡能吃掉整屏，
把前后三条消息全顶出去。超出的内容进详情页。

---

## 9.48 配置页不放解释小字：把"为什么"留给文档

**现象**：接龙 / 抽签的发起页第一版，几乎每个控件下面都挂一句 21rpx 的灰字：
「不设上限就一直能接。设了之后卡片上会显示进度条，满了自动停止。」
「开着的话，点过的人不会在下一轮再被抽到，直到名单抽完。关掉就是每次都从全名单里抽……」

用户反馈：**"太多小文字解释了，界面节俭一点。"**

**错在哪**：写页面的时候怕用户不懂，就把设计文档里的"为什么这么设计"整句搬上了界面。
但这两类读者的处境完全不同 ——

| | 设计文档的读者 | 配置页的读者 |
|---|---|---|
| 目的 | 判断这个方案对不对 | 两分钟内填完发出去 |
| 会读长句吗 | 会 | 不会，会直接跳过 |
| 跳过后有损失吗 | 有 | 没有，所以跳过是对的 |

**判断一条说明该不该留，只看一个问题：删掉它，用户会不会填错？**

按这个标准重新过一遍：

| 原文 | 处置 | 理由 |
|---|---|---|
| 人数上限下的两句 | **删** | 开关 + "人数上限"四个字已经说完了 |
| 截止时间下的两句 | **删** | 同上；"到点自动截止"是预期行为，不是需要学习的知识 |
| 允许改自己的（解释） | **删** | 标签本身就在回答"这是什么" |
| 五种格式各一句 desc | **合成一句** | 见下 |
| 结果可见性两行解释 | **换成开关** | "结果群内公开"一个标签就够了 |
| 底部黄色 tip 块 | **整块删** | 讲的是"接龙不给每人发消息"，纯实现细节 |
| 名单摘要两行长句 | **压成一行** | `57 人 · 4 组，每组 14~15 人` |

**填写格式这一块是重点**：原来是 2 列卡片、5 张、每张名字下面一句 desc，
一共 5 句小字、占 3 行高度。改成 **5 个等分 chip 一行排开 + 下面一行随选中变化的说明**：

```html
<view class="formats">                     <!-- flex, gap:10rpx -->
  <view class="format" :class="{on:...}">仅序号</view>   <!-- flex:1 -->
  ...
</view>
<text class="fmt-note">{{ fmtNote }}</text>
```

```js
const NOTES = {
  1: '点一下就算接上，不用填任何东西',
  2: '填一个数字，比如份数',
  3: '填手机号',
  4: '填一小段文字，比如姓名',
  5: '从你设的选项里选一个'
}
```

文字总量 5 句 → 1 句，高度 3 行 → 1 行，而且**说明和当前选择始终对得上**
（原来 5 句全在，用户要自己找哪句跟自己有关）。

chip 用 `flex: 1` 平分，不写 `calc(20% - 8rpx)` —— 理由同 §9.34，`calc` 里的
百分比在部分真机上会被丢掉，整行会变成一个。

**两页合计少了约 500rpx，差不多半屏。** 少掉的高度全部是"读了也不会改变操作"的文字。

**留下来的小字只有三种**，都符合"不读会填错"：

1. **校验反馈** ——「单选至少要 2 个选项」，在点发布时 toast，不常驻
2. **空状态** ——「这个班还没有在读学生」，只在真的空的时候出现
3. **实时结果** ——「57 人 · 4 组，每组 14~15 人」，是用户改步进器时要看的东西

反过来说：**一条说明如果只在第一次有用，它就不该常驻。** 常驻的说明会被读成
背景噪音，连带把真正需要看的那条（比如校验错误）也一起淹掉。

---

## 9.49 通知不落库：用业务状态当通知状态

首页原来有个「每日金句」框（`耐心是教育的底色。`）。金句读一次就够了，
后来换成了通知框，尺寸一模一样 —— 换内容不换高度，下面的四宫格位置不动。

**通知是每次现算的，没有 `t_notice` 表。**

```
leave     t_leave_request  status=0（待审批）        createdAt 倒序
todo      t_todo  done=0 且 due_at <= 今天 23:59     dueAt 升序
birthday  t_student 月日=今天 且 status=1            姓名
```

**为什么不建表**：建表就必然要带「已读/未读」两个字段，而"已读"是一条
需要人主动点击才成立的状态。老师批完假之后，那条通知本来就不该再出现 ——
用业务状态当通知状态，处理完自动消失，不需要一张会和真实业务脱节的消息表。
代价是没法做"历史通知"，但首页这个框要的是"现在要你处理什么"，
不是"过去发生过什么"。

**`total` 和 `list` 分开**：`list` 最多 `limit` 条（默认 20），`total` 是真实条数。
首页那个框只显示第一条 + 「N 条」，N 必须用 `total` —— 用 `list.length`
的话，21 条通知会显示成「20 条」。

**时间文案在服务端拼**（`刚刚` / `12 分钟前` / `今天 08:12` / `昨天 21:30` / `11-03`）。
不是偷懒，是因为**未来的时间不能算"多久之前"**：待办 `due_at` 是今天晚上 18:00，
`ChronoUnit.MINUTES.between(t, now)` 会得到负数，直接显示成「刚刚」——
一条"今晚才到期"的待办写着"刚刚"，看起来像刚被人改过。
所以先判 `isAfter`，再走相对时间。

**一个诚实的说明**：`leave` 这一类现在几乎不会出现。老师代录的请假是
**已通过**（`status=1`），`status=0` 只有学生端提交才会有，而学生端还没有账号。
这条分支是为"学生自己提交申请"预留的，口径已经对齐，学生端上线就会自动出现。

---

## 9.50 白屏的两个原因：`classId=0`，和"整页只留一个出口"

**现象**：点首页「学生」，出来的页面一片空白。

**直接原因**：`classId=0` 被当成真实 id 发给了后端。

```js
// 首页
uni.navigateTo({ url: `/pages/teacher/student/index?classId=${currentClassId.value}` })
// 学生页
getStudentList({ classId: classId.value, status: -1 })
```

`currentClassId` 在冷启动、清缓存、或者老师还没建班时是 `0`。而后端：

```java
// ClassService.resolveClass
if (classId == null) return myClass(userId);   // null 才是"用第一个班"
ClassInfo clazz = classMapper.selectById(classId);  // 0 → null
if (clazz == null) throw new BizException(1004, "班级不存在或已归档");
```

`0` 不是 `null`，所以 `selectById(0)` 查不到，直接 1004。
**接口文档里写着"不传 = 第一个班"，但前端传的是 `classId=0`，不是不传。**

**为什么最后是"白屏"而不是"错误提示"**：学生页原来是这么写的 ——

```html
<template v-if="!pageFailed"> 头部 / 搜索 / 筛选 </template>
<view v-if="loading"> 骨架屏 </view>
<load-error v-else-if="pageFailed" ... />
<view v-else-if="filtered.length"> 列表 </view>
<view v-else class="empty"> 空状态 </view>
```

请求一失败：头部被藏掉，骨架屏不显示，`v-else-if` 链被 `load-error` 占住，
空状态也不显示。**整页只剩一个出口**。那个出口只要没渲染出来
（组件没加载、或者样式把它顶出可视区），剩下的就是一张纯背景色的白纸。

**两个修复**

1. **`classId` 为 0 时不拼参数**，学生页也不发 `classId=0`：

```js
const cid = currentClassId.value
uni.navigateTo({ url: cid ? `${s.to}?classId=${cid}` : s.to })

// 学生页
const params = { status: -1 }
if (classId.value > 0) params.classId = classId.value
```

2. **头部常驻，失败时数字换成「—」**，不再整块藏：

```js
const countText = (n) => (pageFailed.value || loading.value ? '—' : n)
```

「—」和「0」必须分开：`0` 是"这个班没有人"，`—` 是"没拿到"。
原来那个 `v-if` 就是为了避免显示假 0，但代价是白屏 —— 换成「—」两个问题一起解决。

**教训**：**错误态应该是"局部替换"，不是"整页替换"。**
凡是写出"失败时把上面全藏掉，只留错误块"的地方，都等于把白屏风险
集中到那一个组件上。而白屏是最难排查的故障 —— 用户描述不出任何信息，
开发看不到任何文字。


## 9.51 底部导航加「乐迪」：占位页要克制

底部从 4 个 tab 变成 5 个，顺序固定为 **首页 / 通讯录 / 乐迪 / 请假 / 个人中心**。
乐迪排在通讯录和请假之间 —— 位置本身就是定位说明（左边是人，右边是事，中间是入口），
不需要额外文案去解释它是干什么的。

**占位页只写两行字，不摆按钮。** 这一版没有任何接口调用，页面里连
`onLoad` / `onShow` 都不写 —— 没有数据要刷新，加了就是每次切 tab 白跑一次逻辑。
最容易犯的错是"占位页给一排灰按钮"，点下去没反应比一片空白更让人怀疑是不是坏了。
所以这里只有头像 + 名字 + 一句「还在准备中」。

**tabBar 图标是脚本生成的，不是设计给的。** 现有图标都是 81×81、单色 + alpha
（未选中 `#8A8178`，选中 `#C5342B`），所以新图标也按这个规格用纯 Python 生成
（4 倍超采样做抗锯齿，`zlib` 直接写 PNG，不依赖 Pillow）。造型是方头 + 天线 +
两只眼，和页面里那个 CSS 画的大头像同一套比例 —— 图标和页面头像必须长得像同一个人。

**顺手修掉「班主任是你」。** 通讯录班级卡片副标题原来写死
`24计算机软通1班 · 未填年级 · 班主任是你`，一行字全在说"你"，
反而看不出这个班是谁的。改成读本地缓存的 `userInfo.name`：
`未填年级 · 班主任 李光`。取不到名字就只写「班主任」——
登录时自动建的账号 `name` 是空串，硬拼会变成「班主任 」或者「未设置」。

## 9.52 乐迪：为什么用「工具调用」而不是把数据塞进提示词

第一种做法很诱人：进页面时把全班名单、成绩、请假、待办一次性拼进 system 提示词，
模型想怎么答就怎么答。57 个学生 × 2 次考试 × 3 科 ≈ 三万多个 token，
**每问一句都要重付一次**，而且数据一变就得重拼。更要命的是：数据一多，
模型开始"记混"——把张同学的分安到李同学头上，而且它自己不知道记错了。

所以走 function calling：提示词里只有「你是谁、规则是什么、你能查什么」，
具体数字现查现给。好处是三重的：

1. **token 只在需要时花**。问"你好"一个工具都不调，几十个 token 就答完了。
2. **数字不会串**。每个数字都来自一次明确的查询结果，模型只负责组织和措辞。
3. **权限有边界**。模型能传 `studentName`，但**班 id 永远是后端注入的**。
   这是这条链路最要紧的一点：提示词可以被诱导，"查哪个班"却不能由模型说了算。

**工具出错不抛异常，返回 `{"error": ...}`。** 抛出去整轮对话就 500 了；
而模型看着错误信息，完全可以回一句「本班没有叫某某的学生」——这才是老师想看的。
异常只留给"整个链路都废了"的情况（没配 key、DeepSeek 挂了）。

**对话不落库。** 存了就要建表、要清理、要处理"换手机看不到历史"，
还会产生一个更麻烦的东西：**AI 记住了错误信息，之后一直拿它当事实**。
这一版把历史交给前端带（后端只取最近 8 条），页面关掉就结束。
代价是"问完就忘"，但这恰好符合"查一下就走"的使用方式。

**为什么先不做流式**：小程序端要 `enableChunked` + 手写 SSE 解析 + 处理分片粘包，
而答案只有 6 行，等 2~4 秒完全可以接受。第一版用普通 JSON 接口，
后端一个 `@PostMapping`，前端一个 `request`，出问题也好查。

**一个真踩到的坑：Spring Boot 4 换成了 Jackson 3。**
`com.fasterxml.jackson.databind.ObjectMapper` 这个包**已经不存在了**，
编译报「程序包不存在」，但 `com.fasterxml.jackson.annotation.JsonFormat`
还在（注解包沿用了旧包名）。正确写法是：

```java
import tools.jackson.databind.ObjectMapper;   // Jackson 3
```

顺带一个决定：**不注入 Spring 那个 ObjectMapper**。这里只用来解析模型给的 JSON 参数，
不需要任何全局配置，静态实例就够 —— 少一个构造参数，也不用担心 boot 升级后 bean 类型对不上。

**前端超时要单独放宽。** `request.js` 默认 10 秒，对查库接口是合理的（超过就是出问题），
但 AI 问答后端要串 1~3 次上游请求，10 秒会切在"其实答出来了"的中间。
所以 `askAidi` 传 60 秒，其他接口不动。

## 9.53 乐迪能动手了：为什么是「两段式确认」而不是「一句话直接改」

v0.30 的乐迪只会查。这一版它能加待办、批请假、群发消息、发起接龙抽签、录成绩。

**一开始想的是「模型直接调工具落库」，越想越不对。** 查错了只是看到一个错数字，
改错了是真的把数据改了，而审批请假、群发消息**都没有撤销按钮**——消息发到群里，
撤回是另一件事，而且家长已经看到了。所以走两段：

```
老师说话 → 模型调写工具 → 后端 preview()：只校验、只拼卡片，不写库
                          ↓
                     前端弹确认卡
                          ↓
              老师点按钮 → /aidi/act → execute()：真的写
```

这不是"多一步更安全"的空话，它把两件不同性质的事拆开了：
**提议是模糊的（自然语言），执行是确定的（参数已解析好）**。
模糊的那一步交给模型，确定的那一步交给代码。

几个由此定下来的规则：

**`/aidi/act` 不经过模型。** 参数已经解析好了，再让模型看一眼没有任何好处，
只会多一个出错的机会和一个额外的延迟。所以这个接口里没有一次 HTTP 出去，
几百毫秒就返回，也就敢给它 20 秒的超时（其实根本用不到）。

**`act` 不检查 API Key。** 这条是特意写的：`chat` 没 key 就 5001，但 `act` 照跑。
余额用光的那天，老师屏幕上已经弹出来的那张确认卡，应该能点完。
"因为欠费所以这条待办完成不了"是个很蠢的故障。

**一次只允许一张确认卡。** 模型一轮里调两个写工具时，第二个返回
`{"status":"already_pending"}`。两张卡一起弹，老师很容易点错那张，
而且"确认"这个词就对不上号了。同理，有待确认动作时**只给模型读工具**，
它没法再提一个新动作。

**前端能改 params，所以后端必须重查。** 卡片里的 `_studentId` / `_examId` /
`_todoId` / `_leaveId` 是后端解析出来的，但它们在请求体里裸奔——改一个数字
就能操作别的班的数据。所以 `execute()` 里每一个 `_` 开头的 id 都按 `classId`
重新查一遍归属，查不到就报 3002 / 1004。这条和"班 id 由后端注入"是同一个思路：
**能改的东西就不能信**。

**模型调用写工具时不写库，校验失败也不抛异常。** `preview()` 里"找不到这个学生"
抛的是 `BizException`，但 `AidiServiceImpl` 把它转成工具结果
`{"error":"班里没查到叫「李小明」的学生"}` 喂回模型。抛出去整轮就 500 了，
而模型看着这句话完全可以回一句"你确认下名字？"——实测它就是这么回的。

**`save_scores` 不另写一套校验。** 直接调 `scoreService.batchSave()`。
科目是不是这次考试的、分数超没超满分、考试是不是本班的，这些规则在成绩模块
已经写过一遍了。多一个入口就多一套判断，两套判断迟早会不一致。

**提示词里补了一句"说「我要给…」而不是「已经给你…」"。**
第一版实测，模型在卡片还没点的时候就说"已经给你建好待办了"。
老师不点确认就直接退出，会以为事情办完了。这种"话说早了"比答错更难发现，
因为界面上看起来一切正常。

**已执行的卡片结果要并进历史。** 卡片本身不是消息，下一轮带历史时如果不处理，
老师接着问"刚才那个录好了吗"，模型完全不知道自己做过什么。做法是把执行结果
拼在那条 assistant 消息后面（`content + '\n' + doneText`），而不是单开一条——
避免出现连续两条 assistant。

**取消不发请求。** 没落库就没有要撤销的东西，前端标个状态就够了。
这一点很重要：如果"取消"也要发请求，那"取消失败"就成了一个新的错误态，
而它本来不该存在。

## 9.54 全库没有外键，所以孤儿数据能活一个月没人发现

这个项目从第一张表开始就没建外键——不是漏了，是故意的：软删除（`status=0`）、
班级归档、学生离班都要求"父行还在但不再活跃"，外键的 RESTRICT/CASCADE 两种行为
都不合适，SET NULL 又会把"这个学生离班了"和"这个学生不存在"混成一件事。

代价是**引用完整性全靠应用层自觉**。这次就踩到了：

种子数据按一份 47 人的旧名单生成了 `t_score`(282 行) / `t_leave_request`(4 行) /
`t_seat`(47 行)，`student_id` 是 1~47；后来真实名单导入，一班学生变成 id 121~177。
三张表没跟着改，于是**所有学生名显示成「未知」**，乐迪也只能回"没有记录"。

这个 bug 活了很久才被发现，因为界面上的表现太像"这个学生没成绩"了。
真正把它翻出来的是乐迪——它按姓名查不到人时会老老实实说"班里没叫这个名字的"，
而人眼看到"未知"只会以为数据还没录。

**教训不是"应该加外键"**（加了就得为软删除另设计一套），而是：
引用完整性必须有别的机制兜底。两个成本极低的办法：

1. **改完种子 / 导入数据后跑一次孤儿检查**（10 秒，比查一天便宜）：

```sql
SELECT 't_score' t, COUNT(*) n FROM t_score s
  LEFT JOIN t_student st ON st.id = s.student_id WHERE st.id IS NULL
UNION ALL SELECT 't_leave_request', COUNT(*) FROM t_leave_request s
  LEFT JOIN t_student st ON st.id = s.student_id WHERE st.id IS NULL
UNION ALL SELECT 't_seat', COUNT(*) FROM t_seat s
  LEFT JOIN t_student st ON st.id = s.student_id WHERE st.id IS NULL AND s.student_id IS NOT NULL
UNION ALL SELECT 't_relay_entry', COUNT(*) FROM t_relay_entry s
  LEFT JOIN t_student st ON st.id = s.student_id WHERE st.id IS NULL;
```

2. **JOIN 不到时不要静默显示"未知"**。`未知` 这个字符串太像正常业务状态了。
   要么显示"数据异常"，要么至少在日志里打一条 WARN。静默兜底等于把 bug 藏起来。

修的时候也只能位置映射（第 k 个幽灵 → 名单第 k 人）：成绩行里只有科目和分数，
没有任何能反查身份的字段，原始对应关系不可恢复。
脚本在 `database/migrations/10_remap_orphan_student_id.sql`，
改之前的备份在 `database/backups/2026-11-18_before_remap.sql`。

顺带一个现象：映射后"全班 47 人"而不是 57 人。这不是 bug——排名只统计有成绩的人，
另外 10 个是没参加上次考试的插班生。**统计口径要按"有数据的人"算，
不要按"班里有几个人"算**，否则平均分会被没参加考试的人拉低。

## 9.55 记忆分两层：对话存前端，事实存库

一开始乐迪什么都不记：历史由前端带着走，后端只取最近 8 条，页面关掉就结束。
老师问「它是不是只有短期记忆」，答案是"是，而且只有 4 轮"。
补的时候没有笼统地"加个记忆"，而是先拆成两件事：

| | 存在哪 | 例子 | 失效方式 |
|---|---|---|---|
| 对话 | 前端 storage，40 条 / 7 天 | 「他哪科最弱」里的「他」 | 过期、换班、点「新对话」 |
| 事实 | `t_aidi_memo` | 「周三下午没课」 | 老师说"忘掉"，或一直留着 |

**这两件事的差别不是"长短"，是性质。** 对话是过程，事实是结论。
把过程存成资产，代价是模型会把上周的答案当事实端出来——学生都转走了它还在念旧数据。
而事实层没有这个问题：记的是"规则"，不是"某次查到的数字"。

几个具体决定：

**记忆拼进提示词，不做成 `recall` 工具。** 这是最关键的一条。
做成工具的话，模型得先意识到"这件事我可能记过"才会去查——它往往意识不到，
表现就是"明明记过却说不知道"。拼进提示词则是无条件的：每轮都在上下文里，
模型想不看见都难。代价是 token，30 条 × 40 字 ≈ 1200 字，可以接受。

**记忆不走确认卡。** 上一节刚立了"所有写操作两段式"的规矩，这里就得说清界线：
确认卡是为**不可撤销**设计的（群发消息发出去收不回），记忆是助手自己的笔记，
只影响它怎么说话，一句话就能忘掉。让老师为了"记住周三没课"再点一次确认，
属于把安全做成负担。界线是：**改业务数据要确认，改助手自己的笔记不用。**

**`forget` 命中多条必须反问。** 老师记了「班费由班长收」和「班费每月 50 块」，
说"忘掉班费那条"——如果按 `LIKE` 取第一条删掉，老师根本不会发现，
下次助手的行为就不对了。**忘错了是静默错误**，比记错了更难查。
所以 `forget` 返回 `{"status":"ambiguous","candidates":[...]}`，让模型列出来问。

**上限 30 条，超了拒绝，不自动淘汰。** 悄悄丢掉一条老师以为还记着的规矩，
比直接说"记满了，先清理几条"糟得多。存储空间不值钱，信任值钱。

**`class_id=0` 是通用层。** 一个老师带两个班，「我不喜欢被叫老师」不该记两遍，
「周三下午没课」只对其中一个班成立。查询条件是
`class_id = 当前班 OR class_id = 0`，本班排前面。实测：一班看到 3 条，二班只看到通用那 1 条。

**前端存档不存 `action`。** 这条是安全考虑不是洁癖：确认卡跨会话复活，
等于让三天前那张「群发消息」的卡还能点。已执行的结果存在 `doneText` 里，
历史照样接得上——**能接上的是"做过什么"，不能接上的是"还没做什么"。**

**存档必须配一个「新对话」。** 只加存档不加清空，旧对话会一直赖在页面上删不掉。
顶部那条细栏顺手还解决了另一个问题：老师切了班以后，得看得见"这段对话是哪个班的"。

## 9.56 学生端：学生不是「用户」，鉴权按路径不按 id

老师要能私信学生，可学生从来没登录过——小程序一直只有老师端。
补学生端时有两个岔路口，走错了后面很难改回来。

### 一、学生不进 `t_user`

最省事的做法是往 `t_user` 里塞学生，加个 `role` 字段区分。没这么做，因为这两张表
描述的根本不是同一件事：

| | `t_user` | `t_student` |
|---|---|---|
| 是什么 | 一个**登录账号** | 名册上的**一个人** |
| 关键字段 | openid、昵称、头像 | 学号、姓名、班级 |
| 怎么来的 | 微信授权后自动创建 | 老师导入 xlsx，一次 69 行 |
| 一个班有几个 | 1（班主任） | 47 |

塞在一起的代价不是"多一个字段"，是**老师端的每一条查询都要记得加 `AND role = 1`**。
漏一处，学生就出现在通讯录里、被算进班级人数、甚至出现在成绩排名里。
这种"靠每个查询自觉过滤"的约束，在一个已经有 20 多个接口的项目里迟早会漏。

而且学生的登录语义就不一样：他不是"注册账号"，是**用邀请码认领名册上的一行**。
账号需要创建、密码、找回密码、注销；名册行本来就在那儿，学生只需要证明"我是这行"。
所以第一版**没有学生注册接口**——`POST /api/auth/student-login` 一次完成
"核对邀请码 → 核对学号（可选核对姓名）→ 签发 role=2 的 token"。

代价也得写清楚，别只讲好处：

- 学生没有密码，所以**改不了密码**，也**不能踢掉某台设备**（只能等 7 天 token 过期）
- 换班要**重新用新班的邀请码登录**，旧 token 里的 classId 会失效
- 学生能看到的字段受 `StudentInfoVO` 控制，别图省事直接返回 `Student` 实体

### 二、鉴权必须按路径分权，不能只解析 id

原来的 `AuthInterceptor` 只做一件事：验签、把 `sub` 塞进 `request.getAttribute("userId")`。
学生 token 的 `sub` 是 **studentId（121）**。如果不加判断，这个 121 会被老师端接口
当成 `userId` 拿去查 `t_user`——查不到就返回空数据，看起来像"这个老师没有班级"，
而不是"你没权限"。**又是静默错误**，跟 9.54 那个「未知」是同一类毛病。

所以改成按路径分权：

| 路径 | 要求 | 塞进 request 的属性 | 不满足 |
|---|---|---|---|
| `/api/teacher/**` | `role = 1` | `userId` | 3001 这是教师端的功能 |
| `/api/student/**` | `role = 2` | `studentId`、`classId` | 1002 请先用学号登录学生端 |
| `/api/auth/**` | 免登录 | — | — |

几条具体理由：

**为什么不在每个 controller 里判。** 漏一个就是一个洞，而拦截器是唯一入口。
默认拒绝永远比默认放行好——新加接口时忘了配路径，它会被拦下，而不是裸奔。

**id 撞车不是假设。** `t_user` 现在只有 1 条（id=1），`t_student` 是 121~177，
暂时撞不上。但这是**数据量的巧合，不是设计保证**：两条 id 序列各自自增，
数据库层面没有任何约束阻止它们重叠。真要撞上，就是学生 A 用老师 B 的身份读数据。

**classId 只能从 token 取。** 学生端的 `GET/POST /api/student/message` 请求体里
根本没有 `classId` 和 `studentId` 这两个字段——不是"前端不传"，是 DTO 里就没有。
学生改不了自己的班，这一点不该靠前端自觉。

**学生转班后旧 token 要作废。** `requireStudentInClass` 会拿 token 里的 classId
和库里那行学生实际的 classId 比一次，不一致就报"登录信息已过期"。
光验签不够——签名是对的，但人已经不是那个班的人了。

### 三、私信线程不建会话表

`t_direct_message` 一行一条消息，线程键直接是 `(class_id, student_id)`。
常见做法是 `conversations` + `messages` 两张表，这里不需要，因为这是一对一、
双方固定（一个学生 ↔ 他的班主任）、没有群、也没有"谁先发起"这个概念。

省掉的正好是最烦的那部分：**建线程的时机**。老师先发还是学生先发？
打开页面没说话要不要建空线程？一张表里根本没有"空线程"这个状态。

索引就两条，各自对应一个查询：

- `idx_thread (class_id, student_id, id)` —— 取某人的对话
- `idx_unread (student_id, sender_role, read_at)` —— 学生端/老师端的未读红点

**已读用 `read_at` 单列，不用 `is_read` 布尔。** 两个好处：知道"什么时候读的"；
`markRead` 写成 `UPDATE ... WHERE read_at IS NULL`，天然幂等，重复调用不覆盖首次时间。

**取最近 100 条要 `orderByDesc` 再 reverse。** 直接 `ORDER BY id ASC LIMIT 100`
拿到的是**最早**的 100 条，对话一长就永远卡在开头、看不到新消息。
这个错误在测试里很容易漏——消息少的时候两种写法结果完全一样。

### 四、学生端第一版只做三件事

登录 / 首页（我的班级 + 待办）/ 私信。**故意没做成绩、座位、请假。**

理由是先打通"老师能联系上学生"这条链路，这是老师提的需求本身。
剩下的都是查询页，数据接口现成的，加页面就行。反过来如果先做成绩，
学生登录了却回不了老师一句话，这个功能等于没上。

首页入口在 `pages/index/index.vue` 按 `userRole` 分流：老师 token 误点学生入口
会提示重新登录，而不是拿着 role=1 的 token 去调 `/api/student/**` 撞 1002。

### 五、两套产物目录，改完"没生效"十有八九是这里

uni-app 的 dev 和 build 输出到**两个不同目录**，而微信开发者工具只认其中一个：

| 命令 | 输出目录 |
|---|---|
| `npm run dev:mp-weixin` | `dist/dev/mp-weixin` |
| `npm run build:mp-weixin` | `dist/build/mp-weixin` |

踩过的一次：只跑了 `build`，但开发者工具里开的是 `dist/dev`，于是新加的按钮
怎么都不出现——代码是对的、产物也是对的，就是**不是同一个文件夹**。
更迷惑的是开发者工具会往它打开的那个 `project.config.json` 里回写
`isGameTourist` 之类的字段，看 mtime 还以为是刚构建的。

判断方法：比对 `dist/{dev,build}/mp-weixin/project.config.json` 里的 `appid`
和 `src/manifest.json` 的 `mp-weixin.appid` 是否一致，以及直接
`grep 按钮文字 dist/*/mp-weixin/pages/.../xxx.wxml` 看编译产物里到底有没有。

根治办法是让两边都保持最新（本项目已这么做）：

```bash
npm run build:mp-weixin                                  # → dist/build
UNI_OUTPUT_DIR=dist/dev/mp-weixin npm run build:mp-weixin  # → dist/dev
```

`UNI_OUTPUT_DIR` 是 uni-app 认的环境变量，可以把一次生产构建直接写到 dev 目录，
省掉 `dev` 那个永不退出的 watch 进程——**watch 模式在脚本里会一直挂着，
不能当成"构建一次"来用**。

## 9.57 私信会话列表：聚合放数据库，别放内存

老师发完私信后提了个很实际的问题：**"通讯录里应该有我和他的聊天框，不然每次都得去找。"**
这是 IM 的基本盘——没有会话列表，一对一的私信就只能靠"记得从哪个学生点进去"。

### 一、会话列表怎么算

最省事的写法是"取最近 200 条消息，在 Java 里按 studentId 去重取第一条"。
这个写法有个隐蔽的失效场景：

> 某个学生一个月前跟你聊过，之后没再说话。
> 列表里 200 条消息全是别人的 → **他消失了**。

而他恰恰是最该被看到的那一个（可能就是那次没回的那条）。所以聚合必须交给数据库：

```sql
SELECT student_id, MAX(id) AS lastId,
       SUM(CASE WHEN sender_role = 2 AND read_at IS NULL THEN 1 ELSE 0 END) AS unread
  FROM t_direct_message
 WHERE class_id IN (...)
 GROUP BY student_id
 ORDER BY MAX(id) DESC
 LIMIT 20
```

`SUM(CASE WHEN ...)` 顺带把未读数一起算了。不分开查的理由不只是省一次往返——
分两次查会出现"列表显示 3 条未读、点进去只有 2 条"这种对不上的情况，
因为两次查询之间学生可能刚好又发了一条。

回表两步：`selectBatchIds(lastIds)` 取最后一条内容，`selectBatchIds(studentIds)` 取姓名。
**不要逐行查**，20 行就是 40 次往返。

### 二、几个边界

**跨全部班级，不按班分组。** 老师带两个班时，一班和二班的学生在同一个列表里，
按最近说话时间排。老师关心的是"谁刚找过我"，不是"这是哪个班的"。

**已离班的学生不出现。** 历史消息还在库里，但点进去发不出去（`requireStudent` 会 4004）。
列出来等于给一个假入口。这和「私信按钮只在 status=1 时显示」是同一条规矩。

**接口挂在 `/api/teacher/message` 根路径，不是 `/list`。** 因为下面有 `/{studentId}`，
任何字面量段都会被当成 studentId 试一次。Spring 的路径匹配确实让字面量优先，
但**让路径形状本身不产生歧义**比依赖匹配优先级更稳。

**放在通讯录顶部，不新开一个 tab。** tabBar 已经 5 个了，再加一个会把主操作挤小；
而"找一个人"这件事本来就该在通讯录里完成。

### 三、两个交互上的决定

**导航栏标题改成对方姓名。** 原来 `pages.json` 里写死「私信」，页内再画一行学生名——
从通讯录连着点进好几个学生时，导航栏一直写「私信」，**分不清在跟谁说话**。
现在导航栏是姓名，页内那行只留学号（学生侧留班级名），不再重复。

**新会话不画空状态。** 原来没聊过时显示一个信封图标 + 「还没跟这位学生说过话」+
「这里只有你和学生能看见」。老师的评价是"这个没必要"，对的：
这两句话没有一个字能帮他发出第一条消息，却把输入框往下推了 240rpx。
**空白聊天页是 IM 的正常形态**——微信新会话就是这样，没人觉得它坏了。

顺手记一条：`pages.json` 里的 `navigationBarTitleText` 是**静态默认值**，
凡是标题要跟着数据变的页面，都得在 `onLoad` / 数据回来之后
`uni.setNavigationBarTitle()` 覆盖一次，并且要判空——标题拿到空串会显示一片空白。

---

## 9.58 学生端：同一批数据，换个视角，别换一套表

学生端的六个模块（首页 / 请假 / 成绩 / 座位 / 通知 / 资料）**没有新建任何业务表**。
数据全来自老师端已经在用的那几张：`t_leave_request`、`t_score`、`t_seat`、`t_student`。

这不是偷懒。学生看的本来就是同一批数据，只是视角从「全班」缩到「我自己」。
另建一套表意味着同一个事实存两份，早晚会不一致——而这类不一致最难查，
因为两边各自都"对"。

### 边界画在方法签名上，不画在 if 里

`StudentDataService` 里每个方法的参数都是 `(classId, studentId)`，**没有一个方法
收「要查哪个学生」**。如果收，就得在每个方法里再写一遍「这个学生是不是你」，
写漏一个就是越权。让越权在编译期就写不出来，比靠 review 拦住可靠。

配套的是 `requireStudent(classId, studentId)`：每个入口先过一遍，
查库确认「存在 + 在读 + 属于 token 里那个班」。**每次查库不是浪费**——
token 里的 classId 是登录那一刻的快照，学生换班后旧 token 还带着旧班级。

### 老师端 → 学生端，转的不是页面是「视角」

| 老师端 | 学生端 | 真正变了的东西 |
|---|---|---|
| 请假审批列表（待审批 / 已处理） | 我的请假（待审批 / 已通过 / 已结束） | 从「谁要请假」变成「我的到哪一步了」 |
| 通过 / 驳回 + 审批备注 | 撤销申请 / 销假 + 看审批备注 | 动作反了，备注从"我写"变成"我读" |
| 代请假（选学生） | 申请请假（不选人） | **选人这个控件消失了**——学生只能替自己请 |
| 成绩表（考试 × 全班） | 我的成绩（我 × 历次考试） | 横表转置成竖列；排名从"算给老师看"变成"只给我看名次" |
| 座位图（整张） | 我的座位（一格） | 全班布局是老师的工具，不是学生的信息 |
| 通知（要你处理的事） | 我的通知（已有结果的事） | 方向反过来：从"待办"到"结果" |
| 学生管理（增删改） | 我的资料（改三个字段） | 从"管全班"到"管自己"，且是白名单 |

### 三个具体决定

**1. 撤销 = 真删行，不是加 status=4。**
只有 `status=0` 能撤，这时老师还没看过，删掉不丢任何审批信息。
加 4 的话老师端的状态文案表里没有 4，状态列会变空白——那就得改老师端，
而这次明确不动老师端。以后要做撤回留痕，正确做法是加 4 **并同时改两端文案**。

**2. `canCancel` / `canReport` 由后端算。**
前端如果自己写 `status === 0`，规则一改两边就不同步，而且这种不同步只会以
「按钮能点、点了报错」的形式暴露——用户看到的是 bug，不是规则变更。

**3. 排名后端算，前端只拿自己的名次。**
前端要算就得拿到全班每个人的总分，那等于把全班成绩发给每个学生，
「只看自己」就成了空话。后端返回 `rank` + `classSize` + `classAvg` 三个数，
信息量够学生判断自己什么水平，又不多给一个人的分数。

### 坑：`updateById` 写不进 null

「清空手机号」这个操作，第一版用 `studentMapper.updateById(s)`，`s.setPhone(null)`。
结果：前端显示已清空，刷新一下号码又回来了。

原因：MyBatis-Plus 的 `updateById` 默认 **NOT_NULL 策略**，null 字段直接不参与 SQL。
所以「清空」这种操作在 `updateById` 下**根本不存在**。

正确写法是显式 `LambdaUpdateWrapper.set(field, null)`——`set` 会老老实实写 `= NULL`。
这个 bug 只有真的去清一次才会发现，光看代码觉得"赋值了嘛"。

**顺带一条前端规矩**：`null` = 不改，空串 = 清空。这两个必须能区分，
否则前端只改手机号时会把宿舍一起清掉。

### 学生首页为什么不用自定义导航栏

老师端首页是 `navigationStyle: custom`，因为它**是 tabBar 页**，没有返回键的需求。
学生首页是从入口页 `navigateTo` 进来的，照抄 `custom` 会把返回键一起干掉，
学生只能靠「退出身份」回入口——那太绕了。

**版式可以抄，导航模式不能抄**，两者的前提不一样。

### 学生端底栏：为什么是自定义组件，不是 tabBar

小程序**只有一个 tabBar**（`app.json` 里一份 `list`），老师端已经占满了那 5 个位置。
学生端不可能再注册一套 —— 硬塞会把老师的页面塞进学生底栏。

所以做成 `components/student-tab-bar/`：固定定位、五个条目、参数逐项对齐老师端原生底栏
（高度 100rpx / 图标 48rpx / 文字 20rpx / `#8A8178` / 选中 `#C5342B` / 白底 + 1rpx 顶线）。
两端放在一起看不出差别，**条目也一一对应**：

| 老师端 | 首页 · 通讯录 · 乐迪 · 请假 · 个人中心 |
|---|---|
| 学生端 | 首页 · 通讯录 · 乐迪 · 请假 · 我的 |

第一版学生端底栏放的是「首页 / 请假 / 成绩 / 座位 / 我的」，看起来"更学生"，
实际上犯了两个错：**成绩和座位在首页「常用」四格里已经有入口**，底栏再放一遍是重复；
而且底栏的五个位置应该留给**每天都要点**的东西，成绩和座位是"考完试 / 开学时点一次"。
换成通讯录和乐迪之后，学生从任何一个页面想去哪儿，肌肉记忆和老师端完全一致。

**私信和通知没进底栏**，这一点是照抄老师端的：老师端的「通知」也不在底栏里，
它从首页那条提醒条点进去。学生端也一样 —— 首页那条「暂时没有新通知」本身就是入口，
不必再另做一个「我的通知」长条。私信则整个挪进**通讯录**（班主任那一栏右侧一个「私信」胶囊），
因为通讯录才是"找某个人"的地方。**底栏只放"栏目"，不放"动作"。**

**退出登录放「我的」最下面，不放首页。** 首页是每天看数据的地方，放一个"走人"的按钮
只会被误点；账号相关操作归「我的」。第一版学生首页底部有个「退出学生身份」，
用户直接说"这个不需要"，是对的。

**跳转必须用 `redirectTo`，不能用 `navigateTo`。** 底栏是平级切换，
用 navigateTo 会一层层堆栈，点五次底栏要按五次返回。

更隐蔽的一个坑：**首页的快捷入口和底栏必须用同一种跳转**。
如果首页用 navigateTo 进成绩、底栏用 redirectTo 回首页，栈会变成 `[首页, 首页]` ——
按返回退到一个一模一样的首页。老师端首页那份 `TAB_PAGES` 名单就是为这个存在的，
学生端首页照抄了一份。

**不在底栏的页面，绝不能挂底栏组件。** 这是改底栏条目时踩到的：成绩页和座位页
第一版是底栏页（`current="score"` / `current="seat"`），v0.37 把它们挪出底栏、
改成首页「常用」navigateTo 进去的下一级页面之后，页面里的 `<student-tab-bar>` 忘了删。
后果比"多一条底栏"严重：点底栏首页会 `redirectTo` 把当前页换掉，栈变成
`[首页, 首页]`；而且 `current="score"` 在条目列表里已经不存在，**五个图标一个都不亮**。

所以底栏组件的挂载点必须和 `TAB_PAGES` 名单一一对应，改条目时两处一起改。
判断标准很简单：**这个页面是靠 redirectTo 切进来的（栏目），还是靠 navigateTo
钻进来的（下一级）？** 只有前者挂底栏。

### 学生登录为什么不要邀请码

第一版学生登录是「班级邀请码 + 学号」，抄的是老师端建班时生成 `invite_code` 的思路。
用户一句话点破了问题：**学生可能同时上好几门课**。

邀请码本质上是"指向一个班"的凭证。一门课一个班，三门课就是三个码；
把它当登录闸门，等于要求学生每换一门课就记一个新码 —— 而学生根本分不清
"我现在登的是哪门课的班"。更糟的是，学生手里的码通常是开学时老师口头说了一遍，
一个学期后没人记得。

所以现在的分工是：

| 场景 | 凭什么 |
|---|---|
| 登录学生端（看自己的请假/成绩/座位） | **学号**，不需要任何码 |
| 加入某个班级的群聊 | **邀请码**，因为它确实是"加入这个班"的动作 |

登录只认学号之后，新问题立刻出现：同一个学号可能在多个班里都有花名册行
（这正是"学生有多门课"在数据库里的样子）。处理方式是**不替他选**：

```
按 studentNo 查出所有 status=1 的行
  → 填了姓名就按姓名过滤（防手快输错学号登进别人账号）
  → 按 classId 去重（同班重复行只留一个）
  → 只剩一个：直接签发 token
  → 多个 + 请求带了 classId：命中就签发，没命中报 1004
  → 多个 + 没带 classId：返回 needClass=true + classes[]，token 为 null
```

**多班时绝不给一个默认班。** 进错班的学生看到的是别人的课表、别人的座位，
而且**不会报错** —— 他会以为数据错了，不会想到是班错了。让他在两个班名里点一下，
成本一次点击，收益是"永远进对"。

配套的前端纪律：**`token` 为 `null` 时绝不 `setToken`**。存了空 token，
后面所有业务接口都返回 401，学生看到的是「登录失败」而不是「请选班级」——
错误提示指错了方向，排查会绕远路。

顺带删掉了 `StudentPortalService.findByInviteCode()`。它没有调用方，
留着只会让下一个读代码的人以为"邀请码还能登录"。

### 通讯录 = "我能联系上的人"，不是花名册

第一版学生端通讯录把同班同学全列出来了（只读、不可点）。用户看了一眼就问：
**"这些同学都没加好友，怎么出现在通讯录里了，而且都点不了。"**

这句话里有两个独立的错误，都得改：

1. **语义错了。** 通讯录在任何人心里都是"我能联系上的人"。花名册是"这个班有哪些人"——
   那是班级管理，属于老师的工作台，不是社交。
2. **承诺错了。** 每一行都有头像、都在卡片里、都长得像能点的东西，点下去却没反应。
   一个点不动的东西比一个不存在的东西更糟：不存在的东西用户不会去点。

而这个系统**确实没有好友关系**：`t_direct_message` 的收件人永远是学生、发件人只有老师，
学生之间没有点对点的通道。既然没有通道，就不该出现在"通讯录"这个语义下面。

现在通讯录只有两块：**班主任**（能私信）和**班级群**（能跟全班说话）。
接口 `GET /api/student/classmates` 一起下架了 —— 没有页面用它的接口留着，
下一个人会以为"同学名单"是产品要的功能。

### 班级群为什么不复用老师端的群聊表

老师端已经有群聊了（`t_container_message`），学生端看起来"直接复用"最省事。不行，两个硬障碍：

**一、成员模型对不上。** 容器群的成员是 `t_container_member.user_id → t_user`。
学生**不是** `t_user` 行（§9.9.1 那条决定的直接后果），进不了成员表，`assertCanSpeak` 第一步就挂。

**二、id 会撞号。** `t_container_message.sender_id` 是个裸 `bigint`，
名字靠 `t_user` 反查。硬把 `studentId` 塞进去，`t_user.id=121` 和 `t_student.id=121`
会变成同一个人 —— 历史消息里会出现"学生说的话顶着老师的名字"，
而且是**静默错误**：不报错、不越权、就是名字不对。这种 bug 最难查。

所以新开 `t_class_message`，用两个设计把问题从根上掐掉：

| 设计 | 解决什么 |
|---|---|
| `sender_role`（1 老师 / 2 学生） | 两套 id 空间靠它区分，同一个 `sender_id` 数字在不同 role 下是不同的人 |
| `sender_name` 存快照 | 彻底不需要 join，也不怕改名/退班；聊天记录里的"谁说的"是当时的事实 |

**没有会话表、没有成员表。** 一个班就是一个群：群名取 `t_class.name`，
成员数取花名册行数，进群资格就是"你在花名册里"。再建 `t_class_group` /
`t_class_group_member` 只是把 `t_class` 和 `t_student` 复制一遍，
还得处理"转班了群成员怎么变"——一个本来不存在的问题。

代价是**老师暂时不在这个群里**。要接进来得改老师端（本次约定不动），
但表结构已经留好 `sender_role=1` 的位置，加一个写接口就能通。

### 乐迪"特别慢"怎么拆

用户报"问乐迪回复特别慢"。先量，别猜：本机实测 **0.8~2.6 秒**（简单问题 0.8，
查一次数据 1.7~2.1，查两次 1.9~2.6）。两跳 LLM 调用（决定查什么 → 拿数据回答）
就是这个量级，不是 bug。

但"不是 bug"不等于"不用改"。慢的感受有三层，得分别处理：

| 层 | 现象 | 处理 |
|---|---|---|
| 真实耗时 | 每多一轮工具调用，多一次完整 HTTP 往返 | `MAX_ROUNDS` 3→2；日志打一行 `1740ms，2 轮，1 次工具`，下次说慢先看这行 |
| 等待的感知 | 2 秒里屏幕一动不动，像卡死 | 立刻出「正在查…」占位气泡，3 秒后换「还在查，马上好…」 |
| 白等 | 等完了切到成绩页看一眼，回来对话没了，得重新问 | 对话存本地（按学号 / 40 条 / 7 天），切页面不丢 |

**第三层才是用户真正的痛点。** "看一眼成绩"恰恰是问完"我上次考多少"之后最常做的事，
对话一没，前面那几秒等于白等，而且还得重问一遍 —— 主观上就变成"特别慢"。

存档有两个坑，都在 `utils/student-aidi-store.js` 里挡住了：
- **不能和老师端共用一个 key**（`aidi_chat_v1`）。同一台设备上老师一进乐迪，学生的对话就没了。
- **不能存 `loading` 气泡**。恢复出来会永远转圈 —— 那条气泡的状态是"等一个永远不会来的响应"。

### 邀请码的第三种用法：进班，而不是登录

邀请码在这个项目里被改过三次语义，值得记一下，因为它每次都是被真实需求推着走的：

| 版本 | 邀请码用来干什么 | 为什么改 |
|---|---|---|
| v0.33 | **登录门票**（码 + 学号） | 当时假设"一个学生一个班" |
| v0.38 | 只用来加入群聊，登录只看学号 | 学生可能同时上几门课，一门课一个码，记不住 |
| v0.40 | **进班**（进班即在群里） | "加入群聊"这件事没有单独的入口，因为群就是班 |

到 v0.40 才想明白：**在这个数据模型里，"加入群聊"和"加入班级"是同一件事**。
一个班一个群，花名册里有你你就在群里（§9.10.9）。所以不需要"群成员表"，
也就不需要一个单独的"加群"接口 —— 学生拿着码进班，群自然就有了。

**但"进班"不等于"报名"。** 这个接口只接已经在该班花名册里的学生，
花名册里没有就报错让他找班主任。原因不在技术上，在责任上：
成绩、请假、座位全都挂在 `(classId, studentId)` 上，学生自己造一行，
等于往老师的名单里插一条他不知道的学籍 —— 导名单时出现重名行、
成绩挂到错的人身上，而且**不报错**。花名册归老师，这条边界不该由学生端来推。

真要支持"谁都能自己报名"，正确做法是建一行 `status=2（待审核）` 让班主任批，
而不是直接建成在读。那需要老师端的审批界面 —— 也就是下一轮的事。

### 两端都要有"退出登录"，位置和样式要一致

老师端个人中心一直没有退出登录，学生端「我的」有。用户一眼就看出来了。

补的时候特意**照抄学生端那一版**：整页最下面、白底红字通栏按钮、点了先弹确认。
理由很实际 —— 用户是在两个端之间来回切的，同一个操作在两端的形状不一样，
每次都要重新找一遍。**"退出登录"不是设计亮点，它越不起眼、越一致越好。**

### 登录该问"你是谁"，不该问"你在名单里吗"

学生端前三版登录都是同一个写法：拿学号去 `t_student` 里查，查不到就 1004「没找到学号」。
这行代码看起来天经地义，实际上把**身份识别**和**权限判断**焊在了一起：

> 只有被班主任导入过花名册的人，才配拥有"我是谁"。

后果是转专业的、插班的、名单还没更新的学生**根本进不来**，而且他看到的那句话
（「没找到学号 202411753」）既不告诉他为什么，也不告诉他找谁。一个还没见过任何
班级数据的人，被拒绝的理由却是"数据里没有你"。

v0.41 把这两件事拆开：

| 问题 | 由谁回答 |
|---|---|
| 你是谁 | 学号 + 姓名 → 一张 `role=2` 的 token |
| 你能看哪个班的数据 | token 里有没有 `classId` |

于是 token 有了两种形态：**已入班**（subject=studentId，带 classId）和
**未入班**（subject=0，没有 classId）。后者只能调入班接口和"我是谁"，
其余一律 1009。数据权限仍然完全由 classId 决定，一点没松。

**为什么没建一张 `t_student_account` 表。** 想过，也否掉了：学生没有密码、没有 openid，
这张表里能放的只有"学号 + 姓名"两列 —— 而这两样本来就在 token 的 claim 里。
建表之后还得回答"第一次登录的人账号从哪来"，答案还是"登录时建一个"，
等于把无状态的东西强行做成有状态，多一张表、多一次写、多一个不一致的可能。
**真正需要账号表的时刻，是要绑微信或加密码的那天**，不是今天。

### 1009 不能复用 1002，哪怕它们看起来都是"没权限"

`request.js` 里只有一条硬编码的跳转：**1002 = 清 token、回入口页**。
这是对的，1002 的语义就是"你的 token 不可用了"。

而未入班的学生**登录是成功的**，token 也是好的。如果给他返 1002，
他会被踢回登录页，再登一次还是被踢 —— 一个永远出不去的循环，
而屏幕上只有一句"登录已失效"。

所以新开 1009：「你还没加入班级，先填班主任给的邀请码」。前端对它不做任何跳转，
由页面自己决定去哪（首页遇到 1009 就 reLaunch 到入班页）。

**这类"看起来可以复用"的错误码，复用一次就会把一个正常的用户状态
变成一个 bug 报告。**

### 允许学生自己建花名册行，是有代价的

v0.40 我拒绝过这件事，理由是"成绩/请假/座位都挂在 (classId, studentId) 上，
学生自己造一行等于往老师名单里插一条没人知道的学籍"。这个理由现在依然成立。

但它的代价在 v0.41 变得不可接受：**登录已经放开了，再卡住入班，等于让人登录完看到一堵墙。**
所以改成允许，并用三条约束把风险压回去：

1. 必须持有邀请码 —— 老师发码就是授权，建行只是把授权落成数据；
2. 学号在本班已有**在读**行时，姓名必须一致才绑定 —— 挡住"拿别人学号入班"；
3. 学号在本班是**离班**状态时拒绝，且不新建 —— 否则名单里会出现两个同号的人。

第 3 条容易被忽略：查花名册时**故意不带 `status` 条件**，就是为了区分
"从没来过"（可以建）和"来过又走了"（不能建）。

新建的行 `status` 直接给 1，不是"待审核"。原因很土：**老师端的名单页只认 1 和 0。**
塞一个 2 进去，学生入了班，但班主任在任何列表里都看不到他 —— 比不建还糟。
要加审批，得先给老师端做"待确认"那一屏，那是另一端的事。

### 这套登录的强度，得说清楚

"学号 + 姓名"是弱校验：知道同学学号和姓名的人，能登进那个账号看到他的成绩。
这是**没有密码的系统**的固有上限，不是这次改动引入的。

真正的门是**班级邀请码** —— 但只对"还没进班的人"有效，已经在班里的学生是直接进的。
所以现状是：**同班同学之间基本没有防护**（互相知道学号姓名），跨班是有的。

要真正解决只有两条路，都要动数据模型：
- 绑微信 openid（一台设备一个身份，换设备要重新验证）；
- 给学号加一个只有本人知道的校验位（比如身份证后 6 位）。

两条都值得做，但都不是"改个登录方式"的量级，得单独一轮。

### 两个「班级群」：我上一轮亲手埋的坑

v0.39 用户说"学生端没有群聊"，我给他建了一个：新表 `t_class_message`、
一个"一个班一个群"的接口、一张通讯录卡片。当时文档里写得很自信：
"消息表是 t_class_message，**不是** t_container_message，因为两套自增 id 会撞号。"

v0.42 用户回来问了两句话：

> 「学生端有个班级群但是为什么我进教师端的时候没有发现这个群」
> 「我用教师端24级计算机软通1班的群邀请码让学生加群说无效 953738D26C」

两句话是同一个错误的两面：**老师端的群和学生端的群，从来不是同一个东西。**
老师端的「24计算机软通1班」是 `t_subject_container` 里的一行（`class_id=1`，
邀请码 `953738D26C`，里面已经有 22 条消息）。学生端那个是 `t_class_message` 里的一张空表。
名字一样，数据无关。

**教训不是"表建错了"，是"做之前没问系统里是不是已经有一个 X"。**
当时我看到的是"老师端的容器群要建、要码、要审核，学生进不去"，于是绕开它另起一套 ——
绕开的代价是**同一个名字下有两份数据**，而用户永远只会认为它们是同一份。

正确的判断顺序应该是：**先看用户嘴里的这个词指什么**。他说"班级群"，
指的就是他在老师端看到的那个群；我要做的是让学生进那个群，不是再做一个也叫"班级群"的东西。

### 统一到容器群，靠三个字段解决"两套 id"

学生不是 `t_user`（§9.9.1），这是绕不过去的事实。所以 `t_container_message` 加了：

| 列 | 解决什么 |
|---|---|
| `sender_role`（1 老师 / 2 学生） | 两套自增 id 靠它区分。`t_user.id=121` 和 `t_student.id=121` 是两个人 |
| `sender_name` | 学生姓名快照。学生不在 `t_user` 里，反查只会查到一个不相干的老师 |

**最容易漏的一处是 `mine`。** 它原来是 `Objects.equals(senderId, meId)`。
老师 id=1 打开群，学生 1 号发的话就会被标成"我发的" —— 不报错、不越权，
只是气泡跑到了右边。所以 `mine` 必须写成 `!fromStudent && senderId == meId`。
**凡是有两套 id 空间的地方，每一处 id 比较都要带上角色。** 漏一处就是一个静默 bug。

### 学生不写进 t_container_member

直觉上"进群就该有成员行"，但那张表的 `user_id` 指向 `t_user`。把 `studentId` 塞进去，
老师端的成员管理（禁言、设班委、移除）就会把学生当老师处理；再加一个 `member_type`
就要把 539 行的成员服务从头到尾审一遍，漏一处就是越权。

所以换了个更省事的定义：**学生在不在群里，由"他的班"决定。**
`container.class_id == student.classId` 就可见，不需要任何记录。
只有"用邀请码加入别的群"才需要记一笔（`t_student_container`）——
因为那种群没有班级可以自动判断。

代价是老师端的成员列表里看不到学生。这个代价可以接受：
学生不需要被禁言、不需要当班委、不需要被移除 —— 他们连"退出群"都没有。

**人数是个坑。** 老师端那个 `memberCount` 只数 `t_container_member`，
一个 57 个学生都在说话的群会显示「1 人」。所以 `ContainerVO` / `ContainerDetailVO`
都加了 `studentCount`，前端显示"老师 + 学生"。成员管理那一行仍然只写老师 ——
点进去的名单里确实只有老师，两个数字不一样是对的，加了注释说明。

### 邀请码第四次改语义

| 版本 | 邀请码用来干什么 | 为什么 |
|---|---|---|
| v0.33 | 登录门票（码 + 学号） | 假设一个学生一个班 |
| v0.38 | 只用来加群聊，登录只看学号 | 学生可能上几门课 |
| v0.40 | 进班（进班即在群里） | "加群"没有单独入口，因为群就是班 |
| v0.42 | **进别的群**；本班的群不需要码 | 群是老师建的，一个班可能有好几个群 |

到 v0.42 才把"班"和"群"分开：班是学籍（决定成绩/请假/座位），群是聊天（决定谁能看到消息）。
本班的群自动可见，是因为**不该让学生为了看自己班的群，去跟班主任要一个码**。
邀请码留给"跨班旁听"这种真正需要授权的场景。

### 迁移：先搬数据，再删表

`t_class_message` 里有两条真实的学生消息（「大家好」「我是杨凯」）。
`14_student_in_container.sql` 按 `class_id` 找到对应容器，把它们插进
`t_container_message`（`sender_role=2`、`sender_name` 保留），然后 `DROP TABLE`。

为什么不留着这张表"以防万一"：**留着它，下一个人就会以为"班级群"还是它。**
v0.39 的 `ClassMessage` / `ClassMessageMapper` / `ClassMessageVO` 三个类一起删了 ——
表和代码都不在，才不会有人接回去。

## 9.59 学生端的文案：删掉"说明书"

学生端第一版几乎每页都在解释自己：登录页讲为什么不用邀请码、加群页讲邀请码从哪来、
成绩页讲成绩谁录的、座位页讲哪排离讲台近、通知页讲通知是怎么算出来的。
这些句子有个共同点 —— **它们回答的是"我为什么这么设计"，不是"我现在该干什么"。**

学生打开一页，要的是"我坐哪""我考了多少"。看到一整段灰字，
第一反应不是读完，是"这页是不是出错了"。小字越多，越像免责声明。

删掉的原则：

- **页面底部的小字一律不留。** 没有一条是完成操作必须读的。
- **空态保留。** 「班主任还没建群」「老师录入后会自动出现在这里」是在解释
  *为什么这里是空的*，不解释就会以为加载失败。这不是说明，是状态。
- **会变的数据保留。** 请假页的「共 3 天」是实时算出来的结果，
  不是说明 —— 删了学生得自己数日历。
- **标题下的短副标题保留**（「填班主任给的邀请码」），它和标题一起构成一句话，
  不算段落。

代价：`profile/edit.vue` 那句"姓名学号改不了，找班主任"也删了。
学生可能点进来找"改名字"。接受这个代价 —— 页面上没有那一行，
比有一行解释更像一个正常的表单；真找不到会去问班主任，这是正常的沟通，
不是 bug 报告。

## 9.60 学生端群聊对标老师端，以及一个把群"变没了"的 bug

### 先修 bug：`classNames.get(c.getId())`

老师端「群聊」列表里，每个群显示成**隔壁班**的名字：高等数学 → 24计算机软通1班，
一班 → 24软通班2班，二班 → 未关联班级。学生说"我有个群"，老师在列表里找不到它 ——
群没丢，只是标签全错位了一格。

```java
Map<Long, String> classNames = classNames(rows);   // key 是 classId
...
classNames.get(c.getId())                          // 拿容器 id 去查
```

容器 2 关联 class 1，查 `classNames.get(2)` 拿到 class 2 的名字；容器 3 关联 class 2，
查 `classNames.get(3)` 拿到不存在的 class 3 → null。只有容器 4（id 和 classId 都是 4）
碰巧对。**同名的两个字段（`id` 和 `classId`）都是 `Long`，编译器一个字都不会提醒。**

这是"两张表各有一列自增 id"的第二次翻车（第一次是 `t_user.id` 与 `t_student.id` 撞号，
见 §9.57）。教训一样：**用一个 map 之前，先确认 key 是哪一列**，
并且最好让变量名带上单位 —— `classNames` 该叫 `classNameByClassId`。

### 人数：老师看 1 人，学生看 59 人

老师端通讯录「班级群」那一行用的是 `groupMemberCount`（只数 `t_container_member`），
学生端同一个群是 `memberCount`（老师 + 学生）。同一个群，两端差 58 个人。

现在 `ClassVO` 多了 `groupTotalCount`，通讯录改用它；`groupMemberCount` 保留，
语义收窄成"群里有几个老师"。**两个数字都留着，因为它们是两个问题** ——
"群里有没有人说话"和"有几个老师在看这个群"。

### 聊天页：不是"长得像"，是同一套结构

学生端群聊原来是一个自己写的简化版：原生导航栏、没有 ＋ 面板、没有时间分隔、
没有图片视频气泡。结果同一个群，老师发的一张图在学生那儿是空气泡。

重写时按老师端 `container/chat.vue` 逐项对齐：

- **自定义导航**（返回 / 群名 + 人数 / 详情）。原生导航栏放不下右上角的入口。
- **5 分钟时间分隔**。判断要用原始 `createdAt`，所以 `StudentMessageVO` 加了这一列 ——
  `time` 只有「11-24 09:12」，跨天比大小会错。
- **图片 / 视频 / 文件三种气泡**。视频不嵌 `<video>`：原生组件会盖住工具面板和输入栏。
- **右下角「空 → ＋ / 有字 → 发送」**。空着也显示发送、点了没反应，是最像"坏了"的交互。
- **＋ 面板只给两项**：照片 / 视频、文件。接龙和抽签是老师发起的功能，
  学生端不给入口 —— 放一个点了报错的图标，比不放更糟。
- **卡片消息只读**。接龙卡片在老师端有「我要接龙」按钮，学生端没有参与能力，
  所以只渲染标题。宁可少一个功能，也不要一个按下去 403 的按钮。

### 上传为什么不复用 `/api/container/{id}/upload`

那条接口第一步是 `requireMember`，而学生**不在** `t_container_member` 里（见 §9.58），
永远过不去。所以开了 `/api/student/group/{id}/upload`：权限判断换成 `requireVisible`
（本班的群 / 用码加入的群），落盘、扩展名白名单、大小上限共用同一份
`ContainerFileServiceImpl.store()`。

**权限判断只留一个入口。** `uploadForStudent` 里刻意没有再判一次 ——
在那里用 `requireMember` 判等于把学生全拒掉，用别的方式再判一遍则是两份规则，
迟早不一致。

### 退群：本班的群不给按钮

学生在不在本班的群里，是由 `student.class_id` 派生的，删一行 `t_student_container`
什么也改变不了。所以 `canLeave = !classGroup`，前端连按钮都不渲染；
真调接口也会拿到 1001「本班的群退不了，这是班级的一部分」。

给一个点了没反应的按钮，比不给更糟 —— 这是这一轮反复出现的同一条判断
（见 §9.59 删小字、§9.58 删花名册）。

## 9.61 「正在查」永远不消失：状态别塞进列表里

学生端乐迪的现象是"一直是正在查"，而老师端同样的模型、同样的接口，两秒就出答案。
实测两端接口都是 2 秒左右（`curl` 计时：学生 1.9~2.9s，老师 2.0~2.2s），
所以慢的不是模型，是**那个气泡没人把它关掉**。

原来的写法是把占位气泡当成一条消息塞进列表：

```js
const pending = { role: 'assistant', content: '', loading: true }
list.value.push(pending)
// …请求回来之后
pending.loading = false
pending.content = res.reply
```

`list` 是 `ref([])`，`push` 进去的是**原始对象**；模板拿到的却是它被 `reactive()`
包出来的代理。`pending.loading = false` 改的是原始对象，走不到代理的 `set`，
**不会触发任何更新**。气泡能不能刷新，全靠别处碰巧有个 ref 变化把整页重渲染一次 ——
一旦那条路径不成立，屏幕上就永远停着"正在查…"。

老师端没这个问题，因为它用的是另一套结构：

```js
const thinking = ref(false)   // 独立的状态
// 模板：<view v-if="thinking"> 一个单独的气泡 </view>
```

`thinking` 是 ref，`thinking.value = false` 一定触发更新。**占位气泡不属于消息列表**，
它是个"当前正在发生的事"，用完就消失，不该进历史、不该进存档、也不该靠改字段收尾。

改法（学生端现在和老师端一致）：

- `thinking` 独立成一个 ref，模板里单独渲染一行"正在查…"；
- 三个点做呼吸动画，超过 8 秒补一句"还在查，N 秒了"——静止的四个字和卡死分不出来；
- 失败时**push 一条带 `failed` 的真消息**（它是历史的一部分，该进列表），并给「重试」；
- 客户端加 45 秒硬超时（`Promise.race`）：小程序没有 AbortController，
  也不需要真的中断请求，只要前端不再等它。服务端自己 60 秒才报错，太晚了。

顺带一条纪律：**列表里的对象只做"新增/删除"，不做"改字段"**。
新增走 `push`（数组代理会通知），改字段必须整条替换或改一个 ref。
这一条对任何 `ref([...])` 都成立，不只是聊天。

### 首页通知的红点：不能用 total 判

`GET /api/student/notice` 的 `total` 是"历史上所有请假结果 + 未读私信汇总"，
一个学期下来只会越来越大。拿它当红点，等于**永远亮着**，亮着的东西没人看。

通知本来就没有已读状态（按业务状态算出来的，后端不记谁看过），所以红点只能客户端判：
本地存一个 `student_notice_seen_at`（上次点进通知页的时间），
拿 `notices[0].atMillis` 和它比，比它新就是"有新通知"。

点通知条时先记已看再跳转。换设备会重新亮一次 —— 这个偏差可以接受，
比"永远亮"好得多。视觉上整条换红底 + 左侧呼吸红点 + 数量徽标，
只加一个点的话，手指一划就过去了。

## 9.62 通知不分班，以及一个 `.head` 把机器人压扁的 CSS 事故

### 通知从"第一个班"改成"我的所有班"

`GET /api/teacher/home/notices` 原来的语义是：`classId` 不传 = **我的第一个班**。
学生端做红点的时候照着调，发现老师在首页看到红点、点进通知页却是空的 ——
因为红点算的是"所有班最新一条"，通知页只查了第一个班。

更根本的问题是这条语义本身就不对。老师的通知天然是跨班的：
二班学生请假，不应该要求老师先切到二班才知道。

```java
List<ClassInfo> classes;
if (classId == null) {
    classes = classService.myClasses(userId);          // 所有班
} else {
    ClassInfo one = classService.resolveClass(userId, classId);
    classes = one == null ? List.of() : List.of(one);  // 指定班
}
List<Long> classIds = classes.stream().map(ClassInfo::getId).toList();
```

三条查询（请假结果、接龙截止、未读私信）全部从 `.eq(classId)` 改成 `.in(classIds)`。

**这是破坏性变更**：以前不传 `classId` 只拿到一个班，现在拿到全部。
调用方如果依赖"不传就等于第一个班"，会突然看到更多数据。
`api-contract.md` 已标注。为了合并后的列表还能读，`HomeNoticeVO` 加了
`classId` / `className`，前端在班级数 > 1 时给每条加班级前缀。

排序也要自己定：`atMillis` 倒序，同一毫秒再按 `classId` 排。
不写第二个比较键的话，两个班在同一秒提交的请假每次刷新顺序都会变 ——
列表跳动比顺序难看更让人怀疑数据。

### `.head` 这个类名把乐迪压扁了

老师端乐迪的头像变形：机器人的脑袋被拉成一条横条。原因是页面顶栏和机器人内部
用了同一个类名：

```css
/* 页面顶栏 */
.head { padding: 12rpx 28rpx; display: flex; align-items: center; }
```

```html
<!-- 机器人内部 -->
<view class="head"></view>   <!-- 想要 position:absolute; left:14rpx; top:44rpx; width:132rpx -->
```

`<script setup>` + scoped CSS 的 scoped 只隔离"不同组件之间"，**同一个 .vue 文件里
所有元素共享同一份作用域 id**。所以 `.head` 的 `padding` 和 `display:flex` 直接
盖到机器人脑袋上，把它撑成了一条。

修法不是提高选择器优先级，而是**给内部件加前缀**：`head` → `rd-head`，
`eye` → `rd-eye`，`mouth` → `rd-mouth`。

要加优先级很容易，写 `.robot .head` 就行，但下次再有人写 `.robot .head` 以外的规则
还是会被串。改名的成本是一次性的，而且改完 grep 一下 `残留 .head{` = 0 就能确认
顶栏那条规则还在（它被改名的部分不该被连带删掉）。

**同文件内的类名不做命名空间，就等于全局变量。** 这条对所有 `.vue` 都成立。
`rd-` 这种前缀看着啰嗦，但它换来的是"改一个地方不会震到另一处"。

### 红点两端同一套逻辑，但 key 不同

学生 `student_notice_seen_at`，老师 `teacher_notice_seen_at`。
不共用是因为同一个手机可能被两个人登录过（教室里试账号很常见），
共用 key 会让老师登录时把学生的已读时间当成自己的。

两端都走 `notices[0].atMillis > seen_at` 判定，都没有后端已读表 ——
理由见上一节：通知是算出来的，不是存出来的。

## 9.63 开源前先在一个空库上装一遍

准备把仓库公开时问的是"别人下载了能跑吗"。没测之前以为能跑，测完发现三个阻断，
一个隐私问题。教训很朴素：**不要用自己那台跑了一年的机器验证安装流程。**

### `01_schema.sql` 不是"完整结构"，是"第一天的结构"

`01_schema.sql` 的头部自己写着：

```
-- 说明：本期只建首页闭环需要的 5 张表，其余模块的表做到对应模块时再加
```

它后来被补到了 8 张（`t_exam` / `t_score` / `t_seat` 是后加的），但**它永远只是基线**。
群聊（04/05/06）、接龙抽签（09）、乐迪记忆（11）、私信（12）、学生进群（14）
都在增量脚本里。而 README 当时写的是"全新数据库只需要执行 01 和 02" ——
照着装出来的库只有 8 张表，后端能启动，一进群聊就报表不存在。

实测对照：只跑 `01`+`02` → 8 张表；跑完 `04`~`14` → **22 张表**，与开发机逐列一致。

**为什么没早发现**：开发机的库是一路 `ALTER` 过来的，从来没从 01 重建过。
每次加功能只写增量脚本、只在自己库上跑，基线就越来越不代表现状。

### 增量脚本没有 `USE`，`mysql < file` 就跑不了

`03` 有 `USE \`class_assistant\`;`，`04` 之后全都没有 —— 它们默认"调用方已经选了库"。

```bash
mysql -u root -p < database/migrations/04_subject_container.sql
# ERROR 1046 (3D000): No database selected
```

自己机器上一直是 `mysql -D class_assistant < file`，所以从没撞到。补上 `USE` 之后
裸重定向就能跑通全链。

顺带一个预期内的报错：`03` 在全新库上必然 `ERROR 1060 Duplicate column name 'dormitory'`
（01 的基线已经含那 9 个字段，MySQL 8 又不支持 `ADD COLUMN IF NOT EXISTS`）。
这个不能"修"，只能写清楚"全新库跳过 03"，否则每个新人都要猜一次。

### 环境变量形式不等于安全

```yaml
api-key: ${DEEPSEEK_API_KEY:sk-REDACTED（真实 key 已打码，见 GitHub push protection 记录）}
```

看着是"支持环境变量"，实际上**默认值就是一把能用的真 key**，提交即公开。
`${ENV:默认值}` 这个写法很容易让人以为自己在做安全实践，其实只是给默认值套了个壳。

判断标准很简单：**这个默认值能不能用来访问真实服务？** 能，就不能进仓库。
`DB_PASSWORD:1234` 可以留（只是本地 MySQL 的常见密码，没有对应的真实服务），
`JWT_SECRET:dev-only-change-me` 可以留（自己写的字符串，谁也登不进来）。

去掉默认值后要确认程序还能启动。这里是原本就设计好的：`DeepSeekClient.configured()`
用 `StringUtils.hasText` 守卫，没 key 时启动不报错，只有乐迪回
`5001 乐迪还没接上大脑`，其他接口照常 200（见 §9.52）。

### 文档里的示例数据：先问"这份仓库给谁看"

`api-contract.md` 的示例 JSON 用的是真实学生的姓名和学号 —— 写文档时从库里直接
复制了一条真实响应，之后被复制到十几处。

准备开源时把它们换成了虚构的 `王小明 / 202401001`，后来确认这是个**私有仓库**
（只加同学做协作者），又改回了真实数据。理由很实际：对着文档调试时，
文档里的人和 App 里看到的是同一个，不用在脑子里做一次映射。

所以判断依据不是"名字本身敏不敏感"，而是**这份仓库给谁看**：

| 仓库可见性 | 示例数据 |
|---|---|
| 公开（任何人 + 搜索引擎） | 一律虚构。姓名 + 学号 + 班级凑齐就是可识别信息，而且会被收录 |
| 私有 + 白名单协作者 | 可以用真实数据，省掉一次心智映射 |

但有一条不变：**花名册本身不进仓库。** `02_seed.sql` 里的 47 个学生是 SQL 递归
生成的假数据，谁 clone 都拿到同一份假名单，真实名单只存在各自的数据库里。

### 检查清单

开源前在一个空库上按 README 从头做一遍，然后：

```bash
git init && git add -A
git diff --cached --name-only | wc -l          # 389，看着对不对
git diff --cached -U0 | grep -icE "sk-[a-zA-Z0-9]{20,}"   # 必须是 0
git check-ignore -v uploads database/backups .env          # 该忽略的都被忽略
```

公开仓库再加一步：把库里所有真实学生姓名 grep 一遍仓库 —— 这一条最容易漏，
因为没人觉得自己写过。私有仓库可以跳过，但**如果哪天要转公开，得先跑一遍**。
