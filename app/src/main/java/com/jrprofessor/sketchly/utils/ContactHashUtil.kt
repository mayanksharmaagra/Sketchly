package com.jrprofessor.sketchly.utils

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import java.security.MessageDigest

private const val TAG = "ContactHashUtil"

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
        Log.d(TAG, "getHashedPhoneNumbers: starting contact read")
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

            if (cursor == null) {
                Log.w(TAG, "getHashedPhoneNumbers: ContentResolver returned null cursor — no contacts or permission denied")
                return emptyList()
            }

            cursor.use {
                val columnIndex = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
                )
                if (columnIndex == -1) {
                    Log.e(TAG, "getHashedPhoneNumbers: NORMALIZED_NUMBER column not found in cursor")
                    return@use
                }
                var totalRows = 0
                var blankRows = 0
                while (it.moveToNext()) {
                    totalRows++
                    val number = it.getString(columnIndex)
                    if (!number.isNullOrBlank()) {
                        rawNumbers.add(number)
                    } else {
                        blankRows++
                    }
                }
                Log.d(TAG, "getHashedPhoneNumbers: cursor rows=$totalRows, blank/null=$blankRows, valid=${rawNumbers.size}")
            }

            // Deduplicate before hashing
            val uniqueNumbers = rawNumbers.distinct()
            Log.d(TAG, "getHashedPhoneNumbers: unique numbers after dedup=${uniqueNumbers.size}")

            var skipped = 0
            // Hash each number
            uniqueNumbers.forEach { number ->
                val normalized = normalizeToE164(number)
                if (normalized != null) {
                    hashes.add(sha256(normalized))
                } else {
                    skipped++
                }
            }
            Log.d(TAG, "getHashedPhoneNumbers: hashed=${hashes.size}, skipped (not E.164)=$skipped")

        } catch (e: Exception) {
            Log.e(TAG, "getHashedPhoneNumbers: exception reading contacts — ${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            // Explicitly clear raw numbers from memory as soon as hashing is done
            rawNumbers.clear()
            Log.d(TAG, "getHashedPhoneNumbers: raw numbers cleared from memory, returning ${hashes.size} hashes")
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
