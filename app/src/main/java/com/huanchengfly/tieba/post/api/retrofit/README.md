# `api/retrofit`
本目录封装基于 Retrofit 的接口实现。
这里集中放置 `RetrofitTiebaApi`、空响应转换工厂、子接口定义以及异常处理。网络栈的底层装配优先在这里调整，不要在页面层直接 new Retrofit 组件。
