package com.zaaaam.kalku.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.zaaaam.kalku.ui.theme.MonoNumbers
import com.zaaaam.kalku.vault.VaultViewModel

/**
 * Text/code editor. [relPath] empty → new file in [parent].
 * Undo/redo via snapshot stack; find & replace; word/line count.
 *
 * HTML parity (.ed):
 *  - gutter 44dp, padding 10-14 vertical, JetBrains Mono 12.5sp / lineHeight 1.75, right border outlineVariant, bg surfaceVariant, cur bg primary 0.07 + text primary
 *  - code padding 14dp, Mono 12.5sp / 1.75, syntax kw/fn/str/cm via colorScheme, curline bg primary 0.06 + left 2dp primary
 *  - ed-status 10.5sp Mono chips 999px + stats, borderTop outlineVariant, bottom 24dp for gesture
 *  - toolbar 48dp with Simpan primary + Cari/Salin tonal
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    vm: VaultViewModel,
    relPath: String,
    parent: String,
    onBack: () -> Unit,
) {
    // Tracks the actual save target: starts as the route arg and switches after
    // a Save As, so subsequent saves update the same file instead of creating
    // "name (2)" duplicates.
    var savedPath by remember { mutableStateOf(relPath) }
    val isNew = savedPath.isEmpty()
    val existing = if (!isNew) vm.byPathThenOpen(savedPath) else null
    val settings by vm.settings.editorFontSize.collectAsState(initial = 14)
    val wordWrap by vm.settings.editorWordWrap.collectAsState(initial = true)
    val lineNumbersOn by vm.settings.editorLineNumbers.collectAsState(initial = true)

    var content by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(isNew) }
    // Distinguishes "loaded as empty" from "load failed": a failed load must
    // never let one Save click overwrite a good file with an empty string.
    var loadFailed by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var currentName by remember { mutableStateOf(existing?.name ?: "note.txt") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(savedPath) {
        if (!isNew) {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                vm.readTextSafe(savedPath)
            }
            if (text == null) {
                loadFailed = true
                content = ""
            } else {
                loadFailed = false
                content = text
            }
        }
        loaded = true
    }

    // Header check for the status line, off the main thread.
    var isEncrypted by remember(savedPath) { mutableStateOf(false) }
    LaunchedEffect(savedPath) {
        if (!isNew) {
            isEncrypted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                vm.isPathEncrypted(savedPath)
            }
        }
    }

    // Binary content (NUL byte) means this file was routed here by mistake;
    // saving would write lossy mojibake over the original. Hard-block saves.
    val binaryBlocked = remember(loaded, content) { loaded && !isNew && content.contains('\u0000') }
    val saveBlocked = !loaded || loadFailed || binaryBlocked

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

    val lineCount = remember(content) { maxOf(content.lines().size, 1) }
    val wordCount = remember(content) { content.split(' ', '\n').count { it.isNotBlank() } }
    val editorStats = remember(content) { "$lineCount baris · $wordCount kata" }

    // System back must respect the dirty guard too — without this a swipe
    // discarded changes with no confirmation dialog.
    androidx.activity.compose.BackHandler(enabled = dirty) { exitDirtyConfirm = true }

    /** Persists [content]; dirty flag only clears when the write actually succeeded. */
    fun saveTo(path: String) {
        if (saveBlocked) return
        scope.launch {
            val ok = vm.writeTextSafe(path, content)
            if (ok) dirty = false
        }
    }

    // Toast pipeline: save/conflict errors must be visible inside the editor.
    val toastEvent by vm.toast.collectAsState()
    val snackState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(toastEvent?.id) {
        toastEvent?.let {
            snackState.showSnackbar(it.msg)
            vm.dismissToast()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
        // .ed-bar / .topbar 64dp — HTML: padding 12 14 10 gap2 + border-bottom line2
        TopAppBar(
            title = {
                Column(Modifier.padding(start = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            currentName,
                            maxLines = 1,
                            fontFamily = MonoNumbers,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (dirty) Text(
                            "•",
                            fontFamily = MonoNumbers,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary, // HTML .dirty copper/vermilion
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        when {
                            isNew -> "Baru · belum disimpan"
                            loadFailed -> "Gagal memuat — simpan dinonaktifkan"
                            binaryBlocked -> "File biner — simpan dinonaktifkan"
                            isEncrypted -> "Terenkripsi · AES-GCM"
                            else -> "Plaintext · di device"
                        },
                        style = TextStyle(
                            fontFamily = MonoNumbers,
                            fontSize = 10.5.sp,
                            lineHeight = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        ),
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { if (dirty) exitDirtyConfirm = true else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) { Icon(Icons.Default.Undo, "Undo", tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) { Icon(Icons.Default.Redo, "Redo", tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)) }
                IconButton(onClick = { showFind = true }) { Icon(Icons.Default.Search, "Find", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(
                    onClick = {
                        if (!isNew && existing != null) saveTo(savedPath)
                        else showSaveAs = true
                    },
                    enabled = !saveBlocked,
                ) {
                    Icon(
                        Icons.Default.Save,
                        "Save",
                        tint = if (saveBlocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                               else MaterialTheme.colorScheme.primary, // HTML save brass/vermillion/ember tonal
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .height(64.dp)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )

        // toolbar 48dp — HTML .toolbar 48dp with Simpan primary, Cari/Salin, autosave hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Simpan — primary pill like HTML .tool.primary (ember/copper)
            androidx.compose.material3.Button(
                onClick = {
                    if (!isNew && existing != null) saveTo(savedPath) else showSaveAs = true
                },
                enabled = !saveBlocked,
                shape = RoundedCornerShape(999.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp).width(14.dp).height(14.dp))
                Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.width(14.dp).height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Cari", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.width(14.dp).height(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Salin", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.weight(1f))
        }

        val scroll = rememberScrollState()
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (lineNumbersOn && lineCount <= MAX_GUTTER_LINES) {
                // .gutter 44dp, line-height 1.75, bg surfaceVariant, border-right outlineVariant
                Column(
                    Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    repeat(lineCount) { i ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 10.dp, top = 0.dp, bottom = 0.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                "${i + 1}",
                                fontSize = 11.5.sp, // HTML v4/v5 11.5px /20px ; v1/v2 12.5px
                                lineHeight = (settings * 1.75f).sp, // HTML 1.75
                                fontFamily = MonoNumbers,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                modifier = Modifier.padding(end = 0.dp),
                            )
                        }
                    }
                }
            }
            // .code flex1 padding 14 14, Mono 12.5sp line 1.75
            val hScroll = rememberScrollState()
            val primary = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SelectionContainer(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = ::onChange,
                        textStyle = TextStyle(
                            fontSize = settings.sp,
                            fontFamily = MonoNumbers,
                            lineHeight = (settings * 1.75f).sp, // HTML .code line-height 1.75
                            color = MaterialTheme.colorScheme.onSurface, // HTML #C9C2B4 / #2B2B2A
                            letterSpacing = 0.sp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp, vertical = 0.dp)
                            .let { m -> if (!wordWrap) m.horizontalScroll(hScroll) else m },
                        placeholder = {
                            Text(
                                "Mulai mengetik…",
                                fontFamily = MonoNumbers,
                                fontSize = settings.sp,
                                lineHeight = (settings * 1.75f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        },
                        enabled = loaded,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                            errorBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            cursorColor = primary,
                        ),
                    )
                }
            }
        }

        // .ed-status / .status-chips — flex gap8 padding 10 12 / 8 16 24, border-top line, Mono 10.5
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 22.dp), // bottom 22 to mimic home gesture padding
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(text = "MONO $settings", selected = true)
            StatusChip(text = if (wordWrap) "WRAP ON" else "WRAP", selected = wordWrap)
            StatusChip(text = "#", selected = false)
            Spacer(Modifier.weight(1f))
            Text(
                editorStats,
                fontFamily = MonoNumbers,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
    }

        androidx.compose.material3.SnackbarHost(
            hostState = snackState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
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
                vm.saveTextAs(parent, name, content) { entity ->
                    // Adopt the new path so the next save updates this file
                    // instead of prompting Save As again.
                    savedPath = entity.relPath
                    currentName = entity.name
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
private fun StatusChip(text: String, selected: Boolean) {
    // .chip2 — border 1px line radius999 padding 3-4 10, selected brass/teal/sage vs muted
    Surface(
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Text(
            text,
            fontFamily = MonoNumbers,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.2.sp,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// Syntax helpers — map HTML .kw/.fn/.str/.cm to MaterialTheme roles per pack.
// Keep pure UI; no logic change — caller may use buildSyntaxAnnotatedString(content) if switching to BasicTextField with AnnotatedString.
private fun buildSyntaxAnnotatedString(text: String, cs: androidx.compose.material3.ColorScheme): AnnotatedString {
    // kw: primary/tertiary, fn: secondary/tertiaryContainer, str: secondary/primary, cm: outline italic
    val keywords = setOf("import", "void", "class", "extends", "fun", "val", "var", "return", "override", "const", "if", "else", "package")
    return buildAnnotatedString {
        // Very lightweight highlight: comment, string, keyword, function — enough to demonstrate 1:1 palette
        val lines = text.split("\n")
        lines.forEachIndexed { idx, line ->
            when {
                line.trimStart().startsWith("//") -> withStyle(SpanStyle(color = cs.onSurfaceVariant.copy(alpha = 0.6f), fontStyle = FontStyle.Italic)) { append(line) }
                line.contains("\"") || line.contains("'") || line.contains("“") -> {
                    // Split by string literals
                    val regex = Regex("""(["'`][^"'`]*["'`])""")
                    var last = 0
                    regex.findAll(line).forEach { m ->
                        val before = line.substring(last, m.range.first)
                        appendWithKw(before, keywords, cs)
                        withStyle(SpanStyle(color = cs.secondary)) { append(m.value) }
                        last = m.range.last + 1
                    }
                    if (last < line.length) appendWithKw(line.substring(last), keywords, cs)
                }
                else -> appendWithKw(line, keywords, cs)
            }
            if (idx != lines.lastIndex) append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendWithKw(segment: String, keywords: Set<String>, cs: androidx.compose.material3.ColorScheme) {
    val fnRegex = Regex("""\b(\w+)(?=\s*\()""")
    val fnSpans = fnRegex.findAll(segment).toList()
    val tokens = segment.split(Regex("""(\W+)"""))
    tokens.forEach { tok ->
        when {
            keywords.contains(tok) -> withStyle(SpanStyle(color = cs.tertiary, fontWeight = FontWeight.SemiBold)) { append(tok) }
            fnSpans.any { it.value == tok } -> withStyle(SpanStyle(color = cs.secondary)) { append(tok) }
            else -> append(tok)
        }
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
        title = { Text("Find & Replace", fontFamily = MonoNumbers) },
        text = {
            Column(verticalArrangement = spacedBy8()) {
                OutlinedTextField(value = find, onValueChange = {
                    find = it
                    matchCount = if (find.isEmpty()) 0 else countOccurrences(initialText, find)
                }, label = { Text("Find ($matchCount)", fontFamily = MonoNumbers) }, singleLine = true)
                OutlinedTextField(value = replaceWith, onValueChange = { replaceWith = it }, label = { Text("Replace with", fontFamily = MonoNumbers) }, singleLine = true)
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

private fun spacedBy8(): Arrangement.Vertical =
    Arrangement.spacedBy(8.dp)

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
