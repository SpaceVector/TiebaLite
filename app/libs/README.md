# `app/libs`
本目录用于放置模块级本地依赖，例如手工引入的 `jar` 文件。
只有无法通过 Maven 仓库管理的依赖才应放这里；新增文件时要同时确认 `app/build.gradle` 中的 `fileTree` 或显式依赖是否覆盖到。
