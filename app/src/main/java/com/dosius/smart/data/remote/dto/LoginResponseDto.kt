package com.dosius.smart.data.remote.dto

import kotlinx.serialization.Serializable
@Serializable
data class LoginResponseDto( val status: Int, val data: LoginDataDto? = null )

@Serializable
data class LoginDataDto( val redirect: Boolean = false, val authTicket: AuthTicketDto? = null, val user: UserDto )
@Serializable
data class UserDto( val id: String )

@Serializable
data class AuthTicketDto( val token: String, val duration: Long, val expires: Long )
