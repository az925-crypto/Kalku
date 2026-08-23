package com.zaaaam.kalku.nav

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zaaaam.kalku.MainViewModel
import com.zaaaam.kalku.calc.CalcViewModel
import com.zaaaam.kalku.calc.CalculatorScreen
import com.zaaaam.kalku.core.CategoryDetector
import com.zaaaam.kalku.data.FileEntity
import com.zaaaam.kalku.security.LockController
import com.zaaaam.kalku.settings.SettingsScreen
import com.zaaaam.kalku.vault.BrowserScreen
import com.zaaaam.kalku.vault.DashboardScreen
import com.zaaaam.kalku.vault.FavoritesScreen
import com.zaaaam.kalku.vault.GalleryScreen
import com.zaaaam.kalku.vault.SearchScreen
import com.zaaaam.kalku.vault.TrashScreen
import com.zaaaam.kalku.vault.VaultViewModel
import com.zaaaam.kalku.viewer.ArchiveViewerScreen
import com.zaaaam.kalku.viewer.AudioPlayerScreen
import com.zaaaam.kalku.viewer.ImageViewerScreen
import com.zaaaam.kalku.viewer.PdfViewerScreen
import com.zaaaam.kalku.viewer.TextEditorScreen
import com.zaaaam.kalku.viewer.VideoPlayerScreen
import kotlinx.coroutines.launch

private object Routes {
    const val CALC = "calc"
    const val VAULT = "vault"
    const val BROWSER = "browser/{folder}"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val TRASH = "trash"
    const val GALLERY = "gallery"
    const val SETTINGS = "settings"
    const val IMAGE = "image/{id}"
    const val VIDEO = "video/{id}"
    const val AUDIO = "audio/{id}"
    const val PDF = "pdf/{id}"
    const val ARCHIVE = "archive/{id}"
    const val EDITOR = "editor?path={path}&parent={parent}"

    fun browser(folder: String) = "browser/${Uri.encode(folder)}"
    fun image(id: Long) = "image/$id"
    fun video(id: Long) = "video/$id"
    fun audio(id: Long) = "audio/$id"
    fun pdf(id: Long) = "pdf/$id"
    fun archive(id: Long) = "archive/$id"
    fun editor(path: String, parent: String) =
        "editor?path=${Uri.encode(path)}&parent=${Uri.encode(parent)}"
}

/** Cross-composable flag so first-entry PIN setup survives navigation. */
internal object PinSetupPending {
    val value = mutableStateOf(false)
}

