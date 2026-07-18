package com.example.exotrade.activities

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.example.exotrade.R
import com.example.exotrade.databinding.ActivityNoInternetBinding

/**
 * Activity displayed when the application detects a loss of internet connectivity.
 * It prevents the user from proceeding until a connection is restored.
 */
class NoInternetActivity : BaseActivity() {

    private lateinit var binding: ActivityNoInternetBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoInternetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isNetworkAvailable()) {
                    finish()
                } else {
                    Toast.makeText(
                        this@NoInternetActivity,
                        R.string.no_internet_connection,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    /**
     * Checks if the device has an active internet connection.
     *
     * @return true if internet is available, false otherwise.
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.let {
            val capabilities = it.getNetworkCapabilities(it.activeNetwork)
            return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
        return false
    }
}
