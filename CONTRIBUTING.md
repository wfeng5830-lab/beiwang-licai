# 项目版本管理

仓库：https://github.com/wfeng5830-lab/beiwang-licai

日常开发目录：`E:\codex\计费软件`。此目录直接连接 GitHub，继续在这里开发。

1. 开始前检查 `git status`，保存未提交修改，再执行 `git switch main` 和 `git pull --ff-only`。
2. 新功能使用 `git switch -c feature/简短英文名称`；修复使用 `fix/简短英文名称`。
3. 完成一个独立改动后运行 `npm test`；安卓改动还要执行 README 中的构建与对应 Java 测试，并记录真机验证情况。
4. 用 `git add 文件路径` 选择文件，再用 `git diff --cached` 检查内容，执行 `git commit -m "说明改动内容"`。
5. 执行 `git push -u origin 分支名`，在 GitHub 创建 Pull Request，说明改动及验证结果，检查后合并到 main。
6. 使用 Issues 记录需求和问题；正式发布时创建版本标签和 GitHub Release，上传 APK 与更新说明。

首次导入保留 output 中已有的 1.0～1.4 安装包和说明。后续新 APK 通过 Releases 分发，避免持续增加仓库体积。签名密钥留在本机并另行安全备份，不能提交；更换签名会影响覆盖安装。

真实账单、备忘录、个人导出备份不进入仓库。Git 管理软件文件，手机中的数据仍需在应用设置里导出备份。
