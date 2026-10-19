package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrBulkUsersBody
import com.cooper.binge.companion.seerr.seerr.SeerrCreateUserBody
import com.cooper.binge.companion.seerr.seerr.SeerrImportJellyfinBody
import com.cooper.binge.companion.seerr.seerr.SeerrJellyfinUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrQuotaDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserMainSettingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserPageDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserPasswordInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrUserPermissionsBody
import com.cooper.binge.companion.seerr.seerr.SeerrUserPermissionsDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchlistPageDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val LOCAL_USER = 2
private const val FORBIDDEN = 403
private const val NEW_USER_PERMISSIONS = 32

/** `user`: the accounts, what each may do, their own settings, and adding and importing more. */
internal fun DemoData.userRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "user") { call, _ -> ok(userPage(call)) },
        route("POST", "user") { call, _ -> createUser(call.body) },
        route("PUT", "user") { call, _ -> bulkPermissions(call.body) },
        route("GET", "user", "{}") { _, (id) -> withUser(id) { ok(counted(it)) } },
        route("DELETE", "user", "{}") { _, (id) -> withUser(id) { remove(it) } },
        route("GET", "user", "{}", "quota") { _, _ -> ok(SeerrQuotaDto()) },
        route("GET", "user", "{}", "watch_data") { _, _ -> demoNotFound },
        route("GET", "user", "{}", "watchlist") { _, _ -> ok(SeerrWatchlistPageDto()) },
        route("GET", "user", "{}", "settings", "main") { _, (id) -> withUser(id) { jsonAnswer(mainSettings(it)) } },
        route("POST", "user", "{}", "settings", "main") { call, (id) -> withUser(id) { saveMain(it, call.body) } },
        route("GET", "user", "{}", "settings", "password") { _, _ -> ok(SeerrUserPasswordInfoDto(hasPassword = true)) },
        route("POST", "user", "{}", "settings", "password") { _, _ -> done },
        route("GET", "user", "{}", "settings", "notifications") { _, (id) ->
            jsonAnswer(
                resources.objects["notify-$id"] ?: JsonObject(emptyMap()),
            )
        },
        route("POST", "user", "{}", "settings", "notifications") { call, (id) -> saveNotifications(id, call.body) },
        route(
            "GET",
            "user",
            "{}",
            "settings",
            "permissions",
        ) { _, (id) -> withUser(id) { ok(SeerrUserPermissionsDto(it.permissions ?: 0)) } },
        route("POST", "user", "{}", "settings", "permissions") { call, (id) -> withUser(id) { setPermissions(it, call.body) } },
        route("GET", "settings", "plex", "users") { _, _ -> ok(emptyList<SeerrJellyfinUserDto>()) },
        route("GET", "settings", "jellyfin", "users") { _, _ -> ok(importable()) },
        route("POST", "user", "import-from-plex") { _, _ -> ok(emptyList<SeerrUserDto>()) },
        route("POST", "user", "import-from-jellyfin") { call, _ -> importUsers(call.body) },
    )

private fun DemoData.withUser(
    id: String,
    block: (SeerrUserDto) -> DemoAnswer,
): DemoAnswer = users.firstOrNull { it.id == id.toInt() }?.let(block) ?: demoNotFound

private fun DemoData.counted(user: SeerrUserDto) = user.copy(requestCount = requests.count { it.requestedBy == user.id })

private fun DemoData.userPage(call: DemoCall): SeerrUserPageDto {
    val order: Comparator<SeerrUserDto> =
        when (call.query("sort")) {
            "requests" -> compareByDescending { it.requestCount }
            "displayname" -> compareBy { it.displayName.orEmpty().lowercase() }
            else -> compareBy { it.id }
        }
    val (page, info) = users.map { counted(it) }.sortedWith(order).pageFor(call)
    return SeerrUserPageDto(pageInfo = info, results = page)
}

private fun DemoData.replace(
    user: SeerrUserDto,
    next: SeerrUserDto,
) {
    users[users.indexOf(user)] = next
}

private fun DemoData.remove(user: SeerrUserDto): DemoAnswer {
    if (user.id == DEMO_USER_ID) return message(FORBIDDEN, "You cannot delete the account you are signed in with.")
    users.remove(user)
    requests.removeAll { it.requestedBy == user.id }
    return ok(user)
}

private fun DemoData.createUser(body: String): DemoAnswer {
    val asked = demoJson.decodeFromString<SeerrCreateUserBody>(body)
    val user =
        SeerrUserDto(
            id = resources.nextId(),
            displayName = asked.username,
            username = asked.username,
            email = asked.email,
            permissions = NEW_USER_PERMISSIONS,
            userType = LOCAL_USER,
            createdAt = now().toString(),
        )
    users += user
    return ok(user)
}

private fun DemoData.bulkPermissions(body: String): DemoAnswer {
    val asked = demoJson.decodeFromString<SeerrBulkUsersBody>(body)
    users.filter { it.id in asked.ids }.forEach { replace(it, it.copy(permissions = asked.permissions)) }
    return ok(users.filter { it.id in asked.ids })
}

private fun DemoData.setPermissions(
    user: SeerrUserDto,
    body: String,
): DemoAnswer {
    val permissions = demoJson.decodeFromString<SeerrUserPermissionsBody>(body).permissions
    replace(user, user.copy(permissions = permissions))
    return ok(SeerrUserPermissionsDto(permissions))
}

private fun DemoData.mainSettings(user: SeerrUserDto): JsonObject =
    resources.objects["user-${user.id}-main"]
        ?: toJson(SeerrUserMainSettingsDto(username = user.username ?: user.displayName, email = user.email, locale = "", region = ""))

/** The page's own fields are kept, and a name or an address typed there is the account's name or address from then on. */
private fun DemoData.saveMain(
    user: SeerrUserDto,
    body: String,
): DemoAnswer {
    val sent = parseObject(body)
    val saved = JsonObject(mainSettings(user) + sent)
    resources.objects["user-${user.id}-main"] = saved
    val name = sent["username"]?.jsonPrimitive?.contentOrNull
    replace(
        user,
        user.copy(
            email = sent["email"]?.jsonPrimitive?.contentOrNull ?: user.email,
            username = name ?: user.username,
            displayName =
                name ?: user.displayName,
        ),
    )
    return jsonAnswer(saved)
}

private fun DemoData.saveNotifications(
    id: String,
    body: String,
): DemoAnswer {
    resources.objects["notify-$id"] = parseObject(body)
    return done
}

private fun importable() =
    listOf(
        SeerrJellyfinUserDto(id = "demo-riley", username = "Riley", email = "riley@example.com"),
        SeerrJellyfinUserDto(id = "demo-morgan", username = "Morgan", email = "morgan@example.com"),
    )

private fun DemoData.importUsers(body: String): DemoAnswer {
    val ids = demoJson.decodeFromString<SeerrImportJellyfinBody>(body).jellyfinUserIds
    val added =
        importable().filter { it.id in ids }.map {
            SeerrUserDto(
                id = resources.nextId(),
                displayName = it.username,
                username = it.username,
                email = it.email,
                permissions = NEW_USER_PERMISSIONS,
                userType = 3,
                jellyfinUserId = it.id,
                createdAt = now().toString(),
            )
        }
    users += added
    return ok(added)
}
