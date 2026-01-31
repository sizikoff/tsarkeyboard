package com.amicus.tsarkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
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

        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                // Переключение Shift
                isShifted = !isShifted
                keyboard?.isShifted = isShifted
                keyboardView?.invalidateAllKeys()
            }
            Keyboard.KEYCODE_DELETE -> {
                inputConnection.deleteSurroundingText(1, 0)
            }
            10 -> {
                inputConnection.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                )
            }
            32 -> {
                inputConnection.commitText(" ", 1)
                // TODO: Здесь будет автозамена слова на дореволюционное
            }
            Keyboard.KEYCODE_MODE_CHANGE, -100 -> {
                try {
                    val imeManager = getSystemService(INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                    imeManager.showInputMethodPicker()
                } catch (e: Exception) {
                    switchToNextInputMethod(false)
                }
            }
            else -> {
                var char = primaryCode.toChar()
                // Если Shift нажат — делаем заглавную
                if (isShifted && char.isLetter()) {
                    char = char.uppercaseChar()
                    isShifted = false // Автоматически отключаем Shift после ввода
                    keyboard?.isShifted = false
                    keyboardView?.invalidateAllKeys()
                }
                inputConnection.commitText(char.toString(), 1)
            }
        }
    }


    // Обязательные методы интерфейса
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
