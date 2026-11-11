package br.com.joaoduartemariucio.rodaro.presentation.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.app.LocalDependencies
import br.com.joaoduartemariucio.rodaro.domain.common.services.Account
import br.com.joaoduartemariucio.rodaro.domain.dues.calculations.DueSchedule
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueItem
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.LanguageOption
import br.com.joaoduartemariucio.rodaro.domain.preferences.entities.UnitSystem
import br.com.joaoduartemariucio.rodaro.presentation.common.Chip
import br.com.joaoduartemariucio.rodaro.presentation.common.FailureMessage
import br.com.joaoduartemariucio.rodaro.presentation.common.MetricTileCard
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.PrivacyLinks
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionCard
import br.com.joaoduartemariucio.rodaro.presentation.common.SectionHeader
import br.com.joaoduartemariucio.rodaro.presentation.common.StoreLinks
import br.com.joaoduartemariucio.rodaro.presentation.common.formattedCurrency
import br.com.joaoduartemariucio.rodaro.presentation.dues.form.DueItemFormDialog
import br.com.joaoduartemariucio.rodaro.presentation.dues.form.DueItemFormViewModel
import br.com.joaoduartemariucio.rodaro.presentation.dues.payment.DuePaymentDialog
import br.com.joaoduartemariucio.rodaro.presentation.preferences.PreferencesSection
import br.com.joaoduartemariucio.rodaro.presentation.vehicles.detail.components.DueItemRow
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignIn: () -> Unit,
    onPreferences: (PreferencesSection) -> Unit,
    onBackup: () -> Unit,
) {
    val context = LocalContext.current
    val dependencies = LocalDependencies.current
    val viewModel =
        viewModel {
            ProfileViewModel(
                accountService = dependencies.accountService,
                vehicleRepository = dependencies.vehicleRepository,
                fuelEntryRepository = dependencies.fuelEntryRepository,
                serviceRepository = dependencies.serviceRepository,
                expenseRepository = dependencies.expenseRepository,
                dueItemRepository = dependencies.dueItemRepository,
                preferencesRepository = dependencies.preferencesRepository,
                clearLocalData = dependencies.clearLocalData,
                analytics = dependencies.analytics,
                reminders = dependencies.reminderSync,
            )
        }
    val coroutines = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val version =
        remember(context) {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
        }
    val storeLink = remember(context) { StoreLinks.listing(context.packageName) }
    val shareMessage = stringResource(R.string.profile_share_message, storeLink)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmingDelete by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var driverSheet by remember { mutableStateOf<DriverSheet?>(null) }

    LaunchedEffect(viewModel) { viewModel.load() }

    val driverChanged: () -> Unit = { coroutines.launch { viewModel.driverItemsChanged() } }
    when (val sheet = driverSheet) {
        DriverSheet.New -> {
            DueItemFormDialog(DueItemFormViewModel.Mode.Create(vehicle = null, kind = DueKind.CNH), driverChanged) { driverSheet = null }
        }

        is DriverSheet.Edit -> {
            DueItemFormDialog(DueItemFormViewModel.Mode.Edit(sheet.item, vehicle = null), driverChanged) { driverSheet = null }
        }

        is DriverSheet.Pay -> {
            DuePaymentDialog(sheet.item, driverChanged) { driverSheet = null }
        }

        null -> {
            Unit
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.profile)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            IdentityCard(viewModel.account, viewModel.initials, onSignIn)

            viewModel.summary?.let { summary -> GarageSummary(summary) }

            SectionTitle(stringResource(R.string.profile_driver))
            SectionCard(contentPadding = RowsPadding) {
                for (item in viewModel.driverItems) {
                    Box(Modifier.clickable { driverSheet = DriverSheet.Edit(item) }.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        DueItemRow(
                            item = item,
                            level =
                                DueSchedule.level(
                                    item,
                                    dependencies.dateProvider.now
                                        .atZone(dependencies.dateProvider.zone)
                                        .toLocalDate(),
                                ),
                            zone = dependencies.dateProvider.zone,
                            onPay = if (item.isPaid) null else ({ driverSheet = DriverSheet.Pay(item) }),
                        )
                    }
                    RowDivider()
                }
                ProfileRow(
                    icon = Icons.Filled.Badge,
                    tint = Palette.blue,
                    title = stringResource(R.string.profile_cnh_add),
                    value = stringResource(R.string.profile_cnh_caption),
                    onClick = { driverSheet = DriverSheet.New },
                )
            }

            SectionTitle(stringResource(R.string.profile_settings))
            SectionCard(contentPadding = RowsPadding) {
                ProfileRow(
                    icon = Icons.Filled.Notifications,
                    tint = Palette.red,
                    title = stringResource(R.string.pref_section_notifications),
                    value = notificationsLabel(viewModel.notifications),
                    onClick = { onPreferences(PreferencesSection.NOTIFICATIONS) },
                )
                RowDivider()
                ProfileRow(
                    icon = Icons.Filled.Straighten,
                    tint = Palette.blue,
                    title = stringResource(R.string.profile_units_currency),
                    value = unitsAndCurrencyLabel(viewModel),
                    onClick = { onPreferences(PreferencesSection.DISPLAY) },
                )
                RowDivider()
                ProfileRow(
                    icon = Icons.Filled.Language,
                    tint = Palette.service,
                    title = stringResource(R.string.pref_section_language),
                    value = languageLabel(viewModel.preferences.language),
                    onClick = { onPreferences(PreferencesSection.LANGUAGE) },
                )
                RowDivider()
                ProfileRow(
                    icon = Icons.Filled.PrivacyTip,
                    tint = Palette.green,
                    title = stringResource(R.string.pref_section_privacy),
                    value = stringResource(R.string.profile_privacy_summary),
                    onClick = { onPreferences(PreferencesSection.PRIVACY) },
                )
            }

            SectionTitle(stringResource(R.string.profile_about))
            SectionCard(contentPadding = RowsPadding) {
                ProfileRow(
                    icon = Icons.Filled.Star,
                    tint = Palette.orange,
                    title = stringResource(R.string.profile_rate),
                    trailing = RowTrailing.EXTERNAL,
                    onClick = { uriHandler.openUri(storeLink) },
                )
                RowDivider()
                ProfileRow(
                    icon = Icons.Filled.Share,
                    tint = Palette.fuel,
                    title = stringResource(R.string.profile_share),
                    trailing = RowTrailing.NONE,
                    onClick = {
                        val send =
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, shareMessage)
                        context.startActivity(Intent.createChooser(send, null))
                    },
                )
                RowDivider()
                ProfileRow(
                    icon = Icons.Filled.Description,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = stringResource(R.string.privacy_policy),
                    trailing = RowTrailing.EXTERNAL,
                    onClick = { uriHandler.openUri(PrivacyLinks.POLICY) },
                )
            }

            SectionTitle(stringResource(R.string.profile_data))
            SectionCard(contentPadding = RowsPadding) {
                ProfileRow(
                    icon = Icons.Outlined.Backup,
                    tint = Palette.blue,
                    title = stringResource(R.string.profile_backup),
                    value = stringResource(R.string.profile_backup_caption),
                    onClick = onBackup,
                )
                RowDivider()
                if (viewModel.account != null) {
                    ProfileRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.sign_out),
                        value = stringResource(R.string.profile_sign_out_caption),
                        trailing = RowTrailing.NONE,
                        onClick = { coroutines.launch { viewModel.signOut() } },
                    )
                    RowDivider()
                }
                ProfileRow(
                    icon = Icons.Filled.DeleteForever,
                    tint = Palette.red,
                    title = stringResource(if (viewModel.account == null) R.string.delete_local_data else R.string.delete_account_data),
                    titleColor = Palette.red,
                    value = stringResource(R.string.profile_delete_caption),
                    trailing = if (viewModel.canDelete) RowTrailing.NONE else RowTrailing.PROGRESS,
                    enabled = viewModel.canDelete,
                    onClick = { confirmingDelete = true },
                )
            }

            viewModel.error?.let { error ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) { FailureMessage(error) }
            }

            AppFooter(version)
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = {
                Text(
                    stringResource(
                        if (viewModel.account == null) R.string.delete_local_confirm_title else R.string.delete_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (viewModel.account == null) R.string.delete_local_confirm_message else R.string.delete_confirm_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        coroutines.launch { viewModel.delete() }
                    },
                ) {
                    Text(stringResource(R.string.delete_confirm_action), color = Palette.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    when (viewModel.deleteState) {
        ProfileViewModel.DeleteState.NeedsPassword -> {
            AlertDialog(
                onDismissRequest = {
                    password = ""
                    viewModel.cancelDelete()
                },
                title = { Text(stringResource(R.string.delete_needs_password_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.delete_needs_password_message))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.auth_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = password.isNotEmpty(),
                        onClick = {
                            val submitted = password
                            password = ""
                            coroutines.launch { viewModel.confirmWithPassword(submitted) }
                        },
                    ) {
                        Text(stringResource(R.string.delete_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            password = ""
                            viewModel.cancelDelete()
                        },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        ProfileViewModel.DeleteState.NeedsGoogle -> {
            AlertDialog(
                onDismissRequest = { viewModel.cancelDelete() },
                title = { Text(stringResource(R.string.delete_confirm_google_title)) },
                text = { Text(stringResource(R.string.delete_needs_password_message)) },
                confirmButton = {
                    TextButton(onClick = { coroutines.launch { viewModel.confirmWithGoogle() } }) {
                        Text(stringResource(R.string.delete_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelDelete() }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        ProfileViewModel.DeleteState.Done -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDeleteResult() },
                title = { Text(stringResource(R.string.delete_done_title)) },
                text = {
                    Text(
                        stringResource(
                            if (viewModel.removedAccount) R.string.delete_done_message else R.string.delete_local_done_message,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissDeleteResult() }) { Text(stringResource(R.string.ok)) }
                },
            )
        }

        else -> {
            Unit
        }
    }
}

private val RowsPadding = PaddingValues(vertical = 4.dp)

private enum class RowTrailing {
    CHEVRON,
    EXTERNAL,
    PROGRESS,
    NONE,
}

@Composable
private fun SectionTitle(title: String) {
    SectionHeader(title, Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun IdentityCard(
    account: Account?,
    initials: String?,
    onSignIn: () -> Unit,
) {
    SectionCard(modifier = Modifier.padding(top = 8.dp), contentPadding = PaddingValues(16.dp)) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(initials)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (account == null) {
                    Text(
                        stringResource(R.string.profile_signed_out_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.account_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AccountDetails(account)
                }
            }
        }
        if (account == null) {
            Button(
                onClick = onSignIn,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.auth_sign_in))
            }
        }
    }
}

@Composable
private fun AccountDetails(account: Account) {
    val name = account.displayName?.takeIf { it.isNotBlank() }
    val email = account.email?.takeIf { it.isNotBlank() }
    Text(
        name ?: email.orEmpty(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (name != null && email != null) {
        Text(
            email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Chip(
        stringResource(
            if (account.provider == Account.Provider.GOOGLE) {
                R.string.account_provider_google
            } else {
                R.string.account_provider_password
            },
        ),
        null,
        MaterialTheme.colorScheme.primary,
        Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Avatar(initials: String?) {
    Box(
        Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initials == null) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Text(
                initials,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private sealed interface DriverSheet {
    data object New : DriverSheet

    data class Edit(
        val item: DueItem,
    ) : DriverSheet

    data class Pay(
        val item: DueItem,
    ) : DriverSheet
}

@Composable
private fun GarageSummary(summary: ProfileViewModel.Summary) {
    SectionTitle(stringResource(R.string.profile_garage))
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTileCard(
                title = stringResource(R.string.vehicles),
                value = summary.vehicleCount.toString(),
                caption = null,
                icon = Icons.Filled.DirectionsCar,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            MetricTileCard(
                title = stringResource(R.string.profile_records),
                value = summary.recordCount.toString(),
                caption = stringResource(R.string.profile_records_caption),
                icon = Icons.Filled.LocalGasStation,
                tint = Palette.fuel,
                modifier = Modifier.weight(1f),
            )
        }
        MetricTileCard(
            title = stringResource(R.string.total_spent),
            value = summary.totalSpent.formattedCurrency,
            caption = stringResource(R.string.profile_total_spent_caption),
            icon = Icons.Filled.Payments,
            tint = Palette.green,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: RowTrailing = RowTrailing.CHEVRON,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RowTrailingContent(trailing)
    }
}

@Composable
private fun RowTrailingContent(trailing: RowTrailing) {
    when (trailing) {
        RowTrailing.CHEVRON -> {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RowTrailing.EXTERNAL -> {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        RowTrailing.PROGRESS -> {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        RowTrailing.NONE -> {
            Unit
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun AppFooter(version: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.format_version, version),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.profile_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun notificationsLabel(state: ProfileViewModel.NotificationsState): String =
    stringResource(
        when (state) {
            ProfileViewModel.NotificationsState.ON -> R.string.profile_notifications_on
            ProfileViewModel.NotificationsState.PARTIAL -> R.string.profile_notifications_partial
            ProfileViewModel.NotificationsState.OFF -> R.string.profile_notifications_off
        },
    )

@Composable
private fun unitsAndCurrencyLabel(viewModel: ProfileViewModel): String {
    val currency =
        viewModel.preferences.currency?.code
            ?: runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
            ?: stringResource(R.string.pref_currency_system)
    val units =
        stringResource(
            if (viewModel.preferences.unitSystem == UnitSystem.IMPERIAL) {
                R.string.profile_units_imperial_short
            } else {
                R.string.profile_units_metric_short
            },
        )
    return "$currency · $units"
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
