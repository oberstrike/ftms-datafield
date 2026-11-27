package de.ma.ftms.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.runtime.TreadmillControlState
import de.ma.ftms.bridge.runtime.TreadmillSetting
import de.ma.ftms.bridge.runtime.TreadmillTargetStatus
import de.ma.ftms.bridge.runtime.WorkoutFollowState
import de.ma.ftms.bridge.runtime.isLargeSpeedIncrease
import de.ma.ftms.bridge.runtime.unitsPerOne
import java.util.Locale
import kotlin.math.abs

/**
 * Speed and incline of the connected treadmill: what it reports, steps up and down, and the quick
 * choices from the settings. It works on a moving belt whether or not a session runs; a belt that
 * stands is started on the console, never from here.
 *
 * The speed has two step sizes, the smallest the treadmill takes and [speedStep] from the
 * settings; the incline has the treadmill's own step.
 *
 * @param follow the workout on the watch, shown while the app sets its speeds.
 * @param confirmLargeIncrease whether a quick choice far above the current speed asks first.
 */
@Composable
internal fun TreadmillControlCard(
    control: TreadmillControlState,
    follow: WorkoutFollowState,
    speedPresets: List<Int>,
    inclinePresets: List<Int>,
    speedStep: Int,
    confirmLargeIncrease: Boolean,
    strings: Strings,
    onSet: (TreadmillSetting, Int) -> Unit,
    onStep: (TreadmillSetting, Int) -> Unit,
) {
    val locale = strings.locale
    // The speed a quick choice is to set once the question about it is answered with yes.
    var speedToConfirm by rememberSaveable { mutableStateOf<Int?>(null) }
    val shown = TreadmillSetting.entries.filter { control.of(it).supported }

    InfoCard {
        Text(
            strings.dashboard.treadmillControl,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (follow.active) {
            Text(
                workoutFollowLine(follow, strings),
                style = MaterialTheme.typography.bodyMedium,
                color = if (follow.sending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        shown.forEachIndexed { index, setting ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            val fine = control.fineStep(setting)
            TreadmillSettingBlock(
                control = control,
                setting = setting,
                steps = when (setting) {
                    TreadmillSetting.SPEED -> listOf(fine, speedStep).filter { it >= fine }.distinct().sorted()
                    TreadmillSetting.INCLINE -> listOf(fine)
                },
                presets = when (setting) {
                    TreadmillSetting.SPEED -> speedPresets
                    TreadmillSetting.INCLINE -> inclinePresets
                },
                strings = strings,
                onStep = { delta -> onStep(setting, delta) },
                onPreset = { value ->
                    val asksFirst = setting == TreadmillSetting.SPEED &&
                        confirmLargeIncrease &&
                        isLargeSpeedIncrease(control.speed.measured, value)
                    if (asksFirst) speedToConfirm = value else onSet(setting, value)
                },
            )
        }
        TreadmillStatusLine(
            control = control,
            shown = shown,
            noPresets = speedPresets.isEmpty() && inclinePresets.isEmpty(),
            strings = strings,
        )
    }

    val confirming = speedToConfirm
    if (confirming != null && control.canSet) {
        ConfirmActionDialog(
            title = strings.dashboard.treadmillConfirmTitle(
                formatTreadmillValue(TreadmillSetting.SPEED, confirming, locale),
            ),
            text = strings.dashboard.treadmillConfirmText(
                formatTreadmillValue(TreadmillSetting.SPEED, control.speed.measured ?: 0, locale),
            ),
            confirmLabel = strings.dashboard.treadmillConfirmAction,
            onConfirm = {
                speedToConfirm = null
                onSet(TreadmillSetting.SPEED, confirming)
            },
            onDismiss = { speedToConfirm = null },
        )
    }
}

/** One setting: its name and value, the target while the treadmill is on its way there, steps and quick choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreadmillSettingBlock(
    control: TreadmillControlState,
    setting: TreadmillSetting,
    steps: List<Int>,
    presets: List<Int>,
    strings: Strings,
    onStep: (Int) -> Unit,
    onPreset: (Int) -> Unit,
) {
    val locale = strings.locale
    val state = control.of(setting)
    val canSet = control.canSet
    val target = state.target?.takeIf { it != state.measured }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (setting) {
                        TreadmillSetting.SPEED -> strings.common.speed
                        TreadmillSetting.INCLINE -> strings.common.incline
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    state.measured?.let { formatTreadmillValue(setting, it, locale) } ?: strings.common.missingValue,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (target != null) {
                Text(
                    strings.dashboard.treadmillTarget(formatTreadmillValue(setting, target, locale)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            (steps.reversed().map { -it } + steps).forEach { delta ->
                val amount = formatTreadmillValue(setting, abs(delta), locale, compact = true)
                val description = when (setting) {
                    TreadmillSetting.SPEED ->
                        if (delta < 0) strings.dashboard.treadmillSpeedDecrease(amount) else strings.dashboard.treadmillSpeedIncrease(amount)
                    TreadmillSetting.INCLINE ->
                        if (delta < 0) strings.dashboard.treadmillInclineDecrease(amount) else strings.dashboard.treadmillInclineIncrease(amount)
                }
                OutlinedButton(
                    enabled = canSet,
                    onClick = { onStep(delta) },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = description },
                ) {
                    val number = formatTreadmillNumber(setting, abs(delta), locale, compact = true)
                    Text(if (delta < 0) "−$number" else "+$number", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEach { value ->
                    FilterChip(
                        selected = (state.target ?: state.measured) == value,
                        // A quick choice the treadmill does not take as it is would set another value.
                        enabled = canSet && control.acceptedValue(setting, value) == value,
                        onClick = { onPreset(value) },
                        label = { Text(formatTreadmillValue(setting, value, locale, compact = true)) },
                    )
                }
            }
        }
    }
}

/** One quiet line: why nothing can be set, what happened to the last value, or what the treadmill takes. */
@Composable
private fun TreadmillStatusLine(
    control: TreadmillControlState,
    shown: List<TreadmillSetting>,
    noPresets: Boolean,
    strings: Strings,
) {
    val locale = strings.locale
    val statuses = shown.map { control.of(it).status }
    val failed = TreadmillTargetStatus.FAILED in statuses || TreadmillTargetStatus.REJECTED in statuses
    val text = when {
        !control.beltMoving -> strings.dashboard.treadmillBeltStopped
        TreadmillTargetStatus.FAILED in statuses -> strings.dashboard.treadmillFailed
        TreadmillTargetStatus.REJECTED in statuses -> strings.dashboard.treadmillRejected
        control.speed.ramping && control.speed.target != null -> strings.dashboard.treadmillRamping(
            formatTreadmillValue(TreadmillSetting.SPEED, control.speed.target, locale),
        )
        TreadmillTargetStatus.SENDING in statuses -> strings.dashboard.treadmillSending
        else -> {
            val ranges = shown.mapNotNull { setting ->
                control.of(setting).range?.let { range ->
                    val min = formatTreadmillNumber(setting, range.min, locale, compact = true)
                    "$min–${formatTreadmillValue(setting, range.max, locale, compact = true)}"
                }
            }
            listOfNotNull(
                ranges.takeIf { it.isNotEmpty() }?.let(strings.dashboard::treadmillAccepts),
                strings.dashboard.treadmillNoPresets.takeIf { noPresets },
            ).joinToString(" ")
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text,
            color = if (failed && control.beltMoving) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The workout on the watch in one line, followed or only shown: the speed of the step the watch is in and, if the
 * step ends by time and another speed follows, when that comes.
 */
internal fun workoutFollowLine(follow: WorkoutFollowState, strings: Strings): String {
    val locale = strings.locale
    val now = follow.currentSpeed
        ?.let { strings.dashboard.treadmillFollowNow(formatTreadmillValue(TreadmillSetting.SPEED, it, locale)) }
        ?: strings.dashboard.treadmillFollowNowFree
    val next = if (follow.secondsLeft != null && follow.nextSpeed != null) {
        strings.dashboard.treadmillFollowNext(
            formatMinutesSeconds(follow.secondsLeft),
            formatTreadmillValue(TreadmillSetting.SPEED, follow.nextSpeed, locale),
        )
    } else {
        null
    }
    val title = if (follow.sending) strings.dashboard.treadmillFollowing else strings.dashboard.treadmillWatchWorkout
    return listOfNotNull("$title: $now", next).joinToString(" · ")
}

/** [seconds] as minutes and seconds, as in "1:05". */
internal fun formatMinutesSeconds(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * [value] of [setting] as a number in km/h or percent. A speed has one decimal, an incline one
 * only where it is not whole; [compact] leaves a decimal of zero out of a speed too, for a label
 * that has to be short.
 */
internal fun formatTreadmillNumber(setting: TreadmillSetting, value: Int, locale: Locale, compact: Boolean = false): String {
    val unitsPerOne = setting.unitsPerOne
    val whole = value % unitsPerOne == 0
    val decimals = when {
        setting == TreadmillSetting.SPEED && value % 10 != 0 -> 2
        whole && (compact || setting == TreadmillSetting.INCLINE) -> 0
        else -> 1
    }
    return String.format(locale, "%.${decimals}f", value / unitsPerOne.toDouble())
}

/** [value] of [setting] with its unit, as in "8,5 km/h" or "2 %". */
internal fun formatTreadmillValue(setting: TreadmillSetting, value: Int, locale: Locale, compact: Boolean = false): String {
    val unit = when (setting) {
        TreadmillSetting.SPEED -> "km/h"
        TreadmillSetting.INCLINE -> "%"
    }
    return "${formatTreadmillNumber(setting, value, locale, compact)} $unit"
}
