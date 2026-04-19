# `app/src/main/assets`
本目录存放会原样打包进 APK 的静态资产。
当前主要是 WebView 注入脚本、LitePal 配置和外部能力声明，如 `tblite.js`、`night.js`、`litepal.xml`。这些文件通常由运行时直接读取，不走 Android 资源 ID。
