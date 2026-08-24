package com.zaaaam.kalku.calc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.zaaaam.kalku.ui.theme.MonoNumbers

private enum class KeyRole { DIGIT, OP, UTIL, CLEAR, EQUALS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    vm: CalcViewModel,
    hapticsEnabled: Boolean,
    onUnlocked: (UnlockSignal) -> Unit,
) {
    val history by vm.history.collectAsState()
    val unlock by vm.unlockSignal.collectAsState()
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var showHistory by remember { mutableStateOf(false) }
    var sciMode by remember { mutableStateOf(false) }

    LaunchedEffect(unlock) {
        when (val s = unlock) {
            is UnlockSignal.Enter -> { vm.consumeUnlock(); onUnlocked(s) }
            is UnlockSignal.Setup -> { vm.consumeUnlock(); onUnlocked(s) }
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            SegmentedPill(
                options = listOf("DEG", "RAD"),
                selectedIndex = if (vm.angleMode == AngleMode.DEG) 0 else 1,
                onSelect = { idx ->
                    val targetDeg = idx == 0
                    if (targetDeg != (vm.angleMode == AngleMode.DEG)) vm.toggleAngle()
                },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(vm.expression.ifBlank { "0" }))
            }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = {
                clipboard.getText()?.text?.let { vm.replaceExpression(it.take(200)) }
            }) { Icon(Icons.Default.ContentPaste, contentDescription = "Paste") }
            IconButton(onClick = { sciMode = !sciMode }) {
                Icon(
                    Icons.Default.Functions,
                    contentDescription = "Scientific",
                    tint = if (sciMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showHistory = true }) {
                Icon(Icons.Default.History, contentDescription = "History")
            }
        }

        // Display — recessed LCD feel via surface tone
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
                .padding(top = 6.dp, bottom = 4.dp),
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
                fontFamily = MonoNumbers,
            )
            val pv = vm.preview()
            if (pv.isNotEmpty() && pv != vm.expression) {
                Text(
                    text = "= $pv",
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    fontFamily = MonoNumbers,
                )
            }
        }

            if (sciMode) {
            val sciKeys = listOf("sin(", "cos(", "tan(", "ln(", "log(", "sqrt(", "asin(", "acos(", "atan(", "exp(", "%", "!")
            sciKeys.chunked(6).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { key ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clickable { vm.append(key); tap() },
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    key.removeSuffix("(").replace("sqrt", "√"),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = MonoNumbers,
                                )
                            }
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        data class Pad(val label: String, val role: KeyRole, val token: String? = null)

        // Sesuai HTML: 5 baris tetap [C () ÷] / [789×] / [456−] / [123+] / [⌫ 0 · =]
        val rows = listOf(
            listOf(Pad("C", KeyRole.CLEAR), Pad("(", KeyRole.UTIL, "("), Pad(")", KeyRole.UTIL, ")"), Pad("÷", KeyRole.OP, "/")),
            listOf(Pad("7", KeyRole.DIGIT, "7"), Pad("8", KeyRole.DIGIT, "8"), Pad("9", KeyRole.DIGIT, "9"), Pad("×", KeyRole.OP, "*")),
            listOf(Pad("4", KeyRole.DIGIT, "4"), Pad("5", KeyRole.DIGIT, "5"), Pad("6", KeyRole.DIGIT, "6"), Pad("−", KeyRole.OP, "-")),
            listOf(Pad("1", KeyRole.DIGIT, "1"), Pad("2", KeyRole.DIGIT, "2"), Pad("3", KeyRole.DIGIT, "3"), Pad("+", KeyRole.OP, "+")),
            listOf(Pad("⌫", KeyRole.UTIL), Pad("0", KeyRole.DIGIT, "0"), Pad("·", KeyRole.UTIL, "."), Pad("=", KeyRole.EQUALS)),
        )

        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { pad ->
                    if (pad.label == "⌫") {
                        CalcKey("", KeyRole.UTIL, Modifier.weight(1f).height(64.dp), icon = Icons.AutoMirrored.Filled.Backspace, iconDesc = "Backspace") {
                            vm.backspace(); tap()
                        }
                    } else {
                        CalcKey(role = pad.role, modifier = Modifier.weight(1f).height(64.dp), label = pad.label) {
                            when (pad.label) {
                                "C" -> vm.clearAll()
                                "=" -> vm.onEquals()
                                else -> vm.append(pad.token ?: pad.label)
                            }
                            tap()
                        }
                    }
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            HistorySheet(
                items = history,
                onPick = { item ->
                    vm.replaceExpression(item.expression)
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
    val m = Regex("(\\d+\\.?\\d*)$").find(expr) ?: return
    val num = m.groupValues[1]
    val start = m.range.first
    val hasMinus = start > 0 && expr[start - 1] == '-' && (start == 1 || expr[start - 2] in "+-*/(")
    val updated = if (hasMinus) {
        expr.removeRange(start - 1, start + num.length)
    } else {
        expr.replaceRange(start, start + num.length, "(-$num)")
    }
    vm.replaceExpression(updated)
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
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            items(items, key = { it.id }) { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(item) }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        item.expression,
                        fontSize = 13.sp,
                        fontFamily = MonoNumbers,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "= ${item.result}",
                        fontSize = 17.sp,
                        fontFamily = MonoNumbers,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedPill(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { idx, opt ->
            val selected = idx == selectedIndex
            Text(
                opt,
                fontSize = 11.sp,
                fontFamily = MonoNumbers,
                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onSelect(idx) }
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CalcKey(
    label: String,
    role: KeyRole,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconDesc: String? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    data class Look(val container: androidx.compose.ui.graphics.Color, val content: androidx.compose.ui.graphics.Color)
    val look = when (role) {
        KeyRole.DIGIT -> Look(cs.surfaceVariant, cs.onSurface)
        KeyRole.OP -> Look(cs.secondaryContainer, cs.onSecondaryContainer)
        KeyRole.UTIL -> Look(cs.background, cs.onSurfaceVariant)
        KeyRole.CLEAR -> Look(cs.errorContainer, cs.onErrorContainer)
        KeyRole.EQUALS -> Look(cs.tertiary, cs.onTertiary)
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = look.container,
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = iconDesc, tint = cs.onSurfaceVariant)
            } else {
                Text(
                    label,
                    fontSize = when (role) {
                        KeyRole.OP -> 24.sp
                        KeyRole.EQUALS -> 22.sp
                        KeyRole.UTIL -> 17.sp
                        else -> 21.sp
                    },
                    fontWeight = if (role == KeyRole.EQUALS) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                    fontFamily = MonoNumbers,
                    color = look.content,
                )
            }
        }
    }
}
