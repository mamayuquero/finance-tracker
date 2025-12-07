package com.example.financetracker

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityAnalyticsBinding
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class AnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalyticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadChartData()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadChartData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("transactions").child(userId)

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val categoryMap = HashMap<String, Double>()

                // 1. Group data by Category
                for (data in snapshot.children) {
                    val transaction = data.getValue(Transaction::class.java)
                    // We only chart "Expenses"
                    if (transaction != null && transaction.type == "Expense") {
                        val currentTotal = categoryMap.getOrDefault(transaction.category, 0.0)
                        categoryMap[transaction.category] = currentTotal + transaction.amount
                    }
                }

                // 2. Prepare entries for MPAndroidChart
                val entries = ArrayList<PieEntry>()
                for ((category, amount) in categoryMap) {
                    entries.add(PieEntry(amount.toFloat(), category))
                }

                setupPieChart(entries)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupPieChart(entries: ArrayList<PieEntry>) {
        // Visual Styling
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.parseColor("#4E54C8"), // Blue
            Color.parseColor("#FF5252"), // Red
            Color.parseColor("#00C853"), // Green
            Color.parseColor("#FFC107"), // Yellow
            Color.parseColor("#29B6F6")  // Light Blue
        )
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 14f

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false
        binding.pieChart.centerText = "Expenses"
        binding.pieChart.setCenterTextSize(18f)
        binding.pieChart.animateY(1000) // Animation
        binding.pieChart.invalidate() // Refresh
    }
}