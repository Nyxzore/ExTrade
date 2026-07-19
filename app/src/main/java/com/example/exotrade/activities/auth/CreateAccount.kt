package com.example.exotrade.activities.auth

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.DigitalCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetDigitalCredentialOption
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.activities.MainHostActivity
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.databinding.AuthActivityRegisterBinding
import com.example.exotrade.utils.*
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import java.security.SecureRandom

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
    private lateinit var credentialManager: CredentialManager

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

        credentialManager = CredentialManager.create(this)

        binding.btnSelectImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnVerifyEmail.setOnClickListener { fetchVerifiedEmail() }
        binding.btnCreateAccount.setOnClickListener { create_account() }
        binding.tvLogin.setOnClickListener { switch_to_login() }
    }

    private fun fetchVerifiedEmail() {
        val nonce = generateSecureRandomNonce()
        val openId4vpRequest = """
        {
          "requests": [
            {
              "protocol": "openid4vp-v1-unsigned",
              "data": {
                "response_type": "vp_token",
                "response_mode": "dc_api",
                "nonce": "$nonce",
                "dcql_query": {
                  "credentials": [
                    {
                      "id": "user_info_query",
                      "format": "dc+sd-jwt",
                       "meta": { 
                          "vct_values": ["UserInfoCredential"] 
                       },
                      "claims": [ 
                        {"path": ["email"]}, 
                        {"path": ["name"]},  
                        {"path": ["given_name"]},
                        {"path": ["family_name"]},
                        {"path": ["picture"]},
                        {"path": ["hd"]},
                        {"path": ["email_verified"]}
                      ]
                    }
                  ]
                }
              }
            }
          ]
        }
        """

        val getDigitalCredentialOption = GetDigitalCredentialOption(requestJson = openId4vpRequest)
        val request = GetCredentialRequest(listOf(getDigitalCredentialOption))

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@CreateAccount, request)
                val credential = result.credential
                if (credential is DigitalCredential) {
                    val responseJsonString = credential.credentialJson
                    val responseData = JSONObject(responseJsonString)
                    val vpToken = responseData.getJSONObject("vp_token")
                    val credentialId = vpToken.keys().next()
                    val rawSdJwt = vpToken.getJSONArray(credentialId).getString(0)

                    val claims = SdJwtParser.parse(rawSdJwt)
                    val email = claims.optString("email", "")
                    val name = claims.optString("name", "")

                    if (email.isNotEmpty()) {
                        binding.etEmail.setText(email)
                        if (name.isNotEmpty()) {
                            binding.etUsername.setText(name.replace(" ", "").lowercase())
                        }
                        Toast.makeText(this@CreateAccount, "Email verified: $email", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("CreateAccount", "Credential Manager error", e)
                Toast.makeText(this@CreateAccount, "Verification failed or cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateSecureRandomNonce(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
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
                        session.saveIdentityKeys(
                            privateKey = Base64.encodeToString(it.second, Base64.NO_WRAP),
                            publicKey = it.first
                        )
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
