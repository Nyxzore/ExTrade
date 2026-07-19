package com.example.exotrade.activities.auth

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.databinding.AuthActivityForgotPasswordBinding
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ForgotPasswordActivity : BaseActivity() {

    private lateinit var binding: AuthActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AuthActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendLink.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendResetLink(email)
        }
    }

    private fun sendResetLink(email: String) {
        val params = mapOf("email" to email)
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("auth/forgot-password", params)
                val json = Json.parseToJsonElement(response).jsonObject
                val message = json["message"]?.jsonPrimitive?.content ?: "Request sent"
                
                Toast.makeText(this@ForgotPasswordActivity, message, Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ForgotPasswordActivity, "Failed to send reset link", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
