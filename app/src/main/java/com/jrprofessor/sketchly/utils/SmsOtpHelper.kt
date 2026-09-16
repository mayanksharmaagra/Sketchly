package com.jrprofessor.sketchly.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status

private const val TAG = "SmsOtpHelper"

/**
 * Starts the SMS User Consent flow via Google Play Services [SmsRetriever].
 *
 * The system monitors incoming SMS messages for up to 5 minutes.
 * When a matching SMS arrives, Android displays a one-tap bottom sheet asking
 * the user to allow reading that single message — no READ_SMS permission required.
 *
 * Call this right after the OTP SMS is sent.
 */
fun startSmsUserConsent(
    activity: Activity,
    onStarted: () -> Unit = {},
    onError: (String) -> Unit = {},
) {
    SmsRetriever.getClient(activity)
        .startSmsUserConsent(null /* null = accept from any sender */)
        .addOnSuccessListener {
            Log.d(TAG, "SMS User Consent started")
            onStarted()
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "SMS User Consent failed to start", e)
            onError(e.localizedMessage ?: "SMS auto-read unavailable")
        }
}

/**
 * Creates and returns a [BroadcastReceiver] that listens for the SMS User Consent result.
 *
 * Register with [registerSmsReceiver] when the OTP screen appears; unregister
 * with [unregisterSmsReceiver] when the screen leaves composition.
 *
 * @param onConsentNeeded  Called with the [Intent] to launch for user consent.
 *                         The caller fires startActivityForResult(intent, REQUEST_CODE).
 * @param onCodeReceived   Called with the raw 6-digit code after the user taps "Allow".
 * @param onFailure        Called when consent is denied or the 5-minute window expires.
 */
fun createSmsReceiver(
    onConsentNeeded: (Intent) -> Unit,
    onCodeReceived: (String) -> Unit,
    onFailure: () -> Unit,
): BroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION != intent.action) return
        val extras = intent.extras ?: return
        val status: Status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(SmsRetriever.EXTRA_STATUS, Status::class.java) ?: return
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(SmsRetriever.EXTRA_STATUS) ?: return
        }
        when (status.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                val consentIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT)
                }
                if (consentIntent != null) onConsentNeeded(consentIntent)
            }
            CommonStatusCodes.TIMEOUT -> { Log.d(TAG, "SMS consent timed out"); onFailure() }
            else -> { Log.d(TAG, "SMS consent status: ${status.statusCode}"); onFailure() }
        }
    }
}

/** Registers [receiver] for SMS_RETRIEVED_ACTION. Call once per screen entry. */
fun registerSmsReceiver(context: Context, receiver: BroadcastReceiver) {
    val filter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, filter)
    }
}

/** Safely unregisters [receiver], ignoring double-unregister. */
fun unregisterSmsReceiver(context: Context, receiver: BroadcastReceiver) {
    try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
}

/**
 * Extracts the first 6-digit code from a raw SMS body string.
 * Returns null if no 6-digit sequence is found.
 */
fun extractOtpFromSms(smsBody: String): String? =
    Regex("""\b(\d{6})\b""").find(smsBody)?.groupValues?.get(1)
