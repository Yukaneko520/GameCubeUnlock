package com.yuka.gamecube.tools.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class DeliveryState { SENT, SYNCING, ERROR, WAITING }
internal fun deliveryState(status: String): DeliveryState = when {
    status.startsWith("Configuration sent") -> DeliveryState.SENT
    status.startsWith("Saving and syncing") -> DeliveryState.SYNCING
    status.startsWith("Save failed") || status.startsWith("Sync failed") || status.startsWith("Configuration error") -> DeliveryState.ERROR
    else -> DeliveryState.WAITING
}

@Composable
internal fun SyncStatus(status: String, retry: () -> Unit) {
    val label = connectionLabel(status)
    val tint = when (deliveryState(status)) {
        DeliveryState.SENT -> MiuixTheme.colorScheme.primary
        DeliveryState.ERROR -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    IconButton(onClick = retry, modifier = Modifier.semantics { stateDescription = label },
        minWidth = 48.dp, minHeight = 48.dp) {
        Icon(remember { managerIcon("cloud") }, label, tint = tint)
    }
}
