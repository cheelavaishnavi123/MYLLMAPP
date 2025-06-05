package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.widget.Toast // Added import to resolve Toast errors

class SignOutActivity : AppCompatActivity() {

    private lateinit var signOutButton: MaterialButton
    private val TAG = "SignOutActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_sign_out) // Uses dedicated offline-compatible layout
            Log.d(TAG, "Successfully set activity_sign_out layout")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set activity_sign_out layout: ${e.message}")
            Toast.makeText(this, "Error loading sign-out screen: ${e.message}", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }

        try {
            signOutButton = findViewById(R.id.signOutBtn)
            Log.d(TAG, "SignOut button initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize signOutBtn: ${e.message}")
            Toast.makeText(this, "Error initializing button: ${e.message}", Toast.LENGTH_LONG).show()
            navigateToLogin()
            return
        }

        signOutButton.setOnClickListener {
            Log.d(TAG, "SignOut button clicked")
            try {
                // Clear user session from SharedPreferences (offline operation)
                val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                sharedPref.edit().clear().apply()
                Log.d(TAG, "SharedPreferences cleared (offline)")

                // Navigate to LoginActivity and clear back stack (offline operation)
                navigateToLogin()
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed: ${e.message}")
                Toast.makeText(this, "Error signing out: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToLogin() {
        Log.d(TAG, "Navigating to LoginActivity")
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}