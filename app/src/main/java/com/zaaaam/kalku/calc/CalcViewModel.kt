package com.zaaaam.kalku.calc

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zaaaam.kalku.KalkuApp
import com.zaaaam.kalku.core.AngleMode
import com.zaaaam.kalku.core.EvalResult
import com.zaaaam.kalku.core.Evaluator
import com.zaaaam.kalku.core.PinHasher
import com.zaaaam.kalku.data.CalcHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Default secret used on a fresh install; user must personalize it on first entry. */
const val DEFAULT_PIN = "1234"

sealed interface UnlockSignal {
    /** Secret matched an existing PIN; carries it for session/key unlocking. */
    data class Enter(val pin: String) : UnlockSignal

    /** Default secret hit but no PIN configured yet — run first-time setup. */
    data object Setup : UnlockSignal
}

class CalcViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as KalkuApp).container
    private val settings = c.settings

    var expression by mutableStateOf("")
        private set

    var justEvaluated by mutableStateOf(false)
        private set

    var angleMode by mutableStateOf(AngleMode.DEG)
        private set

    val unlockSignal = kotlinx.coroutines.flow.MutableStateFlow<UnlockSignal?>(null)

    fun consumeUnlock() { unlockSignal.value = null }

    private val precisionState = kotlinx.coroutines.flow.MutableStateFlow(10)
    val precision: StateFlow<Int> get() = precisionState

    init {
        viewModelScope.launch {
            angleMode = runCatching {
                AngleMode.valueOf(settings.angleDefault.first())
            }.getOrDefault(AngleMode.DEG)
            precisionState.value = settings.precision.first()
        }
    }

    val history: StateFlow<List<CalcHistoryEntity>> =
        c.db.calcHistoryDao().observeHistory(100)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Live result preview while typing (empty when incomplete). */
    fun preview(): String =
        Evaluator.preview(expression, angleMode, precisionState.value)

    fun toggleAngle() {
        angleMode = if (angleMode == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
        viewModelScope.launch { settings.setAngleDefault(angleMode.name) }
    }

    fun append(token: String) {
        if (justEvaluated && token.isNotEmpty() && (token.first().isDigit() || token == "." || token == "(")) {
            expression = ""
        }
        justEvaluated = false
        val cur = expression
        val last = cur.lastOrNull()

        when (token) {
            "." -> {
                if (last?.isDigit() != true) {
                    if (cur.endsWith(".")) return
                    expression = cur + if (last?.isLetterOrDigit() == true) "" else "0."
                } else {
                    val numStart = cur.indexOfLast { !it.isDigit() && it != '.' } + 1
                    if (cur.substring(numStart).contains('.')) return
                    expression = cur + token
                }
                return
            }
            "+", "-", "*", "/" -> {
                if (cur.isEmpty()) {
                    if (token == "-" || token == "+") expression = token
                    return
                }
                if (last != null && last in "+-*/") {
                    expression = cur.dropLast(1) + token
                } else {
                    expression = cur + token
                }
                return
            }
            "(", ")" -> { /* fallthrough plain append */ }
        }
        if (cur.length < 200) expression = cur + token
    }

    fun backspace() {
        if (justEvaluated) {
            expression = ""
            justEvaluated = false
        } else {
            expression = expression.dropLast(1)
        }
    }

    fun clearAll() {
        expression = ""
        justEvaluated = false
    }

    /** Replaces the working expression, e.g. from history tap. */
    fun replaceExpression(text: String) {
        expression = text
        justEvaluated = false
    }

    /**
     * Handles '='. A matching secret opens the vault instead of evaluating;
     * everything else evaluates normally (camouflage preserved).
     *
     * Privacy: pure-digit sequences (plausible secret attempts) are never written
     * to history, so near-miss PIN guesses can't leak into the visible history.
     */
    fun onEquals() {
        val expr = expression.trim()
        if (expr.isEmpty()) return
        viewModelScope.launch {
            // Single-flight: spamming '=' must not stack parallel attempts that
            // share one failedAttempts counter (double-counted, split backoff).
            if (!pinCheckMutex.tryLock()) return@launch
            try {
                val looksLikeSecret = expr.all { it.isDigit() } && expr.length in 4..16

                if (looksLikeSecret) {
                    // Backoff applies only to secret-shaped input; plain calculations
                    // must never be delayed by failed PIN attempts.
                    val backoffMs = if (failedAttempts >= 3) minOf((failedAttempts - 2) * 5_000L, 30_000L) else 0L
                    if (backoffMs > 0) kotlinx.coroutines.delay(backoffMs)

                    val storedHash = settings.currentPinHash()
                    val matchesStored = !storedHash.isNullOrBlank() && withContext(kotlinx.coroutines.Dispatchers.Default) {
                        PinHasher.verify(expr, storedHash)
                    }
                    val matchesFresh = storedHash.isNullOrBlank() && expr == DEFAULT_PIN
                    if (matchesStored || matchesFresh) {
                        failedAttempts = 0
                        clearAll()
                        unlockSignal.value =
                            if (matchesFresh) UnlockSignal.Setup else UnlockSignal.Enter(expr)
                        return@launch
                    }
                    failedAttempts++
                }

                when (val r = Evaluator.evaluate(expr, angleMode, precisionState.value)) {
                    is EvalResult.Value -> {
                        // Plausible-secret inputs evaluate silently: the camouflage
                        // stays believable but guesses never leak into history.
                        if (!looksLikeSecret) {
                            c.db.calcHistoryDao().insert(
                                CalcHistoryEntity(
                                    expression = expr,
                                    result = r.formatted,
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                        }
                        expression = r.formatted
                        justEvaluated = true
                    }
                    is EvalResult.Error -> {
                        expression = ""
                        justEvaluated = true
                    }
                }
            } finally {
                pinCheckMutex.unlock()
            }
        }
    }

    private var failedAttempts = 0
    private val pinCheckMutex = kotlinx.coroutines.sync.Mutex()

    fun clearHistory() = viewModelScope.launch { c.db.calcHistoryDao().clear() }
}
