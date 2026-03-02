package com.example.myapplication.model

data class LoginResponse(
    val Token: String,
    val TrackingToken: String,
    val type: String,
    val block: Boolean,
    val msg: String,
    val startTimePause: String,
    val endTimePause: String,
    val userid: String
)