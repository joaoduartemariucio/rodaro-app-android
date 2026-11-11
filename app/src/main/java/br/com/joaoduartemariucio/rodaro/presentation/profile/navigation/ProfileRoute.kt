package br.com.joaoduartemariucio.rodaro.presentation.profile.navigation

import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Route
import br.com.joaoduartemariucio.rodaro.presentation.preferences.PreferencesSection

sealed interface ProfileRoute : Route {
    data object Profile : ProfileRoute

    data object Auth : ProfileRoute

    data class Preferences(
        val section: PreferencesSection,
    ) : ProfileRoute

    data object Backup : ProfileRoute
}
