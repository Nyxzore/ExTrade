package com.example.exotrade.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Utility class for image processing and encoding.
 * Optimizes images for network transmission by resizing and compressing before Base64 encoding.
 */
object ImageUtils {

    /**
     * Resizes and compresses a Bitmap, returning a Base64 string.
     * Reduces data usage by ~70% while maintaining visual quality for mobile.
     *
     * @param bitmap The raw source Bitmap.
     * @return Base64 encoded string of the compressed JPEG, or null if input is null.
     */
    fun compressAndEncode(bitmap: Bitmap?): String? {
        if (bitmap == null) return null

        // 1. Resize: Capping at 1024px for the largest dimension
        val maxWidth = 1024
        val maxHeight = 1024
        val ratio = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        
        val resized = if (ratio < 1.0f) {
            val newWidth = Math.round(ratio * bitmap.width)
            val newHeight = Math.round(ratio * bitmap.height)
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        // 2. Compress: JPEG with 75% quality (sweet spot for size vs quality)
        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val imageBytes = baos.toByteArray()

        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    /**
     * Decodes a Base64 string back into a Bitmap.
     *
     * @param input The Base64 string.
     * @return The decoded Bitmap.
     */
    fun decodeBase64(input: String?): Bitmap? {
        if (input == null) return null
        return try {
            val decodedByte = Base64.decode(input, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
        } catch (e: Exception) {
            null
        }
    }
}
