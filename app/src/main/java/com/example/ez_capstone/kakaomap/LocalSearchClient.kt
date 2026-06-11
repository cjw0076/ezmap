package com.example.ez_capstone.kakaomap

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object LocalSearchClient {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://dapi.kakao.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: LocalSearchApi = retrofit.create(LocalSearchApi::class.java)
}