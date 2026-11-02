package com.sixmonths.app

import android.app.Application
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.notifications.NotificationHelper
import com.sixmonths.app.playback.SoundFx

class SixMonthsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        SoundFx.init(this)
        // The channel must exist before the first notification is posted and before the user
        // looks for it in Android settings. Creating it here also survives a process that was
        // started by WorkManager instead of the launcher.
        NotificationHelper.ensureChannel(this)
    }
}
