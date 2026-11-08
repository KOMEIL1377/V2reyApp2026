package com.example

import android.app.Application
import com.example.data.VpnRepository

class V2reyBndApplication : Application() {

    lateinit var repository: VpnRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = VpnRepository(this)
    }

    companion object {
        lateinit var instance: V2reyBndApplication
            private set
    }
}
