# 前端怎么编译（操作手册）

> 这一篇是给你自己看的。**每次改完前端代码，按这里走一遍就行**，
> 不用等我。看完这一页，你应该能自己判断「我改的东西到底生效了没有」。

---

## 一、先记住这张表

项目里前端有 **三个编译命令**，分别对应三种用途、三个输出目录：

| 命令 | 输出到 | 用途 | 要不要一直开着 |
|---|---|---|---|
| `npm run dev:mp-weixin` | `dist/dev/mp-weixin` | **日常开发**。微信开发者工具导入这个目录 | ✅ 要，watch 模式 |
| `npm run build:mp-weixin` | `dist/build/mp-weixin` | 压缩过的正式包，上传发布用 | ❌ 一次性 |
| `npm run dev:h5` | 不落地，起一个网站 | 浏览器里看效果（`http://localhost:5173`） | ✅ 要，watch 模式 |

**日常开发只需要第一个。**

> ⚠️ **最容易踩的坑**：`dev/` 和 `build/` 是两个完全不同的目录。
> 微信开发者工具只会读你**导入时选的那个**。
> 如果工具导入的是 `dist/dev/mp-weixin`，那你跑 `build:mp-weixin` 它一个字都看不到 ——
> 你会觉得「代码没变」，其实是编译产物写到了另一个文件夹。

---

## 二、微信开发者工具该导入哪个目录

导入项目时，目录选：

```
C:\Users\great\Desktop\Mini Program Development\uni-app\my-mp\dist\dev\mp-weixin
```

注意是 **`dist\dev\mp-weixin`**，不是项目根目录，也不是 `dist\build\mp-weixin`。

**怎么确认自己导入的是哪个**：开发者工具左上角项目名旁边有个下拉，
或者点「详情 → 基本信息 → 项目目录」看一眼。路径里有 `\dev\` 就对了。

---

## 三、日常流程（三步）

### 第 1 步：开一个终端，启动 watch

在项目目录下：

```bash
cd "C:\Users\great\Desktop\Mini Program Development\uni-app\my-mp"
npm run dev:mp-weixin
```

看到这两行就是成功了：

```
DONE  Build complete. Watching for changes...
ready in 15767ms.
```

**这个终端别关。** 它叫 watch 模式 —— 你改任何一个 `.vue` / `.js` / `.json`，
它都会自动重新编译。改一次代码大概 1～3 秒出新产物。

### 第 2 步：改代码

正常改 `src/` 下面的文件。

### 第 3 步：看效果

编译完成后，微信开发者工具一般会自动刷新。
**如果没刷新，点一下工具上方的「编译」按钮**（快捷键 `Ctrl + B`）。

---

## 四、怎么确认「我改的东西生效了」

按顺序查这三步，能定位 95% 的「改了没反应」。

### 检查点 1：产物文件的修改时间

```bash
ls -la "C:/Users/great/Desktop/Mini Program Development/uni-app/my-mp/dist/dev/mp-weixin/pages/teacher/score/"
```

看时间是不是刚才。**如果不是刚才 → watch 没跑起来，或者改错了目录。**

### 检查点 2：在产物里搜你的文案

改了什么文字，就去 `wxml` 里搜一下：

```bash
grep -o "新建考试" "C:/Users/great/Desktop/Mini Program Development/uni-app/my-mp/dist/dev/mp-weixin/pages/teacher/score/index.wxml"
```

**搜得到 → 编译进去了，问题在开发者工具那边**（没刷新 / 没点编译）。
**搜不到 → 编译没跑到这个文件**，检查是不是改了 `dist/` 里的文件（改错了，
`dist/` 是编译产物，改了会被覆盖），或者 watch 进程挂了。

### 检查点 3：开发者工具

- 点「编译」按钮
- 看「调试器 → Console」有没有红色报错
- 确认项目目录是 `dist\dev\mp-weixin`

---

## 五、常见故障对照表

| 现象 | 最可能的原因 | 怎么办 |
|---|---|---|
| 改了代码，小程序一点变化都没有 | 跑的是 `build:mp-weixin`，但工具读 `dev/` | 改跑 `npm run dev:mp-weixin` |
| 同上 | watch 终端已经关了 | 重新 `npm run dev:mp-weixin` |
| 同上 | 改的是 `dist/` 里的文件 | 改 `src/` 里的，`dist/` 是产物 |
| 产物时间对了，工具里还是旧的 | 工具没刷新 | 点「编译」（Ctrl+B） |
| 工具里报「app.json 未找到」 | 导入目录选错了（选成了项目根目录） | 重新导入，选 `dist\dev\mp-weixin` |
| 新加的页面打不开 | 忘了在 `src/pages.json` 里注册 | 加进去，watch 会自动重编译 |
| 编译报 Sass deprecation warning | 正常，只是警告 | 忽略 |
| 编译报 `Cannot find module` | 少装依赖 | `npm install` |

---

## 六、为什么要有两个目录

`dev` 和 `build` 的区别不只是"压缩没压缩"：

| | `dev` | `build` |
|---|---|---|
| 代码 | 不压缩，保留注释和换行 | 压缩成一行 |
| sourcemap | 有，报错能看到源码行号 | 没有 |
| 体积 | 大（可能几 MB） | 小 |
| 速度 | 编译快，适合反复改 | 编译慢，跑一次就够 |
| 谁用 | 你开发时 | 上传微信后台时 |

小程序有 **2MB 主包体积限制**，所以发布必须用 `build` 的产物。
开发时用 `dev`，因为报错信息能看懂。

**结论**：
- 平时 → `dev:mp-weixin`
- 要上传发布 → `build:mp-weixin`，然后在开发者工具里点「上传」

---

## 七、如果要发版

先在 **PowerShell** 里指定线上后端地址，再出正式包：

```powershell
cd "C:\Users\great\Desktop\Mini Program Development\uni-app\my-mp"
$env:VITE_API_BASE_URL = "https://api.example.com/api"
npm run build:mp-weixin
```

> `api.example.com` 要换成你自己的 HTTPS 域名。
> 不配这个变量时，默认还是 `http://localhost:8080/api`，手机上无法使用。

然后：

1. 微信开发者工具 → 导入 `dist/build/mp-weixin`
2. 先点「预览」，用手机扫码验证登录和接口
3. 点「上传」，填版本号和备注
4. 去 mp.weixin.qq.com 后台设体验版，或提交审核后发布

注意第 1 步是 **`dist/build/`**，和开发时的 `dist/dev/` 不是一个。

完整的合法域名、AppSecret、体验版和审核步骤见
`笔记资料/docs/怎么发布微信小程序.md`。

---

## 八、附：我（AI）以后应该怎么验证

这条写给我自己看的，也让你能检查我有没有偷懒：

> **改完前端，必须确认 `dist/dev/mp-weixin` 里有对应的产物，
> 并且时间戳是刚才。只看 `dist/build/` 不算验证。**

因为 `dev` 是 watch 模式，我改代码时它如果开着就会自动重编译；
如果没开，我必须先启动它再检查。

对应的检查命令：

```bash
# 产物在不在、新不新
ls -la "C:/Users/great/Desktop/Mini Program Development/uni-app/my-mp/dist/dev/mp-weixin/pages/teacher/score/"

# 新文案有没有编进去
grep -o "新建考试" ".../dist/dev/mp-weixin/pages/teacher/score/index.wxml"
```
