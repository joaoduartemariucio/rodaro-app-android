package br.com.joaoduartemariucio.rodaro.presentation.profile.navigation

import androidx.navigation3.runtime.EntryProviderScope
import br.com.joaoduartemariucio.rodaro.presentation.auth.AuthScreen
import br.com.joaoduartemariucio.rodaro.presentation.backup.BackupScreen
import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Navigator
import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Route
import br.com.joaoduartemariucio.rodaro.presentation.preferences.PreferencesScreen
import br.com.joaoduartemariucio.rodaro.presentation.profile.ProfileScreen

fun EntryProviderScope<Route>.profileEntries(navigator: Navigator) {
    entry<ProfileRoute.Profile> {
        ProfileScreen(
            onSignIn = { navigator.open(ProfileRoute.Auth) },
            onPreferences = { navigator.open(ProfileRoute.Preferences) },
            onBackup = { navigator.open(ProfileRoute.Backup) },
        )
    }
    entry<ProfileRoute.Auth> {
        AuthScreen(
            onBack = navigator::back,
            onSignedIn = navigator::back,
        )
    }
    entry<ProfileRoute.Preferences> { PreferencesScreen(onBack = navigator::back) }
    entry<ProfileRoute.Backup> { BackupScreen(onBack = navigator::back) }
}
