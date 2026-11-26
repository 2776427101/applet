# 班级助手

面向班主任和学生的班级管理微信小程序。老师端做学生管理、名单导入、成绩、座位表、请假审批、待办、群聊；学生端看自己的成绩、座位、请假、通知，也能进班级群和老师说话。

前端使用 uni-app（Vue 3 + Vite），后端使用 Spring Boot + MyBatis-Plus + MySQL，登录基于微信 `code2session` 和 JWT。两端共用一套后端和一份数据 —— 学生在群里发的话，老师端就是同一个群里的同一条消息。

## 项目结构

```text
class-assistant/
├─ frontend/uni-app/my-mp/         # uni-app 微信小程序（老师端 + 学生端）
├─ backend/class_assistant_server/ # Spring Boot 后端
├─ database/                       # 数据库基线、增量脚本和演示数据
├─ docs/                           # 工程笔记、接口契约、运行/构建/发布文档
├─ .env.example                    # 环境变量示例（复制成 .env）
├─ .gitignore
└─ README.md
```

## 功能概览

**老师端**

- 教师微信登录、多班级绑定与切换
- 首页统计、今日待办、跨班通知（请假 / 接龙截止 / 未读私信 / 生日）
- 学生列表、搜索、详情、新增、编辑、离班
- 学生名单 Excel 导入、预览和字段校验
- 考试管理、成绩录入、成绩统计和 Excel 导入
- 座位表编辑、换座、随机排座
- 请假审批、教师代请假
- 待办新增、完成、删除
- 群聊：图片 / 视频 / 文件、接龙、抽签、禁言、群成员
- 乐迪（AI 助手）：用自然语言查班级数据、执行操作（两段式确认）
- 班级名称、年级和开学日期设置

**学生端**

- 学号 + 姓名登录（不在花名册里也能先进来，再填邀请码入班）
- 首页统计、通知（带未读红点）、今日课程与生日
- 我的成绩、我的座位
- 请假申请与撤销、审批进度
- 班级群聊（与老师端同一个群，支持图片 / 视频 / 文件）
- 个人资料编辑
- 乐迪（AI 助手，与学生本人数据对话）

## 技术栈

| 部分 | 技术 |
|---|---|
| 前端 | uni-app、Vue 3、Vite、SCSS |
| 后端 | Java 21、Spring Boot 4.1.1、MyBatis-Plus |
| 数据库 | MySQL 8.0+ |
| 登录 | 微信小程序登录、JWT |
| 文件导入 | Excel 名单与成绩导入 |

## 本地运行

### 1. 环境要求

- JDK 21
- Maven 3.9+，或使用项目自带的 `mvnw`
- MySQL 8.0+
- Node.js 20+ 和 npm

### 2. 初始化数据库

`database/01_schema.sql` 会创建 `class_assistant` 数据库并**删除同名旧表**，只应在全新的本地数据库执行。

全新数据库要按顺序执行 `01`、`02`，然后**执行 `04` 到 `14` 全部增量脚本**：

```bash
# 基线结构（8 张核心表）+ 演示数据
mysql -u root -p < database/01_schema.sql
mysql -u root -p < database/02_seed.sql

# 增量脚本：群聊、乐迪记忆、私信、接龙抽签、学生进群……
# 01 已经包含 03 的字段，所以全新库跳过 03
for f in database/migrations/0[4-9]*.sql database/migrations/1*.sql; do
  mysql -u root -p --default-character-set=utf8mb4 < "$f"
done
```

**为什么不能只跑 01 和 02**：`01_schema.sql` 只有 8 张表（`t_user`、`t_class`、`t_student`、
`t_leave_request`、`t_todo`、`t_exam`、`t_score`、`t_seat`），是项目早期"只做首页闭环"时的基线。
群聊（`t_subject_container` / `t_container_message`）、乐迪记忆（`t_aidi_memo`）、
私信（`t_direct_message`）、接龙抽签、`t_student_container` 全部在 `04` 之后的增量脚本里。
漏掉它们，后端能启动，但一进群聊 / 乐迪就报表不存在。

跑完应该有 **23 张表**（基线 8 张 + 增量 15 张），可以这样核对：

```bash
mysql -u root -p -e "SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA='class_assistant';"
```

两个"预期内的报错"，看到不用管：

| 文件 | 报错 | 原因 |
|---|---|---|
| `03_alter_student_fields.sql` | `ERROR 1060 Duplicate column name 'dormitory'` | 01 的基线已经包含这 9 个字段。**全新库跳过 03**，它只用于从旧版本升级 |
| `04` 之后任意一个重复执行 | `ERROR 1050 Table already exists` / `ERROR 1060 Duplicate column` | 增量脚本都只该执行一次 |

`02_seed.sql` 是可选的演示数据，会创建 1 位老师、1 个班、47 名假学生和演示成绩。
`07_class_group.sql` 依赖 `02` 建好的班级（它给每个班补一个"班级群"），
`10_remap_orphan_student_id.sql` 修的是种子数据里成绩/请假挂错学生的问题 —— 两个都要求先跑 `02`。

### 3. 启动后端

