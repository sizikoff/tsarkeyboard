package com.amicus.tsarkeyboard
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Кнопка МЕНЮ
        findViewById<MaterialButton>(R.id.menuButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // Версия
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
