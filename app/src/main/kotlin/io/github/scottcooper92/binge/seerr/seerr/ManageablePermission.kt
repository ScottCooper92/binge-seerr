package io.github.scottcooper92.binge.seerr.seerr

/** How the editor groups the toggles; the order is the order shown. */
enum class PermissionGroup { Administration, Requests, Issues, Blocklist }

/** Which server a permission needs: every one, the blocklist's lineage, or one with the watchlist-era bits. */
enum class PermissionEra { Always, Blocklist, Watchlist }

/** The 4K a permission is about, which the server has to have switched on for the toggle to mean anything. */
enum class Needs4k { Both, Movie, Series }

/**
 * What the editor may offer on this server: whether it has the blocklist bits and the watchlist-era bits
 * (Auto-Request, View Recently Added, View Watchlists), and which of its 4K media types are on.
 */
data class PermissionScope(
    val blocklist: Boolean = true,
    val watchlist: Boolean = true,
    val movie4k: Boolean = true,
    val series4k: Boolean = true,
)

fun SeerrServerProfile.permissionScope(): PermissionScope =
    PermissionScope(
        blocklist = hasBlocklist,
        watchlist = hasWatchlistPermissions,
        movie4k = settings.movie4kEnabled,
        series4k = settings.series4kEnabled,
    )

/**
 * The permissions an editor can flip, each with its server bit (`server/lib/permissions.ts`), as the web client's
 * `PermissionEdit` lays them out. [Admin] implies every other one, so the editor shows the rest as granted and locked
 * while it is on; a parent does the same for its children, and Manage Requests for every auto-approve. Bits the server
 * has that are not listed here are preserved untouched on save: the editor only ever changes these.
 */
