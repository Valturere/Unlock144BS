package dev.leonid.unlock144bs.autofix

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoFixMonitoringStateMachineTest {
    private val monitoring = AutoFixMonitoringStateMachine()

    @Test
    fun `service start while screen is off stays suspended`() {
        assertEquals(MonitoringCommand.STOP_LOOP, monitoring.enable(screenInteractive = false))
        assertEquals(MonitoringState.SCREEN_OFF_SUSPENDED, monitoring.state)
    }

    @Test
    fun `screen on resumes suspended monitoring once`() {
        monitoring.enable(screenInteractive = false)

        assertEquals(MonitoringCommand.START_LOOP, monitoring.onScreenOn())
        assertEquals(MonitoringCommand.NONE, monitoring.onScreenOn())
        assertEquals(MonitoringState.RUNNING, monitoring.state)
    }

    @Test
    fun `screen off stops running monitoring once`() {
        monitoring.enable(screenInteractive = true)

        assertEquals(MonitoringCommand.STOP_LOOP, monitoring.onScreenOff())
        assertEquals(MonitoringCommand.NONE, monitoring.onScreenOff())
        assertEquals(MonitoringState.SCREEN_OFF_SUSPENDED, monitoring.state)
    }

    @Test
    fun `user present checks now without starting a second loop`() {
        monitoring.enable(screenInteractive = true)

        assertEquals(
            MonitoringCommand.CHECK_NOW,
            monitoring.onUserPresent(screenInteractive = true),
        )
        assertEquals(MonitoringState.RUNNING, monitoring.state)
    }

    @Test
    fun `user present can recover a missed screen on event`() {
        monitoring.enable(screenInteractive = false)

        assertEquals(
            MonitoringCommand.START_LOOP,
            monitoring.onUserPresent(screenInteractive = true),
        )
        assertEquals(MonitoringState.RUNNING, monitoring.state)
    }

    @Test
    fun `disabled monitoring ignores all screen events`() {
        assertEquals(MonitoringCommand.NONE, monitoring.onScreenOff())
        assertEquals(MonitoringCommand.NONE, monitoring.onScreenOn())
        assertEquals(
            MonitoringCommand.NONE,
            monitoring.onUserPresent(screenInteractive = true),
        )
        assertEquals(MonitoringState.DISABLED, monitoring.state)
    }

    @Test
    fun `disable prevents later screen on from restarting monitoring`() {
        monitoring.enable(screenInteractive = true)
        assertEquals(MonitoringCommand.STOP_LOOP, monitoring.disable())

        assertEquals(MonitoringCommand.NONE, monitoring.onScreenOn())
        assertEquals(MonitoringState.DISABLED, monitoring.state)
    }
}
