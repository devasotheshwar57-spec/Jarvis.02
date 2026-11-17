package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

private const val TAG = "ImageUtils"
private const val MAX_DIMENSION = 1440

object ImageUtils {

    fun createTempCameraUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("jarvis_cam_", ".jpg", dir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }

                val maxSide = max(bounds.outWidth, bounds.outHeight)
                var sampleSize = 1
                while (maxSide / sampleSize > MAX_DIMENSION) {
                    sampleSize *= 2
                }

                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val raw = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                } ?: return@withContext null

                // Scale down precisely if still larger than MAX_DIMENSION
                val largest = max(raw.width, raw.height)
                if (largest > MAX_DIMENSION) {
                    val ratio = MAX_DIMENSION.toFloat() / largest.toFloat()
                    val matrix = Matrix().apply { postScale(ratio, ratio) }
                    val scaled = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                    if (scaled !== raw) raw.recycle()
                    scaled
                } else {
                    raw
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode bitmap from URI", e)
                null
            }
        }
}
