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
