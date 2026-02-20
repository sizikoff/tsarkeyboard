package com.amicus.tsarkeyboard

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PreRevolutionOrthography.PREFS_NAME, MODE_PRIVATE)

        findViewById<MaterialButton>(R.id.menuButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<SwitchCompat>(R.id.switchAutoReplace).apply {
            isChecked = prefs.getBoolean(PreRevolutionOrthography.PREF_AUTO_REPLACE, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(PreRevolutionOrthography.PREF_AUTO_REPLACE, checked).apply()
            }
        }

        findViewById<SwitchCompat>(R.id.switchArchaisms).apply {
            isChecked = prefs.getBoolean(PreRevolutionOrthography.PREF_ARCHAISMS, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(PreRevolutionOrthography.PREF_ARCHAISMS, checked).apply()
            }
        }

        findViewById<SwitchCompat>(R.id.switchVibration).apply {
            isChecked = prefs.getBoolean(PreRevolutionOrthography.PREF_VIBRATION, true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(PreRevolutionOrthography.PREF_VIBRATION, checked).apply()
            }
        }

        findViewById<TextView>(R.id.aboutText).setOnClickListener {
            val versionName = getVersionName() ?: "1.0"
            AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(getString(R.string.about_text, versionName))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        val versionTextView = findViewById<TextView>(R.id.versionText)
        val versionName = getVersionName()
        versionTextView.text = "© Ваше имя, v$versionName"
    }

    private fun getVersionName(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
}
