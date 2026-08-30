package com.financetracker.app.data.receipt

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reads text off a receipt image, entirely on device.
 *
 * The bundled Latin recogniser is used rather than the Play Services one, so nothing is downloaded
 * and nothing leaves the phone - a receipt photograph is about as private as a document gets, and
 * shipping it to a server to be read would undo the point of an app with no network access.
 */
class ReceiptScanner(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Returns what could be read, or a reading with everything null when the image cannot be
     * processed. Failure here is never surfaced as an error: the amount can always be typed, and an
     * apology for a convenience that did not work is just noise.
     */
    suspend fun scan(uri: Uri): ReceiptReading = suspendCancellableCoroutine { continuation ->
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
        if (image == null) {
            continuation.resume(ReceiptReading(null, null, null))
            return@suspendCancellableCoroutine
        }

        recognizer.process(image)
            .addOnSuccessListener { text ->
                // Bounding boxes are carried through, not just the strings. A receipt's labels and
                // amounts come back as separate blocks, so the parser needs position to pair
                // "TOTAL" with the figure printed beside it rather than the one merely next in
                // the list.
                val lines = text.textBlocks
                    .flatMap { block -> block.lines }
                    .mapIndexed { index, line ->
                        val box = line.boundingBox
                        OcrLine(
                            text = line.text,
                            // A missing box is rare; falling back to list order keeps such a line
                            // usable instead of discarding it.
                            top = box?.top ?: (index * 100),
                            bottom = box?.bottom ?: (index * 100 + 40),
                            left = box?.left ?: 0
                        )
                    }
                continuation.resume(ReceiptParser.parse(lines))
            }
            .addOnFailureListener {
                continuation.resume(ReceiptReading(null, null, null))
            }
    }
}
