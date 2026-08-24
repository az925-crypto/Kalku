package com.zaaaam.kalku

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.zaaaam.kalku.nav.KalkuNav
import com.zaaaam.kalku.ui.theme.KalkuTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // Apply before the first frame so a Recents snapshot taken immediately
        // after unlocking can't capture vault content (calculator stays
        // screenshotable on purpose — camouflage).
        applySecureFlag(vm.lock.unlocked)
        lifecycleScope.launch {
            androidx.compose.runtime.snapshotFlow { vm.lock.unlocked }.collect(::applySecureFlag)
        }
        setContent {
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            val pack by vm.themePack.collectAsStateWithLifecycle()
            KalkuTheme(theme, pack) {
                KalkuNav(vm)
            }
        }
    }

    private fun applySecureFlag(unlocked: Boolean) {
        if (unlocked) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uris = MainViewModel.extractSharedUris(intent)
        if (uris.isNotEmpty()) vm.acceptShareIntent(uris)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            vm.lock.relockIfExpired()
            if (vm.pendingShareUris.value.isNotEmpty() && vm.lock.unlocked) {
                vm.drainPendingShares()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.lock.onBackground()
    }
}
