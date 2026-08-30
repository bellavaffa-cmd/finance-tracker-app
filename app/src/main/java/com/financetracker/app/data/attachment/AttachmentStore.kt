package com.financetracker.app.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Receipt images, kept in app-private storage.
 *
 * Private storage rather than the shared gallery for two reasons: it needs no permission at all,
 * and a folder of receipts does not belong in the camera roll where every photo app will index it.
 *
 * Images are downscaled on the way in. A modern phone camera produces four-megabyte photographs,
 * and a few hundred of those would quietly become a gigabyte of receipts; at [MAX_DIMENSION] a
 * receipt is still comfortably readable at a fraction of the size.
 */
class AttachmentStore(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }

    /** Where the camera app writes before the image is imported and downscaled. */
    fun newCameraTarget(): Pair<File, Uri> {
        val file = File(context.cacheDir, "capture-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /**
     * Copies an image into private storage, downscaled, and returns the stored file name.
     *
     * Only the file name is kept, never an absolute path: `filesDir` moves between installs and
     * across a device restore, so a stored absolute path would break in ways that are awkward to
     * diagnose later.
     */
    suspend fun importFrom(source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: return@runCatching null
            storeBytes(bytes)
        }.getOrNull()
    }

    suspend fun importFrom(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val name = storeBytes(file.readBytes())
            file.delete()
            name
        }.getOrNull()
    }

    private fun storeBytes(bytes: ByteArray): String? {
        val bitmap = decodeScaled(bytes) ?: return null
        val name = "receipt-${UUID.randomUUID()}.jpg"
        FileOutputStream(File(directory, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        bitmap.recycle()
        return name
    }

    /**
     * Decodes at a sample size chosen from the image's own bounds, so a very large photo is never
     * fully decoded into memory just to be shrunk immediately afterwards.
     */
    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sample = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width / sample > MAX_DIMENSION || height / sample > MAX_DIMENSION) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun fileFor(name: String): File? =
        File(directory, name).takeIf { it.exists() }

    fun uriFor(name: String): Uri? = fileFor(name)?.let {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        runCatching { File(directory, name).delete() }
        Unit
    }

    /**
     * Removes images no transaction refers to any more.
     *
     * Deleting a transaction only soft-deletes the row, so its receipt is deliberately kept while
     * the row exists at all. This sweeps up files orphaned by a restore replacing the whole ledger,
     * which is the one case where images can be stranded with nothing pointing at them.
     */
    suspend fun pruneOrphans(referenced: Set<String>): Int = withContext(Dispatchers.IO) {
        val files = directory.listFiles().orEmpty()
        var removed = 0
        for (file in files) {
            if (file.name !in referenced) {
                if (file.delete()) removed++
            }
        }
        removed
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().sumOf { it.length() }
    }

    companion object {
        private const val MAX_DIMENSION = 1600
        private const val JPEG_QUALITY = 80
    }
}
