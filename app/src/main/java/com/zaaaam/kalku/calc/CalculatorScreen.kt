package com.zaaaam.kalku.calc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaaaam.kalku.core.AngleMode
import com.zaaaam.kalku.data.CalcHistoryEntity

private fun displayLabel(key: String): String = when (key) {
    "*" -> "×"; "/" -> "÷"; "-" -> "−"; "+" -> "+"
    else -> key
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    vm: CalcViewModel,
    hapticsEnabled: Boolean,
    onUnlocked: (setupNeeded: Boolean) -> Unit,
) {
    val history by vm.history.collectAsState()
    val unlock by vm.unlockSignal.collectAsState()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var showHistory by remember { mutableStateOf(false) }
    var sciMode by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(unlock) {
        when (val s = unlock) {
            is UnlockSignal.Enter -> { vm.consumeUnlock(); onUnlocked(false) }
            is UnlockSignal.Setup -> { vm.consumeUnlock(); onUnlocked(true) }
            null -> {}
        }
    }

    fun tap() {
        if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = { tap(); vm.toggleAngle() },
                label = { Text(if (vm.angleMode == AngleMode.DEG) "DEG" else "RAD") },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(vm.expression.ifBlank { "0" }))
            }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = {
                clipboard.getText()?.text?.let { vm.setExpression(it.take(200)) }
            }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") }
            IconButton(onClick = { sciMode = !sciMode }) {
                Icon(
                    Icons.Default.Functions,
                    contentDescription = "Scientific",
                    tint = if (sciMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showHistory = true }) {
                Icon(Icons.Default.History, contentDescription = "History")
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = vm.expression.ifBlank { "0" },
                fontSize = 38.sp,
                lineHeight = 46.sp,
                textAlign = TextAlign.End,
                maxLines = 4,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val pv = vm.preview()
            if (pv.isNotEmpty() && pv != vm.expression) {
                Text(
                    text = "= $pv",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }

        if (sciMode) {
            val sciRows = listOf(
                listOf("sin(", "cos(", "tan(", "ln(", "log(", "sqrt("),
                listOf("π", "e", "(", ")", "^", "!"),
                listOf("asin(", "acos(", "atan(", "exp(", "%", ","),
            )
            sciRows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clickable {
                                    vm.append(if (key == "π") "π" else if (key == "e") "e" else key)
                                    tap()
                                },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(key.removeSuffix("("), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        val rows = listOf(
            listOf("C", "(", ")", "/"),
            listOf("7", "8", "9", "*"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key -> CalcKey(label = displayLabel(key), modifier = Modifier.weight(1f).height(56.dp)) {
                    when (key) {
                        "C" -> vm.clearAll()
                        else -> vm.append(key)
                    }
                    tap()
                } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalcKey("+/-", Modifier.weight(1f).height(56.dp)) { toggleSign(vm); tap() }
            CalcKey("0", Modifier.weight(1f).height(56.dp)) { vm.append("0"); tap() }
            CalcKey(".", Modifier.weight(1f).height(56.dp)) { vm.append("."); tap() }
            CalcKey("=", Modifier.weight(1f).height(56.dp), emphasized = true) { vm.onEquals(); tap() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalcKey("", Modifier.weight(1f).height(44.dp), icon = Icons.AutoMirrored.Filled.Backspace, iconDesc = "Backspace") {
                vm.backspace(); tap()
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            HistorySheet(
                items = history,
                onPick = { item ->
                    vm.setExpression(item.expression)
                    showHistory = false
                },
                onClear = {
                    vm.clearHistory()
                    showHistory = false
                },
            )
        }
    }
}

private fun toggleSign(vm: CalcViewModel) {
    val expr = vm.expression
    if (expr.isEmpty()) return
    // Wrap the last number in (-n) form: toggling flips the leading sign of that number.
    val m = Regex("(\\d+\\.?\\d*)$").find(expr) ?: return
    val num = m.groupValues[1]
    val start = m.range.first
    val hasMinus = start > 0 && expr[start - 1] == '-' && (start == 1 || expr[start - 2] in "+-*/(")
    val updated = if (hasMinus) {
        expr.removeRange(start - 1, start + num.length)
    } else if (start > 0 && (expr[start - 1] == '(' )) {
        expr.replaceRange(start, start + num.length, "-$num")
    } else {
        expr.replaceRange(start, start + num.length, "(-$num)")
    }
    vm.setExpression(updated)
}

@Composable
private fun CalcKey(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconDesc: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = when {
            emphasized -> MaterialTheme.colorScheme.primary
            label.isEmpty() && icon != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = iconDesc, tint = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = label,
                    fontSize = 22.sp,
                    color = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HistorySheet(
    items: List<CalcHistoryEntity>,
    onPick: (CalcHistoryEntity) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Riwayat", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history")
            }
        }
        if (items.isEmpty()) {
            Text("Belum ada perhitungan", color = MaterialTheme.colorScheme.outline)
        }
        items.forEach { item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(item) }
                    .padding(vertical = 8.dp),
            ) {
                Text(item.expression, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("= ${item.result}", fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
