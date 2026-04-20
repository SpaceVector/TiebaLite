package com.huanchengfly.tieba.post.api.retrofit.interceptors

import com.huanchengfly.tieba.post.App
import com.huanchengfly.tieba.post.utils.ClientUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

object CookieInterceptor : Interceptor {
    private val cookieScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        val cookies = response.headers("Set-Cookie")
        if (cookies.isNotEmpty()) {
            cookies.forEach {
                val cookieName = it.substringBefore("=")
                val cookieValue = it.substringAfter("=").substringBefore(";")
                if (cookieName.equals(
                        "BAIDUID",
                        ignoreCase = true
                    ) && ClientUtils.baiduId.isNullOrEmpty()
                ) {
                    cookieScope.launch {
                        ClientUtils.saveBaiduId(App.INSTANCE, cookieValue)
                    }
                }
            }
        }

        return response
    }
}
