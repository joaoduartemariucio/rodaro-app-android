package br.com.joaoduartemariucio.rodaro.presentation.profile.navigation

import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Route

sealed interface ProfileRoute : Route {
    data object Profile : ProfileRoute

    data object Auth : ProfileRoute

    data object Preferences : ProfileRoute

    data object Backup : ProfileRoute
}
