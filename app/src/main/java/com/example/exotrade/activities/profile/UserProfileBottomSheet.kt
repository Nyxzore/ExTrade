package com.example.exotrade.activities.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.databinding.ProfileBottomSheetBinding
import com.example.exotrade.utils.Helpers
import com.example.exotrade.utils.SocialLinkUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * A simplified bottom sheet for viewing basic user profile information.
 */
class UserProfileBottomSheet : BottomSheetDialogFragment() {

    private var _binding: ProfileBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var userId: String? = null

    companion object {
        fun newInstance(userId: String): UserProfileBottomSheet {
            val fragment = UserProfileBottomSheet()
            val args = Bundle()
            args.putString("user_id", userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString("user_id")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ProfileBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnViewFullProfile.setOnClickListener {
            val intent = Intent(context, Profile::class.java)
            intent.putExtra("user_id", userId)
            startActivity(intent)
            dismiss()
        }

        fetchProfileData()
    }

    private fun fetchProfileData() {
        val targetId = userId ?: return
        val session = ExoTradeApplication.container.sessionRepository
        val params = session.authParams().toMutableMap()
        params["target_user_id"] = targetId

        lifecycleScope.launch {
            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("profile/get_profile.php", params)
                if (!isAdded) return@launch
                
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    val username = json["username"]?.jsonPrimitive?.content ?: ""
                    val picPath = json["profile_picture"]?.jsonPrimitive?.content
                    val tier = json["subscription_tier"]?.jsonPrimitive?.int ?: 0

                    binding.lblUsername.text = username
                    Helpers.loadImage(picPath, binding.imgProfilePicture, R.drawable.ic_person_24)

                    val imgProfile = binding.imgProfilePicture
                    if (imgProfile is ShapeableImageView) {
                        if (tier >= 1) {
                            imgProfile.strokeWidth = Helpers.dpToPx(requireContext(), 1).toFloat()
                            imgProfile.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.tier_1_orange)
                        } else {
                            imgProfile.strokeWidth = 0f
                        }
                    }

                    val whatsapp = json["whatsapp"]?.jsonPrimitive?.content
                    val facebook = json["facebook"]?.jsonPrimitive?.content
                    val instagram = json["instagram"]?.jsonPrimitive?.content

                    SocialLinkUtils.bindProfileIcons(
                        requireActivity(),
                        binding.layoutSocialLinks,
                        binding.imgSocialWhatsApp,
                        binding.imgSocialFacebook,
                        binding.imgSocialInstagram,
                        whatsapp,
                        facebook,
                        instagram
                    )
                }
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(context, "Error loading profile", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
