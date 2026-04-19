# `activities`
本目录存放 Android `Activity` 级入口页面，如 `MainActivity`、`ThreadActivity`、`LoginActivity`。
这里负责页面生命周期、Intent 入口和顶层界面装配。可复用逻辑应下沉到 `fragments/`、`components/` 或 `utils/`，避免 Activity 继续变重。
