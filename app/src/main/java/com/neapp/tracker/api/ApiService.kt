package com.neapp.tracker.api

import com.neapp.tracker.model.DeliveryConfig
import com.neapp.tracker.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface ApiService {

    @GET("api/tracker/drivers/{id}/config/")
    suspend fun getConfig(
        @Path("id") id: String,
        @Header("X-API-Key") apiKey: String = BuildConfig.API_KEY
    ): DeliveryConfig

    companion object {
        private const val BASE_URL = "https://sys.fastvistos.com.br/"

        fun create(): ApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}