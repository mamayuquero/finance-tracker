package com.example.financetracker

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    // Force Indonesian Locale
    private val localeID = Locale("id", "ID")

    fun toRupiah(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(localeID)
        // Remove the ",00" at the end if you want a cleaner look (optional)
        return format.format(amount).replace(",00", "")
    }
}