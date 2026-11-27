package de.ma.ftms.bridge.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.ma.ftms.bridge.R
import de.ma.ftms.bridge.i18n.Strings
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.TreadmillControlState
import de.ma.ftms.bridge.runtime.TreadmillSetting
import de.ma.ftms.bridge.runtime.WORKOUT_SCREEN_FIELD_OPTIONS
import de.ma.ftms.bridge.runtime.WorkoutFollowState
import de.ma.ftms.bridge.runtime.WorkoutStepKind
import de.ma.ftms.bridge.runtime.workoutScreenFieldsWith
import de.ma.ftms.core.SmoothedTreadmillSample
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Opens the workout screen by hand, from wherever the app says that a workout runs on the watch.
 * Provided by [FtmsBridgeApp].
 */
internal val LocalWorkoutScreenOpener = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The workout on the watch while it runs, for a phone that lies on its side in front of the
 * treadmill: read from a distance and in motion, so it shows little and shows it large.
 *
 * On the left the step the watch is in: a ring that fills as the step passes, with the time that
 * is left in it and the speed the step asks for, and below it the step that follows. A step that
 * does not end by time has a dashed ring and counts up. While the watch is in no workout, the
 * ring is dashed too and shows the time of the session. On the right four values; a tap on one
 * opens the choice of what it shows.
 *
 * Where the app may steer the treadmill, the belt is set from here too: minus and plus for the
 * speed in the corners next to the ring, and arrows for the incline in the field that shows it.
 * A tap is the treadmill's smallest step, and a key that is held goes on stepping.
 *
 * The screen has its own dark colours, whatever the theme of the app: it is looked at for the
 * length of a run, often in a dim room, and a run is told from a recovery by colour.
 *
 * All sizes follow the height of the screen, so that the layout is the same on every phone; the
 * font size set in the system does not change them, as there is no room to give.
 */
@Composable
internal fun WorkoutActiveScreen(
    follow: WorkoutFollowState,
    sample: SmoothedTreadmillSample?,
    activeDurationMillis: Long,
    sessionPaused: Boolean,
    type: EquipmentType,
    fields: List<DashboardMetricKey>,
    control: TreadmillControlState,
    steering: Boolean,
    strings: Strings,
    onFieldsChanged: (List<DashboardMetricKey>) -> Unit,
    onStepTreadmill: (TreadmillSetting, Int) -> Unit,
    onClose: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    // The field the choice is for, kept while the choice slides out.
    var pickedField by remember { mutableIntStateOf(0) }
    picking?.let { pickedField = it }
    val speedKeys = workoutKeys(TreadmillSetting.SPEED, steering, control)
    val inclineKeys = workoutKeys(TreadmillSetting.INCLINE, steering, control)
    val reading = { key: DashboardMetricKey ->
        // An incline that can be set here is shown as the control knows it, level included.
        workoutInclineFromControl(sample, control, strings)?.takeIf { key == DashboardMetricKey.INCLINE && inclineKeys != null }
            ?: key.workoutFieldReading(sample, strings, type, activeDurationMillis)
    }

    BackHandler {
        if (picking != null) picking = null else onClose()
    }

    CompositionLocalProvider(LocalContentColor provides WorkoutColors.Text) {
        Box(modifier = Modifier.fillMaxSize().background(WorkoutColors.Ground)) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(SCREEN_PADDING),
            ) {
                // The ring and the card below it share the height in the proportion of the design.
                val ring = min((maxHeight - COLUMN_GAP) * (DESIGN_RING / (DESIGN_RING + DESIGN_NEXT_CARD)), maxWidth * 0.4f)
                    .coerceAtLeast(120.dp)
                CompositionLocalProvider(LocalWorkoutScale provides ring / DESIGN_RING.dp) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(
                            modifier = Modifier.width(ring + scaled(30)).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(COLUMN_GAP),
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(ring)) {
                                StepRing(
                                    follow = follow,
                                    activeDurationMillis = activeDurationMillis,
                                    sessionPaused = sessionPaused,
                                    handSpeed = workoutHandSpeed(follow, control),
                                    strings = strings,
                                    modifier = Modifier.size(ring).align(Alignment.Center),
                                )
                                if (speedKeys != null) {
                                    // In the corners the ring leaves free and far apart: to be
                                    // hit while running, and not one for the other.
                                    TreadmillKey(
                                        setting = TreadmillSetting.SPEED,
                                        delta = -speedKeys.step,
                                        icon = BridgeUiIcons.Remove,
                                        keys = speedKeys,
                                        control = control,
                                        strings = strings,
                                        onStep = onStepTreadmill,
                                        modifier = Modifier.align(Alignment.BottomStart),
                                    )
                                    TreadmillKey(
                                        setting = TreadmillSetting.SPEED,
                                        delta = speedKeys.step,
                                        icon = BridgeUiIcons.Add,
                                        keys = speedKeys,
                                        control = control,
                                        strings = strings,
                                        onStep = onStepTreadmill,
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                    )
                                }
                            }
                            NextStepCard(follow = follow, strings = strings, modifier = Modifier.fillMaxWidth().weight(1f))
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            fields.chunked(FIELD_COLUMNS).forEachIndexed { row, keys ->
                                Row(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    keys.forEachIndexed { column, key ->
                                        FieldCard(
                                            label = key.label(strings, type),
                                            reading = reading(key),
                                            strings = strings,
                                            onClick = { picking = row * FIELD_COLUMNS + column },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            stepper = inclineKeys?.takeIf { key == DashboardMetricKey.INCLINE }?.let { keys ->
                                                { InclineStepper(keys = keys, control = control, strings = strings, onStep = onStepTreadmill) }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    CloseButton(strings = strings, onClose = onClose, modifier = Modifier.align(Alignment.TopStart))
                }
            }

            AnimatedVisibility(visible = picking != null, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WorkoutColors.Scrim)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = strings.workout.closePicker,
                            onClick = { picking = null },
                        ),
                )
            }
            AnimatedVisibility(
                visible = picking != null,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                FieldPicker(
                    fieldNumber = pickedField + 1,
                    selected = fields.getOrNull(pickedField),
                    type = type,
                    strings = strings,
                    reading = reading,
                    onPick = { key ->
                        onFieldsChanged(workoutScreenFieldsWith(fields, pickedField, key))
                        picking = null
                    },
                )
            }
        }
    }
}

