# `app`
这是项目唯一的 Android 应用模块。
模块级构建配置在 `app/build.gradle.kts`，运行时代码、资源、测试和本地依赖都从这里展开。绝大多数功能开发都应在本目录下完成。

Debug 包使用独立包名 `com.huanchengfly.tieba.post.debug`，桌面名称为“贴吧 Lite（测试）”，可与日常应用共存。仪器测试后保留安装包，避免测试清理误删应用数据；不要在私人设备上对 release 包运行带卸载清理的测试。

恢复版本时应核对源码提交与 APK 版本号，不要直接安装 `build/outputs` 中残留的历史包。
