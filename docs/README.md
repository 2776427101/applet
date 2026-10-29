# 项目文档

这里只保留与项目运行和维护直接相关的文档。

| 文档 | 内容 |
|---|---|
| [trial-mode.md](trial-mode.md) | **零成本体验版**：不买服务器不备案，让身边人扫码就能用 |
| [../deploy/README.md](../deploy/README.md) | **上线部署**：买服务器、备案、域名、HTTPS、上线检查清单 |
| [engineering-notes.md](engineering-notes.md) | **为什么这么做**：数据建模决策、框架坑、小程序坑、测试方法 |
| [api-contract.md](api-contract.md) | 接口路径、参数、响应结构和错误码 |
| [backend-setup.md](backend-setup.md) | 后端本地启动、数据库连接和接口验证 |
| [frontend-build.md](frontend-build.md) | uni-app 开发、构建和接口地址配置 |
| [wechat-release.md](wechat-release.md) | 微信小程序构建、上传、体验版和发布流程 |

两条路，按需要选：

- **只想自己测试、给身边几个人玩** → 看 `trial-mode.md`，零成本，不改代码，一天能跑通
- **要让不认识的人用、要关电脑还能用** → 看 `deploy/README.md`，约 210 元/年，2–4 周

实现接口前先看 `engineering-notes.md`（了解设计约束），再查 `api-contract.md`。

开发过程中的逐步教程、个人笔记、UI 草稿和原始 memory 日志没有放入仓库 ——
其中可复用的技术结论已脱敏后整理进 `engineering-notes.md`。
