package com.dosius.smart.data.remote.api

import com.dosius.smart.data.remote.dto.ConnectionsResponseDto
import com.dosius.smart.data.remote.dto.GraphResponseDto
import com.dosius.smart.data.remote.dto.LoginRequestDto
import com.dosius.smart.data.remote.dto.LoginResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface LibreLinkUpApi {
    @POST("llu/auth/login")
    suspend fun login(@Body request: LoginRequestDto): LoginResponseDto

    @GET("llu/connections")
    suspend fun getConnections(
        @Header("Authorization") bearerToken: String,
        @Header("Account-Id") accountId: String
    ): ConnectionsResponseDto

    @GET("llu/connections/{patientId}/graph")
    suspend fun getGraph(
        @Path("patientId") patientId: String,
        @Header("Authorization") bearerToken: String,
        @Header("Account-Id") accountId: String
    ): GraphResponseDto

}