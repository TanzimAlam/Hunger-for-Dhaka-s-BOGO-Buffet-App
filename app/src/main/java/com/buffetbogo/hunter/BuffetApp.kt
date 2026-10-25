package com.buffetbogo.hunter

import android.app.Application
import android.content.Context
import com.buffetbogo.hunter.data.AppDatabase
import com.buffetbogo.hunter.data.OfferRepository
import com.buffetbogo.hunter.data.SettingsStore
import com.buffetbogo.hunter.notify.Notifier
import com.buffetbogo.hunter.work.WorkScheduler

class BuffetApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.ensureChannel(this)
        WorkScheduler.schedulePeriodicSync(this)
    }
}

class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val settingsStore = SettingsStore(context)
    val repository = OfferRepository(context, database.offerDao(), settingsStore)
}
