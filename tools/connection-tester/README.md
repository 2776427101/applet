# 班级助手 · 连接测试工具

给同学用的独立小工具：**验证隧道地址或局域网地址到底能不能连上**，并说清楚卡在哪一环。

位于主仓库 `class-assistant/tools/connection-tester/`，与小程序主工程互不影响——独立 `.csproj`，不参与小程序构建。

- 技术栈：C# / .NET 8 / WinForms
- 产物：`dist/ConnectionTester.exe`（自包含单文件，63 MB，目标机无需安装 .NET）
- **仓库里只存源码**：`bin/` `obj/` `dist/` 均被 `.gitignore` 排除，exe 不进 git（理由见第五节）

---

## 一、它能回答什么问题

| 现象 | 工具给出的答案 |
|---|---|
| 小程序报「网络异常，请检查后端是否启动」 | 是 DNS 挂了、端口没通、证书被拒，还是路径写错 |
| 隧道给了地址但打不开 | 隧道没运行 / 端口抄错 / 被节点拦截（501） |
| 浏览器能开，小程序不能 | 证书是否自签（微信真机会拒，开调试也没用） |
| 别人能不能连我电脑 | 本机 IP 是否正确、防火墙有没有放行 |

**探测分四段，逐段判定**：`DNS → TCP → TLS 证书 → HTTP`。
断在哪一段，结论就写在哪一段，不给「连不上」这种废话。

---

## 二、两种模式

### ① 内网穿透隧道

只读操作，**不修改本机任何设置**。

1. 域名：隧道提供方给的域名，例如 `frp-sea.com`
2. 端口：冒号后面那段，例如 `23200`
3. 协议：隧道开了「自动 HTTPS」就选 `https`，否则 `http`
4. 测试路径：默认 `/api/teacher/home/overview`
5. 点「开始测试」

### ② 局域网 IP 直连

会修改本机：写配置文件 + 可选地改防火墙。

1. 工具自动列出本机 IPv4，默认选真实无线/有线网卡（VMware、Radmin、LetsTAP 等虚拟网卡默认隐藏，可勾「显示虚拟网卡」）
2. 下方实时预览将写入的那一行：`VITE_API_BASE_URL=http://10.0.0.5:8080/api`
3. 点「写入配置」→ 改前自动备份为 `.env.production.bak`，只替换 `VITE_API_BASE_URL` 一行，注释原样保留
4. 写入后按提示 `npm run build:mp-weixin` 重新构建，再在开发者工具里「编译 + 预览」

防火墙三个按钮：**查询规则 / 开放端口 / 关闭端口**，规则名统一为 `班级助手-<端口>`。

---

## 三、权限设计（按需提权）

主程序以**普通权限**运行，只有点防火墙按钮时才通过 `runas` 拉起自身的子进程执行，用完即退。

| 操作 | 是否弹 UAC |
|---|---|
| 打开工具、跑测试 | 否 |
| 写配置文件 | 否 |
| 查询 / 开放 / 关闭端口 | 是（每次一次） |

之所以不做成「整体管理员权限」：典型流程下两者都只弹一次 UAC，体验接近；而整体提权会让程序全程以管理员身份运行，风险面更大，在机房等受限环境也容易被策略拦住。

> 子进程无法重定向标准输出，所以结果通过临时文件回传：`--fw <op> --port <n> --out <文件>`。

---

## 四、命令行自检（无界面）

排查时想快速拿到结论，可以直接跑：

```powershell
.\ConnectionTester.exe --probe --url "https://frp-sea.com:23200/api/teacher/home/overview" --out result.txt
```

退出码：`0` 通或告警，`1` 失败，`2` 参数错误。
适合贴给别人、或以后接自动化检查。

---

## 五、重新构建与分发

```powershell
# 自包含单文件（推荐，发给同学）
.\build.ps1

# 依赖运行时（约 2MB，对方需装 .NET 8 桌面运行时）
.\build.ps1 -FrameworkDependent
```

产物在 `dist\ConnectionTester.exe`。

**exe 不进 git，直接传文件给人。** 微信/QQ 发 63 MB 没问题。不要图省事把它提交进仓库：

- git 存的是每次修改的完整快照，不是一个 diff
- 63 MB 二进制，改一行代码重新编译就多压一份，压缩对它几乎无效
- 提交十次就是 630 MB，`git clone` 会直接卡死

同理 `bin/` 和 `obj/` 也不能提交——本机实测这两个目录合计 **157 MB**，而全部源码只有 **110 KB**。

---

## 六、目录结构

```
connection-tester/
├─ ConnectionTester.csproj
├─ Program.cs              入口：GUI / --fw / --probe 三条分支
├─ MainForm.cs             界面与交互
├─ Core/
│  ├─ ProbeModels.cs       结果模型
│  ├─ Prober.cs            四段探测 + 结论生成
│  ├─ ProbeCli.cs          无界面自检入口
│  ├─ FirewallService.cs   防火墙增删查（提权子进程调度）
│  ├─ FirewallCli.cs       提权子进程入口
│  ├─ EnvWriter.cs         写 .env.production（改前备份）
│  └─ NetInfo.cs           本机 IPv4 枚举 + 配置文件定位
├─ build.ps1               一键发布
└─ dist/                   发布产物
```

---

## 七、已知边界

1. **只支持 Windows**（依赖 `netsh` 和 WinForms）。
2. **自签证书只是「告警」，不是「修复」**。工具能提前告诉你微信会拒，但换成受信任证书得换通道。
3. **配置文件默认自动定位**：从 exe 所在目录逐级向上找 `class-assistant/frontend/uni-app/my-mp/.env.production`。放别处时手动「浏览」选一次即可。
4. **热点重连后 IP 会变**，局域网模式需要重新写配置并重新构建。
