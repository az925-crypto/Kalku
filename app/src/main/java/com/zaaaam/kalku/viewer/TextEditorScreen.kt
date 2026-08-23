package com.zaaaam.kalku.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaaaam.kalku.vault.VaultViewModel

/**
 * Text/code editor. [relPath] empty → new file in [parent].
 * Undo/redo via snapshot stack; find & replace; word/line count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    vm: VaultViewModel,
    relPath: String,
    parent: String,
    onBack: () -> Unit,
) {
    val isNew = relPath.isEmpty()
    val existing = if (!isNew) vm.byPathThenOpen(relPath) else null
    val settings by vm.settings.editorFontSize.collectAsState(initial = 14)
    val wordWrap by vm.settings.editorWordWrap.collectAsState(initial = true)
    val lineNumbersOn by vm.settings.editorLineNumbers.collectAsState(initial = true)

    var content by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(isNew) }
    var dirty by remember { mutableStateOf(false) }
    var currentName by remember { mutableStateOf(existing?.name ?: "note.txt") }

    // Load existing content off the main thread.
    LaunchedEffect(relPath) {
        if (!isNew) {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                vm.readTextSafe(relPath)
            }.orEmpty()
            content = text
        }
        loaded = true
    }

    // undo / redo stacks (byte-capped so huge files can't balloon memory)
    var undoStack by remember { mutableStateOf(listOf<String>()) }
    var redoStack by remember { mutableStateOf(listOf<String>()) }
    fun pushUndo(old: String) {
        undoStack = (undoStack + old).takeLast(100)
        while (undoStack.size > 1 && undoStack.sumOf { it.length } > 2_000_000) {
            undoStack = undoStack.drop(1)
        }
        redoStack = emptyList()
    }
    fun onChange(new: String) {
        pushUndo(content)
        content = new
        dirty = true
    }
    fun undo() {
        val last = undoStack.lastOrNull() ?: return
        redoStack = redoStack + content
        undoStack = undoStack.dropLast(1)
        content = last
        dirty = true
    }
    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        undoStack = undoStack + content
        redoStack = redoStack.dropLast(1)
        content = next
        dirty = true
    }

    var showFind by remember { mutableStateOf(false) }
    var showSaveAs by remember { mutableStateOf(false) }
    var exitDirtyConfirm by remember { mutableStateOf(false) }

    val editorStats = remember(content) {
        "${content.lines().size} lines · ${content.split(' ', '\n').count { it.isNotBlank() }} words"
    }

    fun saveTo(path: String, name: String) {
        vm.writeTextSafe(path, content)
        dirty = false
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(currentName + if (dirty) " •" else "", maxLines = 1)
                    Text(
                        editorStats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { if (dirty) exitDirtyConfirm = true else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) { Icon(Icons.Default.Undo, "Undo") }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) { Icon(Icons.Default.Redo, "Redo") }
                IconButton(onClick = { showFind = true }) { Icon(Icons.Default.Search, "Find & replace") }
                IconButton(onClick = {
                    if (!isNew && existing != null) saveTo(relPath, currentName)
                    else showSaveAs = true
                }) { Icon(Icons.Default.Save, "Save") }
            },
        )

        val scroll = rememberScrollState()
        Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
            if (lineNumbersOn && remember(content) { content.lines().size <= MAX_GUTTER_LINES }) {
                val lineCount = remember(content) { maxOf(content.lines().size, 1) }
                Column(
                    Modifier
                        .width(44.dp)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    repeat(lineCount) { i ->
                        Text(
                            "${i + 1}",
                            fontSize = settings.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
            SelectionContainer(Modifier.weight(1f)) {
                val hScroll = rememberScrollState()
                OutlinedTextField(
                    value = content,
                    onValueChange = ::onChange,
                    textStyle = TextStyle(
                        fontSize = settings.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (settings * 1.4f).sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .let { m -> if (!wordWrap) m.horizontalScroll(hScroll) else m },
                    placeholder = { Text("Mulai mengetik…") },
                    enabled = loaded,
                )
            }
        }
    }

    if (showFind) {
        FindReplaceDialog(
            initialText = content,
            onDismiss = { showFind = false },
            onApply = { newContent -> onChange(newContent); showFind = false },
        )
    }

    if (showSaveAs) {
        com.zaaaam.kalku.ui.TextEntryDialog(
            title = "Save As",
            label = "File name",
            initial = currentName,
            confirmText = "Save",
            onDismiss = { showSaveAs = false },
            onConfirm = { name ->
                vm.saveTextAs(parent, name, content) { _ ->
                    currentName = name.substringAfterLast('/')
                    dirty = false
                }
                showSaveAs = false
            },
        )
    }

    if (exitDirtyConfirm) {
        com.zaaaam.kalku.ui.ConfirmDialog(
            title = "Perubahan belum disimpan",
            message = "Keluar tanpa menyimpan?",
            confirmText = "Discard",
            destructive = true,
            onDismiss = { exitDirtyConfirm = false },
            onConfirm = { exitDirtyConfirm = false; onBack() },
        )
    }
}

@Composable
private fun FindReplaceDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var find by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    var matchCount by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find & Replace") },
        text = {
            Column(spacedBy8()) {
                OutlinedTextField(value = find, onValueChange = {
                    find = it
                    matchCount = if (find.isEmpty()) 0 else countOccurrences(initialText, find)
                }, label = { Text("Find ($matchCount)") }, singleLine = true)
                OutlinedTextField(value = replaceWith, onValueChange = { replaceWith = it }, label = { Text("Replace with") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = find.isNotEmpty(), onClick = {
                onApply(initialText.replace(find, replaceWith))
            }) { Text("Replace all") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun spacedBy8(): androidx.compose.foundation.layout.Arrangement.Vertical =
    androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)

private fun countOccurrences(text: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    var idx = 0
    var n = 0
    while (true) {
        idx = text.indexOf(needle, idx)
        if (idx < 0) break
        n++; idx += needle.length
    }
    return n
}

private const val MAX_GUTTER_LINES = 1500