enum class ManageablePermission(
    val bit: Int,
    val group: PermissionGroup,
    val era: PermissionEra = PermissionEra.Always,
    val needs4k: Needs4k? = null,
) {
    Admin(PERMISSION_ADMIN, PermissionGroup.Administration),
    ManageSettings(PERMISSION_MANAGE_SETTINGS, PermissionGroup.Administration),
    ManageUsers(PERMISSION_MANAGE_USERS, PermissionGroup.Administration),
    ManageRequests(PERMISSION_MANAGE_REQUESTS, PermissionGroup.Requests),
    RequestAdvanced(PERMISSION_REQUEST_ADVANCED, PermissionGroup.Requests),
    ViewRequests(PERMISSION_REQUEST_VIEW, PermissionGroup.Requests),
    ViewRecent(PERMISSION_RECENT_VIEW, PermissionGroup.Requests, PermissionEra.Watchlist),
    ViewWatchlists(PERMISSION_WATCHLIST_VIEW, PermissionGroup.Requests, PermissionEra.Watchlist),
    Request(PERMISSION_REQUEST, PermissionGroup.Requests),
    RequestMovies(PERMISSION_REQUEST_MOVIE, PermissionGroup.Requests),
    RequestSeries(PERMISSION_REQUEST_TV, PermissionGroup.Requests),
    AutoApprove(PERMISSION_AUTO_APPROVE, PermissionGroup.Requests),
    AutoApproveMovies(PERMISSION_AUTO_APPROVE_MOVIE, PermissionGroup.Requests),
    AutoApproveSeries(PERMISSION_AUTO_APPROVE_TV, PermissionGroup.Requests),
    AutoRequest(PERMISSION_AUTO_REQUEST, PermissionGroup.Requests, PermissionEra.Watchlist),
    AutoRequestMovies(PERMISSION_AUTO_REQUEST_MOVIE, PermissionGroup.Requests, PermissionEra.Watchlist),
    AutoRequestSeries(PERMISSION_AUTO_REQUEST_TV, PermissionGroup.Requests, PermissionEra.Watchlist),
    Request4k(PERMISSION_REQUEST_4K, PermissionGroup.Requests, needs4k = Needs4k.Both),
    Request4kMovies(PERMISSION_REQUEST_4K_MOVIE, PermissionGroup.Requests, needs4k = Needs4k.Movie),
    Request4kSeries(PERMISSION_REQUEST_4K_TV, PermissionGroup.Requests, needs4k = Needs4k.Series),
    AutoApprove4k(PERMISSION_AUTO_APPROVE_4K, PermissionGroup.Requests, needs4k = Needs4k.Both),
    AutoApprove4kMovies(PERMISSION_AUTO_APPROVE_4K_MOVIE, PermissionGroup.Requests, needs4k = Needs4k.Movie),
    AutoApprove4kSeries(PERMISSION_AUTO_APPROVE_4K_TV, PermissionGroup.Requests, needs4k = Needs4k.Series),
    ManageIssues(PERMISSION_MANAGE_ISSUES, PermissionGroup.Issues),
    CreateIssues(PERMISSION_CREATE_ISSUES, PermissionGroup.Issues),
    ViewIssues(PERMISSION_VIEW_ISSUES, PermissionGroup.Issues),
    ManageBlocklist(PERMISSION_MANAGE_BLOCKLIST, PermissionGroup.Blocklist, PermissionEra.Blocklist),
    ViewBlocklist(PERMISSION_VIEW_BLOCKLIST, PermissionGroup.Blocklist, PermissionEra.Blocklist),
    ;

    /** The web client's parent for this row: checking the parent checks and locks it. */
    val parent: ManageablePermission?
        get() =
            when (this) {
                RequestAdvanced, ViewRequests, ViewRecent, ViewWatchlists -> ManageRequests
                RequestMovies, RequestSeries -> Request
                AutoApproveMovies, AutoApproveSeries -> AutoApprove
                AutoRequestMovies, AutoRequestSeries -> AutoRequest
                Request4kMovies, Request4kSeries -> Request4k
                AutoApprove4kMovies, AutoApprove4kSeries -> AutoApprove4k
                CreateIssues, ViewIssues -> ManageIssues
                ViewBlocklist -> ManageBlocklist
                else -> null
            }

    /**
     * What has to be granted first, as the web client's `requires`: every set must have one of its members granted.
     * Until then the toggle reads off and can't be flipped.
     */
    val requires: List<Set<ManageablePermission>>
        get() =
            when (this) {
                AutoApprove, AutoRequest -> listOf(setOf(Request))
                AutoApproveMovies, AutoRequestMovies -> listOf(setOf(Request, RequestMovies))
                AutoApproveSeries, AutoRequestSeries -> listOf(setOf(Request, RequestSeries))
                AutoApprove4k -> listOf(setOf(Request4k))
                AutoApprove4kMovies -> listOf(setOf(Request4k, Request4kMovies))
                AutoApprove4kSeries -> listOf(setOf(Request4k, Request4kSeries))
                else -> emptyList()
            }

    /** Whether this server has the bit and, for a 4K one, the 4K it is about. */
    fun availableOn(scope: PermissionScope): Boolean {
        val era =
            when (era) {
                PermissionEra.Always -> true
                PermissionEra.Blocklist -> scope.blocklist
                PermissionEra.Watchlist -> scope.watchlist
            }
        val fourK =
            when (needs4k) {
                null -> true
                Needs4k.Both -> scope.movie4k && scope.series4k
                Needs4k.Movie -> scope.movie4k
                Needs4k.Series -> scope.series4k
            }
        return era && fourK
    }

    companion object {
        val managedMask: Int = entries.fold(0) { acc, permission -> acc or permission.bit }

        /** Every auto-approve, which Manage Requests grants with it, as the web client reads it. */
        private val AUTO_APPROVES =
            setOf(AutoApprove, AutoApproveMovies, AutoApproveSeries, AutoApprove4k, AutoApprove4kMovies, AutoApprove4kSeries)

        /**
         * The toggles this server offers. One whose 4K is off is left out, which the web client shows disabled; the bit
         * is kept on save either way.
         */
        fun offered(scope: PermissionScope): List<ManageablePermission> = entries.filter { it.availableOn(scope) }

        /** The editable permissions a raw bitmask has set; [Admin] is read as its own bit, not as everything. */
        fun decode(bitmask: Int): Set<ManageablePermission> = entries.filterTo(mutableSetOf()) { bitmask and it.bit != 0 }

        /** Re-applies [selected] onto [original], keeping every bit the editor does not manage. */
        fun apply(
            original: Int,
            selected: Set<ManageablePermission>,
        ): Int = (original and managedMask.inv()) or selected.fold(0) { acc, permission -> acc or permission.bit }

        /** Whether [permission] reads as granted under [selected]: its own bit, Admin, its parent, or Manage Requests for an auto-approve. */
        fun isGranted(
            permission: ManageablePermission,
            selected: Set<ManageablePermission>,
        ): Boolean =
            permission in selected ||
                Admin in selected ||
                permission.parent?.let { isGranted(it, selected) } == true ||
                (permission in AUTO_APPROVES && isGranted(ManageRequests, selected))

        /** Whether [permission]'s requirements hold under [selected], so its toggle can be turned on. */
        fun requirementsMet(
            permission: ManageablePermission,
            selected: Set<ManageablePermission>,
        ): Boolean = permission.requires.all { any -> any.any { isGranted(it, selected) } }
    }
}
