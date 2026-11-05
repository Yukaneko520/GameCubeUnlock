package com.yuka.gamecube.tools.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.yuka.gamecube.tools.diagnostics.AppDiagnostics
import com.yuka.gamecube.tools.core.ConfigBridge
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    private lateinit var bridge: ConfigBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bridge = ConfigBridge(this)
        setContent {
            val appearancePreferences = remember { getSharedPreferences("ui_appearance", MODE_PRIVATE) }
            val manager = remember { ViewModelProvider(this@MainActivity)[ManagerState::class.java] }
            var language by remember { mutableStateOf(UiLanguage.fromStored(appearancePreferences.getString("language", null))) }
            var appearance by remember {
                mutableStateOf(Appearance.fromStored(appearancePreferences.getString("theme", null)))
            }
            val dark = when (appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val theme = remember(appearance) {
                ThemeController(
                    colorSchemeMode = appearance.mode,
                    lightColors = lightColorScheme(primary = Color(0xFF1C61D8), background = Color(0xFFF5F5F5),
                        surface = Color.White, error = Color(0xFFB3261E)),
                    darkColors = darkColorScheme(primary = Color(0xFF89B4FF), onPrimary = Color(0xFF092451),
                        background = Color.Black, surface = Color(0xFF242424), surfaceContainer = Color(0xFF242424),
                        error = Color(0xFFFF8C87), onSurfaceVariantActions = Color(0xFFA3A3A3)))
            }
            var config by remember { mutableStateOf(bridge.current()) }
            var status by remember { mutableStateOf(bridge.status()) }
            var compatibility by remember { mutableStateOf(bridge.compatibility()) }
            var nativeScopes by remember { mutableStateOf(bridge.nativeScopes()) }
            var scopeError by remember { mutableStateOf(bridge.scopeError()) }
            var framework by remember { mutableStateOf(bridge.frameworkState()) }
            DisposableEffect(bridge) {
                bridge.setListener(Runnable {
                    runOnUiThread {
                        config = bridge.current()
                        status = bridge.status()
                        compatibility = bridge.compatibility()
                        nativeScopes = bridge.nativeScopes()
                        scopeError = bridge.scopeError()
                        framework = bridge.frameworkState()
                    }
                })
                onDispose { bridge.setListener(null) }
            }
            CompositionLocalProvider(LocalLanguage provides language) {
            androidx.compose.material3.MaterialTheme(colorScheme = if (dark)
                androidx.compose.material3.darkColorScheme(background = Color.Black, surface = Color(0xFF242424),
                    primary = Color(0xFF89B4FF), onBackground = Color(0xFFEDEDED))
                else androidx.compose.material3.lightColorScheme(background = Color(0xFFF5F5F5), surface = Color.White)) {
            MiuixTheme(controller = theme) {
                GameCubeScreen(config, status, compatibility, bridge.supported(), bridge::refresh,
                    appearance, { selected ->
                        appearancePreferences.edit().putString("theme", selected.name).apply()
                        appearance = selected
                    }, manager, language, { selected ->
                        appearancePreferences.edit().putString("language", selected.tag).apply()
                        language = selected
                    }, nativeScopes, scopeError, bridge::requestNativeScope, framework) { next ->
                    try {
                        bridge.save(next)
                        config = bridge.current()
                        status = bridge.status()
                    } catch (failure: IllegalArgumentException) {
                        AppDiagnostics.error("save", failure)
                        Toast.makeText(this, language.text("Cấu hình không hợp lệ", "Invalid configuration"), Toast.LENGTH_LONG).show()
                    } catch (failure: Exception) {
                        AppDiagnostics.error("save", failure)
                        Toast.makeText(this, language.text("Không lưu được cấu hình. Vui lòng thử lại.", "Unable to save. Please try again."), Toast.LENGTH_LONG).show()
                    }
                }
            }
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) bridge.refresh()
    }

    override fun onDestroy() {
        bridge.close()
        super.onDestroy()
    }
}
