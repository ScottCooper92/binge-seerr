package com.cooper.binge.companion.seerr.seerr

import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.Version
import com.binge.companion.contracts.request.v1.VersionStatus

/** The standard version's id: empty, which is how a message that sets no id reads. */
const val VERSION_ID_STANDARD = ""

/** The id this companion gives the 4K version; the host echoes it back verbatim. */
const val VERSION_ID_4K = "4k"

private const val LABEL_4K = "4K"

fun versionIdOf(is4k: Boolean): String = if (is4k) VERSION_ID_4K else VERSION_ID_STANDARD

/**
 * The handshake's version list. An empty list means standard only, so it stays empty when 4K is not offered. A list
 * with 4K in it names the standard version too, first and with the empty id, so the host has a label for each.
 */
fun versionsFor(
    offers4k: Boolean,
    standardLabel: String,
): List<Version> =
    if (offers4k) {
        listOf(
            Version
                .newBuilder()
                .setId(VERSION_ID_STANDARD)
                .setLabel(standardLabel)
                .build(),
            Version
                .newBuilder()
                .setId(VERSION_ID_4K)
                .setLabel(LABEL_4K)
                .setHighResolution(true)
                .build(),
        )
    } else {
        emptyList()
    }

/** The standard version's entry, or an unset one when the status carries none, which the contract reads the same. */
fun RequestStatus.standardVersion(): VersionStatus =
    versionsList.firstOrNull { it.versionId == VERSION_ID_STANDARD } ?: VersionStatus.getDefaultInstance()

/** The 4K version's entry, or an unset one when the status carries none. */
fun RequestStatus.version4k(): VersionStatus =
    versionsList.firstOrNull { it.versionId == VERSION_ID_4K } ?: VersionStatus.getDefaultInstance()
