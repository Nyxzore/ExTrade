package com.example.exotrade.activities.listings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.exotrade.ExoTradeApplication
import com.example.exotrade.R
import com.example.exotrade.activities.MainHostActivity
import com.example.exotrade.activities.breeding.CreateBreedingListing
import com.example.exotrade.databinding.ListingActivityCreateBinding
import com.example.exotrade.data.SessionRepository
import com.example.exotrade.data.SpeciesRepository
import com.example.exotrade.utils.*
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fragment for creating a new animal listing.
 * Provides autocomplete for species names using [SpeciesRepository]
 * and handles image selection and compression before uploading to the server.
 */
class CreateListingFragment : Fragment() {

    private var _binding: ListingActivityCreateBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var session: SessionRepository
    private lateinit var speciesRepository: SpeciesRepository
    private var selectedImageUri: Uri? = null
    private var currentListingType = "sale"
    private var speciesSyncAttempts = 0
    private var isSyncing = false

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            binding.imgPreview.setImageURI(it)
            selectedImageUri = it
            binding.imgPreview.alpha = 1.0f
            
            // Update preview card
            val previewBinding = binding.previewCard
            previewBinding.imgCoverImage.setImageURI(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ListingActivityCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        session = ExoTradeApplication.container.sessionRepository
        speciesRepository = ExoTradeApplication.container.speciesRepository

        setupUI(savedInstanceState)
        loadSpeciesData()
    }

