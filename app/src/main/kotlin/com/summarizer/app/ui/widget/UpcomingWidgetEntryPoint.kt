package com.summarizer.app.ui.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Glance creates the widget and its receiver itself, so they cannot use constructor injection;
 * they reach the app's singletons through this entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpcomingWidgetEntryPoint {
    fun upcomingWidgetUpdater(): UpcomingWidgetUpdater

    companion object {
        fun updater(context: Context): UpcomingWidgetUpdater =
            EntryPointAccessors.fromApplication(context.applicationContext, UpcomingWidgetEntryPoint::class.java)
                .upcomingWidgetUpdater()
    }
}
