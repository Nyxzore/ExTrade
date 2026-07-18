package com.example.exotrade.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.exotrade.R
import com.example.exotrade.databinding.ActivityUpdateRequiredBinding

/**
 * Activity displayed when the application version is no longer supported by the server.
 * It prevents the user from proceeding until the app is updated.
 */
class UpdateRequiredActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateRequiredBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateRequiredBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Consume the event and show a toast
                Toast.makeText(
                    this@UpdateRequiredActivity,
                    R.string.update_required_message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
