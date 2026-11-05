package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, inset: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), insideMargin = if (inset)
        PaddingValues(horizontal = 20.dp, vertical = 16.dp) else PaddingValues(0.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(if (inset) 12.dp else 0.dp), content = content)
    }
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp), fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Medium)
}

@Composable
internal fun ToggleRow(title: String, summary: String, checked: Boolean,
    enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    PreferenceRow(title, summary, enabled = enabled, modifier = Modifier.semantics(mergeDescendants = true) {
        toggleableState = ToggleableState(checked)
        role = Role.Switch
        if (!enabled) disabled()
    }, role = Role.Switch, onClick = { onChange(!checked) }) {
        Switch(checked, null, enabled = enabled, modifier = Modifier.widthIn(min = 52.dp))
    }
}

/** Padding belongs inside the click target, so Card clips feedback at its outer edge. */
@Composable
internal fun PreferenceRow(title: String, summary: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, role: Role? = null, holdDownState: Boolean = false, onClick: () -> Unit,
    endActions: @Composable RowScope.() -> Unit = { Chevron() }) {
    BasicComponent(modifier = modifier, insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        enabled = enabled, role = role, holdDownState = holdDownState, onClick = onClick, endActions = endActions) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface)
            if (summary.isNotBlank()) Caption(summary)
        }
    }
}

@Composable
internal fun SectionDivider() {
    Box(Modifier.fillMaxWidth().height(0.5.dp)
        .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.14f)))
}

@Composable
internal fun InfoRow(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Caption(title)
        Text(value, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
internal fun Chevron() {
    Icon(remember { managerIcon("next") }, contentDescription = null, modifier = Modifier.size(20.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
internal fun HomeRefreshAction(refresh: () -> Unit) {
    IconButton(onClick = refresh, minWidth = 48.dp, minHeight = 48.dp) {
        Icon(remember { managerIcon("refresh") }, tr("Làm mới", "Refresh"),
            tint = MiuixTheme.colorScheme.onSurface)
    }
}
