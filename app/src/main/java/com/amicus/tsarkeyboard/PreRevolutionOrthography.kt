package com.amicus.tsarkeyboard

import android.content.Context
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * Дореформенная орфография и опциональные архаизмы (стилизация под старину).
 * Словари дополняются из assets: orthography.json, archaisms.json.
 * Настройки: SharedPreferences (архаизмы вкл/выкл читаются при init).
 */
object PreRevolutionOrthography {

    const val PREFS_NAME = "tsarkeyboard"
    const val PREF_AUTO_REPLACE = "auto_replace"
    const val PREF_ARCHAISMS = "archaisms"

    private val RUSSIAN_VOWELS = "аеёиоуыэюяь"

    private val dictionary = mutableMapOf(
        "мне" to "мнѣ", "нет" to "нѣт", "свет" to "свѣт", "дело" to "дѣло", "тело" to "тѣло",
        "село" to "сѣло", "везде" to "вездѣ", "хлеб" to "хлѣб", "белый" to "бѣлый", "снег" to "снѣг",
        "след" to "слѣд", "лето" to "лѣто", "мед" to "мѣд", "место" to "мѣсто", "вера" to "вѣра",
        "век" to "вѣкъ", "человек" to "человѣкъ", "левый" to "лѣвый", "зеленый" to "зеленый",
        "деньги" to "деньги", "смотреть" to "смотрѣть", "видеть" to "видѣть", "сидеть" to "сидѣть",
        "есть" to "ѣсть", "смех" to "смѣх", "надежда" to "надѣжда", "средство" to "средство",
        "был" to "былъ", "была" to "была", "было" to "было", "были" to "были",
        "русский" to "русскій", "синий" to "синій", "какой" to "какій", "такой" to "такій",
        "великий" to "великій", "старый" to "старый", "новый" to "новый",
        "мир" to "миръ", "интернет" to "интернетъ", "компьютер" to "компьютеръ",
        "дар" to "даръ", "стол" to "столъ", "год" to "годъ", "народ" to "народъ", "город" to "городъ",
        "сон" to "сонъ", "кон" to "конъ", "брат" to "братъ", "враг" to "врагъ", "круг" to "кругъ",
        "друг" to "другъ", "шаг" to "шагъ", "бег" to "бѣгъ", "срок" to "срокъ",
    )

    private val archaismsMap = mutableMapOf<String, String>()

    private var useArchaisms = false
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        useArchaisms = prefs.getBoolean(PREF_ARCHAISMS, false)

        try {
            context.assets.open("orthography.json").use { stream ->
                val json = JSONObject(InputStreamReader(stream, Charsets.UTF_8).readText())
                json.keys().asSequence().forEach { key ->
                    json.optString(key, "").takeIf { it.isNotEmpty() }?.let { dictionary[key] = it }
                }
            }
        } catch (_: Exception) { }

        try {
            context.assets.open("archaisms.json").use { stream ->
                val json = JSONObject(InputStreamReader(stream, Charsets.UTF_8).readText())
                json.keys().asSequence().forEach { key ->
                    json.optString(key, "").takeIf { it.isNotEmpty() }?.let { archaismsMap[key] = it }
                }
            }
        } catch (_: Exception) { }
    }

    /** Обновить флаг архаизмов (настройки сменились — клавиатура подхватит при следующем пробеле). */
    fun setArchaismsEnabled(enabled: Boolean) {
        useArchaisms = enabled
    }

    /**
     * Возвращает дореформенный вариант слова (орфография + опционально архаизмы).
     */
    fun replaceWord(word: String): String {
        if (word.isEmpty()) return word
        var current = word
        val lower = current.lowercase()

        if (useArchaisms && archaismsMap.isNotEmpty()) {
            archaismsMap[lower]?.let { repl ->
                current = preserveCapitalization(word, repl)
            }
        }

        val lowerCurrent = current.lowercase()
        dictionary[lowerCurrent]?.let { repl ->
            return preserveCapitalization(current, repl)
        }

        if (lowerCurrent.endsWith("ий")) {
            return preserveCapitalization(current, lowerCurrent.dropLast(2) + "ій")
        }

        val last = lowerCurrent.last()
        if (last != 'ь' && last !in RUSSIAN_VOWELS) {
            return preserveCapitalization(current, lowerCurrent + "ъ")
        }

        return current
    }

    private fun preserveCapitalization(original: String, replacement: String): String {
        if (replacement.isEmpty()) return replacement
        if (original.isNotEmpty() && original[0].isUpperCase()) {
            return replacement.replaceFirstChar { it.uppercase() }
        }
        return replacement
    }
}
