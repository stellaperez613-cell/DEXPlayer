package com.example.dexplayer

import android.app.Application
import com.example.dexplayer.util.DexLog

class DexPlayerApp : Application() {

    override fun onCreate() {
        // DexLog MUST be the very first thing — before anything else can crash
        DexLog.init(this)
        DexLog.section("Application.onCreate")

        super.onCreate()

        DexLog.i("DexPlayerApp", "Application created successfully")
    }
}
