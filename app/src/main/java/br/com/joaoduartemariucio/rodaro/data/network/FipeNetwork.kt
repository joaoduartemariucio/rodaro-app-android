package br.com.joaoduartemariucio.rodaro.data.network

import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object FipeNetwork {
    const val BASE_URL = "https://fipe.parallelum.com.br/api/v2/"

    fun api(
        userAgent: String,
        baseUrl: String = BASE_URL,
        cache: Cache? = null,
        log: ((String) -> Unit)? = null,
    ): FipeApi =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(PublicHttp.client(userAgent, cache, log))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FipeApi::class.java)
}
