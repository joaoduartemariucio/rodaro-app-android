package br.com.joaoduartemariucio.rodaro.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Toll
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.ExpenseCategory
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineDiscount
import br.com.joaoduartemariucio.rodaro.domain.costs.entities.FineSeverity

val ExpenseCategory.label: Int
    get() =
        when (this) {
            ExpenseCategory.IPVA -> R.string.expense_category_ipva
            ExpenseCategory.LICENCIAMENTO -> R.string.expense_category_licensing
            ExpenseCategory.SEGURO -> R.string.expense_category_insurance
            ExpenseCategory.PEDAGIO -> R.string.expense_category_toll
            ExpenseCategory.ESTACIONAMENTO -> R.string.expense_category_parking
            ExpenseCategory.LAVAGEM -> R.string.expense_category_wash
            ExpenseCategory.MULTA -> R.string.expense_category_fine
            ExpenseCategory.FINANCIAMENTO -> R.string.expense_category_financing
            ExpenseCategory.ACESSORIOS -> R.string.expense_category_accessories
            ExpenseCategory.OUTROS -> R.string.expense_category_other
        }

val ExpenseCategory.icon: ImageVector
    get() =
        when (this) {
            ExpenseCategory.IPVA -> Icons.Filled.AccountBalance
            ExpenseCategory.LICENCIAMENTO -> Icons.Filled.Description
            ExpenseCategory.SEGURO -> Icons.Filled.Shield
            ExpenseCategory.PEDAGIO -> Icons.Filled.Toll
            ExpenseCategory.ESTACIONAMENTO -> Icons.Filled.LocalParking
            ExpenseCategory.LAVAGEM -> Icons.Filled.LocalCarWash
            ExpenseCategory.MULTA -> Icons.Filled.Gavel
            ExpenseCategory.FINANCIAMENTO -> Icons.Filled.CreditCard
            ExpenseCategory.ACESSORIOS -> Icons.Filled.ShoppingBag
            ExpenseCategory.OUTROS -> Icons.Filled.Receipt
        }

val FineSeverity.label: Int
    get() =
        when (this) {
            FineSeverity.LEVE -> R.string.fine_severity_light
            FineSeverity.MEDIA -> R.string.fine_severity_medium
            FineSeverity.GRAVE -> R.string.fine_severity_serious
            FineSeverity.GRAVISSIMA -> R.string.fine_severity_very_serious
        }

val FineDiscount.label: Int
    get() =
        when (this) {
            FineDiscount.NONE -> R.string.fine_discount_none
            FineDiscount.TWENTY -> R.string.fine_discount_twenty
            FineDiscount.FORTY -> R.string.fine_discount_forty
        }
