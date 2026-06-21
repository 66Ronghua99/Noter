package com.cory.noter.di

import androidx.test.core.app.ApplicationProvider
import com.cory.noter.ai.WorkManagerAiCreateBackgroundScheduler
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {
    @Test
    fun `container exposes stable dependency graph for startup surfaces`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        val firstAlarmRepository = container.alarmRepository
        val secondAlarmRepository = container.alarmRepository
        val firstSettingsRepository = container.settingsRepository
        val secondSettingsRepository = container.settingsRepository
        val firstScheduler = container.alarmScheduler
        val secondScheduler = container.alarmScheduler
        val firstAgentClient = container.openRouterAgentClient
        val secondAgentClient = container.openRouterAgentClient
        val firstAgentLoopRunner = container.agentLoopRunner
        val secondAgentLoopRunner = container.agentLoopRunner
        val firstReconciliation = container.startupReconciliation
        val secondReconciliation = container.startupReconciliation

        assertThat(firstAlarmRepository === secondAlarmRepository).isTrue()
        assertThat(firstSettingsRepository === secondSettingsRepository).isTrue()
        assertThat(firstScheduler === secondScheduler).isTrue()
        assertThat(firstAgentClient === secondAgentClient).isTrue()
        assertThat(firstAgentLoopRunner === secondAgentLoopRunner).isTrue()
        assertThat(firstReconciliation === secondReconciliation).isTrue()
        assertThat(container.aiAlarmCreator === container.aiAlarmCreator).isTrue()
        assertThat(container.aiCreateBackgroundScheduler)
            .isInstanceOf(WorkManagerAiCreateBackgroundScheduler::class.java)
        assertThat(container.alarmRingingCoordinator === container.alarmRingingCoordinator).isTrue()

        val creatorRunnerField = container.aiAlarmCreator.javaClass.getDeclaredField("agentLoopRunner")
        creatorRunnerField.isAccessible = true
        assertThat(creatorRunnerField.get(container.aiAlarmCreator)).isSameInstanceAs(firstAgentLoopRunner)

        val runnerGatewayField = firstAgentLoopRunner.javaClass.getDeclaredField("gateway")
        runnerGatewayField.isAccessible = true
        assertThat(runnerGatewayField.get(firstAgentLoopRunner)).isSameInstanceAs(firstAgentClient)
    }
}
