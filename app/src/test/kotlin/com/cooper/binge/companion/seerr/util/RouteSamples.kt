package com.cooper.binge.companion.seerr.util

import com.cooper.binge.companion.seerr.ui.SeerrRoute

/**
 * One instance of every route, built from its constructor with a sample value for each parameter, so a test that must cover every
 * route cannot skip one added later (`SeerrRoute`'s subclasses are sealed, so the compiler lists them).
 */
internal fun sampleRoutes(): List<SeerrRoute> =
    SeerrRoute::class.java.permittedSubclasses
        .orEmpty()
        .map(::sampleRoute)

/** [type] as a route: an object's instance, or a class built from its constructor. */
internal fun sampleRoute(type: Class<*>): SeerrRoute = sampleOf(type) as SeerrRoute

/** An object's instance, a sealed interface's first subtype, or a class built from its constructor with a sample value for each parameter. */
private fun sampleOf(type: Class<*>): Any {
    if (type.isEnum) return type.enumConstants.first()
    if (type.isInterface) return sampleOf(type.permittedSubclasses.first())
    runCatching { return type.getField("INSTANCE").get(null) }
    val constructor = type.declaredConstructors.first { !it.isSynthetic }
    return constructor.newInstance(*constructor.parameterTypes.map(::sampleValue).toTypedArray())
}

private fun sampleValue(type: Class<*>): Any =
    when {
        type == Int::class.javaPrimitiveType || type == Int::class.javaObjectType -> 1
        type == Long::class.javaPrimitiveType || type == Long::class.javaObjectType -> 1L
        type == Boolean::class.javaPrimitiveType || type == Boolean::class.javaObjectType -> false
        type == String::class.java -> "sample"
        type == Double::class.javaPrimitiveType || type == Double::class.javaObjectType -> 1.0
        List::class.java.isAssignableFrom(type) -> emptyList<Any>()
        Set::class.java.isAssignableFrom(type) -> emptySet<Any>()
        else -> sampleOf(type)
    }
