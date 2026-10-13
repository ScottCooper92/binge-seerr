package com.cooper.binge.companion.seerr.seerr

import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.VersionStatus

/** The standard version's entry, or an unset one when the status carries none, which the contract reads the same. */
fun RequestStatus.standardVersion(): VersionStatus = versionsList.firstOrNull { !it.is4K } ?: VersionStatus.getDefaultInstance()

/** The 4K version's entry, or an unset one when the status carries none. */
fun RequestStatus.version4k(): VersionStatus = versionsList.firstOrNull { it.is4K } ?: VersionStatus.getDefaultInstance()
