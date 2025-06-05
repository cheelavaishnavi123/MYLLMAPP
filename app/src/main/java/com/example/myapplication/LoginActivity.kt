package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.view.animation.AnimationUtils

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var signUpButton: MaterialButton
    private lateinit var dbHelper: UserDatabase
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_login)
            Log.d(TAG, "Successfully set activity_login layout")

            // Apply layout animation
            try {
                val layout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(android.R.id.content)
                val animation = AnimationUtils.loadLayoutAnimation(this, R.anim.login_layout_animation)
                layout.layoutAnimation = animation
                layout.startLayoutAnimation()
                Log.d(TAG, "Applied login layout animation")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply layout animation: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set activity_login layout: ${e.message}", e)
            Toast.makeText(this, "Error loading login screen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        try {
            usernameInput = findViewById(R.id.usernameInput)
            passwordInput = findViewById(R.id.passwordInput)
            usernameLayout = findViewById(R.id.usernameLayout)
            passwordLayout = findViewById(R.id.passwordLayout)
            loginButton = findViewById(R.id.loginButton)
            signUpButton = findViewById(R.id.signUpButton)
            dbHelper = UserDatabase(this)
            Log.d(TAG, "Initialized UI components and database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UI or database: ${e.message}", e)
            Toast.makeText(this, "Error initializing login screen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            Log.d(TAG, "Login attempt: username=$username")

            when {
                username.isEmpty() -> {
                    usernameLayout.error = "Username is required"
                    Log.w(TAG, "Empty username")
                }
                password.isEmpty() -> {
                    passwordLayout.error = "Password is required"
                    Log.w(TAG, "Empty password")
                }
                else -> {
                    usernameLayout.error = null
                    passwordLayout.error = null
                    try {
                        val userId = dbHelper.verifyUser(username, password)
                        if (userId != -1L) {
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Login successful: userId=$userId, username=$username")

                            // Save username and userId to SharedPreferences
                            val sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                            sharedPref.edit()
                                .putString("USERNAME", username)
                                .putLong("USER_ID", userId)
                                .apply()
                            Log.d(TAG, "Saved userId=$userId, username=$username to SharedPreferences")

                            val intent = Intent(this, MainActivity::class.java)
                            intent.putExtra("USER_ID", userId)
                            intent.putExtra("USERNAME", username)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show()
                            Log.w(TAG, "Login failed: invalid credentials")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Login failed due to database error: ${e.message}", e)
                        Toast.makeText(this, "Error during login: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        signUpButton.setOnClickListener {
            Log.d(TAG, "SignUp button clicked")
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }
    }
}