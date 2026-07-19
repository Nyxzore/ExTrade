package com.example.exotrade.activities.auth

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.databinding.AuthActivityResetPasswordBinding
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ResetPasswordActivity : BaseActivity() {

    private lateinit var binding: AuthActivityResetPasswordBinding
    private var token: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AuthActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        token = intent.data?.getQueryParameter("token")
        if (token == null) {
            Toast.makeText(this, "Invalid reset link", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnResetPassword.setOnClickListener {
            val pass = binding.etPassword.text.toString().trim()
            val confirm = binding.etConfirmPassword.text.toString().trim()

            if (pass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pass != confirm) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            resetPassword(pass)
        }
    }

    private fun resetPassword(password: String) {
        val params = mapOf(
            "token" to (token ?: ""),
            "password" to password
        )

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("auth/reset-password", params)
                val json = Json.parseToJsonElement(response).jsonObject
                val message = json["message"]?.jsonPrimitive?.content ?: "Password updated"
                
                Toast.makeText(this@ResetPasswordActivity, message, Toast.LENGTH_LONG).show()
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ResetPasswordActivity, "Failed to reset password", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
