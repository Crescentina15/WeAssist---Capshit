package com.remedio.weassist

import android.content.Context
import com.cloudinary.android.MediaManager

object CloudinaryManager {
    fun initialize(context: Context) {
        val config = mapOf(
            "cloud_name" to "db66niao7",
            "api_key" to "952961727769862",
            "api_secret" to "vSPNsLabxhQkFUlgC6eoQwpsgc0"
        )
        MediaManager.init(context, config)
    }
}
