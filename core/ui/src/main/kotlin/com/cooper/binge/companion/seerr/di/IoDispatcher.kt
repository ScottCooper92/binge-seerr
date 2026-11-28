package com.cooper.binge.companion.seerr.di

import javax.inject.Qualifier

/**
 * The dispatcher a ViewModel launches its network work on, instead of inheriting
 * `Dispatchers.Main.immediate` from `viewModelScope`. A coroutine launched this way never touches
 * `Dispatchers.Main` at all, including after `ViewModelStore.clear()` cancels it and its
 * in-flight call still resumes: it resumes on this dispatcher, not on a Main a test may have
 * already reset. See `MainDispatcherRule`'s KDoc and #177.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher
