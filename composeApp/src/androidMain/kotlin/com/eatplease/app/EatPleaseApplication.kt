package com.eatplease.app

import android.app.Application

class EatPleaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: EatPleaseApplication
            private set
    }
}
