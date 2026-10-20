package com.trenk.downloader

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * הורדה ברקע עם התראת התקדמות (Foreground Service).
 * ההורדות רצות אחת אחרי השנייה; בסוף כל אחת התראה "נשמר" שפותחת את הקובץ.
 */
class DownloadService : Service() {
    private data class Job(
        val id: String, val url: String, val formatId: String, val title: String,
        val probeId: String?, val uploader: String?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val canceled = Collections.synchronizedSet(mutableSetOf<String>())
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        scope.launch { for (job in queue) run(job) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // חייבים להיכנס ל-foreground מיד (תוך כמה שניות), גם לפני שההורדה מתחילה
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, PROGRESS_ID, progressNotification(getString(R.string.dl_starting), null, 0f, true), type)

        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_JOB)?.let { cancel(it) }
            if (pending.get() == 0) stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL)
        val formatId = intent?.getStringExtra(EXTRA_FORMAT)
        if (url == null || formatId == null) {
            if (pending.get() == 0) stopSelf()
            return START_NOT_STICKY
        }
        val job = Job(
            id = UUID.randomUUID().toString(),
            url = url,
            formatId = formatId,
            title = intent.getStringExtra(EXTRA_TITLE) ?: url,
            probeId = intent.getStringExtra(EXTRA_ID),
            uploader = intent.getStringExtra(EXTRA_UPLOADER),
        )
        History.add(
            HistoryItem(
                id = job.id, url = url, formatId = formatId, title = job.title,
                thumbnail = intent.getStringExtra(EXTRA_THUMB), platform = Links.platformOf(url) ?: "",
                status = Status.QUEUED, label = intent.getStringExtra(EXTRA_LABEL),
            )
        )
        pending.incrementAndGet()
        queue.trySend(job)
        return START_NOT_STICKY
    }

    /** ביטול: הורדה שרצה נעצרת; הורדה שבתור מדולגת כשמגיע תורה */
    private fun cancel(id: String) {
        canceled += id
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        History.update(id) { if (it.active) it.copy(status = Status.ERROR, error = getString(R.string.dl_canceled), speed = null) else it }
    }

    private suspend fun run(job: Job) {
        val platform = Links.platformOf(job.url) ?: "video"
        val workDir = File(cacheDir, "dl/${job.id}").apply { mkdirs() }
        try {
            if (job.id in canceled) return
            Engine.awaitReady()
            val baseName = Links.cleanFileName(job.title, job.uploader, job.probeId, platform)
            val request = Engine.downloadRequest(this, job.url, job.formatId, workDir, baseName)
            nm.notify(PROGRESS_ID, progressNotification(job.title, getString(R.string.dl_downloading), 0f, true))
            // נכשל? מעדכנים את המנוע ומנסים שוב פעם אחת ("מתקן... רגע"), ורק אז שגיאה
            val onRepair = {
                if (job.id in canceled) throw IllegalStateException("canceled")
                workDir.listFiles()?.forEach { it.deleteRecursively() }
                History.update(job.id) { it.copy(status = Status.DOWNLOADING, progress = 0f, speed = getString(R.string.repairing)) }
                nm.notify(PROGRESS_ID, progressNotification(job.title, getString(R.string.repairing), 0f, true))
            }
            Engine.withRepair(this, platform, onRepair) { execute(job, request) }

            History.update(job.id) { it.copy(status = Status.SAVING, progress = 100f, speed = null) }
            nm.notify(PROGRESS_ID, progressNotification(job.title, getString(R.string.dl_saving), 100f, true))
            val file = workDir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.length() }
                ?: throw IllegalStateException("download finished without a file")
            val size = file.length()
            val saved = MediaSaver.save(this, file, baseName)
            History.update(job.id) { it.copy(status = Status.DONE, uri = saved.uri.toString(), mime = saved.mime, sizeBytes = size) }
            doneNotification(job, saved.uri, saved.mime, saved.displayName)
        } catch (e: Throwable) {
            if (job.id in canceled) {
                History.update(job.id) { it.copy(status = Status.ERROR, error = getString(R.string.dl_canceled), speed = null) }
            } else {
                Telemetry.downloadError("download", platform, e)
                val msg = Engine.friendlyError(this@DownloadService, e.message, platform)
                History.update(job.id) { it.copy(status = Status.ERROR, error = msg, speed = null) }
                errorNotification(job, msg)
            }
        } finally {
            workDir.deleteRecursively()
            canceled -= job.id
            if (pending.decrementAndGet() == 0) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun execute(job: Job, request: YoutubeDLRequest) {
        var lastUpdate = 0L
        YoutubeDL.getInstance().execute(request, job.id) { progress, eta, line ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate < 700) return@execute // לא להציף את מנהל ההתראות
            lastUpdate = now
            val p = progress.coerceIn(0f, 100f)
            // "[download]  62.3% of 25.00MiB at 3.10MiB/s ETA 00:12"
            val speed = SPEED_RE.find(line)?.groupValues?.get(1)?.replace("MiB", "MB")?.replace("KiB", "KB")
            History.progress(job.id, p, speed, eta)
            val etaText = if (eta > 0) " · " + getString(R.string.dl_eta, formatEta(eta)) else ""
            nm.notify(PROGRESS_ID, progressNotification(job.title, "${p.toInt()}%$etaText", p, p <= 0f))
        }
    }

    private fun formatEta(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)
    else "%d:%02d".format(sec / 60, sec % 60)

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(title: String, text: String?, progress: Float, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, App.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, progress.toInt(), indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainIntent())
            .setColor(0xFFFF5E8A.toInt())
            .build()

    private fun doneNotification(job: Job, uri: Uri, mime: String, name: String) {
        val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, job.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val folder = if (mime.startsWith("audio")) "Music/Downloader" else "Movies/Downloader"
        nm.notify(
            job.id.hashCode(),
            NotificationCompat.Builder(this, App.CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.dl_saved))
                .setContentText(name)
                .setSubText(folder)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(0xFFFF5E8A.toInt())
                .build(),
        )
    }

    private fun errorNotification(job: Job, msg: String) {
        nm.notify(
            job.id.hashCode(),
            NotificationCompat.Builder(this, App.CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.dl_failed))
                .setContentText(job.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText("${job.title}\n$msg"))
                .setContentIntent(mainIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PROGRESS_ID = 1
        private const val ACTION_CANCEL = "com.trenk.downloader.CANCEL"
        private const val EXTRA_JOB = "job"
        private const val EXTRA_URL = "url"
        private const val EXTRA_FORMAT = "format"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THUMB = "thumb"
        private const val EXTRA_ID = "id"
        private const val EXTRA_UPLOADER = "uploader"
        private const val EXTRA_LABEL = "label"
        private val SPEED_RE = Regex("""at\s+([\d.]+\s*[KMG]i?B/s)""")

        fun start(context: Context, probe: Probe, option: FormatOption) =
            startRaw(context, probe.url, option.formatId, probe.title, probe.thumbnail, option.label, probe.id, probe.uploader)

        /** גם ל"נסה שוב" מההיסטוריה */
        fun startRaw(
            context: Context, url: String, formatId: String, title: String?, thumbnail: String?, label: String?,
            id: String? = null, uploader: String? = null,
        ) {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_FORMAT, formatId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_THUMB, thumbnail)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_UPLOADER, uploader)
                .putExtra(EXTRA_LABEL, label)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, jobId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB, jobId),
            )
        }
    }
}
