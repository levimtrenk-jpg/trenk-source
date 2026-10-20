package com.trenk.downloader

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * מעביר קובץ מהתיקייה הזמנית לגלריה/מוזיקה:
 * וידאו → Movies/Downloader, MP3 → Music/Downloader.
 * Android 10+: דרך MediaStore (בלי הרשאות). Android 8-9: העתקה לתיקייה הציבורית + סריקה.
 */
object MediaSaver {
    data class Saved(val uri: Uri, val mime: String, val displayName: String)

    fun save(context: Context, source: File, baseName: String): Saved {
        val ext = source.extension.lowercase()
        val audio = ext in setOf("mp3", "m4a", "opus", "ogg", "wav")
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> if (audio) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            else -> if (audio) "audio/*" else "video/mp4"
        }
        val displayName = "$baseName.$ext"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveMediaStore(context, source, displayName, mime, audio)
        } else {
            saveLegacy(context, source, displayName, mime, audio)
        }
    }

    private fun saveMediaStore(context: Context, source: File, name: String, mime: String, audio: Boolean): Saved {
        val resolver = context.contentResolver
        val collection = if (audio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) "Music/Downloader" else "Movies/Downloader")
            put(MediaStore.MediaColumns.IS_PENDING, 1) // הקובץ לא נראה בגלריה עד שההעתקה מסתיימת
        }
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return Saved(uri, mime, name)
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, source: File, name: String, mime: String, audio: Boolean): Saved {
        val root = Environment.getExternalStoragePublicDirectory(if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
        val dir = File(root, "Downloader").apply { mkdirs() }
        var target = File(dir, name)
        var n = 2
        while (target.exists()) target = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.')}").also { n++ }
        source.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        return Saved(uri, mime, target.name)
    }
}
