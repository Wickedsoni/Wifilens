package com.wickedcoder.wifilens.core.common

import javax.inject.Qualifier

/** `Dispatchers.IO`: blocking I/O such as Room, DataStore, sockets and file access. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

/** `Dispatchers.Default`: CPU-bound work such as coverage grids, the optimizer and model fitting. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

/** App-lifetime `CoroutineScope` (`SupervisorJob` + Default) for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