    private fun setupUI(savedInstanceState: Bundle?) {
        binding.btnAddImage.setOnClickListener { imagePickerLauncher.launch("image/*") }

        val tilPrice = binding.etPrice.parent.parent as TextInputLayout

        binding.toggleListingType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnTypeSale) {
                    currentListingType = "sale"
                    tilPrice.hint = getString(R.string.asking_price)
                } else if (checkedId == R.id.btnTypeBreeding) {
                    startActivity(Intent(requireContext(), CreateBreedingListing::class.java))
                }
            }
        }

        binding.etSex.setSimpleItems(arrayOf("Male", "Female", "Unsexed"))
        binding.etAgeUnit.setSimpleItems(arrayOf(getString(R.string.days), getString(R.string.months), getString(R.string.years)))

        if (savedInstanceState == null) {
            binding.etSex.setText("Unsexed", false)
            binding.etAgeUnit.setText(getString(R.string.months), false)
        } else {
            binding.etSex.setText(savedInstanceState.getString("sex_value", "Unsexed"), false)
            binding.etAgeUnit.setText(savedInstanceState.getString("age_unit_value", getString(R.string.months)), false)
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_info) {
                showInfoDialog()
                true
            } else false
        }

        binding.btnCreateListing.setOnClickListener { createListing() }
        setupPreviewLogic()
    }

    private fun setupPreviewLogic() {
        val previewBinding = binding.previewCard
        
        fun updatePreview() {
            val scientific = binding.etScientificName.text.toString()
            val common = binding.etCommonName.text.toString()
            val price = binding.etPrice.text.toString()
            val desc = binding.etDescription.text.toString()

            val isNoCommon = common == Helpers.NO_COMMON_NAME_PLACEHOLDER || common.isEmpty()
            previewBinding.lblCommonName.text = if (isNoCommon) scientific.ifEmpty { "Species Name" } else common
            
            if (isNoCommon) {
                previewBinding.lblScientificName.visibility = View.GONE
            } else {
                previewBinding.lblScientificName.visibility = View.VISIBLE
                previewBinding.lblScientificName.text = scientific.ifEmpty { "Scientific Name" }
            }

            previewBinding.lblPrice.text = if (price.isEmpty()) "R 0.00" else "R $price"
            previewBinding.lblDescription.text = desc.ifEmpty { "Description goes here..." }
            
            // If it's breeding, change price label logic
            if (currentListingType == "breeding") {
                previewBinding.lblPrice.text = "Stud Fee: R $price"
            }
        }

        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }

        binding.etScientificName.addTextChangedListener(watcher)
        binding.etCommonName.addTextChangedListener(watcher)
        binding.etPrice.addTextChangedListener(watcher)
        binding.etDescription.addTextChangedListener(watcher)

        // Initial update
        updatePreview()
    }

    private fun showInfoDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Create Listing Help")
            .setMessage("Fill in the details of the animal you wish to sell. \n\n" +
                    "• Scientific Name: Required for categorization.\n" +
                    "• Price: The amount in Rands.\n" +
                    "• Description: Be specific about health, history, and temperament.\n" +
                    "• Photos: High-quality photos increase buyer trust.")
            .setPositiveButton("Got it", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.let {
            outState.putString("sex_value", it.etSex.text.toString())
            outState.putString("age_unit_value", it.etAgeUnit.text.toString())
        }
    }

    private fun loadSpeciesData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val scientificList = speciesRepository.getNames(isScientific = true)
            val commonList = speciesRepository.getNames(isScientific = false)

            if (scientificList.isEmpty()) {
                if (isSyncing) return@launch
                if (speciesSyncAttempts >= 2) {
                    Toast.makeText(requireContext(), "Couldn't load species list. Pull down to retry.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                speciesSyncAttempts++
                isSyncing = true
                speciesRepository.syncFromServer(true)
                isSyncing = false
                loadSpeciesData()
                return@launch
            }

            val sAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, scientificList)
            binding.etScientificName.setAdapter(sAdapter)
            binding.etScientificName.threshold = 1

            val cAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, commonList)
            binding.etCommonName.setAdapter(cAdapter)
            binding.etCommonName.threshold = 1

            binding.etCommonName.setOnItemClickListener { parent, _, position, _ ->
                val selectedCommon = parent.getItemAtPosition(position) as String
                viewLifecycleOwner.lifecycleScope.launch {
                    val scientific = speciesRepository.getScientificName(selectedCommon)
                    if (!scientific.isNullOrEmpty()) {
                        binding.etScientificName.setText(scientific, false)
                    }
                }
            }

            binding.etScientificName.setOnItemClickListener { parent, _, position, _ ->
                val selectedScientific = parent.getItemAtPosition(position) as String
                viewLifecycleOwner.lifecycleScope.launch {
                    val common = speciesRepository.getCommonName(selectedScientific)
                    if (!common.isNullOrEmpty()) {
                        binding.etCommonName.setText(common, false)
                    } else {
                        binding.etCommonName.setText(Helpers.NO_COMMON_NAME_PLACEHOLDER, false)
                    }
                }
            }
        }
    }

    private suspend fun encodeImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                ImageUtils.compressAndEncode(bitmap)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createListing() {
        val scientificName = binding.etScientificName.text.toString().trim()
        val commonName = binding.etCommonName.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val sex = binding.etSex.text.toString().trim()
        val size = binding.etSize.text.toString().trim()
        val ageStr = binding.etAge.text.toString().trim()
        val unit = binding.etAgeUnit.text.toString().trim()

        if (scientificName.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in scientific name", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentListingType == "sale" && price.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in price", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val isValid = speciesRepository.isValidSpecies(scientificName)
            if (!isValid) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Unverified Species")
                    .setMessage("This scientific name is not in our database. You can still publish, but it will be marked as unverified until an admin reviews it. \n\nContinue?")
                    .setPositiveButton("Publish Anyway") { _, _ ->
                        performPublish(scientificName, commonName, price, description, sex, size, ageStr, unit, true)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            val isCommonValid = commonName.isEmpty() || speciesRepository.getScientificName(commonName) == scientificName
            if (!isCommonValid) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Unverified Common Name")
                    .setMessage("This common name is not recognized for this species. It will be sent for admin review. \n\nContinue?")
                    .setPositiveButton("Publish Anyway") { _, _ ->
                        performPublish(scientificName, commonName, price, description, sex, size, ageStr, unit, false)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return@launch
            }

            performPublish(scientificName, commonName, price, description, sex, size, ageStr, unit, false)
        }
    }

    private fun performPublish(
        scientificName: String,
        commonName: String,
        price: String,
        description: String,
        sex: String,
        size: String,
        ageStr: String,
        unit: String,
        isUnverifiedScientific: Boolean
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val params = session.authParams().toMutableMap()
            params["price"] = price
            params["description"] = description
            params["sex"] = sex
            params["size_in_cm"] = size

            if (isUnverifiedScientific) {
                params["unverified_scientific_name"] = scientificName
                params["unverified_common_name"] = if (commonName == Helpers.NO_COMMON_NAME_PLACEHOLDER) "" else commonName
            } else {
                val lsid = speciesRepository.getLsid(scientificName)
                params["species_lsid"] = lsid ?: ""
                
                // Check if common name is custom even if scientific is valid
                if (commonName.isNotEmpty() && commonName != Helpers.NO_COMMON_NAME_PLACEHOLDER && speciesRepository.getScientificName(commonName) != scientificName) {
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
                binding.btnCreateListing.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
                val imageData = encodeImage(it)
                if (imageData != null) {
                    params["image_data"] = imageData
                }
            } ?: run {
                binding.btnCreateListing.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
            }

            try {
                val response: String = ExoTradeApplication.container.apiService.postForm("listings/create_listing", params)
                binding.btnCreateListing.isEnabled = true
                binding.progressBar.visibility = View.GONE
                
                val json = Json.parseToJsonElement(response).jsonObject
                if ("success" == json["status"]?.jsonPrimitive?.content) {
                    Toast.makeText(requireContext(), "Listing posted successfully!", Toast.LENGTH_SHORT).show()
                    (requireActivity() as? MainHostActivity)?.switchTab(R.id.nav_profile)
                } else {
                    Toast.makeText(requireContext(), "Error: " + json["message"]?.jsonPrimitive?.content, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.btnCreateListing.isEnabled = true
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to connect to server", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
