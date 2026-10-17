# GitHub Actions 与自动发布

`workflows/build.yml` 编译 Release APK，保留 APK 和 R8 mapping 的 Actions 附件；构建任务全部成功后，独立的 `release` 任务将这两份产物发布到当前仓库的 GitHub Releases，不会重新编译。

## 发布规则

- **只有 `4.0-dev` 分支自动发布**；`master`、其他分支、标签和所有 PR 都只构建、不发布。
- 工作流进入默认分支后，可以在 Actions 中用 **Run workflow** 手动构建；只有选择 `4.0-dev` 时才发布。
- 分支构建自动创建 `v<APK versionName>` 标签，例如 `v4.0.0-beta.1+6b45870`，并明确指向本次构建的提交。同一个提交重跑不会覆盖已发布的版本；上传中断遗留的草稿会在重跑时继续发布。
- 根据 APK 版本中的预发布标识自动设置 **Pre-release**（例如 `beta`、`rc`）；非预发布版本发布为普通 Release。
- 自动从同一提交历史中最近已发布的版本生成非合并提交日志，包含直接提交而不仅是 PR。首次发布时回退到最近祖先标签；没有祖先标签时使用提交历史。页面展示最近 100 条并附完整记录及 Actions 链接。

`publish_release.sh` 使用 GitHub 托管 Ubuntu runner 自带的 `gh`，不需要额外的 Release Action 或个人访问令牌。构建任务只有 `contents: read`，发布任务单独申请 `contents: write`。如果仓库的组织策略限制写权限，需要管理员允许该发布权限；Fork 仓库也需要先启用 Actions。

## 正式签名配置

在仓库 **Settings → Secrets and variables → Actions** 设置以下配置（沿用已有工作流的名称）：

| 类型 | 名称 | 内容 |
| --- | --- | --- |
| Variable | `RELEASE_KEYSTORE` | 仓库根目录下的密钥库文件名，例如 `release.jks` |
| Variable | `RELEASE_KEY_ALIAS` | 密钥别名 |
| Secret | `KEYSTORE` | 密钥库文件的 Base64 内容 |
| Secret | `RELEASESTOREPASSWORD` | 密钥库密码 |
| Secret | `RELEASEKEYPASSWORD` | 密钥密码 |

未配齐时保留项目现有的 debug 签名回退行为，仍可发布 Release 构建，但工作流和 Release 说明都会警告。这不是正式签名，且不同 runner 生成的 debug 密钥可能不同，无法保证覆盖安装；需要连续升级时务必使用固定的正式密钥。不要提交密钥文件或 `keystore.properties`。

## 本地检查

在仓库根目录运行 `python3 .github/test_publish_release.py`。测试使用临时 Git 仓库和模拟的 `gh`，不会创建远程标签、上传文件或发布 Release。
