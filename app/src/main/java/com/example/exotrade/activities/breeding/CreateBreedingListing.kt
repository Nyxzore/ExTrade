package com.example.exotrade.activities.breeding

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.BaseActivity
import com.example.exotrade.activities.MainHostActivity
import com.example.exotrade.databinding.BreedingActivityCreateBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.data.SpeciesRepository
import com.example.exotrade.utils.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activity for creating a breeding-specific listing.
 */
class CreateBreedingListing : BaseActivity() {

    private lateinit var binding: BreedingActivityCreateBinding
    private lateinit var session: SessionRepository
    private lateinit var speciesRepository: SpeciesRepository
    private var selectedImageUri: Uri? = null
    private var currentBreedingType = "seeking"
    private var isSyncing = false
    private var speciesSyncAttempts = 0

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            binding.imgPreview.setImageURI(it)
            selectedImageUri = it
            binding.imgPreview.alpha = 1.0f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BreedingActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = ExoTradeApplication.container.sessionRepository
        speciesRepository = ExoTradeApplication.container.speciesRepository

        binding.btnAddImage.setOnClickListener { imagePickerLauncher.launch("image/*") }

        binding.toggleBreedingType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnSeeking -> {
                        currentBreedingType = "seeking"
                        binding.layoutLoanFee.visibility = View.GONE
                    }
                    R.id.btnLoan -> {
                        currentBreedingType = "loan"
                        binding.layoutLoanFee.visibility = View.VISIBLE
                    }
                }
            }
        }

        val sexOptions = arrayOf("Male", "Female")
        binding.etSex.setSimpleItems(sexOptions)
        
        val unitOptions = arrayOf(getString(R.string.days), getString(R.string.months), getString(R.string.years))
        binding.etAgeUnit.setSimpleItems(unitOptions)

        if (savedInstanceState == null) {
            binding.etSex.setText("Male", false)
            binding.etAgeUnit.setText(getString(R.string.months), false)
        } else {
            binding.etSex.setText(savedInstanceState.getString("sex_value", "Male"), false)
            binding.etAgeUnit.setText(savedInstanceState.getString("age_unit_value", getString(R.string.months)), false)
        }

        NavigationHelper.setup(this, binding.bottomNavigation, R.id.nav_add)

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_info) {
                showInfoDialog()
                true
            } else false
        }

        loadSpeciesData()

        binding.btnCreateBreedingListing.setOnClickListener { createBreedingListing() }
    }

    private fun showInfoDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Breeding Help")
            .setMessage("List your animal for breeding services. \n\n" +
                    "• Looking for Partner: You have an animal and need a mate.\n" +
                    "• Willing to Loan: You are willing to loan your animal for a fee (Stud Fee).")
            .setPositiveButton("Got it", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("sex_value", binding.etSex.text.toString())
        outState.putString("age_unit_value", binding.etAgeUnit.text.toString())
    }

    override fun onResume() {
        super.onResume()
        Helpers.updateUnreadBadge(binding.bottomNavigation)
    }

    private fun loadSpeciesData() {
        lifecycleScope.launch {
            val scientificList = speciesRepository.getNames(isScientific = true)
            val commonList = speciesRepository.getNames(isScientific = false)

            if (scientificList.isEmpty()) {
                if (isSyncing) return@launch
                if (speciesSyncAttempts >= 2) {
                    Toast.makeText(this@CreateBreedingListing, "Couldn't load species list. Pull down to retry.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                speciesSyncAttempts++
                isSyncing = true
                speciesRepository.syncFromServer(true)
                isSyncing = false
                loadSpeciesData()
                return@launch
            }

            val sAdapter = ArrayAdapter(this@CreateBreedingListing, android.R.layout.simple_dropdown_item_1line, scientificList)
            binding.etScientificName.setAdapter(sAdapter)
            binding.etScientificName.threshold = 1

            val cAdapter = ArrayAdapter(this@CreateBreedingListing, android.R.layout.simple_dropdown_item_1line, commonList)
            binding.etCommonName.setAdapter(cAdapter)
            binding.etCommonName.threshold = 1

            binding.etCommonName.setOnItemClickListener { parent, _, position, _ ->
                val selectedCommon = parent.getItemAtPosition(position) as String
                lifecycleScope.launch {
                    val scientific = speciesRepository.getScientificName(selectedCommon)
                    if (!scientific.isNullOrEmpty()) {
                        binding.etScientificName.setText(scientific, false)
                    }
                }
            }

            binding.etScientificName.setOnItemClickListener { parent, _, position, _ ->
                val selectedScientific = parent.getItemAtPosition(position) as String
                lifecycleScope.launch {
                    val common = speciesRepository.getCommonName(selectedScientific)
                    if (!common.isNullOrEmpty()) {
                        binding.etCommonName.setText(common, false)
                    }
                }
            }
        }
    }

    private fun createBreedingListing() {
        val scientificName = binding.etScientificName.text.toString().trim()
        val commonName = binding.etCommonName.text.toString().trim()
        val fee = binding.etLoanFee.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val sex = binding.etSex.text.toString().trim()
        val size = binding.etSize.text.toString().trim()
        val ageStr = binding.etAge.text.toString().trim()
        val unit = binding.etAgeUnit.text.toString().trim()

        lifecycleScope.launch {
            if (scientificName.isEmpty()) {
                Toast.makeText(this@CreateBreedingListing, "Scientific name required", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val isValid = speciesRepository.isValidSpecies(scientificName)
            if (!isValid) {
                androidx.appcompat.app.AlertDialog.Builder(this@CreateBreedingListing)
                    .setTitle("Unverified Species")
                    .setMessage("This scientific name is not in our database. You can still publish, but it will be marked as unverified until an admin reviews it. \n\nContinue?")
                    .setPositiveButton("Publish Anyway") { _, _ ->
                        performPublish(scientificName, commonName, fee, description, sex, size, ageStr, unit, true)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            val isCommonValid = commonName.isEmpty() || speciesRepository.getScientificName(commonName) == scientificName
            if (!isCommonValid) {
                androidx.appcompat.app.AlertDialog.Builder(this@CreateBreedingListing)
                    .setTitle("Unverified Common Name")
                    .setMessage("This common name is not recognized for this species. It will be sent for admin review. \n\nContinue?")
                    .setPositiveButton("Publish Anyway") { _, _ ->
                        performPublish(scientificName, commonName, fee, description, sex, size, ageStr, unit, false)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            performPublish(scientificName, commonName, fee, description, sex, size, ageStr, unit, false)
        }
    }

    private fun performPublish(
        scientificName: String,
        commonName: String,
        fee: String,
        description: String,
        sex: String,
        size: String,
        ageStr: String,
        unit: String,
        isUnverifiedScientific: Boolean
    ) {
        lifecycleScope.launch {
            val params = session.authParams().toMutableMap()
            params["sex"] = sex
            params["breeding_type"] = currentBreedingType
            if (currentBreedingType == "loan") {
                params["loan_fee"] = fee
            }
            params["description"] = description
            params["size_in_cm"] = size

            if (isUnverifiedScientific) {
                params["unverified_scientific_name"] = scientificName
                params["unverified_common_name"] = commonName
            } else {
                val lsid = speciesRepository.getLsid(scientificName) ?: ""
                params["species_lsid"] = lsid
                
                if (commonName.isNotEmpty() && speciesRepository.getScientificName(commonName) != scientificName) {
                    params["unverified_common_name"] = commonName
                }
            }
            
            if (ageStr.isNotEmpty()) {
                try {
                    val ageVal = ageStr.toInt()
                    var days = 0
                    when (unit) {
                        getString(R.string.days) -> days = ageVal
                        getString(R.string.months) -> days = ageVal * 30
                        getString(R.string.years) -> days = ageVal * 365
                    }
                    params["age_in_days"] = days.toString()
                } catch (e: NumberFormatException) {}
            }

            selectedImageUri?.let {
                params["image_data"] = encodeImage(it) ?: ""
            }

            binding.btnCreateBreedingListing.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("breeding/create_breeding_listing", params)
                binding.btnCreateBreedingListing.isEnabled = true
                binding.progressBar.visibility = View.GONE
                
                val json = Json.parseToJsonElement(response).jsonObject
                if (json["status"]?.jsonPrimitive?.content == "success") {
                    Toast.makeText(this@CreateBreedingListing, "Breeding listing posted!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@CreateBreedingListing, MainHostActivity::class.java).apply {
                        putExtra("initial_tab", R.id.nav_profile)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@CreateBreedingListing, json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.btnCreateBreedingListing.isEnabled = true
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@CreateBreedingListing, "Failed to connect to server", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encodeImage(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                ImageUtils.compressAndEncode(bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }
}
