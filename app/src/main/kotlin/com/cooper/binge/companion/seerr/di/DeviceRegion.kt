package com.cooper.binge.companion.seerr.di

import java.util.Locale

private const val REGION_CODE_LENGTH = 2

/** The country the phone is set to, as the two-letter code Seerr's regions use; null where the locale names none. */
internal fun deviceRegion(): String? = Locale.getDefault().country.takeIf { it.length == REGION_CODE_LENGTH }
