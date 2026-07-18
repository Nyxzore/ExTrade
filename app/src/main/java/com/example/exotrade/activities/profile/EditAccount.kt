package com.example.exotrade.activities.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.databinding.ProfileActivityEditBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.utils.*
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity for editing user account details, including profile picture,
 * contact information, and security settings.
 */
class EditAccount : AppCompatActivity() {

    private enum class SocialPlatform { WHATSAPP, FACEBOOK, INSTAGRAM }

    private lateinit var binding: ProfileActivityEditBinding
    private lateinit var session: SessionRepository
    private var selectedImageUri: Uri? = null
    private var activeWhatsAppInput: TextInputEditText? = null

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchContactPicker()
        } else {
            Toast.makeText(this, R.string.contacts_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            try {
                contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val number = cursor.getString(numberIndex)
                        applyWhatsAppValue(number)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_contact_picker, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val phoneHintLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                val phoneNumber = Identity.getSignInClient(this)
                    .getPhoneNumberFromIntent(result.data)
                activeWhatsAppInput?.setText(phoneNumber) ?: applyWhatsAppValue(phoneNumber)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_phone_hint, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            binding.imgProfile.setImageURI(uri)
            selectedImageUri = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ProfileActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository

        binding.btnSelectImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnUpdateProfile.setOnClickListener { updateProfile() }

        binding.btnLinkWhatsApp.setOnClickListener { showWhatsAppDialog() }
        binding.btnLinkFacebook.setOnClickListener { showSocialLinkDialog(SocialPlatform.FACEBOOK) }
        binding.btnLinkInstagram.setOnClickListener { showSocialLinkDialog(SocialPlatform.INSTAGRAM) }

        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("profile/get_profile.php", session.authParams())
                binding.progressBar.visibility = View.GONE
                
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    binding.etUsername.setText(json["username"]?.jsonPrimitive?.content)
                    binding.etEmail.setText(json["email"]?.jsonPrimitive?.content)
                    binding.etWhatsApp.setText(json["whatsapp"]?.jsonPrimitive?.content)
                    binding.etFacebook.setText(json["facebook"]?.jsonPrimitive?.content)
                    binding.etInstagram.setText(json["instagram"]?.jsonPrimitive?.content)

                    refreshSocialButtons()
                    val picPath = json["profile_picture"]?.jsonPrimitive?.content
                    Helpers.loadImage(picPath, binding.imgProfile, R.drawable.ic_person_24)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@EditAccount, R.string.error_loading_profile, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWhatsAppDialog() {
        val dialogView = layoutInflater.inflate(R.layout.profile_dialog_social_link, null)
        val tilInput = dialogView.findViewById<TextInputLayout>(R.id.tilSocialInput)
        val input = dialogView.findViewById<TextInputEditText>(R.id.etSocialInput)
        val btnSelectNumber = dialogView.findViewById<MaterialButton>(R.id.btnSelectNumber)

        tilInput.hint = getString(R.string.whatsapp_number_hint)
        input.inputType = InputType.TYPE_CLASS_PHONE
        input.setText(binding.etWhatsApp.text.toString())

        btnSelectNumber.setText(R.string.select_phone_number)
        btnSelectNumber.setOnClickListener { requestPhoneNumber(input) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.link_platform_title, getString(R.string.whatsapp)))
            .setMessage(R.string.whatsapp_link_message)
            .setView(dialogView)
            .setPositiveButton(R.string.link, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        if (binding.etWhatsApp.text.toString().isNotEmpty()) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.unlink)) { _, _ -> applyWhatsAppValue("") }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text?.toString()?.trim() ?: ""
                val error = SocialLinkUtils.validateWhatsApp(raw)
                if (error != null) {
                    tilInput.error = getString(error)
                    return@setOnClickListener
                }
                tilInput.error = null
                applyWhatsAppValue(raw)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showSocialLinkDialog(platform: SocialPlatform) {
        val hiddenField = when (platform) {
            SocialPlatform.FACEBOOK -> binding.etFacebook
            SocialPlatform.INSTAGRAM -> binding.etInstagram
            else -> return
        }
        val currentValue = hiddenField.text.toString()
        val platformName = if (platform == SocialPlatform.FACEBOOK) getString(R.string.facebook) else getString(R.string.instagram)
        val openUrl = if (platform == SocialPlatform.FACEBOOK) "https://facebook.com" else "https://instagram.com"
        val hintRes = if (platform == SocialPlatform.FACEBOOK) R.string.facebook_handle_hint else R.string.instagram_handle_hint

        val dialogView = layoutInflater.inflate(R.layout.profile_dialog_social_link, null)
        val tilInput = dialogView.findViewById<TextInputLayout>(R.id.tilSocialInput)
        val input = dialogView.findViewById<TextInputEditText>(R.id.etSocialInput)
        val btnSelectNumber = dialogView.findViewById<MaterialButton>(R.id.btnSelectNumber)

        tilInput.hint = getString(hintRes)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        input.setText(currentValue)

        btnSelectNumber.text = getString(R.string.open_platform_to_find_handle, platformName)
        btnSelectNumber.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.link_platform_title, platformName))
            .setMessage(getString(R.string.link_platform_message, platformName))
            .setView(dialogView)
            .setPositiveButton(R.string.link, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        if (currentValue.isNotEmpty()) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.unlink)) { _, _ ->
                hiddenField.setText("")
                refreshSocialButtons()
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text?.toString()?.trim() ?: ""
                val error = if (platform == SocialPlatform.FACEBOOK) SocialLinkUtils.validateFacebook(raw) else SocialLinkUtils.validateInstagram(raw)
                if (error != null) {
                    tilInput.error = getString(error)
                    return@setOnClickListener
                }

                val normalized = if (platform == SocialPlatform.FACEBOOK) SocialLinkUtils.normalizeFacebook(raw) else SocialLinkUtils.normalizeInstagram(raw)

                tilInput.error = null
                hiddenField.setText(normalized ?: "")
                refreshSocialButtons()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun requestPhoneNumber(targetInput: TextInputEditText) {
        activeWhatsAppInput = targetInput
        val request = GetPhoneNumberHintIntentRequest.builder().build()
        Identity.getSignInClient(this)
            .getPhoneNumberHintIntent(request)
            .addOnSuccessListener { pendingIntent ->
                try {
                    phoneHintLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } catch (e: Exception) {
                    pickContactWithPermission()
                }
            }
            .addOnFailureListener { pickContactWithPermission() }
    }

    private fun pickContactWithPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun applyWhatsAppValue(raw: String) {
        if (SocialLinkUtils.isBlank(raw)) {
            binding.etWhatsApp.setText("")
            refreshSocialButtons()
            return
        }

        val error = SocialLinkUtils.validateWhatsApp(raw)
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return
        }

        val normalized = SocialLinkUtils.normalizeWhatsApp(raw)
        binding.etWhatsApp.setText(normalized ?: "")
        refreshSocialButtons()
    }

    private fun refreshSocialButtons() {
        updateSocialButton(SocialPlatform.WHATSAPP, binding.etWhatsApp.text.toString(), binding.btnLinkWhatsApp)
        updateSocialButton(SocialPlatform.FACEBOOK, binding.etFacebook.text.toString(), binding.btnLinkFacebook)
        updateSocialButton(SocialPlatform.INSTAGRAM, binding.etInstagram.text.toString(), binding.btnLinkInstagram)
    }

    private fun updateSocialButton(platform: SocialPlatform, value: String, button: MaterialButton) {
        val linked = !SocialLinkUtils.isBlank(value)
        when (platform) {
            SocialPlatform.WHATSAPP -> {
                button.text = if (linked) getString(R.string.whatsapp_linked_value, SocialLinkUtils.formatWhatsAppDisplay(SocialLinkUtils.normalizeWhatsApp(value))) else getString(R.string.link_whatsapp)
                button.setIconResource(R.drawable.ic_whatsapp)
            }
            SocialPlatform.FACEBOOK -> {
                button.text = if (linked) getString(R.string.facebook_linked_value, SocialLinkUtils.formatFacebookDisplay(value).replace("@", "")) else getString(R.string.link_facebook)
                button.setIconResource(R.drawable.ic_facebook)
            }
            SocialPlatform.INSTAGRAM -> {
                button.text = if (linked) getString(R.string.instagram_linked_value, SocialLinkUtils.formatInstagramDisplay(value).replace("@", "")) else getString(R.string.link_instagram)
                button.setIconResource(R.drawable.ic_instagram)
            }
        }
    }

    private suspend fun encodeImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                ImageUtils.compressAndEncode(bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun updateProfile() {
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()

        val whatsapp = SocialLinkUtils.normalizeWhatsApp(binding.etWhatsApp.text.toString().trim())
        val facebook = SocialLinkUtils.normalizeFacebook(binding.etFacebook.text.toString().trim())
        val instagram = SocialLinkUtils.normalizeInstagram(binding.etInstagram.text.toString().trim())

        if (username.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, R.string.error_required_fields, Toast.LENGTH_SHORT).show()
            return
        }

        if (SocialLinkUtils.validateWhatsApp(binding.etWhatsApp.text.toString().trim()) != null ||
            SocialLinkUtils.validateFacebook(binding.etFacebook.text.toString().trim()) != null ||
            SocialLinkUtils.validateInstagram(binding.etInstagram.text.toString().trim()) != null) {
            Toast.makeText(this, R.string.error_required_fields, Toast.LENGTH_SHORT).show() // Use required fields as a fallback
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val params = session.authParams().toMutableMap()
            params["username"] = username
            params["email"] = email
            params["whatsapp"] = whatsapp ?: ""
            params["facebook"] = facebook ?: ""
            params["instagram"] = instagram ?: ""

            if (newPassword.isNotEmpty()) {
                params["new_password"] = newPassword
                try {
                    val encryptionManager = ExoTradeApplication.container.encryptionManager
                    val privKeyBase64 = session.getIdentityPrivateKey() ?: throw Exception("Security keys not in memory. Please log in again.")
                    val secretKeyBytes = Base64.decode(privKeyBase64, Base64.NO_WRAP)
                    
                    val salt = encryptionManager.generateSalt()
                    val nonce = encryptionManager.generateNonce()
                    val newBackupKey = encryptionManager.deriveBackupKey(newPassword, salt)
                    val encryptedPrivKey = encryptionManager.encryptPrivateKey(secretKeyBytes, newBackupKey, nonce)

                    params["encrypted_private_key"] = encryptedPrivKey
                    params["private_key_nonce"] = Base64.encodeToString(nonce, Base64.NO_WRAP)
                    params["kdf_salt"] = Base64.encodeToString(salt, Base64.NO_WRAP)
                } catch (e: Exception) {
                    Toast.makeText(this@EditAccount, getString(R.string.error_security_backup, e.message), Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }
            }

            selectedImageUri?.let { uri ->
                val imageData = encodeImage(uri)
                if (imageData != null) {
                    params["profile_picture_data"] = imageData
                }
            }

            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("profile/update_profile.php", params)
                binding.progressBar.visibility = View.GONE
                
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    session.updateUserInfo(
                        username = username,
                        profilePic = json["profile_picture"]?.jsonPrimitive?.content ?: "",
                        tier = session.getSubscriptionTier(),
                        isAdmin = session.isAdmin()
                    )
                    Toast.makeText(this@EditAccount, R.string.profile_updated, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@EditAccount, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@EditAccount, R.string.error_updating_profile, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
