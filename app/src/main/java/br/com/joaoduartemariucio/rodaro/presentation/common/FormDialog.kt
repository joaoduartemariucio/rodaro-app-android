package br.com.joaoduartemariucio.rodaro.presentation.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.presentation.common.ads.AdBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDialog(
    title: String,
    state: FormState,
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    saveLabel: String = stringResource(R.string.save),
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val isDark = isSystemInDarkTheme()
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
                    actions = {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = onSave, enabled = canSave) {
                                Text(saveLabel, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            },
        ) { padding ->
            CompositionLocalProvider(LocalKeyboardTapTracker provides remember { KeyboardTapTracker() }) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().imePadding().dismissesKeyboard(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
                    content = {
                        content()
                        item { AdBanner(Modifier.padding(top = 16.dp)) }
                    },
                )
            }
        }
    }
}
