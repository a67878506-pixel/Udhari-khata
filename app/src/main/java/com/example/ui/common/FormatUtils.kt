package com.example.ui.common

import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

object FormatUtils {
    private val indianLocale = Locale.forLanguageTag("en-IN")
    private val currencyFormat = NumberFormat.getCurrencyInstance(indianLocale).apply {
        maximumFractionDigits = 0
    }

    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", indianLocale)
    private val shortDateFormat = SimpleDateFormat("dd MMM, hh:mm a", indianLocale)

    fun formatRupees(amount: Long): String {
        return try {
            val formatted = currencyFormat.format(abs(amount))
            if (amount < 0) "-$formatted" else formatted
        } catch (_: Exception) {
            if (amount < 0) "-₹${abs(amount)}" else "₹$amount"
        }
    }

    fun formatDateTime(timestamp: Timestamp?): String {
        if (timestamp == null) return "Just now"
        return try {
            dateTimeFormat.format(timestamp.toDate())
        } catch (_: Exception) {
            ""
        }
    }

    fun formatShortDateTime(timestamp: Timestamp?): String {
        if (timestamp == null) return "Just now"
        return try {
            shortDateFormat.format(timestamp.toDate())
        } catch (_: Exception) {
            ""
        }
    }

    fun getNetBalanceLabel(netBalance: Long): String {
        return when {
            netBalance > 0 -> "${formatRupees(netBalance)} Lena"
            netBalance < 0 -> "${formatRupees(abs(netBalance))} Dena"
            else -> "Hisab Barabar"
        }
    }
}
