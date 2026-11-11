package br.com.joaoduartemariucio.rodaro.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers

interface AnpApi {
    @Headers("Cache-Control: no-cache")
    @GET("anp/precos-uf.json")
    suspend fun prices(): ResponseBody
}
