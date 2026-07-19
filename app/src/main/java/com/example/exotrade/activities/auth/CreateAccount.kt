package com.example.exotrade.activities.auth

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.activities.MainHostActivity
import com.example.exotrade.databinding.AuthActivityRegisterBinding
import com.example.exotrade.utils.ImageUtils
import com.example.exotrade.data.SessionRepository
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity for registering a new user account.
 * Handles profile picture selection, E2EE identity key generation (X25519),
 * and secure backup of the private key using Argon2id key derivation.
 */
class CreateAccount : AppCompatActivity() {

    private lateinit var binding: AuthActivityRegisterBinding
    private lateinit var session: SessionRepository
    private var selectedImageUri: Uri? = null
    private var pendingIdentityKeys: Pair<String, ByteArray>? = null

    /**
     * Launcher for the system image picker to select a profile picture.
     */
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            binding.imgProfile.setImageURI(uri)
            selectedImageUri = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = ExoTradeApplication.container.sessionRepository

        // Check if user is already logged in via "Remember Me"
        if (session.isLoggedIn() && session.isRememberMe()) {
            lifecycleScope.launch {
                ExoTradeApplication.container.speciesRepository.syncFromServer(false)

                val intent = Intent(this@CreateAccount, MainHostActivity::class.java)
                startActivity(intent)
                finish()
            }
            return
        }

        binding = AuthActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnCreateAccount.setOnClickListener { create_account() }
        binding.tvLogin.setOnClickListener { switch_to_login() }
    }

    /**
     * Compresses and encodes an image from a URI into a Base64 string.
     *
     * @param uri The URI of the image to encode.
     * @return The Base64 encoded string of the image, or null if encoding fails.
     */
    private fun encodeImage(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                ImageUtils.compressAndEncode(bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates input, generates encryption keys, and submits the registration request.
     */
    private fun create_account() {
        val email = binding.etEmail.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        val params = mutableMapOf(
            "email" to email,
            "username" to username,
            "password" to password,
            "mode" to "register"
        )

        // E2EE Key Generation and Backup
        try {
            val encryptionManager = ExoTradeApplication.container.encryptionManager
            val keys = encryptionManager.generateIdentityKeys()
            pendingIdentityKeys = keys

            val salt = encryptionManager.generateSalt()
            val nonce = encryptionManager.generateNonce()

            val backupKey = encryptionManager.deriveBackupKey(password, salt)

            val encryptedPrivKey = encryptionManager.encryptPrivateKey(
                keys.second,
                backupKey,
                nonce
            )

            params["public_key"] = keys.first
            params["encrypted_private_key"] = encryptedPrivKey
            params["private_key_nonce"] = Base64.encodeToString(nonce, Base64.NO_WRAP)
            params["kdf_salt"] = Base64.encodeToString(salt, Base64.NO_WRAP)

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to generate security keys: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        selectedImageUri?.let { uri ->
            encodeImage(uri)?.let { imageData ->
                params["profile_picture_data"] = imageData
            }
        }

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("auth/auth", params)
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    val uuid = json["uuid"]?.jsonPrimitive?.content ?: ""
                    val token = json["auth_token"]?.jsonPrimitive?.content ?: ""

                    session.createLoginSession(uuid, token, username, false, 0, true)
                    session.updateUserInfo(
                        username = username,
                        profilePic = json["profile_picture"]?.jsonPrimitive?.content ?: "",
                        tier = 0,
                        isAdmin = false
                    )

                    pendingIdentityKeys?.let {
                        session.saveIdentityKeys(it.first, Base64.encodeToString(it.second, Base64.NO_WRAP))
                    }

                    ExoTradeApplication.container.speciesRepository.syncFromServer(false)

                    Toast.makeText(this@CreateAccount, "Account created successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@CreateAccount, MainHostActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    val message = json["message"]?.jsonPrimitive?.content ?: "Registration failed"
                    Toast.makeText(this@CreateAccount, message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is ResponseException) {
                    try {
                        val errorBody = e.response.bodyAsText()
                        val json = Json.parseToJsonElement(errorBody).jsonObject
                        val message = json["message"]?.jsonPrimitive?.content ?: "Registration failed"
                        Toast.makeText(this@CreateAccount, message, Toast.LENGTH_SHORT).show()
                    } catch (ex: Exception) {
                        Toast.makeText(this@CreateAccount, "Registration failed (${e.response.status.value})", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@CreateAccount, "Error connecting to server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    /** Switches to the login screen. */
    private fun switch_to_login() {
        val intent = Intent(this, Login::class.java)
        startActivity(intent)
        finish()
    }
}
