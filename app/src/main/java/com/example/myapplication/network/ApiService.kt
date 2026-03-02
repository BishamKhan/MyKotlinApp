package com.example.myapplication.network

import com.example.myapplication.model.LoginResponse
import com.example.myapplication.model.PermissionsResponse
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @FormUrlEncoded
    @POST("gestione/api/login")
    fun login(
        @Field("u") username: String,
        @Field("p") password: String,
        @Field("lat") latitude: String,
        @Field("lng") longitude: String
    ): Call<LoginResponse>

    @GET("gestione/api/waterPermissions")
    fun getPermissions(
        @Header("Token") token: String
    ): Call<PermissionsResponse>
}