/**
 * Gives the screen the whole display: the system bars go, and come back with a swipe from the
 * edge. Opened by hand, the screen also turns the display on its side, whatever the system's
 * rotation lock says; that is undone when the screen leaves.
 */
@Composable
internal fun WorkoutScreenWindow(forceLandscape: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(activity, forceLandscape) {
        if (forceLandscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            // Turning the display builds the activity anew, and with it this screen. Giving the
            // rotation back at that moment would turn the display upright again, and so on.
            if (forceLandscape && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

@Composable
private fun StepRing(
    follow: WorkoutFollowState,
    activeDurationMillis: Long,
    sessionPaused: Boolean,
    handSpeed: Int?,
    strings: Strings,
    modifier: Modifier = Modifier,
) {
    val color = follow.currentKind.color()
    val progress = workoutStepProgress(follow)
    // A new step starts with an empty ring instead of running back from a full one.
    val shown = key(follow.stepNumber) {
        animateFloatAsState(
            targetValue = progress ?: 0f,
            animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
            label = "step",
        ).value
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.minDimension / DESIGN_RING
            val inset = unit * 12f
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            drawArc(
                color = WorkoutColors.Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = unit * 14f,
                    // Without an end by time there is nothing to fill: the ring is dashed.
                    pathEffect = if (progress == null) PathEffect.dashPathEffect(floatArrayOf(unit * 3f, unit * 13f)) else null,
                ),
            )
            if (progress != null && shown > 0.002f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * shown,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = unit * 14f, cap = StrokeCap.Round),
                )
            }
        }
        Column(
            // A little below the middle: the title above the time is narrower than what is below it.
            modifier = Modifier.fillMaxWidth(0.8f).offset(y = scaled(6)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(scaled(2)),
        ) {
            // This high up the ring leaves less room than further down: a long title gets smaller.
            Label(
                text = workoutRingTitle(follow, sessionPaused, strings),
                color = if (follow.paused || (!follow.active && sessionPaused)) WorkoutColors.Label else color,
                modifier = Modifier.fillMaxWidth(0.85f),
                textAlign = TextAlign.Center,
                shrinkTo = scaled(8),
            )
            Numeral(
                text = workoutRingClock(follow, activeDurationMillis),
                size = scaled(100),
                shrinkTo = scaled(48),
                height = scaled(84),
            )
            if (follow.active && follow.secondsLeft == null) {
                Text(
                    text = if (follow.endsWithLapKey) strings.workout.endsWithLapKey else strings.workout.endsByWatch,
                    style = plainStyle(scaled(13), FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                )
            }
            val speed = follow.currentSpeed.takeIf { follow.active }
            when {
                // What was asked for by hand counts until the next step, so it is what stands here.
                handSpeed != null -> SpeedLine(name = strings.workout.byHand, speed = handSpeed, strings = strings, byHand = true)
                !follow.active -> Small(text = strings.workout.noWorkout, textAlign = TextAlign.Center)
                speed != null -> SpeedLine(name = strings.workout.target, speed = speed, strings = strings)
                else -> Small(
                    text = if (follow.secondsLeft == null) strings.workout.noPaceBeltStays else strings.workout.noPace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A speed with what it is: the one the step asks for, or the one that was set by hand. */
@Composable
private fun SpeedLine(name: String, speed: Int, strings: Strings, byHand: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(scaled(5))) {
        Small(
            text = name,
            modifier = Modifier.alignByBaseline(),
            color = if (byHand) WorkoutColors.Text else WorkoutColors.Label,
        )
        Numeral(text = formatWorkoutSpeed(speed, strings), size = scaled(30), modifier = Modifier.alignByBaseline())
        Small(text = "km/h", modifier = Modifier.alignByBaseline())
    }
}

/**
 * A key that moves [setting] of the treadmill by [delta]. A tap is one step. Held, it goes on
 * stepping after a moment, and the steps add up: each starts from what was asked for before, not
 * from what the belt has reached. How far one hold goes is said by [heldKeyStepsOn].
 */
@Composable
private fun TreadmillKey(
    setting: TreadmillSetting,
    delta: Int,
    icon: ImageVector,
    keys: WorkoutKeys,
    control: TreadmillControlState,
    strings: Strings,
    onStep: (TreadmillSetting, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val currentControl by rememberUpdatedState(control)
    val currentOnStep by rememberUpdatedState(onStep)
    // Set once holding the key has made steps: letting go is then not one more.
    val steppedWhileHeld = remember { booleanArrayOf(false) }
    LaunchedEffect(pressed, keys.enabled) {
        if (!pressed || !keys.enabled) {
            steppedWhileHeld[0] = false
            return@LaunchedEffect
        }
        val startedFrom = currentControl.steppingBase(setting)
        delay(HOLD_BEFORE_REPEAT_MILLIS)
        while (heldKeyStepsOn(setting, delta, startedFrom, currentControl.steppingBase(setting))) {
            steppedWhileHeld[0] = true
            currentOnStep(setting, delta)
            delay(setting.holdRepeatMillis)
        }
    }

    val amount = formatTreadmillValue(setting, abs(delta), strings.locale, compact = true)
    val description = when (setting) {
        TreadmillSetting.SPEED ->
            if (delta < 0) strings.dashboard.treadmillSpeedDecrease(amount) else strings.dashboard.treadmillSpeedIncrease(amount)
        TreadmillSetting.INCLINE ->
            if (delta < 0) strings.dashboard.treadmillInclineDecrease(amount) else strings.dashboard.treadmillInclineIncrease(amount)
    }
    Box(
        modifier = modifier
            .size(max(scaled(52), 48.dp))
            .clip(CircleShape)
            .background(if (pressed && keys.enabled) WorkoutColors.KeyPressed else WorkoutColors.Track)
            // Never switched off as a target of taps: a tap on a key that does nothing right now
            // must not reach the field below it and open the choice of values.
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                when {
                    !keys.enabled -> Unit
                    steppedWhileHeld[0] -> steppedWhileHeld[0] = false
                    else -> onStep(setting, delta)
                }
            }
            .semantics { if (!keys.enabled) disabled() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (keys.enabled) WorkoutColors.Text else WorkoutColors.Label.copy(alpha = 0.45f),
            modifier = Modifier.size(scaled(24)),
        )
    }
}

/**
 * The keys of the field that shows the incline: up and down, and between them the incline that
 * was asked for while the treadmill is still on its way there.
 */
@Composable
private fun InclineStepper(
    keys: WorkoutKeys,
    control: TreadmillControlState,
    strings: Strings,
    onStep: (TreadmillSetting, Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxHeight().padding(top = scaled(8), end = scaled(8), bottom = scaled(8)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TreadmillKey(
            setting = TreadmillSetting.INCLINE,
            delta = keys.step,
            icon = BridgeUiIcons.ArrowUp,
            keys = keys,
            control = control,
            strings = strings,
            onStep = onStep,
        )
        workoutInclineOnItsWay(control)?.let { asked ->
            Numeral(
                text = formatTreadmillValue(TreadmillSetting.INCLINE, asked, strings.locale, compact = true),
                size = scaled(20),
            )
        }
        TreadmillKey(
            setting = TreadmillSetting.INCLINE,
            delta = -keys.step,
            icon = BridgeUiIcons.ArrowDown,
            keys = keys,
            control = control,
            strings = strings,
            onStep = onStep,
        )
    }
}

@Composable
private fun NextStepCard(follow: WorkoutFollowState, strings: Strings, modifier: Modifier = Modifier) {
    val title = workoutNextTitle(follow, strings)
    if (!follow.active) {
        // No workout, no next step: the card says what would stand here.
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(scaled(18)))
                .background(WorkoutColors.Card)
                .padding(horizontal = scaled(16)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Small(text = strings.workout.stepsAppearHere)
        }
        return
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(scaled(18)))
            .background(if (follow.speedingUpAhead) WorkoutColors.Ahead else WorkoutColors.Card)
            .padding(horizontal = scaled(16)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(scaled(8)),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(scaled(1))) {
            Small(text = if (follow.speedingUpAhead) strings.workout.nextAhead else strings.workout.next)
            if (title != null) {
                Text(
                    text = title,
                    style = plainStyle(scaled(15), FontWeight.SemiBold),
                    color = follow.nextKind.color(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(text = strings.workout.noNext, style = plainStyle(scaled(13), FontWeight.SemiBold), maxLines = 2)
            }
        }
        val speed = follow.nextSpeed
        when {
            title == null -> Unit
            // Number and unit share a baseline; together they stand in the middle of the card.
            speed != null -> Row(horizontalArrangement = Arrangement.spacedBy(scaled(8))) {
                Numeral(text = formatWorkoutSpeed(speed, strings), size = scaled(34), modifier = Modifier.alignByBaseline())
                Small(text = "km/h", modifier = Modifier.alignByBaseline())
            }
            else -> Small(text = strings.workout.noPace)
        }
    }
}

@Composable
private fun FieldCard(
    label: String,
    reading: WorkoutFieldReading,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stepper: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(scaled(20)))
            .background(WorkoutColors.Card)
            .clickable(onClickLabel = strings.workout.chooseValue, role = Role.Button, onClick = onClick),
    ) {
        FieldReading(
            label = label,
            reading = reading,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = scaled(18), end = scaled(if (stepper == null) 18 else 8), top = scaled(14), bottom = scaled(14)),
        )
        stepper?.invoke()
    }
}

@Composable
private fun FieldReading(label: String, reading: WorkoutFieldReading, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(scaled(6))) {
            Label(text = label, color = WorkoutColors.Label, modifier = Modifier.weight(1f))
            Icon(
                imageVector = BridgeUiIcons.ExpandMore,
                contentDescription = null,
                tint = WorkoutColors.Label,
                modifier = Modifier.size(scaled(18)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(scaled(6))) {
            Numeral(
                text = reading.value,
                size = scaled(88),
                shrinkTo = scaled(40),
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            )
            if (reading.unit.isNotEmpty()) {
                Text(
                    text = reading.unit,
                    style = plainStyle(scaled(15), FontWeight.Normal),
                    color = WorkoutColors.Label,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

@Composable
private fun CloseButton(strings: Strings, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(max(scaled(44), 48.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(WorkoutColors.Card)
            .clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BridgeUiIcons.Close,
            contentDescription = strings.workout.close,
            tint = WorkoutColors.Text,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The choice of what a field shows: every value on offer with what it reads right now. */
@Composable
private fun FieldPicker(
    fieldNumber: Int,
    selected: DashboardMetricKey?,
    type: EquipmentType,
    strings: Strings,
    reading: (DashboardMetricKey) -> WorkoutFieldReading,
    onPick: (DashboardMetricKey) -> Unit,
) {
    // A surface keeps taps on it from reaching what lies below, which would close the choice.
    Surface(
        color = WorkoutColors.Sheet,
        contentColor = WorkoutColors.Text,
        modifier = Modifier.fillMaxHeight().fillMaxWidth(0.48f),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.workout.fieldNumber(fieldNumber),
                    style = plainStyle(16.dp, FontWeight.SemiBold),
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = strings.workout.fieldShows,
                    style = plainStyle(12.dp, FontWeight.Normal),
                    color = WorkoutColors.Label,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val rows = WORKOUT_SCREEN_FIELD_OPTIONS.chunked(FIELD_COLUMNS)
                // The rows share the height; where that is too little for a finger, they scroll.
                val rowHeight = max(48.dp, (maxHeight - PICKER_GAP * (rows.size - 1)) / rows.size)
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(PICKER_GAP),
                ) {
                    rows.forEach { keys ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(rowHeight),
                            horizontalArrangement = Arrangement.spacedBy(PICKER_GAP),
                        ) {
                            keys.forEach { key ->
                                FieldOption(
                                    label = key.label(strings, type),
                                    value = reading(key).value,
                                    selected = key == selected,
                                    onPick = { onPick(key) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldOption(
    label: String,
    value: String,
    selected: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (selected) WorkoutColors.Ground else WorkoutColors.Text
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) WorkoutColors.Run else WorkoutColors.Track)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = plainStyle(14.dp, FontWeight.SemiBold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Numeral(text = value, size = 20.dp, color = content)
    }
}

/**
 * A number in the condensed face of the screen; with [shrinkTo] it gets smaller instead of
 * overflowing. A line of this face is a good deal higher than its digits; where that air is in
 * the way, [height] gives the number less room than its line, and the digits stand in its middle.
 */
@Composable
private fun Numeral(
    text: String,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    shrinkTo: Dp? = null,
    height: Dp? = null,
) {
    val style = TextStyle(
        fontFamily = NumeralFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = size.asTextSize(),
        lineHeight = 1.em,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    Text(
        text = text,
        modifier = if (height == null) {
            modifier
        } else {
            modifier.height(height).wrapContentHeight(align = DIGITS_IN_THE_MIDDLE, unbounded = true)
        },
        color = color,
        maxLines = 1,
        softWrap = false,
        style = style,
        autoSize = shrinkTo?.let {
            TextAutoSize.StepBased(minFontSize = it.asTextSize(), maxFontSize = size.asTextSize(), stepSize = 2.dp.asTextSize())
        },
    )
}

/**
 * The name of a value or a step: small capitals with air between the letters. With [shrinkTo] a
 * name that is too long gets smaller instead of being cut off.
 */
@Composable
private fun Label(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    shrinkTo: Dp? = null,
) {
    val size = scaled(12)
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        style = plainStyle(size, FontWeight.SemiBold).copy(letterSpacing = 0.08.em),
        autoSize = shrinkTo?.let {
            TextAutoSize.StepBased(minFontSize = it.asTextSize(), maxFontSize = size.asTextSize(), stepSize = 0.5.dp.asTextSize())
        },
    )
}

/** A unit or a word that goes with a number. */
@Composable
private fun Small(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    color: Color = WorkoutColors.Label,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        style = plainStyle(scaled(12), FontWeight.Normal),
    )
}

@Composable
private fun plainStyle(size: Dp, weight: FontWeight): TextStyle =
    TextStyle(
        fontSize = size.asTextSize(),
        fontWeight = weight,
        lineHeight = 1.25.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

/** [designPixels] of the design, which is 360 high, on this screen. */
@Composable
private fun scaled(designPixels: Int): Dp = designPixels.dp * LocalWorkoutScale.current

/** This length as a text size that the font size of the system does not change. */
@Composable
private fun Dp.asTextSize(): TextUnit = with(LocalDensity.current) { toSp() }

private fun WorkoutStepKind.color(): Color =
    when (this) {
        WorkoutStepKind.RUN -> WorkoutColors.Run
        WorkoutStepKind.RECOVERY -> WorkoutColors.Recovery
        else -> WorkoutColors.Text
    }

private object WorkoutColors {
    val Ground = Color(0xFF0B0F14)
    val Card = Color(0xFF141A22)
    val Track = Color(0xFF243040)
    val KeyPressed = Color(0xFF3A4C63)
    val Sheet = Color(0xFF1B232D)
    val Text = Color(0xFFF2F5F7)
    val Label = Color(0xFF9AA7B4)
    val Run = Color(0xFFC8F53C)
    val Recovery = Color(0xFF6FD3FF)
    val Ahead = Color(0xFF26331A)
    val Scrim = Color(0xB805080B)
}

private val NumeralFont = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))

private val LocalWorkoutScale = staticCompositionLocalOf { 1f }

// A line of the face has more air above its digits than below them, so a line that is higher
// than its room is not centred in it but stands a little lower: then the digits are.
private val DIGITS_IN_THE_MIDDLE = BiasAlignment.Vertical(0.28f)

// The design is drawn 780 by 360: a ring of 256 above a card of 68.
private const val DESIGN_RING = 256f
private const val DESIGN_NEXT_CARD = 68f
private const val FIELD_COLUMNS = 2
private val SCREEN_PADDING = 14.dp
private val COLUMN_GAP = 8.dp
private val PICKER_GAP = 8.dp
