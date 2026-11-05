package com.yuka.gamecube.tools.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*

internal fun numericEditAllowed(text: String, decimal: Boolean = false, negative: Boolean = false): Boolean {
    if (text.length > 12) return false
    val unsigned = if (negative && text.startsWith('-')) text.drop(1) else text
    return unsigned.all { it in '0'..'9' || decimal && it == '.' } && unsigned.count { it == '.' } <= 1
}

@Composable
internal fun NumericField(value: String, change: (String) -> Unit, label: String, enabled: Boolean,
    modifier: Modifier = Modifier, decimal: Boolean = false, negative: Boolean = false) {
    TextField(value, { if (numericEditAllowed(it, decimal, negative)) change(it) }, label = label,
        singleLine = true, enabled = enabled, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next))
}

internal fun replaceFrameCell(text: String, row: Int, column: Int, value: String): String {
    require(numericEditAllowed(value) && column in 0..2)
    val rows = text.lines().toMutableList()
    val cells = rows[row].split('/').toMutableList()
    require(cells.size == 3)
    cells[column] = value
    rows[row] = cells.joinToString("/")
    return rows.joinToString("\n")
}

@Composable
internal fun FrameModeEditor(title: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    val rows = value.lines()
    Text(title)
    rows.forEachIndexed { row, line ->
        val cells = line.split('/')
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tr("Game", "Game"), tr("Đầu vào", "Input"), tr("Đầu ra", "Output")).forEachIndexed { column, label ->
                NumericField(cells.getOrElse(column) { "" }, { change(replaceFrameCell(value, row, column, it)) },
                    label, enabled, Modifier.weight(1f))
            }
            if (rows.size > 1) IconButton(onClick = {
                change(rows.filterIndexed { index, _ -> index != row }.joinToString("\n"))
            }, enabled = enabled) { Icon(remember { managerIcon("delete") }, tr("Xóa mức FPS", "Remove FPS level")) }
        }
    }
    if (rows.size < 12) TextButton(tr("Thêm mức FPS game", "Add game FPS level"), {
        change(value + "\n//")
    }, enabled = enabled, modifier = Modifier.fillMaxWidth())
}
