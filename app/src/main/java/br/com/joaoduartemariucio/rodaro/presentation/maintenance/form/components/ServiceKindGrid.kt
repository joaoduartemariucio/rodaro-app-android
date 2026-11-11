package br.com.joaoduartemariucio.rodaro.presentation.maintenance.form.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.joaoduartemariucio.rodaro.domain.maintenance.entities.ServiceKind
import br.com.joaoduartemariucio.rodaro.presentation.common.Palette
import br.com.joaoduartemariucio.rodaro.presentation.common.icon
import br.com.joaoduartemariucio.rodaro.presentation.common.label

@Composable
fun ServiceKindGrid(
    kinds: List<ServiceKind>,
    selection: ServiceKind,
    onSelect: (ServiceKind) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints {
        val spacing = 8.dp
        val columns = maxOf(1, ((maxWidth + spacing) / (96.dp + spacing)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            kinds.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { kind ->
                        val isSelected = kind == selection
                        Column(
                            Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Palette.service else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .selectable(selected = isSelected, role = Role.RadioButton) {
                                    if (kind != selection) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    onSelect(kind)
                                }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                        ) {
                            val tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            Icon(kind.icon, contentDescription = null, tint = tint)
                            Text(
                                stringResource(kind.label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = tint,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
