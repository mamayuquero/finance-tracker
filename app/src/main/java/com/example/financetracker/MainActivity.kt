package com.example.financetracker

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.financetracker.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var database: DatabaseReference
    private lateinit var transactionList: MutableList<Transaction> // The filtered list we show

    // Cache to hold ALL data from Firebase so we can filter locally without re-downloading
    private var fullTransactionList = mutableListOf<Transaction>()

    // Default to current date
    private var selectedDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Toolbar Setup
        setSupportActionBar(binding.toolbar)

        // 2. Authentication Check
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 3. RecyclerView Setup
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        transactionList = mutableListOf()

        // Setup Swipe-to-Delete
        setupSwipeToDelete()

        // 4. Date Filter Setup
        updateDateDisplay()

        // Open Calendar when clicking the "Pill"
        binding.cardDateSelector.setOnClickListener {
            showDatePickerDialog()
        }

        // 5. Firebase Listener
        database = FirebaseDatabase.getInstance().getReference("transactions").child(userId)
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fullTransactionList.clear()

                for (data in snapshot.children) {
                    val transaction = data.getValue(Transaction::class.java)
                    transaction?.let { fullTransactionList.add(it) }
                }

                // Apply the date filter immediately after fetching data
                filterList()
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle database error
            }
        })

        // 6. Floating Action Button (Add)
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }
    }

    // --- TOOLBAR MENU (Analytics & Logout) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_analytics -> {
                startActivity(Intent(this, AnalyticsActivity::class.java))
                true
            }
            R.id.action_logout -> {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- DATE LOGIC ---
    private fun showDatePickerDialog() {
        val datePicker = android.app.DatePickerDialog(
            this,
            { _, year, month, _ ->
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, 1) // Default to 1st of month

                updateDateDisplay()
                filterList() // Refresh list for new month
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    private fun updateDateDisplay() {
        // ID Locale for month names (e.g., "Desember 2025")
        val format = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        binding.tvCurrentMonth.text = format.format(selectedDate.time)
    }

    // --- FILTER & DISPLAY LOGIC ---
    private fun filterList() {
        transactionList.clear()
        var totalBalance = 0.0

        val selectedMonth = selectedDate.get(Calendar.MONTH)
        val selectedYear = selectedDate.get(Calendar.YEAR)

        for (item in fullTransactionList) {
            val itemDate = Calendar.getInstance()
            itemDate.timeInMillis = item.timestamp

            // Check if transaction belongs to the selected Month & Year
            if (itemDate.get(Calendar.MONTH) == selectedMonth &&
                itemDate.get(Calendar.YEAR) == selectedYear) {

                transactionList.add(item)

                if (item.type == "Income") totalBalance += item.amount
                else totalBalance -= item.amount
            }
        }

        // Sort: Newest first
        transactionList.sortByDescending { it.timestamp }

        // Update Adapter
        binding.recyclerView.adapter = TransactionAdapter(transactionList)

        // Update Total Balance using the Indonesian Currency Helper
        binding.tvTotalBalance.text = CurrencyUtils.toRupiah(totalBalance)
    }

    // --- SWIPE TO DELETE ---
    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ): Boolean { return false } // We don't support drag & drop

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position < transactionList.size) {
                    val transaction = transactionList[position]

                    // Remove from Firebase
                    database.child(transaction.id).removeValue()
                        .addOnSuccessListener {
                            Snackbar.make(binding.root, "Transaction Deleted", Snackbar.LENGTH_LONG).show()
                        }
                }
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }
}