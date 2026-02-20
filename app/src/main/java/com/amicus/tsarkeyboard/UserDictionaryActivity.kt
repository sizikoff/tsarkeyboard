package com.amicus.tsarkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.io.InputStreamReader

class UserDictionaryActivity : AppCompatActivity() {

    private lateinit var listWords: ListView
    private val entries = mutableListOf<Pair<String, String>>()

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val json = InputStreamReader(stream).readText()
                val count = PreRevolutionOrthography.importUserDictionary(this, json)
                Toast.makeText(this, "Добавлено пар: $count", Toast.LENGTH_SHORT).show()
                refreshList()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка чтения файла", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_dictionary)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        listWords = findViewById(R.id.listWords)
        refreshList()

        listWords.setOnItemLongClickListener { _, _, position, _ ->
            val (word, repl) = entries[position]
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.delete_confirm))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    PreRevolutionOrthography.removeUserEntry(this, word)
                    refreshList()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { showAddDialog() }
        findViewById<MaterialButton>(R.id.btnExport).setOnClickListener { exportDictionary() }
        findViewById<MaterialButton>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun refreshList() {
        PreRevolutionOrthography.init(this)
        entries.clear()
        entries.addAll(PreRevolutionOrthography.getUserEntries(this))
        listWords.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            entries.map { "${it.first} → ${it.second}" }
        )
    }

    private fun showAddDialog() {
        val container = layoutInflater.inflate(R.layout.dialog_add_word, null)
        val inputWord = container.findViewById<TextInputEditText>(R.id.inputWord)
        val inputReplacement = container.findViewById<TextInputEditText>(R.id.inputReplacement)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_word_dialog_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val w = inputWord.text?.toString()?.trim() ?: return@setPositiveButton
                val r = inputReplacement.text?.toString()?.trim() ?: return@setPositiveButton
                if (w.isNotEmpty() && r.isNotEmpty()) {
                    PreRevolutionOrthography.addUserEntry(this, w, r)
                    refreshList()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportDictionary() {
        val json = PreRevolutionOrthography.exportUserDictionary(this)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("user_orthography.json", json))
        Toast.makeText(this, "Словарь скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json)
            putExtra(Intent.EXTRA_TITLE, "user_orthography.json")
        }
        startActivity(Intent.createChooser(share, "Экспорт словаря"))
    }
}