```bash
cd backend/class_assistant_server

DB_PASSWORD="你的数据库密码" \
./mvnw spring-boot:run \
  '-Dspring-boot.run.arguments=--server.port=8080'
```

如果本机已经安装 Maven，也可以把 `./mvnw` 换成 `mvn`。

默认配置见 `backend/class_assistant_server/src/main/resources/application.yml`。开发环境默认开启微信 mock 登录，便于本地联调。

后端启动后可验证：

```bash
curl http://localhost:8080/api/auth/me
```

未携带 token 时返回未登录错误属于正常现象。

### 4. 启动前端

```bash
cd frontend/uni-app/my-mp

npm ci
VITE_API_BASE_URL="http://localhost:8080/api" npm run dev:mp-weixin
```

然后用微信开发者工具导入：

```text
frontend/uni-app/my-mp/dist/dev/mp-weixin
```

使用 `npm run build:mp-weixin` 生成上传用的正式包，产物位于：

```text
frontend/uni-app/my-mp/dist/build/mp-weixin
```

## 环境变量

### 后端

| 变量 | 必填 | 说明 |
|---|---:|---|
| `DB_USER` | 否 | 数据库用户名，默认 `root` |
| `DB_PASSWORD` | 是 | 数据库密码（默认值是 `1234`，只为你自己方便，请务必用环境变量覆盖） |
| `WX_APPID` | 正式环境必填 | 微信小程序 AppID |
| `WX_SECRET` | 正式环境必填 | 微信小程序 AppSecret，只能保存在后端 |
| `WX_MOCK` | 否 | 开发默认 `true`；正式环境必须设为 `false` |
| `JWT_SECRET` | 正式环境必填 | JWT 签名密钥，使用足够长的随机字符串 |
| `DEEPSEEK_API_KEY` | 乐迪必填 | DeepSeek API key。**仓库里没有、也不该有**；不配也能启动，只有乐迪会提示未配置 |
| `DEEPSEEK_BASE_URL` | 否 | 默认 `https://api.deepseek.com`，换兼容 OpenAI 协议的模型服务时用 |
| `DEEPSEEK_MODEL` | 否 | 默认 `deepseek-chat` |
| `UPLOAD_DIR` | 否 | 群聊附件落盘目录，默认 `./uploads`（相对启动时的工作目录） |

最省事的做法是复制一份 `.env.example` 成 `.env` 填好，再 `source` 进当前 shell：

```bash
cp .env.example .env      # 填 DB_PASSWORD 和 DEEPSEEK_API_KEY
set -a && source .env && set +a
```

`.env` 已在 `.gitignore` 里，不会被提交。

### 前端

| 变量 | 说明 |
|---|---|
| `VITE_API_BASE_URL` | 后端 API 地址，例如 `https://api.example.com/api` |
| `VITE_USE_MOCK_LOGIN` | 开发时显式设为 `false` 可联调真实微信登录 |

正式构建示例：

```bash
VITE_API_BASE_URL="https://api.example.com/api" npm run build:mp-weixin
```

## 文档

- [**工程笔记与设计决策**](docs/engineering-notes.md) —— 改代码前先看这份
- [接口契约](docs/api-contract.md)
- [后端本地运行](docs/backend-setup.md)
- [前端构建](docs/frontend-build.md)
- [微信小程序发布](docs/wechat-release.md)
- [文档索引](docs/README.md)

## 安全说明

这是一个**私有仓库**，只对项目同学开放协作者权限。

仓库不包含完整的学生花名册或成绩表 —— `02_seed.sql` 里的 47 个学生是 SQL 递归生成的
假数据，谁 clone 都拿到同一份。真实名单只存在各自的本地数据库里。

文档（`docs/`）的示例 JSON 里引用了真实学生姓名和学号，这是有意的：对着文档调试时
和 App 里看到的一致，省掉一次心智映射。**如果这个仓库以后要转为公开，先把 `docs/`
里的真实姓名换成虚构数据**（判据见 `docs/engineering-notes.md` §9.63）。

`application.yml` 里所有敏感项都是 `${环境变量:默认值}` 形式，默认值只够跑通本地演示。

| 项 | 仓库里的值 | 上线前必须做什么 |
|---|---|---|
| `DB_PASSWORD` | `1234`（本地 MySQL 默认） | 换成环境变量注入 |
| `JWT_SECRET` | `dev-only-change-me-please-32-chars-min` | 换成 `openssl rand -base64 48` |
| `DEEPSEEK_API_KEY` | **空** | 通过环境变量注入 |
| `WX_APPID` / `WX_SECRET` | 空 | 通过环境变量注入，且 `WX_MOCK=false` |

`WX_MOCK=true` 时所有人都会登录成种子数据里的同一个老师，**正式环境必须设 false**。

请不要提交以下内容：

- `.env`、`application-local.yml` 等本地配置
- `ID.txt`、AppSecret、JWT 私钥或证书
- `.idea/`、`node_modules/`、`target/`、`dist/`
- 真实学生名单或成绩 Excel
- 日志、压缩包和 memory 文件
- `uploads/`（用户上传的图片/视频/文件）和 `database/backups/`（本地库快照）

正式环境必须设置 `WX_MOCK=false`，并且只通过环境变量注入敏感信息。
