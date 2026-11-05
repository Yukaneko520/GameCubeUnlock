package com.yuka.gamecube.tools.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

internal class ManagerState(application: Application) : AndroidViewModel(application) {
    val apps = InstalledApps(application)
    private val preferences = application.getSharedPreferences("ui_appearance", 0)
    val developer = DeveloperSettings(preferences)
    var query by mutableStateOf("")
    var refreshToken by mutableIntStateOf(0)
    var loadedRefreshToken = 0
    var showSystem by mutableStateOf(preferences.getBoolean("show_system", false))
        private set
    var sort by mutableStateOf(runCatching {
        AppSort.valueOf(preferences.getString("app_sort", "NAME")!!)
    }.getOrDefault(AppSort.NAME))
        private set
    var reversed by mutableStateOf(preferences.getBoolean("sort_reversed", false))
        private set
    fun filters(system: Boolean = showSystem) {
        preferences.edit().putBoolean("show_system", system).apply()
        showSystem = system
    }
    fun sorting(value: AppSort = sort, reverse: Boolean = reversed) {
        preferences.edit().putString("app_sort", value.name).putBoolean("sort_reversed", reverse).apply()
        sort = value; reversed = reverse
    }
}
