package com.example.myapplication

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair

class SplashActivity : AppCompatActivity() {
    private val TAG = "SplashActivity"
    private val SPLASH_DURATION = 2500L // Extended to 2.5s for smoother animations

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_splash)
            Log.d(TAG, "Successfully set activity_splash layout")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set activity_splash layout: ${e.message}", e)
            navigateToLogin()
            return
        }

        try {
            val splashText = findViewById<TextView>(R.id.splashText)
            Log.d(TAG, "Splash text view initialized")

            // Create unique and smooth animations
            val fadeIn = ObjectAnimator.ofFloat(splashText, "alpha", 0f, 1f).apply {
                duration = 1200
                interpolator = AccelerateDecelerateInterpolator()
            }

            val scaleX = ObjectAnimator.ofFloat(splashText, "scaleX", 0.5f, 1.2f, 1f).apply {
                duration = 1500
                interpolator = OvershootInterpolator()
            }

            val scaleY = ObjectAnimator.ofFloat(splashText, "scaleY", 0.5f, 1.2f, 1f).apply {
                duration = 1500
                interpolator = OvershootInterpolator()
            }

            val rotate = ObjectAnimator.ofFloat(splashText, "rotation", 0f, 360f).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
            }

            val translateY = ObjectAnimator.ofFloat(splashText, "translationY", 100f, 0f).apply {
                duration = 1200
                interpolator = AccelerateDecelerateInterpolator()
            }

            // Pulsating glow effect
            val glow = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 800
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    val value = it.animatedValue as Float
                    splashText.setShadowLayer(10f * value, 0f, 0f, 0xFF0288D1.toInt())
                }
            }

            // Combine animations
            val animatorSet = AnimatorSet().apply {
                playTogether(fadeIn, scaleX, scaleY, rotate, translateY, glow)
                start()
                Log.d(TAG, "Started combined animations")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize or animate splash text: ${e.message}", e)
            navigateToLogin()
            return
        }

        // Transition after 2.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Log.d(TAG, "Splash duration completed, navigating to WelcomeActivity")
                val intent = Intent(this, WelcomeActivity::class.java)

                // Smooth activity transition (optional, with fallback)
                val splashTextView = findViewById<TextView>(R.id.splashText)
                val options = if (splashTextView != null) {
                    ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this,
                        Pair.create(splashTextView, "splash_text_transition")
                    )
                } else {
                    ActivityOptionsCompat.makeSceneTransitionAnimation(this)
                }
                startActivity(intent, options.toBundle())
                finish()
                Log.d(TAG, "Navigated to WelcomeActivity with transition")
            } catch (e: Exception) {
                Log.e(TAG, "Navigation failed: ${e.message}", e)
                navigateToLogin()
            }
        }, SPLASH_DURATION)
    }

    private fun navigateToLogin() {
        Log.d(TAG, "Navigating to LoginActivity (fallback)")
        try {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to LoginActivity: ${e.message}", e)
        }
    }
}


//package com.example.myapplication
//
//import android.animation.AnimatorSet
//import android.animation.ObjectAnimator
//import android.animation.ValueAnimator
//import android.content.Intent
//import android.os.Bundle
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import android.view.animation.AccelerateDecelerateInterpolator
//import android.view.animation.OvershootInterpolator
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityOptionsCompat
//import androidx.core.util.Pair
//
//class SplashActivity : AppCompatActivity() {
//    private val TAG = "SplashActivity"
//    private val SPLASH_DURATION = 2500L // Extended to 2.5s for smoother animations
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        try {
//            setContentView(R.layout.activity_splash)
//            Log.d(TAG, "Successfully set activity_splash layout")
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to set activity_splash layout: ${e.message}", e)
//            navigateToLogin()
//            return
//        }
//
//        try {
//            val splashText = findViewById<TextView>(R.id.splashText)
//            Log.d(TAG, "Splash text view initialized")
//
//            // Create unique and smooth animations
//            val fadeIn = ObjectAnimator.ofFloat(splashText, "alpha", 0f, 1f).apply {
//                duration = 1200
//                interpolator = AccelerateDecelerateInterpolator()
//            }
//
//            val scaleX = ObjectAnimator.ofFloat(splashText, "scaleX", 0.5f, 1.2f, 1f).apply {
//                duration = 1500
//                interpolator = OvershootInterpolator()
//            }
//
//            val scaleY = ObjectAnimator.ofFloat(splashText, "scaleY", 0.5f, 1.2f, 1f).apply {
//                duration = 1500
//                interpolator = OvershootInterpolator()
//            }
//
//            val rotate = ObjectAnimator.ofFloat(splashText, "rotation", 0f, 360f).apply {
//                duration = 1000
//                interpolator = AccelerateDecelerateInterpolator()
//            }
//
//            val translateY = ObjectAnimator.ofFloat(splashText, "translationY", 100f, 0f).apply {
//                duration = 1200
//                interpolator = AccelerateDecelerateInterpolator()
//            }
//
//            // Pulsating glow effect
//            val glow = ValueAnimator.ofFloat(0f, 1f).apply {
//                duration = 800
//                repeatCount = ValueAnimator.INFINITE
//                repeatMode = ValueAnimator.REVERSE
//                addUpdateListener {
//                    val value = it.animatedValue as Float
//                    splashText.setShadowLayer(10f * value, 0f, 0f, 0xFF0288D1.toInt())
//                }
//            }
//
//            // Combine animations
//            val animatorSet = AnimatorSet().apply {
//                playTogether(fadeIn, scaleX, scaleY, rotate, translateY, glow)
//                start()
//                Log.d(TAG, "Started combined animations")
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to initialize or animate splash text: ${e.message}", e)
//            navigateToLogin()
//            return
//        }
//
//        // Transition after 2.5 seconds
//        Handler(Looper.getMainLooper()).postDelayed({
//            try {
//                Log.d(TAG, "Splash duration completed, checking SharedPreferences")
//                val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
//                val username = sharedPref.getString("USERNAME", null)
//                Log.d(TAG, "Username from SharedPreferences: $username")
//
//                val intent = if (username != null && username.isNotBlank()) {
//                    Intent(this, MainActivity::class.java).apply {
//                        putExtra("USERNAME", username)
//                    }
//                } else {
//                    Intent(this, LoginActivity::class.java)
//                }
//
//                // Smooth activity transition
//                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
//                    this,
//                    Pair.create(findViewById(R.id.splashText), "splash_text_transition")
//                )
//                startActivity(intent, options.toBundle())
//                finish()
//                Log.d(TAG, "Navigated to ${if (username != null) "MainActivity" else "LoginActivity"} with transition")
//            } catch (e: Exception) {
//                Log.e(TAG, "Navigation failed: ${e.message}", e)
//                navigateToLogin()
//            }
//        }, SPLASH_DURATION)
//    }
//
//    private fun navigateToLogin() {
//        Log.d(TAG, "Navigating to LoginActivity (fallback)")
//        try {
//            val intent = Intent(this, LoginActivity::class.java)
//            startActivity(intent)
//            finish()
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to navigate to LoginActivity: ${e.message}", e)
//        }
//    }
//}