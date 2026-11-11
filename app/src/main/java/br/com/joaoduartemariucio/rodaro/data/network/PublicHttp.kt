package br.com.joaoduartemariucio.rodaro.data.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object PublicHttp {
    private const val TIMEOUT_SECONDS = 20L

    fun client(
        userAgent: String,
        cache: Cache? = null,
        log: ((String) -> Unit)? = null,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .cache(cache)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", userAgent)
                        .build(),
                )
            }.apply { log?.let { addInterceptor(HttpLogInterceptor(it)) } }
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun userAgent(versionName: String): String = "Rodaro-Android/$versionName"
}
