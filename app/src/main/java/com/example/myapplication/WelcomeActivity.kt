package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    private lateinit var continueButton: MaterialButton
    private val TAG = "WelcomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_welcome)
            Log.d(TAG, "Successfully set activity_welcome layout")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set activity_welcome layout: ${e.message}", e)
            Toast.makeText(this, "Error loading Welcome screen: ${e.message}", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }

        try {
            continueButton = findViewById(R.id.continueButton)
            Log.d(TAG, "Continue button initialized")

            continueButton.setOnClickListener {
                Log.d(TAG, "Continue button clicked")
                try {
                    // Check login state
                    val sharedPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    val userId = sharedPrefs.getLong("USER_ID", -1L)
                    val username = sharedPrefs.getString("USERNAME", null)

                    val intent = if (userId != -1L && username != null) {
                        Log.d(TAG, "User logged in: userId=$userId, username=$username")
                        Intent(this, MainActivity::class.java).apply {
                            putExtra("USER_ID", userId)
                            putExtra("USERNAME", username)
                        }
                    } else {
                        Log.d(TAG, "No user logged in, redirecting to LoginActivity")
                        Intent(this, LoginActivity::class.java)
                    }
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to navigate: ${e.message}")
                    Toast.makeText(this, "Error navigating: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize continue button: ${e.message}", e)
            navigateToLogin()
        }
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
//import android.content.Intent
//import android.os.Bundle
//import android.util.Log
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import com.google.android.material.button.MaterialButton
//
//class WelcomeActivity : AppCompatActivity() {
//
//    private lateinit var continueButton: MaterialButton
//    private val TAG = "WelcomeActivity"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_welcome)
//
//        continueButton = findViewById(R.id.continueButton)
//
//        val username = intent.getStringExtra("USERNAME") ?: run {
//            Log.e(TAG, "No USERNAME extra provided")
//            Toast.makeText(this, "Error: No username provided", Toast.LENGTH_LONG).show()
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()
//            return
//        }
//
//        if (username.isBlank()) {
//            Log.e(TAG, "USERNAME is empty or blank")
//            Toast.makeText(this, "Error: Invalid username", Toast.LENGTH_LONG).show()
//            startActivity(Intent(this, LoginActivity::class.java))
//            finish()
//            return
//        }
//
//        Log.d(TAG, "Received username: $username")
//
//        continueButton.setOnClickListener {
//            Log.d(TAG, "Continue button clicked for username: $username")
//            try {
//                val intent = Intent(this, MainActivity::class.java)
//                intent.putExtra("USERNAME", username)
//                startActivity(intent)
//                finish()
//            } catch (e: Exception) {
//                Log.e(TAG, "Failed to start MainActivity: ${e.message}")
//                Toast.makeText(this, "Error starting main activity", Toast.LENGTH_LONG).show()
//            }
//        }
//    }
//}