package com.kidslauncher.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Copies the chosen picture into the app's private storage, shrunk to screen size. */
object WallpaperStore {

    private fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    fun exists(ctx: Context) = file(ctx).exists()

    fun load(ctx: Context): Bitmap? =
        file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }

    /** Returns true on success. Call off the main thread. */
    fun save(ctx: Context, uri: Uri): Boolean = try {
        val resolver = ctx.contentResolver
        val dm = ctx.resources.displayMetrics
        val target = max(dm.widthPixels, dm.heightPixels)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("Not an image")

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2

        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Could not decode")

        // Photos from the camera are often stored sideways with a rotation tag.
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation != 0f) {
            val m = Matrix().apply { postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }

        FileOutputStream(file(ctx)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) {
        false
    }
}
