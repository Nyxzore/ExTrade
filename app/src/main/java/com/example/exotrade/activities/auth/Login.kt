package com.example.exotrade.activities.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.activities.MainHostActivity
import com.example.exotrade.databinding.AuthActivityLoginBinding
import com.example.exotrade.viewmodels.LoginViewModel
import com.example.exotrade.viewmodels.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * Entry activity for user authentication.
 * Handles persistent login and manages the recovery
 * of E2EE identity keys using the user's password.
 */
class Login : BaseActivity() {

    private lateinit var binding: AuthActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels { ViewModelFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val session = ExoTradeApplication.container.sessionRepository
        if (session.isLoggedIn() && session.isRememberMe()) {
            goToMainPage()
            return
        }

        binding = AuthActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        observeViewModel()
        
        if (session.isLoggedIn()) {
            viewModel.verifySession()
        }
    }

    private fun initViews() {
        val session = ExoTradeApplication.container.sessionRepository
        binding.etUsername.setText(session.getUsername() ?: "")
        binding.cbRememberMe.isChecked = session.isRememberMe()

        // Optimistic performance: preload species
        viewModel.preloadSpecies()

        binding.btnLogin.setOnClickListener { login() }
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, CreateAccount::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.sessionVerified.collect { verified ->
                        if (verified == true) {
                            goToMainPage()
                        }
                    }
                }
                launch {
                    viewModel.loginSuccess.collect { success ->
                        if (success) {
                            goToMainPage()
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { message ->
                        if (message != null) {
                            Toast.makeText(this@Login, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.btnLogin.isEnabled = !isLoading
                        // Optionally show a progress bar
                    }
                }
            }
        }
    }

    private fun login() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val rememberMe = binding.cbRememberMe.isChecked

        viewModel.login(username, password, rememberMe)
    }

    private fun goToMainPage() {
        val intent = Intent(this, MainHostActivity::class.java)
        intent.data = this.intent.data
        startActivity(intent)
        finish()
    }
}
