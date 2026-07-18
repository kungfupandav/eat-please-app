package com.eatplease.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
