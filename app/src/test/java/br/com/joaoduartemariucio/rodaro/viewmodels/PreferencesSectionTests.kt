package br.com.joaoduartemariucio.rodaro.viewmodels

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.presentation.common.navigation.Navigator
import br.com.joaoduartemariucio.rodaro.presentation.preferences.PreferencesSection
import br.com.joaoduartemariucio.rodaro.presentation.profile.navigation.ProfileRoute
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PreferencesSectionTests {
    @Test
    fun `each section is titled after the profile row that opens it`() {
        assertEquals(R.string.pref_section_notifications, PreferencesSection.NOTIFICATIONS.titleRes)
        assertEquals(R.string.profile_units_currency, PreferencesSection.DISPLAY.titleRes)
        assertEquals(R.string.pref_section_privacy, PreferencesSection.PRIVACY.titleRes)
        assertEquals(R.string.pref_section_language, PreferencesSection.LANGUAGE.titleRes)
    }

    @Test
    fun `preferences routes for different sections are different destinations`() {
        val navigator = Navigator(listOf(ProfileRoute.Profile))

        navigator.open(ProfileRoute.Preferences(PreferencesSection.LANGUAGE))

        assertEquals(PreferencesSection.LANGUAGE, (navigator.backStack.last() as ProfileRoute.Preferences).section)
        assertNotEquals(
            ProfileRoute.Preferences(PreferencesSection.LANGUAGE),
            ProfileRoute.Preferences(PreferencesSection.PRIVACY),
        )
    }
}
