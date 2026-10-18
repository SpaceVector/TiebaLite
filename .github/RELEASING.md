# GitHub Actions 与自动发布

`workflows/build.yml` 编译 Release APK，分别保留 APK 和 R8 mapping 的 Actions 附件；构建任务全部成功后，独立的 `release` 任务**只将 APK 文件**发布到当前仓库的 GitHub Releases，不会重新编译。

Actions 页面下载的 artifact 仍是 ZIP，但 APK artifact 内仅包含 `.apk`。发布任务会自动解包并直接上传 APK，不上传 ZIP、mapping、`output-metadata.json` 或 baseline 文件。发布脚本也会拒绝任何非 `.apk` 的附件。

GitHub 自带的 **Source code (zip / tar.gz)** 下载链接仍会显示，它们不是工作流上传的附件，参见[官方说明](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)。

## 发布规则

- **只有 `4.0-dev` 分支自动发布**；`master`、其他分支、标签和所有 PR 都只构建、不发布。
- 工作流进入默认分支后，可以在 Actions 中用 **Run workflow** 手动构建；只有选择 `4.0-dev` 时才发布。
- 分支构建自动创建 `v<APK versionName>` 标签，例如 `v4.0.0-beta.1+6b45870`，并明确指向本次构建的提交。同一个提交重跑不会覆盖已发布的版本；上传中断遗留的草稿会在重跑时继续发布。
- 根据 APK 版本中的预发布标识自动设置 **Pre-release**（例如 `beta`、`rc`）；非预发布版本发布为普通 Release。
- 自动从同一提交历史中最近已发布的版本生成非合并提交日志，包含直接提交而不仅是 PR。首次发布时回退到最近祖先标签；没有祖先标签时使用提交历史。页面展示最近 100 条并附完整记录及 Actions 链接。

`publish_release.sh` 使用 GitHub 托管 Ubuntu runner 自带的 `gh`，不需要额外的 Release Action 或个人访问令牌。构建任务只有 `contents: read`，发布任务单独申请 `contents: write`。如果仓库的组织策略限制写权限，需要管理员允许该发布权限；Fork 仓库也需要先启用 Actions。

## 固定 Release 签名配置

从 2026-10-18 起，Release 使用一次性创建、长期保存的专用密钥，不再使用 runner 临时生成的 debug 密钥：

- 证书 SHA-256：`56b77f9d4f8ea1ac77f7761721a173e3bd15300127fbc64429ab595f221c5183`
- 密钥别名：`tiebalite-release`
- 证书名称：`CN=TiebaLite Release, O=SpaceVector, C=CN`
- 密钥算法：RSA-3072；密钥和密码必须保留离线备份，禁止提交到仓库。

**首次迁移说明：** 该证书不同于 `6b45870` APK 的旧证书 `3223c578b313b1b04224c510dc6707a9c437fff5de0512e40101f41b1ea8a485`，因此首次安装不能直接覆盖旧签名版本，需要一次卸载重装。安装迁移由用户自行完成，工作流不会卸载或操作手机。迁移之后，只要保留本密钥、包名和不降低 versionCode，后续 Release 即可覆盖更新。固定新签名不代表恢复了旧私钥。

在仓库 **Settings → Secrets and variables → Actions** 设置以下配置（沿用已有工作流的名称）：

| 类型 | 名称 | 内容 |
| --- | --- | --- |
| Variable | `RELEASE_KEYSTORE` | 还原密钥库的文件名：`release.jks` |
| Variable | `RELEASE_KEY_ALIAS` | `tiebalite-release` |
| Secret | `KEYSTORE` | 固定 `release.jks` 文件的 Base64 内容，不是 APK 或证书文件 |
| Secret | `RELEASESTOREPASSWORD` | 密钥库密码 |
| Secret | `RELEASEKEYPASSWORD` | 密钥密码 |

`4.0-dev` 构建若缺少任一配置则立即失败；还原密钥库后先核验其证书，再在 APK 生成后核验每个 APK 的签名。证书与上述固定指纹不一致、APK 验签失败或签名配置未确认，均不得上传发布产物。验证逻辑在 `verify_signing.py`，公钥指纹直接固定在代码中，不能仅替换 Secrets 就悄悄换签。

Release 已取消 debug 签名回退。PR 或未配置密钥的其他分支可以构建未签名 APK，但不允许发布；Debug 测试包仍使用本机 debug 密钥，不会因为 Release 配置变化而换用另一把密钥。

所有自动构建只能从上述 Secrets 还原同一份密钥，禁止每次运行 `keytool -genkeypair` 或回退到 debug 签名。密钥丢失时必须从备份恢复，不能重新生成同名文件替代。不要提交密钥文件、密码或 `keystore.properties`。

`keystore.properties` 仅由应用的 Gradle 构建脚本通过 `java.util.Properties` 读取，不参与 SweetProperty 代码生成，避免签名密码成为应用常量。

## 本地检查

在仓库根目录运行 `python3 -m unittest discover -s .github -p 'test_*.py' -v`。测试使用临时 Git 仓库、模拟的 `gh` 和公开证书测试数据，不读取真实私钥，也不会创建远程标签、上传文件或发布 Release。
