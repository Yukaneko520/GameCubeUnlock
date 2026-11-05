package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
internal fun AppListActions(sort: AppSort, reversed: Boolean, showSystem: Boolean,
    onSort: (AppSort, Boolean) -> Unit, onFilter: (Boolean) -> Unit) {
    var sorting by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { sorting = true }, holdDownState = sorting) { Icon(remember { managerIcon("sort") }, tr("Sắp xếp", "Sort")) }
        WindowListPopup(sorting, alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { sorting = false }) { ListPopupColumn {
            AppSort.entries.forEachIndexed { index, value ->
                val title = when (value) { AppSort.NAME -> tr("Tên ứng dụng", "App name")
                    AppSort.PACKAGE -> tr("Tên gói", "Package name")
                    AppSort.INSTALLED -> tr("Ngày cài đặt", "Install date")
                    AppSort.UPDATED -> tr("Ngày cập nhật", "Update date") }
                DropdownImpl(title, AppSort.entries.size + 1, sort == value, index,
                    onSelectedIndexChange = { onSort(value, reversed); sorting = false })
            }
            DropdownImpl(tr("Đảo thứ tự", "Reverse order"), AppSort.entries.size + 1, reversed,
                AppSort.entries.size, onSelectedIndexChange = { onSort(sort, !reversed); sorting = false })
        } }
    }
    Box {
        IconButton(onClick = { filters = true }, holdDownState = filters) { Icon(remember { managerIcon("more") }, tr("Tùy chọn", "Options")) }
        WindowListPopup(filters, alignment = PopupPositionProvider.Align.TopEnd,
            onDismissRequest = { filters = false }) { ListPopupColumn {
            DropdownImpl(tr("Hiển thị ứng dụng hệ thống", "Show system apps"), 1, showSystem, 0,
                onSelectedIndexChange = { onFilter(!showSystem); filters = false })
        } }
    }
}
