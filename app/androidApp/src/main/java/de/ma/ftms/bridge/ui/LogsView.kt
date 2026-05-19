package de.ma.ftms.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.debug.LogEntry
import de.ma.ftms.bridge.debug.LogLevel
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.navigation.LogsComponent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsView(component: LogsComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val strings = LocalStrings.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLevelNames by rememberSaveable { mutableStateOf(LogLevel.entries.map { it.name }) }
    val selectedLevels = remember(selectedLevelNames) { selectedLevelNames.mapNotNull { runCatching { LogLevel.valueOf(it) }.getOrNull() }.toSet() }

    val filteredLogs = remember(state.logs, searchQuery, selectedLevels) {
        val query = searchQuery.trim()
        state.logs
            .filter { it.level in selectedLevels }
            .filter {
                query.isEmpty() ||
                    it.tag.contains(query, ignoreCase = true) ||
                    it.message.contains(query, ignoreCase = true)
            }
    }
    val filteredDiagnosticLogs = remember(state.diagnosticLogs, searchQuery, selectedLevels) {
        val query = searchQuery.trim()
        state.diagnosticLogs
            .filter { it.level in selectedLevels }
            .filter {
                query.isEmpty() ||
                    it.tag.contains(query, ignoreCase = true) ||
                    it.message.contains(query, ignoreCase = true)
            }
    }

    ScreenContainer(title = strings.logs.title) {
        LogControls(
            strings = strings,
            totalCount = state.logs.size,
            filteredCount = filteredLogs.size,
            diagnosticCount = state.diagnosticLogs.size,
            diagnosticLogPath = state.latestDiagnosticLogPath,
            capturing = state.logCaptureEnabled,
            canClear = state.logs.isNotEmpty() || state.diagnosticLogs.isNotEmpty(),
            canShare = state.latestLogFilePath != null,
            searchQuery = searchQuery,
            selectedLevels = selectedLevels,
            onSearchChange = { searchQuery = it },
            onToggleCapture = component::onToggleCapture,
            onClear = component::onClearLogs,
            onShare = { component.onShareLatestLog(context) },
            onToggleLevel = { level, selected ->
                selectedLevelNames = if (selected) {
                    (selectedLevelNames + level.name).distinct()
                } else {
                    selectedLevelNames - level.name
                }
            },
        )

        if (filteredLogs.isEmpty() && filteredDiagnosticLogs.isEmpty()) {
            InfoCard {
                Text(strings.logs.noDebugLogEntries, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@ScreenContainer
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (filteredDiagnosticLogs.isNotEmpty()) {
                item {
                    Text(strings.logs.diagnosticLog, fontWeight = FontWeight.Bold)
                }
                items(filteredDiagnosticLogs, key = { "diag-${it.entryId}" }) { log ->
                    LogEntryRow(log)
                }
            }
            if (filteredLogs.isNotEmpty()) {
                item {
                    Text(strings.logs.androidLogcatLog, fontWeight = FontWeight.Bold)
                }
            }
            items(filteredLogs, key = { "logcat-${it.entryId}" }) { log ->
                LogEntryRow(log)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogControls(
    strings: Strings,
    totalCount: Int,
    filteredCount: Int,
    diagnosticCount: Int,
    diagnosticLogPath: String?,
    capturing: Boolean,
    canClear: Boolean,
    canShare: Boolean,
    searchQuery: String,
    selectedLevels: Set<LogLevel>,
    onSearchChange: (String) -> Unit,
    onToggleCapture: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onToggleLevel: (LogLevel, Boolean) -> Unit,
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.logs.debugLog, fontWeight = FontWeight.Bold)
                Text(
                    strings.logs.entryCount(filteredCount, totalCount, capturing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(if (capturing) strings.logs.live else strings.logs.stopped, if (capturing) BadgeTone.SUCCESS else BadgeTone.NEUTRAL)
        }

        StatusRow(strings.logs.diagnosticLog, "$diagnosticCount")
        StatusRow(strings.logs.diagnosticLogPath, diagnosticLogPath ?: strings.logs.noDiagnosticLogFile)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onToggleCapture, modifier = Modifier.weight(1f)) {
                Text(if (capturing) strings.common.stop else strings.common.start)
            }
            OutlinedButton(enabled = canClear, onClick = onClear, modifier = Modifier.weight(1f)) {
                Text(strings.common.clear)
            }
            OutlinedButton(enabled = canShare, onClick = onShare, modifier = Modifier.weight(1f)) {
                Text(strings.common.share)
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LogLevel.entries.forEach { level ->
                LevelFilterChip(
                    level = level,
                    strings = strings,
                    selected = level in selectedLevels,
                    onSelectionChanged = { selected -> onToggleLevel(level, selected) },
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text(strings.logs.searchLogs) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LevelFilterChip(
    level: LogLevel,
    strings: Strings,
    selected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
) {
    val color = logLevelColor(level)
    FilterChip(
        selected = selected,
        onClick = { onSelectionChanged(!selected) },
        label = { Text(level.label(strings)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.18f),
            selectedLabelColor = color,
        ),
    )
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val levelColor = logLevelColor(entry.level)
    val levelContentColor = logLevelContentColor(entry.level)

    InfoCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = entry.level.char,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = levelContentColor,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(levelColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.outline
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

@Composable
private fun logLevelContentColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface
    LogLevel.INFO -> MaterialTheme.colorScheme.onPrimary
    LogLevel.WARN -> MaterialTheme.colorScheme.onTertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.onError
}

private fun LogLevel.label(strings: Strings): String =
    when (this) {
        LogLevel.DEBUG -> strings.logs.levelDebug
        LogLevel.INFO -> strings.logs.levelInfo
        LogLevel.WARN -> strings.logs.levelWarn
        LogLevel.ERROR -> strings.logs.levelError
    }

private fun formatTimestamp(epochMillis: Long): String {
    val totalSeconds = epochMillis / 1000
    val millis = epochMillis % 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = (totalSeconds / 3600) % 24
    return "${hours.pad(2)}:${minutes.pad(2)}:${seconds.pad(2)}.${millis.pad(3)}"
}

private fun Long.pad(length: Int): String = toString().padStart(length, '0')
