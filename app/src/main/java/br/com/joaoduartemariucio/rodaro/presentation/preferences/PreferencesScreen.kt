package br.com.joaoduartemariucio.rodaro.presentation.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.CurrencyOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.FormState
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionDivider
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionFooter
import kotlinx.coroutines.launch
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    section: PreferencesSection,
    onBack: () -> Unit,
) {
    val dependencies = LocalDependencies.current
    val viewModel =
        viewModel {
            PreferencesViewModel(
                repository = dependencies.preferencesRepository,
                applyLanguage = dependencies.localeLanguageApplier::apply,
                syncReminders = { dependencies.reminderSync.sync() },
                applyPrivacy = dependencies.applyPrivacy,
                analytics = dependencies.analytics,
            )
        }
    val coroutines = rememberCoroutineScope()

    LaunchedEffect(viewModel) { viewModel.load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(section.titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (section == PreferencesSection.NOTIFICATIONS) {
                item {
                    SectionCard {
                        SwitchRow(
                            title = stringResource(R.string.pref_push_title),
                            description = stringResource(R.string.pref_push_description),
                            checked = viewModel.preferences.pushEnabled,
                            onCheckedChange = { enabled -> coroutines.launch { viewModel.setPushEnabled(enabled) } },
                        )
                        SectionDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_reminders_title),
                            description = stringResource(R.string.pref_reminders_description),
                            checked = viewModel.preferences.remindersEnabled,
                            onCheckedChange = { enabled -> coroutines.launch { viewModel.setRemindersEnabled(enabled) } },
                        )
                    }
                }
            }

            if (section == PreferencesSection.DISPLAY) {
                item {
                    SectionCard {
                        Text(
                            stringResource(R.string.pref_currency_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        OptionRow(
                            title = stringResource(R.string.pref_currency_system),
                            selected = viewModel.preferences.currency == null,
                            onClick = { coroutines.launch { viewModel.setCurrency(null) } },
                        )
                        CurrencyOption.entries.forEach { option ->
                            SectionDivider()
                            OptionRow(
                                title = currencyLabel(option),
                                selected = viewModel.preferences.currency == option,
                                onClick = { coroutines.launch { viewModel.setCurrency(option) } },
                            )
                        }
                    }
                    SectionFooter {
                        Text(
                            stringResource(R.string.pref_currency_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SectionCard {
                        Text(
                            stringResource(R.string.pref_units_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        OptionRow(
                            title = stringResource(R.string.pref_units_metric),
                            selected = viewModel.preferences.unitSystem == UnitSystem.METRIC,
                            onClick = { coroutines.launch { viewModel.setUnitSystem(UnitSystem.METRIC) } },
                        )
                        SectionDivider()
                        OptionRow(
                            title = stringResource(R.string.pref_units_imperial),
                            selected = viewModel.preferences.unitSystem == UnitSystem.IMPERIAL,
                            onClick = { coroutines.launch { viewModel.setUnitSystem(UnitSystem.IMPERIAL) } },
                        )
                    }
                }
            }

            if (section == PreferencesSection.PRIVACY) {
                item {
                    SectionCard {
                        SwitchRow(
                            title = stringResource(R.string.pref_usage_statistics_title),
                            description = stringResource(R.string.pref_usage_statistics_description),
                            checked = viewModel.preferences.usageStatisticsEnabled,
                            onCheckedChange = { enabled -> coroutines.launch { viewModel.setUsageStatisticsEnabled(enabled) } },
                        )
                        SectionDivider()
                        SwitchRow(
                            title = stringResource(R.string.pref_personalized_ads_title),
                            description = stringResource(R.string.pref_personalized_ads_description),
                            checked = viewModel.preferences.personalizedAdsEnabled,
                            onCheckedChange = { enabled -> coroutines.launch { viewModel.setPersonalizedAdsEnabled(enabled) } },
                        )
                    }
                }
            }

            if (section == PreferencesSection.LANGUAGE) {
                item {
                    SectionCard {
                        LanguageOption.entries.forEach { option ->
                            if (option != LanguageOption.SYSTEM) SectionDivider()
                            OptionRow(
                                title = languageLabel(option),
                                selected = viewModel.preferences.language == option,
                                onClick = { coroutines.launch { viewModel.setLanguage(option) } },
                            )
                        }
                    }
                }
            }

            if (viewModel.state is FormState.Failed) {
                item {
                    SectionFooter {
                        FailureMessage((viewModel.state as FormState.Failed).error)
                    }
                }
            }
        }
    }
}

@Composable
private fun currencyLabel(option: CurrencyOption): String {
    val symbol =
        runCatching { Currency.getInstance(option.code).symbol }
            .getOrNull()
    return if (symbol.isNullOrBlank()) option.code else "${option.code} — $symbol"
}

@Composable
private fun languageLabel(option: LanguageOption): String =
    stringResource(
        when (option) {
            LanguageOption.SYSTEM -> R.string.pref_language_system
            LanguageOption.PORTUGUESE -> R.string.pref_language_portuguese
            LanguageOption.ENGLISH -> R.string.pref_language_english
        },
    )

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
