# 班级助手

面向班主任的班级管理微信小程序，包含教师首页、学生管理、名单导入、成绩管理与导入、座位表、请假审批、待办和班级设置。

前端使用 uni-app（Vue 3 + Vite），后端使用 Spring Boot + MyBatis-Plus + MySQL，登录基于微信 `code2session` 和 JWT。

## 项目结构

```text
class-assistant/
├─ frontend/uni-app/my-mp/         # uni-app 微信小程序
├─ backend/class_assistant_server/ # Spring Boot 后端
├─ database/                       # 数据库结构和演示数据
├─ deploy/                         # 生产部署：Docker 编排、Nginx、部署手册
├─ docs/                           # 工程笔记、接口契约、运行/构建/发布文档
├─ .gitignore
└─ README.md
```

## 功能概览

- 教师微信登录、班级绑定
- 首页统计、今日待办、快捷入口
- 学生列表、搜索、详情、新增、编辑、离班
- 学生名单 Excel 导入、预览和字段校验
- 考试管理、成绩录入、成绩统计和 Excel 导入
- 座位表编辑、换座、随机排座
- 请假审批、教师代请假
- 待办新增、完成、删除
- 班级名称、年级和开学日期设置

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

```bash
mysql -u root -p < database/01_schema.sql
mysql -u root -p < database/02_seed.sql
```

`02_seed.sql` 是可选的演示数据，会创建 1 位老师、1 个班、47 名假学生和演示成绩。

全新数据库只需要执行 `01` 和 `02`。`database/migrations/03_alter_student_fields.sql` 只用于从旧版本数据库升级，基线结构已经包含这些字段，重复执行会提示列已存在。

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
| `DB_PASSWORD` | 是 | 数据库密码 |
| `WX_APPID` | 正式环境必填 | 微信小程序 AppID |
| `WX_SECRET` | 正式环境必填 | 微信小程序 AppSecret，只能保存在后端 |
| `WX_MOCK` | 否 | 开发默认 `true`；正式环境必须设为 `false` |
| `JWT_SECRET` | 正式环境必填 | JWT 签名密钥，使用足够长的随机字符串 |

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
- [**生产部署手册**](deploy/README.md) —— 上线流程、备案、HTTPS、故障速查
- [接口契约](docs/api-contract.md)
- [后端本地运行](docs/backend-setup.md)
- [前端构建](docs/frontend-build.md)
- [微信小程序发布](docs/wechat-release.md)
- [文档索引](docs/README.md)

## 安全说明

仓库不包含真实学生名单、成绩表、数据库密码、微信 AppSecret、开发者工具配置或本地开发记忆。

请不要提交以下内容：

- `.env`、`application-local.yml` 等本地配置
- `ID.txt`、AppSecret、JWT 私钥或证书
- `.idea/`、`node_modules/`、`target/`、`dist/`
- 真实学生名单或成绩 Excel
- 日志、压缩包和 memory 文件

正式环境必须设置 `WX_MOCK=false`，并且只通过环境变量注入敏感信息。
