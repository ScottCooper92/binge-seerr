package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrDownloadStatusDto
import com.cooper.binge.companion.seerr.seerr.downloadFraction
import com.cooper.binge.companion.seerr.seerr.isDownloading
import com.cooper.binge.companion.seerr.ui.state.DownloadActivity

/**
 * What the download client is doing for one version, from the queue Seerr reports for it. [queueTracked] is whether
 * an empty queue can be trusted to be empty; without it, an empty queue claims nothing.
 */
fun List<SeerrDownloadStatusDto>.downloadActivity(queueTracked: Boolean = false): DownloadActivity =
    when {
        isEmpty() -> if (queueTracked) DownloadActivity.None else DownloadActivity.Untracked
        isDownloading(downloadFraction()) -> DownloadActivity.Downloading
        else -> DownloadActivity.Queued
    }
