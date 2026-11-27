package de.ma.ftms.bridge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.ma.ftms.bridge.i18n.LocalStrings
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.i18n.fmt
import de.ma.ftms.bridge.navigation.HistoryComponent
import de.ma.ftms.bridge.runtime.BridgeUiState
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.SESSION_STATUS_RUNNING
import de.ma.ftms.bridge.runtime.SensorSources
import de.ma.ftms.bridge.runtime.SourcedMetric
import de.ma.ftms.core.storage.SessionAverages
import de.ma.ftms.core.storage.SessionSampleRecord
import de.ma.ftms.core.storage.WorkoutSessionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The widest label a value axis of a session's charts has, so all their plots begin at the same place. */
private const val WIDEST_AXIS_LABEL = "00:00"

/**
 * The history: the recorded sessions by month below their sums, or one of them with its figures
 * and charts. The title, the way back and the actions that delete are in [HistoryTopBar].
 */
@Composable
fun HistoryView(component: HistoryComponent) {
    val state by component.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val selected = state.sessions.firstOrNull { it.sessionId == state.selectedHistorySessionId }
    // Kept here, above list and session, so the list is as it was left when the session is closed.
    var typeFilter by rememberSaveable { mutableStateOf<EquipmentType?>(null) }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.ALL) }
    val listState = rememberLazyListState()

    val marked = state.markedHistorySessionIds

    BackHandler(enabled = selected != null || marked.isNotEmpty()) {
        if (selected != null) component.onSelectSession(null) else component.onClearSessionMarks()
    }
    // Marks are for an action now; they do not wait behind another tab for a later delete.
    DisposableEffect(Unit) {
        onDispose { component.onClearSessionMarks() }
    }

    ScreenContainer(title = strings.history.title) {
        if (selected == null) {
            HistoryList(
                strings = strings,
                sessions = state.sessions,
                averages = state.sessionAverages,
                marked = marked,
                typeFilter = typeFilter,
                onTypeFilter = {
                    typeFilter = it
                    // A mark on a session the filter hides would be deleted unseen.
                    component.onClearSessionMarks()
                },
                period = period,
                onPeriod = { period = it },
                listState = listState,
                onSelectSession = component::onSelectSession,
                onToggleMark = component::onToggleSessionMark,
            )
        } else {
            val type = selected.equipmentType
            key(selected.sessionId) {
                HistoryDetail(
                    strings = strings,
                    session = selected,
                    averages = state.sessionAverages[selected.sessionId],
                    sources = state.settings.sensorSources(type),
                    chartKeys = state.settings.historyCharts(type),
                    samples = state.selectedHistorySamples,
                    samplesLoaded = state.selectedHistorySamplesLoaded,
                )
            }
        }
    }
}

/**
 * The bar above the history. Over the list it names the screen and offers to clear the history;
 * while sessions are marked it counts them, offers to delete them and to take the marks away; over
 * a session it names the session, leads back to the list and offers to delete the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryTopBar(
    state: BridgeUiState,
    strings: Strings,
    onBack: () -> Unit,
    onDeleteSessions: (Set<String>) -> Unit,
    onClearMarks: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val selected = state.sessions.firstOrNull { it.sessionId == state.selectedHistorySessionId }
    // What the menu deletes: the session that is open, else the marked ones, else the whole history.
    val toDelete = selected?.let { setOf(it.sessionId) } ?: state.markedHistorySessionIds
    val marking = selected == null && toDelete.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val menu: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(BridgeUiIcons.MoreVertical, contentDescription = strings.history.moreOptions)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            when (toDelete.size) {
                                0 -> strings.history.clearHistory
                                1 -> strings.history.deleteSession
                                else -> strings.history.deleteSessions
                            },
                        )
                    },
                    onClick = {
                        menuOpen = false
                        confirming = true
                    },
                )
            }
        }
    }

    when {
        selected != null -> {
            val dateFormat = remember(strings) { SimpleDateFormat(strings.history.sessionDatePattern, strings.locale) }
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(BridgeUiIcons.ArrowBack, contentDescription = strings.common.back)
                    }
                },
                title = {
                    Column {
                        Text(
                            dateFormat.format(Date(selected.startedAtMillis)),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                equipmentTypeLabel(selected.equipmentType, strings),
                                selected.treadmillName?.takeUnless { it.isBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = { menu() },
            )
        }
        marking -> TopAppBar(
            navigationIcon = {
                IconButton(onClick = onClearMarks) {
                    Icon(BridgeUiIcons.Close, contentDescription = strings.history.clearMarks)
                }
            },
            title = { Text(strings.history.markedSessions(toDelete.size)) },
            actions = { menu() },
        )
        else -> CenterAlignedTopAppBar(
            title = { Text(strings.history.title) },
            actions = { if (state.sessions.isNotEmpty()) menu() },
        )
    }

    if (confirming) {
        ConfirmActionDialog(
            title = when (toDelete.size) {
                0 -> strings.history.clearHistoryTitle
                1 -> strings.history.deleteSessionTitle
                else -> strings.history.deleteSessionsTitle(toDelete.size)
            },
            text = when (toDelete.size) {
                0 -> strings.history.clearHistoryText
                1 -> strings.history.deleteSessionText
                else -> strings.history.deleteSessionsText
            },
            confirmLabel = if (toDelete.isEmpty()) strings.common.clear else strings.common.delete,
            onConfirm = {
                confirming = false
                if (toDelete.isEmpty()) onClearHistory() else onDeleteSessions(toDelete)
            },
            onDismiss = { confirming = false },
        )
    }
}

// ---- The list ----

@Composable
private fun HistoryList(
    strings: Strings,
    sessions: List<WorkoutSessionRecord>,
    averages: Map<String, SessionAverages>,
    marked: Set<String>,
    typeFilter: EquipmentType?,
    onTypeFilter: (EquipmentType?) -> Unit,
    period: HistoryPeriod,
    onPeriod: (HistoryPeriod) -> Unit,
    listState: LazyListState,
    onSelectSession: (String) -> Unit,
    onToggleMark: (String) -> Unit,
) {
    if (sessions.isEmpty()) {
        InfoCard {
            Text(strings.history.noSessionsStored, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val zone = remember { TimeZone.getDefault() }
    val recordedTypes = remember(sessions) { recordedEquipmentTypes(sessions) }
    val activeFilter = typeFilter?.takeIf { it in recordedTypes }
    val shownSessions = remember(sessions, activeFilter) { filterSessionsByType(sessions, activeFilter) }
    val months = remember(shownSessions, zone) { groupSessionsByMonth(shownSessions, zone) }
    val periodTotals = remember(shownSessions, averages, period, zone) {
        val start = period.startMillis(System.currentTimeMillis(), zone, Locale.getDefault())
        historyTotals(sessionsSince(shownSessions, start), averages)
    }
    val showAscent = shownSessions.any { it.equipmentType.showsClimb }
    // The type says nothing new where every row is of the same one.
    val showType = recordedTypes.size > 1 && activeFilter == null
    val dayFormat = remember(strings) { SimpleDateFormat(strings.history.sessionDayPattern, strings.locale) }
    val monthFormat = remember(strings) { SimpleDateFormat(strings.history.monthPattern, strings.locale) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        if (recordedTypes.size > 1) {
            item(key = "filter") {
                EquipmentTypeFilter(
                    strings = strings,
                    types = recordedTypes,
                    selected = activeFilter,
                    onSelected = onTypeFilter,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        item(key = "summary") {
            PeriodSummary(
                strings = strings,
                period = period,
                onPeriod = onPeriod,
                totals = periodTotals,
                showAscent = showAscent,
            )
        }
        months.forEach { month ->
            item(key = "month-${month.startMillis}") {
                MonthHeader(
                    name = monthFormat.format(Date(month.startMillis)),
                    totals = historyTotals(month.sessions, averages),
                    strings = strings,
                )
            }
            itemsIndexed(month.sessions, key = { _, session -> "session-${session.sessionId}" }) { index, session ->
                SessionRow(
                    strings = strings,
                    session = session,
                    averages = averages[session.sessionId],
                    day = dayFormat.format(Date(session.startedAtMillis)),
                    showType = showType,
                    first = index == 0,
                    last = index == month.sessions.lastIndex,
                    marking = marked.isNotEmpty(),
                    marked = session.sessionId in marked,
                    onOpen = { onSelectSession(session.sessionId) },
                    onToggleMark = { onToggleMark(session.sessionId) },
                )
            }
        }
        item(key = "end") { VerticalGap() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquipmentTypeFilter(
    strings: Strings,
    types: List<EquipmentType>,
    selected: EquipmentType?,
    onSelected: (EquipmentType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(strings.history.filterAll) },
        )
        types.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelected(type) },
                label = { Text(equipmentTypeLabel(type, strings)) },
            )
        }
    }
}

/** The sums of the sessions of the running week, month or year, or of all of them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodSummary(
    strings: Strings,
    period: HistoryPeriod,
    onPeriod: (HistoryPeriod) -> Unit,
    totals: HistoryTotals,
    showAscent: Boolean,
) {
    InfoCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HistoryPeriod.entries.forEach { option ->
                FilterChip(
                    selected = option == period,
                    onClick = { onPeriod(option) },
                    label = { Text(option.label(strings)) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.titleMedium
            FigureBlock(FigureText(totals.sessionCount.toString(), ""), strings.history.totalSessions, style, Modifier.weight(1f))
            FigureBlock(
                FigureText(strings.fmt("%.1f", totals.distanceM / 1000.0), "km"),
                strings.common.distance,
                style,
                Modifier.weight(1f),
            )
            FigureBlock(FigureText(formatHoursMinutes(totals.durationMillis), "h"), strings.common.duration, style, Modifier.weight(1f))
            if (showAscent) {
                FigureBlock(FigureText(strings.fmt("%.0f", totals.ascentM), "m"), strings.common.ascent, style, Modifier.weight(1f))
            }
        }
    }
}

private fun HistoryPeriod.label(strings: Strings): String =
    when (this) {
        HistoryPeriod.WEEK -> strings.history.periodWeek
        HistoryPeriod.MONTH -> strings.history.periodMonth
        HistoryPeriod.YEAR -> strings.history.periodYear
        HistoryPeriod.ALL -> strings.history.allTime
    }

/** The name of a month above its sessions, with what they add up to. */
@Composable
private fun MonthHeader(name: String, totals: HistoryTotals, strings: Strings) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            name.uppercase(strings.locale),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            listOf(
                totals.sessionCount.toString(),
                strings.fmt("%.1f km", totals.distanceM / 1000.0),
                "${formatHoursMinutes(totals.durationMillis)} h",
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One session in the list: when it was, then its three figures and the average heart rate. The
 * rows of a month form one block, so only the [first] and the [last] have rounded corners.
 *
 * A tap opens the session and a long press marks it. While any session is marked ([marking]),
 * every row shows a tick box and a tap switches its mark instead. A session that is still
 * recording cannot be marked; the long press does nothing there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    strings: Strings,
    session: WorkoutSessionRecord,
    averages: SessionAverages?,
    day: String,
    showType: Boolean,
    first: Boolean,
    last: Boolean,
    marking: Boolean,
    marked: Boolean,
    onOpen: () -> Unit,
    onToggleMark: () -> Unit,
) {
    val type = session.equipmentType
    val figures = sessionFigures(session, averages)
    val corner = 12.dp
    val shape = RoundedCornerShape(
        topStart = if (first) corner else 0.dp,
        topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp,
        bottomEnd = if (last) corner else 0.dp,
    )
    val markable = session.finalStatus != SESSION_STATUS_RUNNING
    val touch = if (marking) {
        Modifier.toggleable(value = marked, enabled = markable, role = Role.Checkbox, onValueChange = { onToggleMark() })
    } else {
        Modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = onToggleMark.takeIf { markable },
            onLongClickLabel = strings.history.markSession.takeIf { markable },
        )
    }
    Surface(
        shape = shape,
        color = if (marked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(touch),
    ) {
        Column {
            if (!first) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (marking) {
                    // The mark is not told by the colour of the row alone.
                    Checkbox(
                        checked = marked,
                        onCheckedChange = null,
                        enabled = markable,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(day, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        if (showType) {
                            Text(
                                equipmentTypeLabel(type, strings),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (session.finalStatus != SESSION_STATUS_STOPPED) {
                            // Only a session that did not end the usual way says so.
                            Text(
                                sessionStatusLabel(session.finalStatus, strings),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (session.finalStatus == SESSION_STATUS_RUNNING) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        listFigures(type).forEach { figure ->
                            Text(
                                figure.text(figures, type, strings)?.toString() ?: strings.common.missingValue,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        val heartRate = SessionFigure.AVERAGE_HEART_RATE.text(figures, type, strings)
                        Text(
                            heartRate?.let { "♥ ${it.value}" }.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(0.7f)
                                .semantics {
                                    if (heartRate != null) {
                                        contentDescription = "${strings.common.averageHeartRate} $heartRate"
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

private fun sessionStatusLabel(status: String, strings: Strings): String =
    when (status) {
        SESSION_STATUS_STOPPED -> strings.history.statusStopped
        SESSION_STATUS_RUNNING -> strings.history.statusRunning
        "interrupted" -> strings.history.statusInterrupted
        else -> status
    }

// ---- One session ----

/**
 * One recorded session on a single page: its figures, its charts, then everything that was
 * recorded as a list, what equipment and watch each measured, and the technical details.
 *
 * @param averages what the list knows of the session; shown until the [samples] are read.
 * @param chartKeys the charts the settings choose for this equipment type, in their order.
 */
@Composable
private fun HistoryDetail(
    strings: Strings,
    session: WorkoutSessionRecord,
    averages: SessionAverages?,
    sources: SensorSources,
    chartKeys: List<DashboardMetricKey>,
    samples: List<SessionSampleRecord>,
    samplesLoaded: Boolean,
) {
    val type = session.equipmentType
    val figures = remember(session, samples, averages) {
        if (samples.isEmpty()) sessionFigures(session, averages) else sessionFigures(session, samples)
    }
    val charts = remember(chartKeys, samples, type, sources, strings) {
        historyCharts(chartKeys, samples, type, sources, strings)
    }
    val valueRows = remember(figures, type, samples, sources, strings) {
        sessionValueRows(figures, type, samples, sources, strings)
    }
    val comparisons = remember(samples) {
        SourcedMetric.entries.mapNotNull { metric -> compareSources(samples, metric)?.let { metric to it } }
    }
    // All charts span the whole session and share the finger: one touch reads them all at that time.
    val timeRange = remember(samples) { 0.0..((samples.lastOrNull()?.offsetMillis ?: 0L) / 1000.0).coerceAtLeast(1.0) }
    val cursor = remember { ChartCursor() }
    var showValues by rememberSaveable { mutableStateOf(true) }
    var showSources by rememberSaveable { mutableStateOf(true) }
    var showTechnical by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "headline") { Headline(figures = figures, type = type, strings = strings) }
        charts.forEach { chart ->
            item(key = "chart-${chart.key.name}") {
                SessionChart(
                    chart = chart,
                    figures = figures,
                    cursor = cursor,
                    timeRange = timeRange,
                    strings = strings,
                )
            }
        }
        if (charts.isEmpty() && samplesLoaded) {
            item(key = "no-charts") {
                InfoCard {
                    Text(strings.history.noGraphSamplesStored, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(key = "values") {
            CollapsibleSection(strings.history.sectionAllValues, showValues, strings, onToggle = { showValues = !showValues }) {
                val otherColor = chartLineColor(ChartLineRole.OTHER_SOURCE)
                valueRows.forEach { row -> StatusRow(row.label, row.value.withBracketsIn(otherColor, row.bracketed)) }
            }
        }
        if (comparisons.isNotEmpty()) {
            item(key = "sources") {
                CollapsibleSection(strings.history.sourceComparison, showSources, strings, onToggle = { showSources = !showSources }) {
                    SourceComparisonTable(comparisons = comparisons, type = type, strings = strings)
                }
            }
        }
        item(key = "technical") {
            CollapsibleSection(strings.history.sectionTechnical, showTechnical, strings, onToggle = { showTechnical = !showTechnical }) {
                StatusRow(strings.common.status, sessionStatusLabel(session.finalStatus, strings))
                StatusRow(equipmentTypeLabel(type, strings), session.treadmillName.orMissing(strings))
                StatusRow(strings.common.garmin, session.garminName.orMissing(strings))
                StatusRow(strings.common.packets, session.packetCount.toString())
                StatusRow(strings.common.sends, strings.common.sendSummary(session.sendSuccessCount, session.sendFailureCount))
                if (session.lastError.isNotBlank()) {
                    StatusRow(strings.common.lastError, session.lastError)
                }
            }
        }
        item(key = "end") { VerticalGap() }
    }
}

/** A stored name, or the sign for a missing value where none was stored. */
private fun String?.orMissing(strings: Strings): String =
    this?.takeUnless { it.isBlank() } ?: strings.common.missingValue

/** The figures that sum the session up, three to a row, without a frame around them. */
@Composable
private fun Headline(figures: SessionFigures, type: EquipmentType, strings: Strings) {
    val shown = headlineFigures(type).mapNotNull { figure ->
        figure.text(figures, type, strings)?.let { figure.label(type, strings) to it }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        shown.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, text) ->
                    FigureBlock(text, label, MaterialTheme.typography.titleLarge, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A number with its unit set smaller next to it, and below it what the number is. */
@Composable
private fun FigureBlock(text: FigureText, label: String, valueStyle: TextStyle, modifier: Modifier = Modifier) {
    val unitStyle = SpanStyle(
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            buildAnnotatedString {
                append(text.value)
                if (text.unit.isNotEmpty()) {
                    append(" ")
                    withStyle(unitStyle) { append(text.unit) }
                }
            },
            style = valueStyle,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One value of the session over time, as a card like those of the dashboard. */
@Composable
private fun SessionChart(
    chart: HistoryChart,
    figures: SessionFigures,
    cursor: ChartCursor,
    timeRange: ClosedFloatingPointRange<Double>,
    strings: Strings,
) {
    val format = remember(chart.key, strings) { chart.key.chartFormat(strings) }
    InfoCard {
        Text(chart.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TimeSeriesChart(
            title = chart.title,
            lines = chart.lines,
            unit = chart.unit,
            strings = strings,
            format = format,
            reverseY = chart.key.reverseChartYAxis(),
            showStats = chart.key.chartShowsStats(),
            chartHeight = 150.dp,
            cursor = cursor,
            timeRange = timeRange,
            widestAxisLabel = WIDEST_AXIS_LABEL,
            recorded = true,
            // The mean of a pace over time is not the average pace; that one is time over distance.
            average = if (chart.key == DashboardMetricKey.PACE) averagePaceMinutes(figures) else null,
        )
    }
}

private fun averagePaceMinutes(figures: SessionFigures): Double? =
    averageSpeedKmh(figures.distanceM, figures.durationMillis)?.let { 60.0 / it }

/** A card under a title that opens and closes it. */
@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    strings: Strings,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    InfoCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    onClickLabel = if (expanded) strings.history.collapse else strings.history.expand,
                    role = Role.Button,
                    onClick = onToggle,
                ),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) BridgeUiIcons.ExpandLess else BridgeUiIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

/**
 * The averages of what equipment and watch both measured, side by side, with how far the watch is
 * above or below the equipment.
 */
@Composable
private fun SourceComparisonTable(
    comparisons: List<Pair<SourcedMetric, SourceComparison>>,
    type: EquipmentType,
    strings: Strings,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Text(
            strings.common.sourceEquipment,
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.8f),
        )
        Text(
            strings.common.sourceWatch,
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.3f),
        )
    }
    comparisons.forEach { (metric, comparison) ->
        val unit = sourcedMetricUnit(metric, type)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                sourcedMetricLabel(metric, strings, type),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            Text(
                strings.fmt("%.0f $unit", comparison.equipmentAverage),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.8f),
            )
            Text(
                comparison.differencePercent
                    ?.let { strings.fmt("%.0f $unit (%+.1f %%)", comparison.watchAverage, it) }
                    ?: strings.fmt("%.0f $unit", comparison.watchAverage),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1.3f),
            )
        }
    }
}