@Composable
fun KalkuNav(mainVm: MainViewModel) {
    val nav = rememberNavController()
    val calcVm: CalcViewModel = viewModel()
    val vaultVm: VaultViewModel = viewModel()

    // Share-intent drain once unlocked & storage reachable.
    val pendingShares by mainVm.pendingShareUris.collectAsState()
    LaunchedEffect(pendingShares, mainVm.lock.unlocked) {
        if (pendingShares.isNotEmpty() && mainVm.lock.unlocked) {
            mainVm.drainPendingShares()
        }
    }

    NavHost(navController = nav, startDestination = Routes.CALC) {
        composable(Routes.CALC) {
            CalculatorScreen(
                vm = calcVm,
                hapticsEnabled = true,
                onUnlocked = { setupNeeded ->
                    mainVm.lock.unlock()
                    nav.navigate(Routes.VAULT)
                    if (setupNeeded) PinSetupPending.value = true
                },
            )
        }

        composable(Routes.VAULT) {
            Guarded(mainVm.lock, nav) {
                var showPinSetup by remember { mutableStateOf(PinSetupPending.value) }
                DashboardScreen(
                    vm = vaultVm,
                    onOpenFolder = { folder -> nav.navigate(Routes.browser(folder)) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onFavorites = { nav.navigate(Routes.FAVORITES) },
                    onTrash = { nav.navigate(Routes.TRASH) },
                    onGallery = { nav.navigate(Routes.GALLERY) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onLock = {
                        mainVm.lock.lock()
                        nav.popBackStack(Routes.CALC, inclusive = false)
                    },
                    onOpenFile = { entry -> openEntry(nav, vaultVm, entry) },
                )
                if (showPinSetup) {
                    PinSetupDialog(
                        lock = mainVm.lock,
                        onDone = { showPinSetup = false; PinSetupPending.value = false },
                    )
                }
            }
        }

        composable(Routes.BROWSER) { backStack ->
            val folder = backStack.arguments?.getString("folder").orEmpty().let(Uri::decode)
            Guarded(mainVm.lock, nav) {
                BrowserScreen(
                    vm = vaultVm,
                    folder = folder,
                    onBack = { nav.popBackStack() },
                    onOpenFolder = { f -> nav.navigate(Routes.browser(f)) },
                    onOpenFile = { entry -> openEntry(nav, vaultVm, entry) },
                )
            }
        }

        composable(Routes.SEARCH) {
            Guarded(mainVm.lock, nav) {
                SearchScreen(vm = vaultVm, onBack = { nav.popBackStack() }, onOpenFile = { e -> openEntry(nav, vaultVm, e) })
            }
        }
        composable(Routes.FAVORITES) {
            Guarded(mainVm.lock, nav) {
                FavoritesScreen(vm = vaultVm, onBack = { nav.popBackStack() }, onOpenFile = { e -> openEntry(nav, vaultVm, e) })
            }
        }
        composable(Routes.TRASH) {
            Guarded(mainVm.lock, nav) {
                TrashScreen(vm = vaultVm, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.GALLERY) {
            Guarded(mainVm.lock, nav) {
                GalleryScreen(
                    vm = vaultVm,
                    onBack = { nav.popBackStack() },
                    onOpenImage = { index ->
                        vaultVm.images.value.getOrNull(index)?.let { nav.navigate(Routes.image(it.id)) }
                    },
                )
            }
        }
        composable(Routes.SETTINGS) {
            Guarded(mainVm.lock, nav) {
                SettingsScreen(vm = vaultVm, lock = mainVm.lock, onBack = { nav.popBackStack() })
            }
        }

        composable(Routes.IMAGE) { backStack ->
            Guarded(mainVm.lock, nav) {
                ImageViewerScreen(vm = vaultVm, id = backStack.arguments?.getString("id")?.toLongOrNull() ?: -1L, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.VIDEO) { backStack ->
            Guarded(mainVm.lock, nav) {
                VideoPlayerScreen(vm = vaultVm, id = backStack.arguments?.getString("id")?.toLongOrNull() ?: -1L, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.AUDIO) { backStack ->
            Guarded(mainVm.lock, nav) {
                AudioPlayerScreen(vm = vaultVm, id = backStack.arguments?.getString("id")?.toLongOrNull() ?: -1L, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.PDF) { backStack ->
            Guarded(mainVm.lock, nav) {
                PdfViewerScreen(vm = vaultVm, id = backStack.arguments?.getString("id")?.toLongOrNull() ?: -1L, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.ARCHIVE) { backStack ->
            Guarded(mainVm.lock, nav) {
                ArchiveViewerScreen(vm = vaultVm, id = backStack.arguments?.getString("id")?.toLongOrNull() ?: -1L, onBack = { nav.popBackStack() })
            }
        }
        composable(Routes.EDITOR) { backStack ->
            val path = Uri.decode(backStack.arguments?.getString("path").orEmpty())
            val parent = Uri.decode(backStack.arguments?.getString("parent") ?: "")
            Guarded(mainVm.lock, nav) {
                TextEditorScreen(vm = vaultVm, relPath = path, parent = parent, onBack = { nav.popBackStack() })
            }
        }
    }
}

/**
 * Gate for every protected destination. LockController.unlocked is snapshot
 * state, so manual lock, auto-lock expiry or process restore recomposes here
 * and pops everything back to the calculator.
 */
@Composable
private fun Guarded(lock: LockController, nav: NavHostController, content: @Composable () -> Unit) {
    val unlocked = lock.unlocked
    LaunchedEffect(unlocked) {
        if (!unlocked) nav.popBackStack(Routes.CALC, inclusive = false)
    }
    if (unlocked) content()
}

private fun openEntry(nav: NavHostController, vm: VaultViewModel, entry: FileEntity) {
    if (entry.isFolder) {
        nav.navigate(Routes.browser(entry.relPath))
        return
    }
    val ext = CategoryDetector.extensionOf(entry.name).lowercase()
    when (entry.category) {
        "IMAGE" -> nav.navigate(Routes.image(entry.id))
        "VIDEO" -> nav.navigate(Routes.video(entry.id))
        "AUDIO" -> nav.navigate(Routes.audio(entry.id))
        "DOCUMENT" -> if (ext == "pdf") nav.navigate(Routes.pdf(entry.id))
                      else nav.navigate(Routes.editor(entry.relPath, entry.parent))
        "CODE" -> nav.navigate(Routes.editor(entry.relPath, entry.parent))
        "ARCHIVE" -> if (ext == "zip") nav.navigate(Routes.archive(entry.id)) else {
            vm.toast.value = "Format archive ini belum bisa dibuka — gunakan Export/Share"
        }
        else -> {
            if (entry.size <= 1_000_000) nav.navigate(Routes.editor(entry.relPath, entry.parent))
            else vm.toast.value = "Preview tidak tersedia untuk file ini"
        }
    }
}

@Composable
private fun PinSetupDialog(lock: LockController, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Set PIN Vault") },
        text = {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "PIN default berhasil dipakai. Amankan vault dengan PIN pribadi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(16) }, label = { Text("PIN baru (4-16 digit)") }, singleLine = true)
                OutlinedTextField(value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(16) }, label = { Text("Ulangi PIN") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length < 4 -> error = "Minimal 4 digit"
                    pin != confirm -> error = "Konfirmasi tidak sama"
                    else -> scope.launch {
                        lock.setPin(pin)
                        onDone()
                    }
                }
            }) { Text("Save") }
        },
    )
}
