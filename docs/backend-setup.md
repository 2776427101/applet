# 本地跑通登录接口

这份文档的目标只有一个：**让 `POST /api/auth/wx-login` 真的返回一个 token**。

做到这一步，就证明「MySQL → Mapper → Service → Controller → JWT → 异常处理」整条链路是通的。剩下的接口都是照着它改字段。

---

## 先看总览：一共 3 步

| 步骤 | 做什么 | 在哪做 | 备注 |
|---|---|---|---|
| **第 1 步** | 建库、建表、灌测试数据 | Git Bash | 只做一次 |
| **第 2 步** | 启动服务 | **IDEA 或 命令行，二选一** | 每次开发都要做 |
| **第 3 步** | 测试接口 | **Apifox** | 验证服务是否正常 |

**前置条件**：MySQL 已装好、能登录；后端代码已能编译（`mvn compile` 通过）。

---

# 第 1 步：建库建表

**在哪做**：Git Bash
**做几次**：只做一次（除非你想清空重来）

### 1.1 执行 SQL

```bash
cd "/c/Users/great/Desktop/Mini Program Development"

cat 笔记资料/sql/01_schema.sql 笔记资料/sql/02_seed.sql \
  | /c/programme/MySQL/mysql-9.5.0-winx64/mysql-9.5.0-winx64/bin/mysql -uroot -p
```

回车后会提示 `Enter password:`，输入你的 MySQL root 密码（输入时屏幕不显示字符，是正常的）。

**两条 SQL 一起执行，只输一次密码。**
- `01_schema.sql`：建库 + 建 5 张表
- `02_seed.sql`：灌测试数据（1 个老师、1 个班、47 个学生、1 条请假、1 条待办）

### 1.2 验证建好了

```bash
/c/programme/MySQL/mysql-9.5.0-winx64/mysql-9.5.0-winx64/bin/mysql -uroot -p \
  -e "USE class_assistant; SHOW TABLES;"
```

应该输出：

```
Tables_in_class_assistant
t_class
t_leave_request
t_student
t_todo
t_user
```

看到 5 张表就说明第 1 步完成了。

> **警告**：`01_schema.sql` 开头有 `DROP TABLE IF EXISTS`。重复执行会**清空所有数据**。只在首次初始化时跑。

---

# 第 2 步：启动服务

## 这里是二选一，不是两件事

> **下面的方式 A 和方式 B，选一个做完就行。**
> 不要 A 做完再去做 B，那会启动两个服务，第二个会因为端口 8080 被占用而报错。

| 选哪个 | 适合谁 |
|---|---|
| **方式 A：IDEA 启动** | 推荐。配置一次，以后点绿三角 |
| **方式 B：命令行启动** | 想用命令行、或者 IDEA 不在手边 |

---

## 方式 A：用 IDEA 启动（推荐）

### A-1 配置数据库密码

后端需要知道你 MySQL 的 root 密码，配置位置：

```
Run → Edit Configurations → 选中 ClassAssistantServerApplication
→ 找到 "Environment variables" 这一栏 → 点右边的图标
→ 在弹出框里点 "+" → Name 填 DB_PASSWORD，Value 填你的密码
→ OK
```

填完是这样：

```
DB_PASSWORD=你的MySQL密码
```

**这一步只需要做一次**，IDEA 会记住。

> 为什么要这样做：`application.yml` 里写的是 `password: ${DB_PASSWORD:}`，意思是"从环境变量 DB_PASSWORD 取值，取不到就用空串"。所以配了这个变量，密码就不会硬编码在代码里，也不会被提交到 git。

### A-2 启动

点右上角的绿色三角，或者按 `Shift + F10`。

### A-3 确认启动成功

控制台最后几行应该是：

```
Tomcat started on port 8080 (http)
Started ClassAssistantServerApplication in 3.456 seconds
```

看到 `Started ...` 就说明服务起来了，可以跳到第 3 步。

**端口说明**：端口用 `application.yml` 里的 8080，不需要加任何参数。

---

## 方式 B：用命令行启动

### B-1 设置数据库密码（每次开新终端都要设）

```bash
export DB_PASSWORD="你的MySQL密码"
```

> 这个变量只在当前 Git Bash 窗口有效，关掉窗口就没了。下次开新窗口要重新设。

### B-2 启动

```bash
cd "/c/Users/great/Desktop/Mini Program Development/class-assistant-server/class_assistant_server"
mvn spring-boot:run
```

