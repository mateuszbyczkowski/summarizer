package com.summarizer.app.util

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The phone's own lock: screen lock state and BiometricPrompt. */
object BiometricHelper {

    private const val SYSTEM_LOCK_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /** Why a prompt ended without success. */
    sealed interface Failure {
        /** Dismissed by the user or the system; nothing to show. */
        data object Cancelled : Failure

        /** The phone no longer has a screen lock (or no enrolled biometrics). */
        data object NoCredential : Failure

        /** Any other error, e.g. too many attempts; [message] comes from the system. */
        data class Error(val message: String) : Failure
    }

    /** True when the phone has a PIN, pattern or password screen lock. */
    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** True when a strong biometric (e.g. fingerprint) is present and enrolled. */
    fun canUseBiometric(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** True when the phone's own lock (strong biometric or screen lock) can be prompted. */
    fun canUseSystemLock(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(SYSTEM_LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt. With [negativeButtonText] null it accepts the phone's screen lock
     * as well as a strong biometric; otherwise only a strong biometric, with that button to
     * dismiss it (e.g. "Use PIN").
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        negativeButtonText: String?,
        onSuccess: () -> Unit,
        onFailure: (Failure) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> Failure.Cancelled
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                            BiometricPrompt.ERROR_NO_BIOMETRICS -> Failure.NoCredential
                            else -> Failure.Error(errString.toString())
                        }
                    )
                }

                // onAuthenticationFailed (a single unrecognised attempt) keeps the prompt open
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .apply {
                if (negativeButtonText == null) {
                    setAllowedAuthenticators(SYSTEM_LOCK_AUTHENTICATORS)
                } else {
                    setAllowedAuthenticators(BIOMETRIC_STRONG)
                    setNegativeButtonText(negativeButtonText)
                }
            }
            .build()

        prompt.authenticate(promptInfo)
    }
}
