package com.homecam.te

import android.app.Application
import androidx.room.Room
import com.homecam.te.data.CameraDatabase

class HomeCamApp : Application() {

    val database: CameraDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            CameraDatabase::class.java,
            "homecam-te.db"
        ).build()
    }

    companion object {
        lateinit var instance: HomeCamApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
