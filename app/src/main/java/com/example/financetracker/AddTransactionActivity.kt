package com.example.financetracker

import android.content.IntentSender
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityAddTransactionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var categoryList: MutableList<String>

    // 1. New Document Scanner Launcher
    private val scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    // Get the high-res cropped image URI
                    val imageUri = pages[0].imageUri
                    processReceiptImage(imageUri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadCategories()

        binding.btnAddCategory.setOnClickListener { showAddCategoryDialog() }
        binding.btnSave.setOnClickListener { saveTransaction() }

        // 2. Start the High-Quality Document Scanner
        binding.layoutAmount.setEndIconOnClickListener {
            startDocumentScan()
        }
    }

    private fun startDocumentScan() {
        val options = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true) // Allow picking from gallery
            .setResultFormats(
                com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Could not start scanner: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processReceiptImage(imageUri: android.net.Uri) {
        try {
            val image = InputImage.fromFilePath(this, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            binding.etAmount.setText("Processing...")
            binding.etAmount.isEnabled = false

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val detectedAmount = extractIndonesianPrice(visionText.text)

                    if (detectedAmount != null) {
                        // Display as plain number (e.g. 50000) for the EditText
                        binding.etAmount.setText(detectedAmount.toLong().toString())
                        Toast.makeText(this, "Found: Rp ${CurrencyUtils.toRupiah(detectedAmount)}", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.etAmount.setText("")
                        Toast.makeText(this, "Total not found. Please enter manually.", Toast.LENGTH_LONG).show()
                    }
                    binding.etAmount.isEnabled = true
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Scan Failed", Toast.LENGTH_SHORT).show()
                    binding.etAmount.isEnabled = true
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 3. Specialized Logic for Indonesian Receipts
    private fun extractIndonesianPrice(text: String): Double? {
        val lines = text.split("\n")
        var maxAmount = 0.0

        // Regex looks for patterns like:
        // 50.000
        // Rp 50.000
        // 50.000,00
        // It ignores dots used as thousand separators
        val idrRegex = Regex("(?i)(?:Rp\\.?\\s*)?([0-9]{1,3}(?:\\.[0-9]{3})*(?:,[0-9]{1,2})?)")

        for (line in lines) {
            val cleanLine = line.trim()

            // Find all matches in the line
            val matches = idrRegex.findAll(cleanLine)

            for (match in matches) {
                // The capture group 1 contains the number part (e.g., "50.000,00")
                var numberStr = match.groupValues[1]

                // CLEANUP for IDR logic:
                // 1. Remove thousands separators (.) -> "50000,00"
                numberStr = numberStr.replace(".", "")
                // 2. Replace decimal separator (,) with (.) -> "50000.00"
                numberStr = numberStr.replace(",", ".")

                try {
                    val value = numberStr.toDouble()
                    // Filter out likely noise (e.g., dates like 2025, phone numbers)
                    // Receipt totals are usually the largest number found
                    if (value > maxAmount && value < 100000000) { // Cap at 100 million to avoid phone numbers
                        maxAmount = value
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }

        return if (maxAmount > 0.0) maxAmount else null
    }

    // ... Standard Category Loading & Save Logic (Unchanged) ...
    private fun loadCategories() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val categoryRef = FirebaseDatabase.getInstance().getReference("categories").child(userId)
        categoryList = mutableListOf()
        categoryRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                categoryList.clear()
                if (snapshot.exists()) {
                    for (data in snapshot.children) {
                        val cat = data.getValue(String::class.java)
                        cat?.let { categoryList.add(it) }
                    }
                } else {
                    categoryList.addAll(listOf("Food", "Transportation", "Utilities", "Salary", "Entertainment"))
                }
                val adapter = ArrayAdapter(this@AddTransactionActivity, android.R.layout.simple_spinner_dropdown_item, categoryList)
                binding.spinnerCategory.adapter = adapter
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddCategoryDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Category")
        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton("Add") { _, _ ->
            val newCategory = input.text.toString().trim()
            if (newCategory.isNotEmpty()) {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton
                FirebaseDatabase.getInstance().getReference("categories").child(userId).push().setValue(newCategory)
            }
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun saveTransaction() {
        val title = binding.etTitle.text.toString()
        val amountString = binding.etAmount.text.toString()

        if (title.isEmpty() || amountString.isEmpty()) {
            Toast.makeText(this, "Please fill all the list", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountString.toDoubleOrNull() ?: 0.0
        val category = binding.spinnerCategory.selectedItem?.toString() ?: "Umum"
        val type = if (binding.rbIncome.isChecked) "Income" else "Expense"

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().getReference("transactions").child(userId)
        val id = ref.push().key ?: ""

        val transaction = Transaction(id, title, amount, type, category, System.currentTimeMillis())

        ref.child(id).setValue(transaction).addOnSuccessListener { finish() }
    }
}