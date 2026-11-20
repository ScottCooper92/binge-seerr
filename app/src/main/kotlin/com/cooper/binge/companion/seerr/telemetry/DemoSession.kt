package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.demo.isDemo
import com.cooper.homelab.core.telemetry.DemoSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map

/**
 * The shared demo session, followed from this app's saved connection: `true` for the demo, `false` for a real server, and `null`
 * for nothing saved. The first answer, `null` included, counts as the read.
 */
fun CredentialStore.demoSession(scope: CoroutineScope): DemoSession = DemoSession(credentials.map { it?.isDemo }, scope)
