package br.com.joaoduartemariucio.rodaro.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.maintenance.calculations.MaintenanceStatus

object Palette {
    val fuel = Color(0xFF30B0C7)
    val service = Color(0xFF5856D6)
    val expense = Color(0xFFA2845E)
    val green = Color(0xFF34C759)
    val orange = Color(0xFFFF9500)
    val red = Color(0xFFFF3B30)
    val blue = Color(0xFF007AFF)
}

val MaintenanceStatus.Level.label: Int
    get() =
        when (this) {
            MaintenanceStatus.Level.ON_TRACK -> R.string.status_on_track
            MaintenanceStatus.Level.DUE_SOON -> R.string.status_due_soon
            MaintenanceStatus.Level.OVERDUE -> R.string.status_overdue
        }

val MaintenanceStatus.Level.icon: ImageVector
    get() =
        when (this) {
            MaintenanceStatus.Level.ON_TRACK -> Icons.Filled.CheckCircle
            MaintenanceStatus.Level.DUE_SOON -> Icons.Filled.PendingActions
            MaintenanceStatus.Level.OVERDUE -> Icons.Filled.Warning
        }

val MaintenanceStatus.Level.color: Color
    get() =
        when (this) {
            MaintenanceStatus.Level.ON_TRACK -> Palette.green
            MaintenanceStatus.Level.DUE_SOON -> Palette.orange
            MaintenanceStatus.Level.OVERDUE -> Palette.red
        }
