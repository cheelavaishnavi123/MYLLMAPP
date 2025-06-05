package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SignUpActivity : AppCompatActivity() {

    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var signupButton: MaterialButton
    private lateinit var dbHelper: UserDatabase
    private val TAG = "SignUpActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        try {
            usernameInput = findViewById(R.id.usernameInput)
            passwordInput = findViewById(R.id.passwordInput)
            confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
            usernameLayout = findViewById(R.id.usernameLayout)
            passwordLayout = findViewById(R.id.passwordLayout)
            confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
            signupButton = findViewById(R.id.signupButton)
            dbHelper = UserDatabase(this)
            Log.d(TAG, "Initialized UI components and database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UI or database: ${e.message}", e)
            Toast.makeText(this, "Error initializing signup screen: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        signupButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            Log.d(TAG, "Signup attempt: username=$username")

            when {
                username.isEmpty() -> {
                    usernameLayout.error = "Username is required"
                    Log.w(TAG, "Empty username")
                }
                password.isEmpty() -> {
                    passwordLayout.error = "Password is required"
                    Log.w(TAG, "Empty password")
                }
                confirmPassword.isEmpty() -> {
                    confirmPasswordLayout.error = "Please confirm password"
                    Log.w(TAG, "Empty confirm password")
                }
                password != confirmPassword -> {
                    confirmPasswordLayout.error = "Passwords do not match"
                    Log.w(TAG, "Passwords do not match")
                }
                else -> {
                    usernameLayout.error = null
                    passwordLayout.error = null
                    confirmPasswordLayout.error = null
                    try {
                        if (dbHelper.addUser(username, password)) {
                            Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Signup successful: username=$username")
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        } else {
                            usernameLayout.error = "Username already exists"
                            Log.w(TAG, "Signup failed: username already exists")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Signup failed due to database error: ${e.message}", e)
                        Toast.makeText(this, "Error during signup: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}