# `.circleci`
本目录存放 CircleCI 持续集成配置。
当前 `config.yml` 负责在 CI 中解密签名文件、生成 `keystore.properties`、下载 Gradle 依赖，并构建 `resguardRelease` 产物。这里只处理流水线与发布环境问题，不放应用业务代码。
