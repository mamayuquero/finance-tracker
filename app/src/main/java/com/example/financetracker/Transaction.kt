package com.example.financetracker

data class Transaction(
    var id: String = "",
    var title: String = "",
    var amount: Double = 0.0,
    var type: String = "Expense", // "Income" or "Expense"
    var category: String = "General",
    var timestamp: Long = System.currentTimeMillis()
)