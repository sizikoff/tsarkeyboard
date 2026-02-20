package com.amicus.tsarkeyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection

class PreRevolutionKeyboard : InputMethodService(),
    KeyboardView.OnKeyboardActionListener {

    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isShifted = false

    override fun onCreate() {
        super.onCreate()
        PreRevolutionOrthography.init(applicationContext)
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(
            R.layout.keyboard,
            null
        ) as KeyboardView

        keyboard = Keyboard(this, R.xml.keyboard_russian)
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)

        return keyboardView!!
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return

        // Shift
        if (primaryCode == -1 || primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift()
            return
        }

        // Backspace: если есть выделение — удаляем его, иначе один символ слева
        if (primaryCode == -5 || primaryCode == Keyboard.KEYCODE_DELETE) {
            val selected = inputConnection.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                inputConnection.commitText("", 1)
            } else {
                inputConnection.deleteSurroundingText(1, 0)
            }
            return
        }

        // Enter — орфография и перенос строки
        if (primaryCode == 10) {
            handleWordEnd(inputConnection, "\n")
            return
        }

        // Пробел, запятая, точка, ; : ? ! — подставляем орфографию перед знаком
        when (primaryCode) {
            32 -> { handleWordEnd(inputConnection, " "); return }
            44 -> { handleWordEnd(inputConnection, ","); return }
            46 -> { handleWordEnd(inputConnection, "."); return }
            59 -> { handleWordEnd(inputConnection, ";"); return }
            58 -> { handleWordEnd(inputConnection, ":"); return }
            63 -> { handleWordEnd(inputConnection, "?"); return }
            33 -> { handleWordEnd(inputConnection, "!"); return }
        }

        // Переключение клавиатуры
        if (primaryCode == -100 || primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            try {
                val imeManager = getSystemService(INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imeManager.showInputMethodPicker()
            } catch (e: Exception) {
                switchToNextInputMethod(false)
            }
            return
        }

        // Обычные буквы - только валидные коды
        if (primaryCode in 32..65535) {
            var char = primaryCode.toChar()

            if (isShifted && char.isLetter()) {
                char = char.uppercaseChar()
                isShifted = false
                updateShiftState()
            }

            inputConnection.commitText(char.toString(), 1)
        }
    }


    /** Подставляет дореформенную орфографию для последнего слова и вставляет суффикс (пробел, запятая, точка). */
    private fun handleWordEnd(inputConnection: InputConnection, suffix: String) {
        val prefs = getSharedPreferences(PreRevolutionOrthography.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PreRevolutionOrthography.PREF_AUTO_REPLACE, true)) {
            inputConnection.commitText(suffix, 1)
            return
        }
        PreRevolutionOrthography.setArchaismsEnabled(
            prefs.getBoolean(PreRevolutionOrthography.PREF_ARCHAISMS, false)
        )
        val word = getLastWord(inputConnection) ?: run {
            inputConnection.commitText(suffix, 1)
            return
        }
        val replacement = PreRevolutionOrthography.replaceWord(word)
        if (replacement != word) {
            inputConnection.deleteSurroundingText(word.length, 0)
            inputConnection.commitText(replacement + suffix, 1)
        } else {
            inputConnection.commitText(suffix, 1)
        }
    }

    /** Возвращает последнее слово перед курсором (только буквы) или null если пусто. */
    private fun getLastWord(inputConnection: InputConnection): String? {
        val before = inputConnection.getTextBeforeCursor(80, 0) ?: return null
        val str = before.toString()
        var i = str.length - 1
        while (i >= 0 && str[i].isLetter()) i--
        val word = str.substring(i + 1)
        return word.ifEmpty { null }
    }

    private fun handleShift() {
        isShifted = !isShifted
        updateShiftState()
    }

    private fun updateShiftState() {
        keyboard?.let { kb ->
            kb.isShifted = isShifted
            keyboardView?.invalidateAllKeys()
        }
    }

    override fun onPress(primaryCode: Int) {
        val prefs = getSharedPreferences(PreRevolutionOrthography.PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PreRevolutionOrthography.PREF_VIBRATION, true)) return
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        } catch (_: Exception) { }
    }


    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
