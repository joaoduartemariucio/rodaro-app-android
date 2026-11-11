package br.com.joaoduartemariucio.rodaro.data.network

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface FipeApi {
    @GET("{type}/brands")
    suspend fun brands(
        @Path("type") type: String,
    ): List<FipeOptionDto>

    @GET("{type}/brands/{brand}/models")
    suspend fun models(
        @Path("type") type: String,
        @Path("brand") brand: String,
    ): List<FipeOptionDto>

    @GET("{type}/brands/{brand}/models/{model}/years")
    suspend fun years(
        @Path("type") type: String,
        @Path("brand") brand: String,
        @Path("model") model: String,
    ): List<FipeOptionDto>

    @Headers("Cache-Control: no-cache")
    @GET("{type}/brands/{brand}/models/{model}/years/{year}")
    suspend fun detail(
        @Path("type") type: String,
        @Path("brand") brand: String,
        @Path("model") model: String,
        @Path("year") year: String,
    ): FipeDetailDto

    @Headers("Cache-Control: no-cache")
    @GET("{type}/{fipeCode}/years/{year}")
    suspend fun price(
        @Path("type") type: String,
        @Path("fipeCode") fipeCode: String,
        @Path("year") year: String,
    ): FipeDetailDto

    @Headers("Cache-Control: no-cache")
    @GET("{type}/{fipeCode}/years/{year}/history")
    suspend fun history(
        @Path("type") type: String,
        @Path("fipeCode") fipeCode: String,
        @Path("year") year: String,
    ): FipeDetailDto
}
