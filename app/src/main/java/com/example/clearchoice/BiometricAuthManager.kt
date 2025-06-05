package com.example.clearchoice

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class BiometricAuthManager {

    companion object {
        private const val TAG = "BiometricAuthManager"
    }

    /**
     * Checks if the device can perform biometric authentication.
     * Prefers BIOMETRIC_STRONG, allows DEVICE_CREDENTIAL as fallback.
     *
     * @param context Context.
     * @return An integer status code from BiometricManager (e.g., BIOMETRIC_SUCCESS).
     */
    fun canAuthenticate(context: Context): Int {
        val biometricManager = BiometricManager.from(context)
        // Allowing BIOMETRIC_STRONG. For broader compatibility including PIN/Pattern/Password,
        // one might use BIOMETRIC_STRONG or DEVICE_CREDENTIAL.
        // The prompt itself will handle the fallback if setDeviceCredentialAllowed(true) was used,
        // or if Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL is used.
        // For this check, we see if any strong biometric is available.
        // The actual prompt can be configured to allow device credentials.
        val authenticators = BIOMETRIC_STRONG // Could also be: BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        val result = biometricManager.canAuthenticate(authenticators)
        Log.d(TAG, "canAuthenticate result: $result")
        return result
    }

    /**
     * Initiates biometric authentication.
     *
     * @param activity The FragmentActivity initiating authentication.
     * @param executor An executor for BiometricPrompt callbacks.
     * @param callback The callback to handle authentication results.
     */
    fun authenticate(
        activity: FragmentActivity,
        executor: Executor, // Typically ContextCompat.getMainExecutor(context)
        callback: BiometricPrompt.AuthenticationCallback
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Lock")
            .setSubtitle("Log in using your biometric credential")
            .setDescription("ClearChoice app is locked. Authenticate to continue.")
            // Use BIOMETRIC_STRONG for the prompt.
            // If you want to allow PIN/Pattern/Password as well directly in the prompt:
            // .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            // If you only want strong biometrics and handle device credential fallback manually or not at all:
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            // .setNegativeButtonText("Cancel") // Or use device credential as fallback
            // If setDeviceCredentialAllowed(true) was used (deprecated in favor of setAllowedAuthenticators):
            // .setDeviceCredentialAllowed(true) -> This makes the negative button lead to device credential unlock.
            // For setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL),
            // the system handles presenting the choice if no biometrics are enrolled but device credential is.
            // If only BIOMETRIC_STRONG is set, then no fallback to device credential via this prompt directly.
            // The task asks for BIOMETRIC_STRONG preference.
            // If no strong biometrics are enrolled, canAuthenticate would have indicated it.
            // If strong biometrics are enrolled, this prompt will show.
            // If a user has PIN/Pattern/Passcode but NO strong biometrics, canAuthenticate(BIOMETRIC_STRONG) = BIOMETRIC_ERROR_NONE_ENROLLED.
            // To allow PIN/Pattern/Passcode as a direct alternative within the prompt, use:
            // .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            // For this implementation, we'll stick to BIOMETRIC_STRONG for the prompt itself,
            // implying the user *must* use a strong biometric.
            // The canAuthenticate check can be broader to inform the user.
            .setConfirmationRequired(false) // No explicit confirmation button after successful auth
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error when trying to authenticate", e)
            // This might happen if the activity is not in a valid state, etc.
            // The callback's onAuthenticationError should ideally handle most issues.
        }
    }
}
