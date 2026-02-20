package com.amicus.tsarkeyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView

class PreRevolutionKeyboard : InputMethodService(),
    KeyboardView.OnKeyboardActionListener {

    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null
    private var isShifted = false

    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null
    private var backspaceHoldMode = false

    private var candidateStripScroll: View? = null
    private var candidateChip: TextView? = null
    private var candidateWord: String = ""
    private var candidateReplacement: String = ""

    override fun onCreate() {
        super.onCreate()
        PreRevolutionOrthography.init(applicationContext)
    }

    override fun onCreateInputView(): View {
        val container = layoutInflater.inflate(R.layout.keyboard_with_candidates, null)
        candidateStripScroll = container.findViewById(R.id.candidateStripScroll)
        candidateChip = container.findViewById(R.id.candidateChip)
        keyboardView = container.findViewById(R.id.keyboardInclude) as KeyboardView

        keyboard = Keyboard(this, R.xml.keyboard_russian)
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)

        candidateChip?.setOnClickListener {
            applyCandidate()
        }

        return container
    }

    /** Вставить подсказку вместо текущего слова и скрыть полосу. */
    private fun applyCandidate() {
        if (candidateWord.isEmpty() || candidateReplacement.isEmpty()) return
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(candidateWord.length, 0)
        ic.commitText(candidateReplacement, 1)
        hideCandidateStrip()
    }

    private fun showCandidateStrip(replacement: String) {
        candidateWord = getLastWord(currentInputConnection ?: return) ?: return
        candidateReplacement = replacement
        candidateChip?.text = replacement
        candidateChip?.visibility = View.VISIBLE
        candidateStripScroll?.visibility = View.VISIBLE
    }

    private fun hideCandidateStrip() {
        candidateWord = ""
        candidateReplacement = ""
        candidateStripScroll?.visibility = View.GONE
        candidateChip?.visibility = View.GONE
    }

    /** Обновить подсказку по последнему слову перед курсором. */
    private fun updateCandidateStrip() {
        val ic = currentInputConnection ?: run {
            hideCandidateStrip()
            return
        }
        val word = getLastWord(ic)
        if (word.isNullOrEmpty()) {
            hideCandidateStrip()
            return
        }
        val prefs = getSharedPreferences(PreRevolutionOrthography.PREFS_NAME, Context.MODE_PRIVATE)
        PreRevolutionOrthography.setArchaismsEnabled(prefs.getBoolean(PreRevolutionOrthography.PREF_ARCHAISMS, false))
        val replacement = PreRevolutionOrthography.replaceWord(word)
        if (replacement != word) {
            candidateWord = word
            candidateReplacement = replacement
            candidateChip?.text = replacement
            candidateChip?.visibility = View.VISIBLE
            candidateStripScroll?.visibility = View.VISIBLE
        } else {
            hideCandidateStrip()
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection ?: return

        // Shift
        if (primaryCode == -1 || primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift()
            return
        }

        // Backspace: выделение → удалить; удержание (повтор) → удалить слово; иначе 1 символ
        if (primaryCode == -5 || primaryCode == Keyboard.KEYCODE_DELETE) {
            val selected = inputConnection.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                inputConnection.commitText("", 1)
            } else if (backspaceHoldMode) {
                deleteLastWord(inputConnection)
            } else {
                inputConnection.deleteSurroundingText(1, 0)
                backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
                backspaceRunnable = Runnable {
                    backspaceHoldMode = true
                }
                backspaceHandler.postDelayed(backspaceRunnable!!, 500)
            }
            updateCandidateStrip()
            return
        }

        // Enter — орфография и перенос строки
        if (primaryCode == 10) {
            handleWordEnd(inputConnection, "\n")
            hideCandidateStrip()
            return
        }

        // Пробел, запятая, точка, ; : ? ! — подставляем орфографию перед знаком
        when (primaryCode) {
            32 -> { handleWordEnd(inputConnection, " "); hideCandidateStrip(); return }
            44 -> { handleWordEnd(inputConnection, ","); hideCandidateStrip(); return }
            46 -> { handleWordEnd(inputConnection, "."); hideCandidateStrip(); return }
            59 -> { handleWordEnd(inputConnection, ";"); hideCandidateStrip(); return }
            58 -> { handleWordEnd(inputConnection, ":"); hideCandidateStrip(); return }
            63 -> { handleWordEnd(inputConnection, "?"); hideCandidateStrip(); return }
            33 -> { handleWordEnd(inputConnection, "!"); hideCandidateStrip(); return }
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
            updateCandidateStrip()
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

    /** Удаляет последнее слово перед курсором (как в GBoard при удержании Backspace). */
    private fun deleteLastWord(inputConnection: InputConnection) {
        val word = getLastWord(inputConnection) ?: run {
            inputConnection.deleteSurroundingText(1, 0)
            return
        }
        inputConnection.deleteSurroundingText(word.length, 0)
    }

    override fun onRelease(primaryCode: Int) {
        if (primaryCode == -5 || primaryCode == Keyboard.KEYCODE_DELETE) {
            backspaceRunnable?.let { backspaceHandler.removeCallbacks(it) }
            backspaceRunnable = null
            backspaceHoldMode = false
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


    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