就这样，**后面不加任何参数**。

### B-3 确认启动成功

和方式 A 一样，看控制台有没有：

```
Tomcat started on port 8080 (http)
Started ClassAssistantServerApplication in 3.456 seconds
```

> **注意**：服务启动后会一直占着这个终端窗口，这是正常的。要停止服务按 `Ctrl + C`。

---

# 第 3 步：用 Apifox 测试接口

## 这一步在做什么

服务已经在第 2 步启动了（并且还在运行），现在从外面发请求去"敲"它，看它返回什么。

- 用什么敲：Apifox
- 敲完看什么：响应 JSON 里的 `code` 字段

**6 条请求分两组，顺序不能乱**：

| 组 | 请求 | 测什么 | 碰数据库吗 |
|---|---|---|---|
| 第一组 | ①②③ | 拦得住吗（校验、鉴权、404） | 不碰 |
| 第二组 | ④⑤⑥ | 查得对吗（登录、用户、首页） | 要碰 |

先跑第一组，是因为它们不依赖数据库。如果第一组就不通，说明服务没起来或端口不对，跟数据库没关系，排查方向完全不同。

---

## 3.1 导入接口集合

项目根目录有个文件：`api-test.postman_collection.json`，6 个请求的参数都配好了。

Apifox 里操作：

1. 打开你的项目
2. 左上角点 **「导入」**
3. 数据格式选 **Postman**
4. 选择 `api-test.postman_collection.json`
5. 导入完成，左边目录树里会出现 6 个接口

---

## 3.2 配服务地址（必做，否则会报 Invalid URI）

导入后直接点发送，大概率会报这个错：

```
Invalid URI "http:///api/auth/wx-login"
```

注意 `http:///` 这里是**三个斜杠**——正常应该是 `http://localhost:8080/api/...`。三个斜杠说明协议有、主机和端口是空的。

**原因**：Apifox 把完整地址拆成「服务地址 + 路径」两段。导入时 `http://localhost:8080` 被抽出来当服务地址，但项目里还没建这个服务，拼回去就空了。

**修法**：

1. 右上角有个环境下拉框（默认显示「无环境」），点它 → 选「管理环境」
2. 点「+」新建环境，名称填 `本地`
3. 找到 **「服务（前置 URL）」** 这一栏，填：

```
http://localhost:8080
```

4. 保存，回主界面，右上角环境选择器切到「本地」

**怎么确认配好了**：URL 输入框应该显示成三段：

```
POST ▼ | http://localhost:8080 ▼ | /api/auth/wx-login
```

中间那段是空的，就还没配好。

> 还不行的话，把 `localhost` 换成 `127.0.0.1` 再试。有些环境 Apifox 解析 `localhost` 会出问题。

---

## 3.3 逐条测试

### 测试 ①：参数校验

| 项目 | 内容 |
|---|---|
| 点左边 | `① 参数校验（空 code 应返回 400 + 1001）` |
| 方法 | POST |
| 路径 | `/api/auth/wx-login` |
| Body | `{"code": ""}` |

点 **发送**。

**预期响应**：

```json
{"code": 1001, "data": null, "message": "code不能为空"}
```

> **注意：这条返回 1001 就是成功，不是失败。**
> 它本来就是故意传一个空 `code` 去撞校验规则的。看到 `code不能为空` 说明校验按设计工作了。

**通过说明**：Controller 收到请求 → `@Valid` 生效 → `@NotBlank` 拦住 → 全局异常处理器转成 1001。

---

### 测试 ②：登录拦截

| 项目 | 内容 |
|---|---|
| 点左边 | `② 拦截器（不带 token 应返回 401 + 1002）` |
| 方法 | GET |
| 路径 | `/api/auth/me` |
| Header | 空（不带 Authorization） |

点 **发送**。

**预期响应**：

```json
{"code": 1002, "data": null, "message": "请先登录"}
```

**通过说明**：`/me` 需要登录，但这条没带 token，被 `AuthInterceptor` 拦下了。

---

### 测试 ③：404 处理

| 项目 | 内容 |
|---|---|
| 点左边 | `③ 404（不存在的路径应返回 404 + 1004）` |
| 方法 | GET |
| 路径 | `/foo` |

点 **发送**。

**预期响应**：

```json
{"code": 1004, "data": null, "message": "接口不存在"}
```

