package br.com.joaoduartemariucio.rodaro.presentation.common

import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.backup.errors.BackupError
import br.com.joaoduartemariucio.rodaro.domain.common.errors.AccountError
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RemoteDataError
import br.com.joaoduartemariucio.rodaro.domain.common.errors.RepositoryError

fun Throwable.message(localizer: Localizer): String =
    when (this) {
        is RepositoryError.VehicleNotFound -> localizer.string(R.string.error_vehicle_not_found)
        is RepositoryError.EntryNotFound -> localizer.string(R.string.error_entry_not_found)
        is RepositoryError.PersistenceFailed -> localizer.string(R.string.error_persistence_failed)
        is AccountError.InvalidEmail -> localizer.string(R.string.auth_email_invalid)
        is AccountError.InvalidCredentials -> localizer.string(R.string.error_invalid_credentials)
        is AccountError.EmailInUse -> localizer.string(R.string.error_email_in_use)
        is AccountError.WeakPassword -> localizer.string(R.string.error_weak_password)
        is AccountError.TooManyRequests -> localizer.string(R.string.error_too_many_requests)
        is AccountError.Network -> localizer.string(R.string.error_network)
        is AccountError.RecentLoginRequired -> localizer.string(R.string.error_recent_login_required)
        is AccountError.OperationNotAllowed -> localizer.string(R.string.error_operation_not_allowed)
        is AccountError.GoogleNotConfigured -> localizer.string(R.string.error_google_not_configured)
        is AccountError.NoGoogleAccount -> localizer.string(R.string.error_no_google_account)
        is AccountError.Cancelled -> localizer.string(R.string.error_account_failed)
        is AccountError.NotSignedIn -> localizer.string(R.string.account_none)
        is BackupError.Unreadable -> localizer.string(R.string.error_backup_unreadable)
        is BackupError.NewerFormat -> localizer.string(R.string.error_backup_newer)
        is BackupError.Invalid -> localizer.string(R.string.error_backup_invalid)
        is BackupError.TooLarge -> localizer.string(R.string.error_backup_too_large)
        is BackupError.Storage -> localizer.string(R.string.error_backup_storage)
        is AccountError.Failed -> localizer.string(R.string.error_account_failed)
        is RemoteDataError.NetworkUnavailable -> localizer.string(R.string.error_remote_network)
        is RemoteDataError.QuotaExceeded -> localizer.string(R.string.error_remote_quota)
        is RemoteDataError.NotFound -> localizer.string(R.string.error_remote_not_found)
        is RemoteDataError.Unreadable, is RemoteDataError.RequestFailed -> localizer.string(R.string.error_remote_failed)
        else -> localizedMessage ?: localizer.string(R.string.error_persistence_failed)
    }
