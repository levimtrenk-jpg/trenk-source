package com.trenk.downloader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class Status { QUEUED, DOWNLOADING, SAVING, DONE, ERROR }

data class HistoryItem(
    val id: String,
    val url: String,
    val formatId: String,
    val title: String,
    val thumbnail: String?,
    val platform: String,
    val status: Status,
    val progress: Float = 0f,
    val uri: String? = null,
    val mime: String? = null,
    val error: String? = null,
    val time: Long = System.currentTimeMillis(),
    val label: String? = null, // "MP4 1080p" / "MP3"
    val sizeBytes: Long = 0,
    // רק בזמן הורדה, לא נשמר
    val speed: String? = null,
    val eta: Long = 0,
) {
    val active get() = status == Status.QUEUED || status == Status.DOWNLOADING || status == Status.SAVING
}

/** היסטוריית ההורדות: רשימה בזיכרון (StateFlow למסך) + קובץ JSON פשוט ב-filesDir. */
object History {
    private const val MAX = 200
    private lateinit var file: File
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items

    fun load(context: Context) {
        file = File(context.filesDir, "history.json")
        _items.value = runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
                // הורדה שנקטעה (האפליקציה נסגרה באמצע) לא תמשיך מעצמה
                .map { if (it.status in setOf(Status.QUEUED, Status.DOWNLOADING, Status.SAVING)) it.copy(status = Status.ERROR, error = context.getString(R.string.err_interrupted)) else it }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(item: HistoryItem) {
        _items.value = (listOf(item) + _items.value).take(MAX)
        save()
    }

    /** עדכון התקדמות: רק בזיכרון (קורה כמה פעמים בשנייה) */
    fun progress(id: String, progress: Float, speed: String?, eta: Long) {
        _items.value = _items.value.map { if (it.id == id) it.copy(status = Status.DOWNLOADING, progress = progress, speed = speed, eta = eta) else it }
    }

    @Synchronized
    fun update(id: String, change: (HistoryItem) -> HistoryItem) {
        _items.value = _items.value.map { if (it.id == id) change(it) else it }
        save()
    }

    @Synchronized
    fun clearFinished() {
        _items.value = _items.value.filter { it.active }
        save()
    }

    private fun save() {
        if (!::file.isInitialized) return
        val arr = JSONArray()
        _items.value.forEach { arr.put(toJson(it)) }
        runCatching { file.writeText(arr.toString()) }
    }

    private fun toJson(i: HistoryItem) = JSONObject().apply {
        put("id", i.id); put("url", i.url); put("formatId", i.formatId); put("title", i.title)
        put("thumbnail", i.thumbnail); put("platform", i.platform); put("status", i.status.name)
        put("uri", i.uri); put("mime", i.mime); put("error", i.error); put("time", i.time)
        put("label", i.label); put("sizeBytes", i.sizeBytes)
    }

    private fun fromJson(o: JSONObject) = HistoryItem(
        id = o.getString("id"),
        url = o.optString("url"),
        formatId = o.optString("formatId"),
        title = o.optString("title"),
        thumbnail = o.optString("thumbnail").takeIf { it.isNotEmpty() && it != "null" },
        platform = o.optString("platform"),
        status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.ERROR),
        uri = o.optString("uri").takeIf { it.isNotEmpty() && it != "null" },
        mime = o.optString("mime").takeIf { it.isNotEmpty() && it != "null" },
        error = o.optString("error").takeIf { it.isNotEmpty() && it != "null" },
        time = o.optLong("time"),
        label = o.optString("label").takeIf { it.isNotEmpty() && it != "null" },
        sizeBytes = o.optLong("sizeBytes"),
    )
}
