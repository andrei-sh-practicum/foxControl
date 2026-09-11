package com.andrew.foxcontrol.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object AvatarImageUtil {

    private const val AVATAR_DIR = "avatars"
    private const val AVATAR_SIZE = 256
    private const val JPEG_QUALITY = 85

    /**
     * Resizes an image from the given Uri to 256x256 JPEG and saves it
     * in the app's private avatar directory.
     *
     * Returns the file:// URI of the saved avatar, or null on failure.
     */
    fun resizeAndSaveAvatar(context: Context, uri: Uri): String? {
        val avatarDir = File(context.filesDir, AVATAR_DIR)
        if (!avatarDir.exists()) {
            avatarDir.mkdirs()
        }

        // Delete previous avatar file if exists
        val oldFile = getPreviousAvatarFile(context)
        oldFile?.delete()

        // Decode with inSampleSize to avoid OOM for large images
        val srcBitmap = decodeSampledBitmap(context, uri, AVATAR_SIZE, AVATAR_SIZE)
            ?: return null

        try {
            // Crop center square and resize
            val resized = centerCropToSquare(srcBitmap, AVATAR_SIZE)
            srcBitmap.recycle()

            // Save as JPEG
            val fileName = "avatar_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())}.jpg"
            val file = File(avatarDir, fileName)

            file.outputStream().use { out ->
                resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.flush()
            }
            resized.recycle()

            return "file://${file.absolutePath}"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return null

            // First pass: decode bounds
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate inSampleSize
            val sampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Second pass: decode with sample size
            val options2 = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
            }
            val inputStream2 = context.contentResolver.openInputStream(uri)
                ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream2, null, options2)
            inputStream2.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var sampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun centerCropToSquare(source: Bitmap, size: Int): Bitmap {
        val srcWidth = source.width
        val srcHeight = source.height

        // Determine the smaller dimension to crop to a square
        val minDim = minOf(srcWidth, srcHeight)

        // Calculate source rectangle (center crop)
        val srcX = (srcWidth - minDim) / 2
        val srcY = (srcHeight - minDim) / 2

        return Bitmap.createBitmap(source, srcX, srcY, minDim, minDim,
            Matrix(), true)
            .also { cropped ->
                if (cropped != source) {
                    // If same bitmap (no crop needed), still resize
                    if (cropped.width == size && cropped.height == size) return@also
                }
            }
            .let { bitmap ->
                if (bitmap.width != size || bitmap.height != size) {
                    Bitmap.createScaledBitmap(bitmap, size, size, true)
                        .also { bitmap.recycle() }
                } else {
                    bitmap
                }
            }
    }

    /**
     * Returns the current avatar file path stored in the database (for cleanup).
     * We don't have access to DB here, so we just return null.
     * The caller (UserRepository) handles cleanup.
     */
    private fun getPreviousAvatarFile(context: Context): File? {
        // This is called before the new avatar is saved,
        // so we can't know the previous file path here.
        // Cleanup is handled by the repository layer.
        return null
    }
}
