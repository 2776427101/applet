# 微信小程序第一次打包上传（操作手册）

> 这一篇按「先检查 → 再构建 → 再上传 → 再体验/发布」的顺序写。
> **上传代码**和**发布给所有人使用**不是一回事：上传后只是开发版本，
> 还要设体验版、提交审核、审核通过后发布。

---

## 一、先说当前项目的检查结果

2026-10-28 已实际检查：

- 前端 `npm run build:mp-weixin`：通过，只有 Sass 弃用警告，不影响运行
- 后端 `./mvnw compile`：通过
- 首页、学生、成绩、待办、请假、座位、班级设置接口：只读检查通过
- 成绩导入：预览、提交、覆盖、缺考、错误行、异常参数均已验证
- 班名一键修改：已验证，测试后已恢复为「628班」
- 正式包体积：约 **488 KB**，远低于小程序 2 MB 主包限制
- AppID 已配置：`wxd3a43865e64253c9`

**但目前还不能直接上传后就在手机上正常使用**，因为还缺线上后端环境：

1. 前端开发环境默认请求 `http://localhost:8080/api`
2. 后端开发环境默认 `WX_MOCK=true`，所有人都登录成同一个测试账号
3. 真机要求后端使用 **HTTPS 域名**，不能使用 localhost
4. 微信后台还要配置 request / uploadFile 合法域名

代码已经为发布做了环境区分：**开发默认测试登录，正式构建自动走真实微信登录**。
你不需要每次发版前手动改 `USE_MOCK_LOGIN`。

---

## 二、准备线上后端（第一次发布必须有）

你需要准备：

- 一台能长期运行的服务器（不能用自己电脑的 localhost）
- 一个域名，并给域名配置 HTTPS 证书
- 服务器上的 MySQL 和本项目数据库
- 微信公众平台里的 AppSecret

假设最后后端地址是：

```text
https://api.example.com/api
```

> `api.example.com` 只是示例，要换成你自己的域名。

后端正式环境要设置这些环境变量：

```text
DB_USER=数据库账号
DB_PASSWORD=数据库密码
WX_APPID=wxd3a43865e64253c9
WX_SECRET=微信公众平台里的AppSecret
WX_MOCK=false
JWT_SECRET=一段足够长的随机字符串
```

注意：

- **不要把 AppSecret、数据库密码、JWT_SECRET 写进前端代码或提交到 git**
- `WX_MOCK=false` 很关键。否则所有人都会进入同一个测试账号
- 数据库要备份，不要把开发测试数据直接当正式数据

---

## 三、微信后台配置合法域名

打开 [微信公众平台](https://mp.weixin.qq.com/)，进入你的小程序：

1. 左侧找「开发管理」
2. 进入「开发设置」
3. 找「服务器域名」
4. 把后端域名加入：
   - **request 合法域名**
   - **uploadFile 合法域名**（学生名单、成绩表导入需要）

应该填：

```text
https://api.example.com
```

不要填成：

```text
https://api.example.com/api       ← 多了路径
http://api.example.com            ← 正式环境不能只配 HTTP
http://localhost:8080             ← 手机访问不到你电脑
```

> 开发者工具里的「不校验合法域名」只对本地调试用，
> **对体验版和正式版无效**。

---

## 四、构建正式小程序包

在 **PowerShell** 里执行（把域名换成你的）：

```powershell
cd "C:\Users\great\Desktop\Mini Program Development\uni-app\my-mp"
$env:VITE_API_BASE_URL = "https://api.example.com/api"
npm run build:mp-weixin
```

看到下面这行就是成功：

```text
DONE  Build complete.
Run method: open Weixin Mini Program Devtools, import dist\build\mp-weixin run.
```

正式包在：

```text
C:\Users\great\Desktop\Mini Program Development\uni-app\my-mp\dist\build\mp-weixin
```

### 构建后一定要检查接口地址

在同一个 PowerShell 窗口执行：

```powershell
Select-String -Path "dist\build\mp-weixin\api\request.js" -Pattern "https://api.example.com/api"
```

能搜到你的线上地址才算对。

再检查有没有把 localhost 打进正式包：

```powershell
Get-ChildItem "dist\build\mp-weixin" -Recurse -File |
  Select-String -Pattern "http://localhost:8080"
```

**没有输出才是正常的。** 如果搜到了，说明刚才没设置环境变量，重新执行第四节。

---

## 五、微信开发者工具里预览和上传

### 1. 导入正式包

微信开发者工具 →「导入项目」→ 目录选择：

```text
C:\Users\great\Desktop\Mini Program Development\uni-app\my-mp\dist\build\mp-weixin
```

AppID 应该自动显示为 `wxd3a43865e64253c9`。

> 注意是 `dist\build\mp-weixin`，不是平时开发用的 `dist\dev\mp-weixin`。

### 2. 先在工具里点「编译」

检查：

- 能进入首页
- 学生列表能打开
- 成绩页能打开
- 待办能显示真实数据
- 控制台没有红色报错

### 3. 点「预览」，用手机扫码

这一步非常重要。开发者工具能运行，不代表手机能运行。

手机上至少检查：

- 微信登录是否正常
- 是否进入自己的班级，而不是测试账号
- 首页和学生数据能否加载
- 从聊天记录选择 Excel 能否上传
- 学生导入、成绩导入是否能到预览页

### 4. 点「上传」

开发者工具右上角 →「上传」：

- 版本号：`1.0.0`
- 项目备注：可以写「教师端首个版本：学生、成绩、待办、座位、请假」

上传成功后，代码会出现在微信公众平台的「版本管理」里。

---

## 六、上传之后：体验版、审核、发布

上传只代表代码到了微信后台，**用户还访问不到**。

### 只想先给少数人体验

微信公众平台 →「管理」→「版本管理」：

1. 找到刚上传的开发版本
2. 设为「体验版」
3. 在「成员管理 → 体验成员」里添加体验者微信
4. 体验者扫描体验版二维码使用

### 想正式上线

1. 体验版确认没有问题
2. 在版本管理里「提交审核」
3. 按页面要求填写类目、功能说明、测试账号等
4. 审核通过后再点「发布」

---

## 七、如果暂时没有服务器和域名

你仍然可以在开发者工具里点「上传」，把代码保存到微信后台。

但这时候：

- 手机体验版连不上 localhost
- 真实微信登录也无法完成
- 不适合提交审核或发布

所以更准确的顺序是：

```text
先准备线上后端和 HTTPS 域名
→ 配置微信合法域名
→ 构建正式包
→ 手机预览
→ 上传体验版
→ 提交审核
→ 发布
```

---

## 八、常见问题对照表

| 现象 | 原因 | 处理 |
|---|---|---|
| 开发者工具能用，手机一直加载失败 | 还在请求 localhost | 重新带 `VITE_API_BASE_URL` 构建 |
| 手机提示域名不合法 | 没配微信合法域名 | 配 request / uploadFile 域名 |
| 所有人登录后都是同一个班 | 后端还开着 mock | 正式环境设 `WX_MOCK=false` |
| 登录一直失败 | WX_APPID / WX_SECRET 没配或配错 | 检查服务器环境变量 |
| 名单、成绩导入失败，但其他接口正常 | 只配了 request 域名 | 再配 uploadFile 合法域名 |
| 上传后别人搜不到小程序 | 上传不等于发布 | 还要体验版 / 审核 / 发布 |
| 改了代码，上传的还是旧功能 | 构建到了别的目录 | 必须重新 build，并导入 `dist\build\mp-weixin` |
| 上传提示包太大 | 主包超过 2MB | 当前包约 488KB，正常不会遇到 |
