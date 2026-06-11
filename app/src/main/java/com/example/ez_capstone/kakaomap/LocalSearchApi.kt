package com.example.ez_capstone.kakaomap

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface LocalSearchApi {

    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("size") size: Int = 10
    ): LocalSearchResponse
}