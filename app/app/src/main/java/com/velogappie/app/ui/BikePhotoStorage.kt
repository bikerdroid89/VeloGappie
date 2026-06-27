package com.velogappie.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

private const val PHOTO_FILENAME = "bike_photo.png"
private const val PREFS_NAME = "app_settings"
private const val PREF_HAS_BIKE_PHOTO = "has_bike_photo"
private const val PREF_BIKE_MODEL_NAME = "bike_model_name"

object BikePhotoStorage {

    fun photoFile(context: Context): File =
        File(context.filesDir, PHOTO_FILENAME)

    fun hasPhoto(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_HAS_BIKE_PHOTO, false) && photoFile(context).exists()

    fun saveCroppedBitmap(context: Context, bitmap: Bitmap) {
        photoFile(context).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_HAS_BIKE_PHOTO, true).apply()
    }

    fun loadBitmap(context: Context): Bitmap? {
        val file = photoFile(context)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }

    fun saveModelName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_BIKE_MODEL_NAME, name).apply()
    }

    fun loadModelName(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_BIKE_MODEL_NAME, null)

    fun deletePhoto(context: Context) {
        photoFile(context).delete()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_HAS_BIKE_PHOTO, false).apply()
    }
}
