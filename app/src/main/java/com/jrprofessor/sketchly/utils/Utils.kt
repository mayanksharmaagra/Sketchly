package com.jrprofessor.sketchly.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

class PhoneVisualTransformation(
    private val countryCode: String,
    private val placeholderColor: Color = Color.LightGray
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= 10) text.text.substring(0..9) else text.text

        val annotatedString = buildAnnotatedString {
            // Actual text part
            append(countryCode)
            append(" (")

            for (i in trimmed.indices) {
                append(trimmed[i])
                if (i == 2) append(") ")
                if (i == 5) append(" ")
            }

            // Placeholder part
            val mask = when (trimmed.length) {
                in 0..2 -> {
                    val currentInParens = trimmed.length
                    "_".repeat(3 - currentInParens) + ") ___ ____"
                }
                in 3..5 -> {
                    val currentInMiddle = trimmed.length - 3
                    "_".repeat(3 - currentInMiddle) + " ____"
                }
                else -> {
                    val currentInLast = trimmed.length - 6
                    "_".repeat(4 - currentInLast)
                }
            }

            withStyle(style = SpanStyle(color = placeholderColor)) {
                append(mask)
            }
        }

        val prefixLength = countryCode.length + 2

        val numberOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val transformedOffset = when {
                    offset <= 2 -> offset + prefixLength
                    offset <= 5 -> offset + prefixLength + 2
                    offset <= 9 -> offset + prefixLength + 3
                    else -> 13 + prefixLength
                }
                return transformedOffset.coerceAtMost(annotatedString.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val originalOffset = when {
                    offset <= prefixLength -> 0
                    offset <= prefixLength + 3 -> offset - prefixLength
                    offset <= prefixLength + 4 -> 3
                    offset <= prefixLength + 7 -> offset - (prefixLength + 2)
                    offset <= prefixLength + 8 -> 6
                    else -> offset - (prefixLength + 3)
                }
                return originalOffset.coerceAtMost(text.text.length)
            }
        }

        return TransformedText(annotatedString, numberOffsetTranslator)
    }
}

/**
 * Returns the phone country code based on the current system locale.
 */
fun getCountryPhoneCode(): String {
    val countryIso = java.util.Locale.getDefault().country
    return countryCodeMap[countryIso] ?: "+1" // Default to +1
}

private val countryCodeMap = mapOf(
    "US" to "+1",
    "IN" to "+91",
    "GB" to "+44",
    "CA" to "+1",
    "AU" to "+61",
    "DE" to "+49",
    "FR" to "+33",
    "IT" to "+39",
    "ES" to "+34",
    "BR" to "+55",
    "RU" to "+7",
    "JP" to "+81",
    "CN" to "+86",
    "KR" to "+82",
    "AE" to "+971",
    "SA" to "+966",
    "ZA" to "+27",
    "NG" to "+234",
    "EG" to "+20",
    "PK" to "+92",
    "BD" to "+880",
    "ID" to "+62",
    "MY" to "+60",
    "SG" to "+65",
    "PH" to "+63",
    "TH" to "+66",
    "VN" to "+84",
)
