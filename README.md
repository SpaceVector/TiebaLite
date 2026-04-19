# <p align="center">Tieba Lite</p>
<p align="center">
    <a href="https://github.com/HuanCheng65/TiebaLite/actions/workflows/build.yml">
        <img alt="Build Status" src="https://github.com/HuanCheng65/TiebaLite/actions/workflows/build.yml/badge.svg?branch=4.0-dev">
    </a>
    <a href="https://t.me/tblite_discuss">
        <img alt="Status" src="https://img.shields.io/badge/-Telegram-blue?logo=telegram&style=flat">
    </a>
</p>

贴吧 Lite 是一个**非官方**的贴吧客户端。

## 说明

**本软件及源码仅供学习交流使用，严禁用于商业用途。**

## 友情链接

+ [Starry-OvO/aiotieba: Asynchronous I/O Client for Baidu Tieba](https://github.com/Starry-OvO/aiotieba)
+ [n0099/tbclient.protobuf: 百度贴吧客户端 Protocol Buffers 定义文件合集](https://github.com/n0099/tbclient.protobuf)

## 开发环境
当前开发分支 `4.0-dev` 正在升级到 `Android 16 (API 36)`。

- Gradle Wrapper: `8.11.1`
- Android Gradle Plugin: `8.10.1`
- Gradle JDK: `17`
- compileSdk / targetSdk: `36`
- Android Studio: `Meerkat 2024.3.1 Patch 1` 或更新版本

如果 Android Studio Sync 失败，请先确认项目的 `Gradle JDK` 已切换到 `17`。  
在 macOS 命令行中，可以用 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` 切到 JDK 17。
