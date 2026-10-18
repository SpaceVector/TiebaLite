# `app`
这是项目唯一的 Android 应用模块。
模块级构建配置在 `app/build.gradle.kts`，运行时代码、资源、测试和本地依赖都从这里展开。绝大多数功能开发都应在本目录下完成。

Debug 包使用独立包名 `com.huanchengfly.tieba.post.debug`，桌面名称为“贴吧 Lite（测试）”，可与日常应用共存。仪器测试后保留安装包，避免测试清理误删应用数据；不要在私人设备上对 release 包运行带卸载清理的测试。

恢复版本时应核对源码提交与 APK 版本号，不要直接安装 `build/outputs` 中残留的历史包。

Release 仅使用 `keystore.properties` 指定的固定专用密钥，不再回退到自动生成的 debug 密钥；未配置时只能构建未签名 APK，`4.0-dev` 的 CI 发布会直接失败。Debug 测试包仍使用本机 debug 密钥，不改变现有测试安装的签名。固定 Release 证书及 CI 配置见 `.github/RELEASING.md`。
