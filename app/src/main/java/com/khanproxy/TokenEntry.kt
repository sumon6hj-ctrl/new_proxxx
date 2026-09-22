package com.khanproxy

data class TokenEntry(
    val id: Long,
    val mode: String,
    val player: String,
    val region: String,
    val jwt: String = "",
    val openId: String = "",
    val accessToken: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
