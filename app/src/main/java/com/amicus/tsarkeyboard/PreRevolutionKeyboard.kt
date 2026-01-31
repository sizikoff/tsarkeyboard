package com.amicus.tsarkeyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View

class PreRevolutionKeyboard : InputMethodService(),
    KeyboardView.OnKeyboardActionListener {

    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isShifted = false

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

        // Backspace
        if (primaryCode == -5 || primaryCode == Keyboard.KEYCODE_DELETE) {
            inputConnection.deleteSurroundingText(1, 0)
            return
        }

        // Enter
        if (primaryCode == 10) {
            inputConnection.sendKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            )
            return
        }

        // Пробел
        if (primaryCode == 32) {
            inputConnection.commitText(" ", 1)
            return
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

    // Обязательные методы интерфейса
    override fun onPress(primaryCode: Int) {
        // Короткая и ощутимая вибрация
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Используем EFFECT_CLICK - стандартный клик-эффект
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10) // Короткая вибрация 10ms
            }
        } catch (e: Exception) {
            // Игнорируем ошибки
        }
    }


    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
