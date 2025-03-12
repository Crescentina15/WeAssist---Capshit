package com.remedio.weassist

import android.app.Application
import com.cloudinary.android.MediaManager

class WeAssistApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Cloudinary once globally
        CloudinaryManager.initialize(this)
    }
}
