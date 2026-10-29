# 项目文档

这里只保留与项目运行和维护直接相关的文档。

| 文档 | 内容 |
|---|---|
| [engineering-notes.md](engineering-notes.md) | **为什么这么做**：数据建模决策、框架坑、小程序坑、测试方法 |
| [api-contract.md](api-contract.md) | 接口路径、参数、响应结构和错误码 |
| [backend-setup.md](backend-setup.md) | 后端本地启动、数据库连接和接口验证 |
| [frontend-build.md](frontend-build.md) | uni-app 开发、构建和接口地址配置 |
| [wechat-release.md](wechat-release.md) | 微信小程序构建、上传、体验版和发布流程 |

建议阅读顺序：先 `engineering-notes.md`（了解设计约束），再 `api-contract.md`（实现接口），
最后按需查 `backend-setup.md` / `frontend-build.md`。

开发过程中的逐步教程、个人笔记、UI 草稿和原始 memory 日志没有放入仓库 ——
其中可复用的技术结论已脱敏后整理进 `engineering-notes.md`。
