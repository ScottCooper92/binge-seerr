package com.cooper.binge.companion.seerr.util

import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.DownloadProgress
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.VersionStatus
import com.cooper.binge.companion.seerr.seerr.VERSION_ID_4K

/** A status whose standard version is [availability], with a 4K entry only when [availability4k] is given. */
fun requestStatusOf(
    availability: Availability,
    availability4k: Availability? = null,
    download: DownloadProgress? = null,
): RequestStatus {
    val standard = VersionStatus.newBuilder().setAvailability(availability)
    download?.let(standard::setDownload)
    val builder = RequestStatus.newBuilder().addVersions(standard)
    availability4k?.let { builder.addVersions(VersionStatus.newBuilder().setVersionId(VERSION_ID_4K).setAvailability(it)) }
    return builder.build()
}
