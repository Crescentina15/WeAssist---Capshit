package com.remedio.weassist.LoginAndRegister

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.remedio.weassist.R

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DISPLAY_LENGTH = 3000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Get the logo image view
        val logoImageView = findViewById<ImageView>(R.id.imageView)

        // Initially hide the logo
        logoImageView.alpha = 0f

        // Create animations
        animateLogo(logoImageView)

        // Using Handler to delay the transition
        Handler(Looper.getMainLooper()).postDelayed({
            // Create exit animation
            val fadeOut = ObjectAnimator.ofFloat(logoImageView, View.ALPHA, 1f, 0f)
            fadeOut.duration = 500
            fadeOut.start()

            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Start the next activity
                    val mainIntent = Intent(this@SplashActivity, Login::class.java)
                    startActivity(mainIntent)

                    // Add a custom transition animation
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

                    finish() // Close the splash activity so the user won't go back to it
                }
            })
        }, SPLASH_DISPLAY_LENGTH.toLong())
    }

    private fun animateLogo(logoImageView: ImageView) {
        // Create fade-in animation
        val fadeIn = ObjectAnimator.ofFloat(logoImageView, View.ALPHA, 0f, 1f)
        fadeIn.duration = 1000

        // Create zoom animation
        val scaleX = ObjectAnimator.ofFloat(logoImageView, View.SCALE_X, 0.7f, 1f)
        val scaleY = ObjectAnimator.ofFloat(logoImageView, View.SCALE_Y, 0.7f, 1f)

        scaleX.duration = 1200
        scaleY.duration = 1200

        // Combine animations
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, scaleX, scaleY)
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }
}