package br.com.joaoduartemariucio.rodaro.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.joaoduartemariucio.rodaro.R
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind

val ServiceKind.label: Int
    get() =
        when (this) {
            ServiceKind.OIL_CHANGE -> R.string.service_oil_change
            ServiceKind.OIL_FILTER -> R.string.service_oil_filter
            ServiceKind.AIR_FILTER -> R.string.service_air_filter
            ServiceKind.FUEL_FILTER -> R.string.service_fuel_filter
            ServiceKind.SPARK_PLUGS -> R.string.service_spark_plugs
            ServiceKind.BRAKES -> R.string.service_brakes
            ServiceKind.TIRES -> R.string.service_tires
            ServiceKind.CHAIN -> R.string.service_chain
            ServiceKind.COOLANT -> R.string.service_coolant
            ServiceKind.INSPECTION -> R.string.service_inspection
            ServiceKind.TIMING_BELT -> R.string.service_timing_belt
            ServiceKind.BRAKE_FLUID -> R.string.service_brake_fluid
            ServiceKind.BATTERY -> R.string.service_battery
            ServiceKind.ALIGNMENT -> R.string.service_alignment
            ServiceKind.CABIN_FILTER -> R.string.service_cabin_filter
            ServiceKind.TRANSMISSION_OIL -> R.string.service_transmission_oil
            ServiceKind.CHAIN_KIT -> R.string.service_chain_kit
            ServiceKind.CVT_BELT -> R.string.service_cvt_belt
            ServiceKind.VALVE_CLEARANCE -> R.string.service_valve_clearance
            ServiceKind.OTHER -> R.string.service_other
        }

val ServiceKind.icon: ImageVector
    get() =
        when (this) {
            ServiceKind.OIL_CHANGE, ServiceKind.OIL_FILTER -> Icons.Filled.OilBarrel
            ServiceKind.AIR_FILTER -> Icons.Filled.Air
            ServiceKind.FUEL_FILTER -> Icons.Filled.FilterAlt
            ServiceKind.SPARK_PLUGS -> Icons.Filled.Bolt
            ServiceKind.BRAKES -> Icons.Filled.DiscFull
            ServiceKind.TIRES -> Icons.Filled.TireRepair
            ServiceKind.CHAIN -> Icons.Filled.Link
            ServiceKind.COOLANT -> Icons.Filled.Thermostat
            ServiceKind.INSPECTION -> Icons.Filled.Checklist
            ServiceKind.TIMING_BELT -> Icons.Filled.Settings
            ServiceKind.BRAKE_FLUID -> Icons.Filled.WaterDrop
            ServiceKind.BATTERY -> Icons.Filled.BatteryChargingFull
            ServiceKind.ALIGNMENT -> Icons.Filled.Straighten
            ServiceKind.CABIN_FILTER -> Icons.Filled.AcUnit
            ServiceKind.TRANSMISSION_OIL -> Icons.Filled.Opacity
            ServiceKind.CHAIN_KIT -> Icons.Filled.Handyman
            ServiceKind.CVT_BELT -> Icons.Filled.Sync
            ServiceKind.VALVE_CLEARANCE -> Icons.Filled.Tune
            ServiceKind.OTHER -> Icons.Filled.Build
        }
