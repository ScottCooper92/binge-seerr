package com.cooper.binge.companion.seerr.demo

/**
 * The titles that stand for each state of the request sheet in the demo, for the "Sheet states" list. The names and ids are the
 * demo's own, kept in step with [DEMO_TITLES] by a test, so a title that is renamed or moved cannot leave the list pointing at
 * another one.
 */
enum class DemoSheetState(
    val tmdbId: Int,
    val isMovie: Boolean,
    val title: String,
) {
    NotRequested(900_001, true, "Elephants Dream"),
    PendingOthers(45745, true, "Sintel"),
    PendingOwn(653, true, "Nosferatu"),
    ApprovedWaiting(10378, true, "Big Buck Bunny"),
    Downloading(63639, false, "The Expanse"),
    QueuedAndPaused(900_002, true, "Caminandes"),
    PartlyAvailable(95396, false, "Severance"),
    AvailableWithWatch(19, true, "Metropolis"),
    AvailableWithPending4k(129, true, "Spirited Away"),
    SeveralRequests(900_003, false, "Dune"),
    Declined(133701, true, "Tears of Steel"),
    Failed(603, true, "The Matrix"),
    OpenIssue(10331, true, "Night of the Living Dead"),
}
