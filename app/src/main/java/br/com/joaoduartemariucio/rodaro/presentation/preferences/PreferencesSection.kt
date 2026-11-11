package br.com.joaoduartemariucio.rodaro.presentation.preferences

import androidx.annotation.StringRes
import br.com.joaoduartemariucio.rodaro.R

enum class PreferencesSection(
    @StringRes val titleRes: Int,
) {
    NOTIFICATIONS(R.string.pref_section_notifications),
    DISPLAY(R.string.profile_units_currency),
    PRIVACY(R.string.pref_section_privacy),
    LANGUAGE(R.string.pref_section_language),
}
