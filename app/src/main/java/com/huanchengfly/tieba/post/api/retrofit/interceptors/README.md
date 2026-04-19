# `api/retrofit/interceptors`
本目录存放 Retrofit/OkHttp 链路上的请求与响应拦截器。
这里处理 Cookie、公共请求头、通用参数、失败响应和登录态校验等横切关注点。任何改动都可能影响全局请求行为，需要审慎验证。
