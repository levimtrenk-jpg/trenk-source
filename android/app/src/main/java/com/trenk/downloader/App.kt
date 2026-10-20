package com.trenk.downloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Telemetry.init(this)
        History.load(this)
        AppUpdate.loadCachedTip(this)
        createChannels()
        // הפעלה ראשונה מחלצת python + yt-dlp + ffmpeg (כמה שניות), לכן ברקע
        Engine.initAsync(this)
        // yt-dlp מתעדכן לבד: עכשיו ברקע, ופעם ביום ב-Wi-Fi. בלי כפתור ובלי הודעות.
        Engine.autoUpdateAsync(this)
        EngineUpdateWorker.schedule(this)
        Stats.firstOpen(this)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, getString(R.string.channel_progress), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, getString(R.string.channel_done), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    companion object {
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_DONE = "done"
    }
}
