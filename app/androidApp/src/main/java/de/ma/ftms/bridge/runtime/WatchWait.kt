package de.ma.ftms.bridge.runtime

/**
 * True when the bridge should stay in the foreground service without a session and wait for the
 * activity on the watch.
 *
 * Android hands the watch's messages to this app only while its process is active: with the app
 * on screen or with a foreground service. Without one of them a session cannot start with the
 * activity. Waiting therefore keeps the foreground service, and with it a notification, from the
 * moment the app was opened until the user ends it there ([dismissed]) or the conditions for a
 * start by the watch no longer hold.
 */
internal fun isWatchWaitWanted(state: BridgeUiState, dismissed: Boolean): Boolean =
    state.settings.waitForWatch &&
        state.settings.followWatchTimer &&
        !dismissed &&
        state.permissionsGranted &&
        state.selectedTreadmillAddress != null &&
        state.selectedGarminId != null

/**
 * True when the foreground service has nothing left to stay for: no session runs or is still
 * being written, and the bridge does not wait for the watch ([waiting]).
 */
internal fun shouldStopBridgeForegroundService(
    running: Boolean,
    activeSessionStartedAtMillis: Long,
    waiting: Boolean = false,
): Boolean =
    !running && activeSessionStartedAtMillis <= 0 && !waiting
