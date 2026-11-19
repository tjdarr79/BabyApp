package dev.babysim.spike

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Bundled synthetic clips (res/raw, from spike/tools/make_clips.py) and candidate clips the tester imports on
 * the phone. Imported clips stay on the phone; they are never committed (licences, docs/08).
 */
object Clips {
    private const val RAW = "raw:"
    private val BUNDLED = mapOf("cry_loop" to R.raw.cry_loop, "cry_short" to R.raw.cry_short, "ambient_loop" to R.raw.ambient_loop)

    fun dir(context: Context): File = File(context.filesDir, "clips").apply { mkdirs() }

    fun all(context: Context): List<String> =
        listOf("${RAW}cry_loop", "${RAW}cry_short") + dir(context).listFiles().orEmpty().sortedBy { it.name }.map { it.path }

    fun name(clip: String): String = if (clip.startsWith(RAW)) clip.removePrefix(RAW) + " (synthetic)" else File(clip).name

    fun player(
        context: Context,
        clip: String,
        attrs: AudioAttributes,
        loop: Boolean,
    ): MediaPlayer =
        MediaPlayer().apply {
            setAudioAttributes(attrs)
            if (clip.startsWith(RAW)) {
                val id = BUNDLED.getValue(clip.removePrefix(RAW))
                context.resources.openRawResourceFd(id).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            } else {
                setDataSource(clip)
            }
            isLooping = loop
            prepare()
        }

    /** Copies a picked audio file into app storage and returns its path. */
    fun import(
        context: Context,
        uri: Uri,
    ): String {
        val name =
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "clip-${System.currentTimeMillis()}"
        val target = File(dir(context), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        return target.path
    }
}
