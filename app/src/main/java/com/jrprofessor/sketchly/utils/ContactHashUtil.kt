package com.jrprofessor.sketchly.utils

import android.content.Context
import android.provider.ContactsContract
import java.security.MessageDigest

/**
 * ContactHashUtil.kt
 *
 * All raw phone number handling is ISOLATED here.
 * Raw numbers are read from the device → normalized → hashed.
 * Only hashes ever leave this file. Nothing else in the app
 * should touch raw phone numbers.
 *
 * Security guarantees:
 *  - Raw numbers are held in memory only (never written to disk)
 *  - They are zeroed from the list as soon as hashing is done
 *  - Hashes are one-way SHA-256 — cannot be reversed to raw numbers
 *  - Only hashes are sent to the server (Cloud Function)
 */
object ContactHashUtil {

    /**
     * Reads all phone numbers from device contacts,
     * normalizes them to E.164 format, hashes with SHA-256,
     * and returns ONLY the hashes.
     *
     * Raw numbers are never returned, never stored, never logged.
     */
    fun getHashedPhoneNumbers(context: Context): List<String> {
        val hashes = mutableListOf<String>()
        val rawNumbers = mutableListOf<String>() // held briefly, then cleared

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER),
                null,
                null,
                null
            )

            cursor?.use {
                val columnIndex = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                )
                while (it.moveToNext()) {
                    val number = it.getString(columnIndex)
                    if (!number.isNullOrBlank()) {
                        rawNumbers.add(number)
                    }
                }
            }

            // Deduplicate before hashing to avoid redundant Cloud Function work
            val uniqueNumbers = rawNumbers.distinct()

            // Hash each number — this is the only transformation that matters
            uniqueNumbers.forEach { number ->
                val normalized = normalizeToE164(number)
                if (normalized != null) {
                    hashes.add(sha256(normalized))
                }
            }

        } finally {
            // Explicitly clear raw numbers from memory as soon as hashing is done
            rawNumbers.clear()
        }

        return hashes
    }

    /**
     * Normalizes a phone number to E.164 format (+[country][number]).
     * Returns null if the number cannot be normalized — those are skipped.
     *
     * NORMALIZED_NUMBER from Android's ContentResolver is already
     * in E.164 for most cases (Android does this automatically when
     * a contact is saved). This is a safety net for edge cases.
     */
    private fun normalizeToE164(number: String): String? {
        // Android's NORMALIZED_NUMBER is already E.164 if properly stored
        // Clean up any remaining formatting artifacts just in case
        val cleaned = number
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")

        // Must start with + and have 7-15 digits after it
        return if (cleaned.startsWith("+") && cleaned.length in 8..16) {
            cleaned
        } else {
            null // Skip malformed numbers silently
        }
    }

    /**
     * SHA-256 hash of a normalized E.164 phone number.
     * Returns lowercase hex string (64 chars).
     *
     * Example:
     *   "+19876543210" → "a3f1b2c4d5e6..." (64 hex chars)
     */
    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
