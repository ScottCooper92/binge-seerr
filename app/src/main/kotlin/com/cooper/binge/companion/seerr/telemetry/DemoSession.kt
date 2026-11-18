package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.demo.isDemo
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.homelab.core.telemetry.AnalyticsDecorator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the saved connection is the demo, which [DemoAnalyticsDecorator] uses to mark what analytics reports. */
interface DemoSession {
    val isDemo: Boolean

    /** Suspends until the saved connection has been read, so a screen held from a cold start is marked correctly. */
    suspend fun awaitRead()
}

/** [DemoSession] followed from the saved connection. */
@Singleton
class SavedConnectionDemoSession
    @Inject
    constructor(
        store: CredentialStore,
        @ApplicationScope scope: CoroutineScope,
    ) : DemoSession {
        @Volatile
        override var isDemo: Boolean = false
            private set

        private val read = MutableStateFlow(false)

        init {
            scope.launch {
                store.credentials.collect { saved ->
                    isDemo = saved?.isDemo == true
                    read.value = true
                }
            }
        }

        override suspend fun awaitRead() {
            read.first { it }
        }
    }

/** Marks what analytics reports with `demo=true` while the saved connection is the demo, so those sessions can be left out. */
class DemoAnalyticsDecorator(
    private val demo: DemoSession,
) : AnalyticsDecorator {
    override suspend fun awaitReady() = demo.awaitRead()

    override fun decorate(properties: Map<String, Any>): Map<String, Any> =
        if (demo.isDemo) properties + (DEMO_PROPERTY to true) else properties

    private companion object {
        const val DEMO_PROPERTY = "demo"
    }
}