**通过说明**：路径不存在时，`GlobalExceptionHandler` 返回 404 而不是 5000，排查问题时不会误判。

> 为什么不用 `/api/xxx` 测 404？因为 `/api/**` 会先被拦截器拦住返回 401。这是故意的安全设计——不告诉未登录的人"这个接口存不存在"。

---

### 测试 ④：登录（第一次真正查数据库）

| 项目 | 内容 |
|---|---|
| 点左边 | `④ 登录（应返回 code=0 和 token）` |
| 方法 | POST |
| 路径 | `/api/auth/wx-login` |
| Body | `{"code": "test_openid_001"}` |

点 **发送**。

**预期响应**：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyIiwicm9sZSI6MX0.xxxxx",
    "expiresIn": 604800,
    "needBindClass": true,
    "user": {
      "id": 2,
      "name": "",
      "role": 1,
      "avatar": null,
      "classId": null,
      "className": null
    }
  }
}
```

**看到 `"code": 0` 就说明整条链路通了。**

三个字段解释：

| 字段 | 为什么是这个值 |
|---|---|
| `token` | 后端签发的 JWT，后面 ⑤⑥ 两条要用 |
| `needBindClass: true` | 这个用户还没绑班级，前端应该引导他去"加入班级"页 |
| `user.id: 2` | seed 里的老师是 id=1，你这条是新插入的，所以是 2 |

**如果返回 `"code": 5000`**，说明数据库层出错了。去看启动服务的窗口（IDEA 的 Console 或 Git Bash），找 `Caused by:` 那一行：

| 报错 | 原因 | 回去看 |
|---|---|---|
| `Access denied ... (using password: NO)` | 密码没配上 | 第 2 步 |
| `Unknown database 'class_assistant'` | 库没建 | 第 1 步 |
| `Table 'class_assistant.t_user' doesn't exist` | 表没建 | 第 1 步 |

---

### 测试 ⑤：查当前用户

**先把 token 填进去**：

1. 点左边 `⑤ 当前用户（先把 ④ 的 token 粘进来）`
2. 找到 **Headers** 标签
3. 有一行 `Authorization`，值是 `Bearer 粘贴你的token`
4. 把 `粘贴你的token` 替换成测试 ④ 返回的完整 token（`Bearer` 后面要有一个空格）

填完长这样：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyIiwicm9sZSI6MX0.xxxxx
```

点 **发送**。

**预期响应**：

```json
{
  "code": 0,
  "data": { "id": 2, "name": "", "role": 1, "avatar": null, "classId": null, "className": null }
}
```

**通过说明**：token 能被正确解析，拦截器把 `userId` 传给了 Controller。

---

### 测试 ⑥：首页聚合数据

同样把 token 填进 Header，点 **发送**。

**预期响应**：

```json
{
  "code": 0,
  "data": {
    "classInfo": null,
    "stats": { "leaveToday": 0, "birthdayToday": 0, "studentCount": 0, "todoUndone": 0 },
    "term": { "weekNo": 0, "termStartDate": null },
    "quote": "耐心是教育的底色。"
  }
}
```

**`classInfo: null` 和统计全 0 是正常的**——你这个新用户还没绑班。下一步解决它。

---

## 3.4 测试结果对照表

跑完 6 条，对照一下：

| 序号 | 预期 code | 说明 |
|---|---|---|
| ① | 1001 | 参数校验生效 |
| ② | 1002 | 拦截器生效 |
| ③ | 1004 | 404 处理生效 |
| ④ | **0** | **登录成功，拿到 token** |
| ⑤ | 0 | token 解析成功 |
| ⑥ | 0 | 首页数据能查（但还没绑班，数字是 0） |

**6 条全部符合预期，后端就算开发完了。** 可以去接前端了。

---

# 第 4 步（可选）：让首页看到 seed 里的真实数据

现在你的用户是 `id=2`，但 seed 里的班级属于 `id=1`，所以查不到。

把班级的班主任改成你：

```bash
/c/programme/MySQL/mysql-9.5.0-winx64/mysql-9.5.0-winx64/bin/mysql -uroot -p \
  -e "USE class_assistant; UPDATE t_class SET head_teacher_id = 2 WHERE id = 1;"
