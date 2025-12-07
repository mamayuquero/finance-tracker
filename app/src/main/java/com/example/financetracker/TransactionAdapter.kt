package com.example.financetracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.databinding.ItemTransactionBinding
import java.text.NumberFormat
import java.util.Locale

class TransactionAdapter(private val transactionList: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    // ViewBinding makes it easy to access views without findViewById
    class ViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = transactionList[position]

        // 1. Set Basic Text
        holder.binding.tvTitle.text = item.title
        holder.binding.tvCategory.text = item.category

        // 2. Set Icon Letter (Visual Polish)
        // We take the first letter of the category, capitalize it, and put it in the circle
        if (item.category.isNotEmpty()) {
            holder.binding.tvIconText.text = item.category.first().toString().uppercase()
        } else {
            holder.binding.tvIconText.text = "?"
        }


        val formattedAmount = CurrencyUtils.toRupiah(item.amount)

        // 4. Color Logic (The "Clean UI" Requirement)
        if (item.type == "Income") {
            // INCOME: Green Color, Plus Sign
            holder.binding.tvAmount.setTextColor(Color.parseColor("#00C853"))
            holder.binding.tvAmount.text = "+ $formattedAmount"

        } else {
            // EXPENSE: Red Color, Minus Sign
            holder.binding.tvAmount.setTextColor(Color.parseColor("#FF5252"))
            holder.binding.tvAmount.text = "- $formattedAmount"

            // Optional: You could also change the Icon Letter color to match
            holder.binding.tvIconText.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    override fun getItemCount() = transactionList.size
}