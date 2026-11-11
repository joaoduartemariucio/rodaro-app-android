package br.com.joaoduartemariucio.rodaro.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.dues.entities.DueKind

val DueKind.label: Int
    get() =
        when (this) {
            DueKind.IPVA -> R.string.due_kind_ipva
            DueKind.LICENCIAMENTO -> R.string.due_kind_licensing
            DueKind.SEGURO -> R.string.due_kind_insurance
            DueKind.CNH -> R.string.due_kind_cnh
        }

val DueKind.icon: ImageVector
    get() =
        when (this) {
            DueKind.IPVA -> Icons.Filled.AccountBalance
            DueKind.LICENCIAMENTO -> Icons.Filled.Description
            DueKind.SEGURO -> Icons.Filled.Shield
            DueKind.CNH -> Icons.Filled.Badge
        }