```

> `2` 换成测试 ④ 返回的 `user.id`。

然后在 Apifox 里**再点一次测试 ⑥ 的发送**（token 不用换）：

```json
"classInfo": { "id": 1, "name": "628班", "grade": "高一", "teacherName": "", "teacherTitle": "班主任" },
"stats": { "leaveToday": 1, "birthdayToday": 1, "studentCount": 47, "todoUndone": 1 },
"term": { "weekNo": 8, "termStartDate": "2026-09-03" }
```

**四个数字都有值了，说明整条链路彻底通了，而且查出来的业务数据是对的。**

---

# 附录 A：常见错误对照表

| 你看到的 | 原因 | 去哪一步 |
|---|---|---|
| `Invalid URI "http:///api/..."` | Apifox 没配服务地址，host 丢了 | 3.2 |
| `Access denied for user 'root'@'localhost' (using password: NO)` | 没配 `DB_PASSWORD` | 第 2 步 |
| `Unknown database 'class_assistant'` | 库没建 | 第 1 步 |
| `Table 'class_assistant.t_user' doesn't exist` | 只建了库没建表 | 第 1 步 |
| `Web server failed to start. Port 8080 was already in use` | 已经有一个服务在跑了 | 关掉旧的，或 `Ctrl+C` 停掉 |
| `No qualifying bean of type '...Mapper'` | 启动类缺 `@MapperScan` | 检查启动类注解 |
| 登录返回 `code: 2002` | `mock: false` 且 appid/secret 是空的 | `application.yml` 里 `app.wx.mock: true` |
| 登录返回 `code: 5000` | 数据库层报错 | 看启动窗口的 `Caused by:` |
| ⑤⑥ 返回 1002 | Header 里的 token 没填、或 `Bearer` 后面漏了空格 | 3.3 的测试 ⑤ |

---

# 附录 B：不用 Apifox 的话（curl 命令）

如果哪天 Apifox 不在手边，下面这些命令在 **Git Bash** 里效果完全一样。

> **PowerShell 里不能用**：PowerShell 5.1 会把参数里的双引号吃掉，实测 `-d '{"code":""}'` 到达程序时变成 `-d {code:}`。详见附录 C。

```bash
# ① 参数校验
curl -s -X POST http://localhost:8080/api/auth/wx-login \
  -H "Content-Type: application/json" -d '{"code":""}'

# ② 拦截器
curl -s http://localhost:8080/api/auth/me

# ③ 404
curl -s http://localhost:8080/foo

# ④ 登录
curl -s -X POST http://localhost:8080/api/auth/wx-login \
  -H "Content-Type: application/json" -d '{"code":"test_openid_001"}'

# ⑤ 当前用户（先替换 token）
TOKEN="粘贴你的token"
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"

# ⑥ 首页聚合
curl -s http://localhost:8080/api/teacher/home/overview -H "Authorization: Bearer $TOKEN"
```

---

# 附录 C：非要用 PowerShell 怎么办

PowerShell 5.1 给外部程序传参数时，会把参数里的双引号吃掉。实测：

```
你输入：-d '{"code":""}'
程序收到：-d {code:}
```

所以附录 B 的 `curl` 命令**在 PowerShell 里不能用**。

### 办法一：换 Git Bash（推荐）

开始菜单搜 "Git Bash"，打开就能用，命令和文档完全一致。

### 办法二：用 PowerShell 原生的 `Invoke-RestMethod`

```powershell
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/auth/wx-login" -Method Post `
      -ContentType "application/json" -Body '{"code":""}'
} catch {
    $_.ErrorDetails.Message
}
```

`Invoke-RestMethod` 的参数是 .NET 对象，不经过命令行参数传递，所以没有引号问题。但它遇到 4xx/5xx 会抛异常，要用 `try/catch` 包起来才能看到返回的 JSON。

### PowerShell 里启动服务要这样设密码

```powershell
$env:DB_PASSWORD = "你的密码"
mvn spring-boot:run
```

`export DB_PASSWORD=xxx` 是 Git Bash 的语法，PowerShell 不认。

---

# 附录 D：上线前必须改回来的两处

| 配置 | 现在的值 | 上线要改成 |
|---|---|---|
| `app.wx.mock` | `true` | `false`，并填真实的 appid / secret |
| `app.jwt.secret` | 默认的 `dev-only-...` | 换成 32 位以上随机串，用环境变量 `JWT_SECRET` 注入 |

`mock: true` 意味着**任何人只要随便传一个 code 就能登录**。这是开发期为了方便，上线前必须关掉，否则等于没有登录验证。
