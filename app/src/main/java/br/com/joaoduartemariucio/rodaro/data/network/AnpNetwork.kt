package br.com.joaoduartemariucio.rodaro.data.network

import retrofit2.Retrofit

object AnpNetwork {
    const val BASE_URL = "https://rodaro.web.app/"

    fun api(
        userAgent: String,
        baseUrl: String = BASE_URL,
        log: ((String) -> Unit)? = null,
    ): AnpApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(PublicHttp.client(userAgent, log = log))
            .build()
            .create(AnpApi::class.java)
}
