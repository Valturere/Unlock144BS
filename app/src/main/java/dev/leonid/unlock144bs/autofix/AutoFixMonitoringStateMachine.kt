package dev.leonid.unlock144bs.autofix

enum class MonitoringState {
    DISABLED,
    RUNNING,
    SCREEN_OFF_SUSPENDED,
}

enum class MonitoringCommand {
    NONE,
    START_LOOP,
    STOP_LOOP,
    CHECK_NOW,
}

/**
 * Pure state holder for the service lifecycle. Scheduling remains owned by AutoFixService.
 */
class AutoFixMonitoringStateMachine {
    var state: MonitoringState = MonitoringState.DISABLED
        private set

    fun enable(screenInteractive: Boolean): MonitoringCommand {
        val nextState = if (screenInteractive) {
            MonitoringState.RUNNING
        } else {
            MonitoringState.SCREEN_OFF_SUSPENDED
        }
        if (state == nextState) return MonitoringCommand.NONE

        state = nextState
        return if (nextState == MonitoringState.RUNNING) {
            MonitoringCommand.START_LOOP
        } else {
            MonitoringCommand.STOP_LOOP
        }
    }

    fun onScreenOff(): MonitoringCommand {
        if (state != MonitoringState.RUNNING) return MonitoringCommand.NONE
        state = MonitoringState.SCREEN_OFF_SUSPENDED
        return MonitoringCommand.STOP_LOOP
    }

    fun onScreenOn(): MonitoringCommand {
        if (state != MonitoringState.SCREEN_OFF_SUSPENDED) return MonitoringCommand.NONE
        state = MonitoringState.RUNNING
        return MonitoringCommand.START_LOOP
    }

    fun onUserPresent(screenInteractive: Boolean): MonitoringCommand = when {
        state == MonitoringState.DISABLED -> MonitoringCommand.NONE
        !screenInteractive -> MonitoringCommand.NONE
        state == MonitoringState.SCREEN_OFF_SUSPENDED -> {
            state = MonitoringState.RUNNING
            MonitoringCommand.START_LOOP
        }
        else -> MonitoringCommand.CHECK_NOW
    }

    fun disable(): MonitoringCommand {
        if (state == MonitoringState.DISABLED) return MonitoringCommand.NONE
        state = MonitoringState.DISABLED
        return MonitoringCommand.STOP_LOOP
    }
}
