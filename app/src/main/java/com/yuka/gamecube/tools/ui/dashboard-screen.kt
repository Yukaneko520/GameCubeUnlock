// SPDX-License-Identifier: GPL-3.0-only
// Adapted from SukiSU-Ultra HomeMaterial (v4.2.1) and KernelSU Home (v1.0.5); see assets/licenses/NOTICE.
package com.yuka.gamecube.tools.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuka.gamecube.tools.BuildConfig
import com.yuka.gamecube.tools.core.ConfigSnapshot
import com.yuka.gamecube.tools.core.FrameworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class DeviceSummary(val device: String, val android: String, val gameCube: String?, val gameWatch: String? = null)

@Composable
internal fun DashboardScreen(config: ConfigSnapshot, status: String, compatibility: String,
    supported: Boolean, framework: FrameworkState, save: (ConfigSnapshot) -> Unit) {
    val context = LocalContext.current
    val versions by produceState<Pair<String?, String?>>(null to null, context) {
        value = withContext(Dispatchers.IO) {
            fun version(pkg: String) = runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(pkg, 0)
                "${info.versionName} (${info.longVersionCode})"
            }.getOrDefault("")
            version("com.vivo.gamecube") to version("com.vivo.gamewatch")
        }
    }
    DashboardContent(config, status, supported,
        DeviceSummary("${Build.MODEL} · ${Build.DEVICE}", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", versions.first, versions.second),
        framework = framework, compatibility = compatibility, save = save)
}

@Composable
internal fun DashboardContent(config: ConfigSnapshot, status: String, supported: Boolean,
    device: DeviceSummary, framework: FrameworkState = FrameworkState.disconnected(),
    compatibility: String = "", save: (ConfigSnapshot) -> Unit) {
    val health = moduleHealth(config, supported, status, framework)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ManagerStatusBanner(config.enabled, status, config.apps.values.count { it.enabled }, health)
        if (!supported && compatibility.isNotBlank()) SectionCard {
            Caption(compatibilityLabel(compatibility))
        }
        SectionCard {
            DeviceInfoRow("version", tr("Phiên bản ứng dụng", "App version"),
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            DeviceInfoRow("phone", tr("Thiết bị", "Device"), device.device)
            DeviceInfoRow("android", tr("Phiên bản Android", "Android version"),
                device.android)
            DeviceInfoRow("cube", tr("Phiên bản GameCube", "GameCube version"),
                device.gameCube?.ifEmpty { tr("Chưa tìm thấy", "Not found") } ?: tr("Đang đọc…", "Loading…"))
            DeviceInfoRow("gamepad", tr("Phiên bản GameWatch", "GameWatch version"),
                device.gameWatch?.ifEmpty { tr("Chưa tìm thấy", "Not found") } ?: tr("Đang đọc…", "Loading…"))
        }
        GlobalControls(config, supported, save)
        if (!supported) SectionCard {
            Text(tr("Chưa tương thích", "Not compatible"), fontWeight = FontWeight.SemiBold)
            Caption(tr("Xem thông tin chi tiết và nhật ký trong Cài đặt.",
                "See Settings for compatibility details and logs."))
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun ManagerStatusBanner(enabled: Boolean, status: String, enabledApps: Int = 0, health: ModuleHealth) {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val warning = health.warning
    val failed = health.issue.isNotEmpty() && !warning
    val active = enabled && health.issue.isEmpty()
    val container = if (warning) { if (dark) Color(0xFF483514) else Color(0xFFFFEBC2) }
        else if (failed) { if (dark) Color(0xFF4A2020) else Color(0xFFFADBD8) }
        else if (active) { if (dark) Color(0xFF193B27) else Color(0xFFD5EDDB) }
        else MiuixTheme.colorScheme.surface
    val foreground = if (warning && dark) Color(0xFFFFE1A8) else if (failed && dark) Color(0xFFFFDAD6)
        else if (active && dark) Color(0xFFE1EDE4) else MiuixTheme.colorScheme.onSurface
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), color = container,
        shape = RoundedCornerShape(20.dp)) {
        Box(Modifier.heightIn(min = 128.dp)) {
            Canvas(Modifier.align(Alignment.BottomEnd).offset(x = 20.dp, y = 22.dp).size(112.dp)) {
                val ink = if (warning) { if (dark) Color(0xFFFFCA70) else Color(0xFF8B5700) }
                    else if (failed) { if (dark) Color(0xFFFF8A80) else Color(0xFFC62828) }
                    else if (active) Color(0xFF2ECC63) else Color(0xFF858585)
                drawCircle(ink, radius = size.minDimension / 2 - 5.dp.toPx(), style = Stroke(9.dp.toPx()))
                val path = Path().apply {
                    if (warning) {
                        moveTo(size.width * .5f, size.height * .27f); lineTo(size.width * .5f, size.height * .53f)
                        moveTo(size.width * .5f, size.height * .7f); lineTo(size.width * .5f, size.height * .71f)
                    } else if (active) {
                        moveTo(size.width * .27f, size.height * .50f)
                        lineTo(size.width * .44f, size.height * .67f)
                        lineTo(size.width * .76f, size.height * .34f)
                    } else {
                        moveTo(size.width * .34f, size.height * .34f); lineTo(size.width * .66f, size.height * .66f)
                        moveTo(size.width * .66f, size.height * .34f); lineTo(size.width * .34f, size.height * .66f)
                    }
                }
                drawPath(path, ink, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(Modifier.padding(18.dp).fillMaxWidth(.79f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(when { warning -> tr("Cần kiểm tra", "Needs attention")
                    failed -> tr("Mô-đun chưa sẵn sàng", "Module not ready")
                    enabled -> tr("Mô-đun đã bật", "Module enabled")
                    else -> tr("Mô-đun đang tắt", "Module disabled") },
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = foreground)
                Text(tr("$enabledApps ứng dụng đã bật", "$enabledApps apps enabled"), fontSize = 14.sp, color = foreground)
                Text(if (health.issue.isNotEmpty()) healthReason(health, LocalLanguage.current) else connectionLabel(status), fontSize = 14.sp, color = foreground)
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(icon: String, title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Icon(remember(icon) { managerIcon(icon) }, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 16.sp)
            Caption(value)
        }
    }
}

internal fun compactConnectionStatus(status: String): String =
    status.substringBefore(" #").substringBefore(": ").substringBefore(";")
