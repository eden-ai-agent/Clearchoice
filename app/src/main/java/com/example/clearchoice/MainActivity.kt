package com.example.clearchoice

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var executor: Executor

    // Simulate feature flag. In a real app, this would come from SharedPreferences or similar.
    private val BIOMETRIC_AUTH_ENABLED = true
    private var uiSetupDone = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        // Hide BottomNav initially until auth is successful or not needed
        bottomNavigationView.visibility = View.GONE

        if (savedInstanceState != null) {
            uiSetupDone = savedInstanceState.getBoolean("uiSetupDone", false)
        }


        if (BIOMETRIC_AUTH_ENABLED && !uiSetupDone) {
            biometricAuthManager = BiometricAuthManager()
            executor = ContextCompat.getMainExecutor(this)

            val canAuthStatus = biometricAuthManager.canAuthenticate(this)
            Log.d(TAG, "Biometric canAuthenticate status: $canAuthStatus")

            when (canAuthStatus) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    Log.d(TAG, "Biometric authentication can be performed.")
                    showBiometricPrompt()
                }
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                    Log.w(TAG, "No biometric features available on this device.")
                    Toast.makeText(this, getString(R.string.biometric_auth_no_hardware), Toast.LENGTH_LONG).show()
                    setupMainUI() // Bypass lock as per subtask simplification
                }
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                    Log.w(TAG, "Biometric features are currently unavailable.")
                    Toast.makeText(this, getString(R.string.biometric_auth_hw_unavailable), Toast.LENGTH_LONG).show()
                    setupMainUI() // Bypass lock
                }
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                    Log.w(TAG, "The user hasn't associated any biometric credentials with their account.")
                    Toast.makeText(this, getString(R.string.biometric_auth_none_enrolled), Toast.LENGTH_LONG).show()
                    setupMainUI() // Bypass lock
                }
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                     Log.w(TAG, "Biometric security update required.")
                     Toast.makeText(this, getString(R.string.biometric_auth_security_update_required), Toast.LENGTH_LONG).show()
                     setupMainUI() // Bypass
                }
                 BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                     Log.w(TAG, "Biometric option not supported.")
                     Toast.makeText(this, getString(R.string.biometric_auth_unsupported), Toast.LENGTH_LONG).show()
                     setupMainUI() // Bypass
                 }
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                    Log.w(TAG, "Biometric status unknown.")
                    Toast.makeText(this, getString(R.string.biometric_auth_status_unknown), Toast.LENGTH_LONG).show();
                    setupMainUI(); // Bypass
                }
            }
        } else if (uiSetupDone) {
            // If UI was already set up (e.g. after rotation and successful auth), just make sure Nav is visible
            setupMainUIWithoutDefaultFragment()
        }
         else {
            Log.d(TAG, "Biometric auth not enabled or UI already set up, proceeding to main UI.")
            setupMainUI()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("uiSetupDone", uiSetupDone)
    }


    private fun showBiometricPrompt() {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Biometric authentication succeeded.")
                Toast.makeText(applicationContext, getString(R.string.biometric_auth_succeeded), Toast.LENGTH_SHORT).show()
                runOnUiThread { // Ensure UI updates are on the main thread
                    setupMainUI()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "Biometric authentication error: $errorCode - $errString")
                Toast.makeText(applicationContext, getString(R.string.biometric_auth_error_generic, errString.toString()), Toast.LENGTH_LONG).show()
                // Do not load UI, or show a specific error screen.
                // For this subtask, just showing Toast and not loading UI.
                // Optionally, could finish an activity: if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) finish()
                // Or display a message on the existing blank screen.
                 findViewById<TextView>(android.R.id.content).apply {
                     // This is a bit of a hack to display message on blank screen.
                     // A dedicated layout for "auth failed" would be better.
                     // text = "Authentication failed. Please restart the app."
                     // visibility = View.VISIBLE
                 }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric authentication failed (e.g., wrong finger). Prompt will reappear.")
                Toast.makeText(applicationContext, getString(R.string.biometric_auth_failed), Toast.LENGTH_SHORT).show()
            }
        }
        Log.d(TAG, "Showing biometric prompt.")
        biometricAuthManager.authenticate(this, executor, callback)
    }

    private fun setupMainUI() {
        Log.d(TAG, "Setting up main UI.")
        bottomNavigationView.visibility = View.VISIBLE
        uiSetupDone = true
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            var selectedFragment: Fragment? = null
            when (menuItem.itemId) {
                R.id.navigation_record -> {
                    selectedFragment = RecordFragment()
                }
                R.id.navigation_sessions -> {
                    selectedFragment = SessionListFragment()
                }
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit()
            }
            true
        }

        // Set default fragment only if not already set (e.g. on config change after auth)
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
             bottomNavigationView.selectedItemId = R.id.navigation_record
        }
    }

    private fun setupMainUIWithoutDefaultFragment() {
        // This is for cases like screen rotation after auth, where fragment is already restored.
        Log.d(TAG, "Restoring main UI, BottomNav visibility.")
        bottomNavigationView.visibility = View.VISIBLE
        uiSetupDone = true
         bottomNavigationView.setOnItemSelectedListener { menuItem ->
            var selectedFragment: Fragment? = null
            when (menuItem.itemId) {
                R.id.navigation_record -> {
                    selectedFragment = RecordFragment()
                }
                R.id.navigation_sessions -> {
                    selectedFragment = SessionListFragment()
                }
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit()
            }
            true
        }
        // Do not set selectedItemId here as the fragment manager will restore the previous one.
    }


    companion object {
        private const val TAG = "MainActivity"
    }
}
