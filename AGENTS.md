# 项目约定

- 本项目为日常账本，远程仓库：https://github.com/wfeng5830-lab/beiwang-licai 。日常开发目录为 E:\codex\计费软件。
- 后续采用 GitHub 工作流：先检查工作区和远程状态，使用 feature/ 或 fix/ 分支，独立提交，通过 Pull Request 检查与合并。禁止强制推送和覆盖他人改动。
- 需求与缺陷使用 Issues；发布使用版本标签和 Releases。遵循 CONTRIBUTING.md。
- 修改后运行 npm test；安卓相关变更执行相应 Java 测试和构建，明确尚未完成的真机验证。
- 不提交 .tools、构建缓存、签名密钥、个人账单或备忘录导出文件。已有 APK 为首次归档，后续安装包通过 Releases 分发。
- 用户提出 demo 时，使用模拟数据，重点展示整体界面和操作流程，无需实现具体后端。
- 打开网页默认使用 Chrome。
- 新建项目时，先在 GitHub 查找相似项目，反馈结果并提供链接。
