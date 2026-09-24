package com.relationship.graph.ui.graph

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.relationship.graph.data.local.PersonEntity
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberAvatarImages(people: List<PersonEntity>): Map<String, ImageBitmap> {
    val avatarKeys = remember(people) {
        people.mapNotNull { person ->
            person.avatarPath?.let { path -> person.id to path }
        }
    }
    var images by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }

    LaunchedEffect(avatarKeys) {
        images = withContext(Dispatchers.IO) {
            avatarKeys.mapNotNull { (personId, path) ->
                decodeAvatar(path)?.let { personId to it.asImageBitmap() }
            }.toMap()
        }
    }
    return images
}

private fun decodeAvatar(path: String): Bitmap? {
    if (!File(path).exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val longestSide = max(bounds.outWidth, bounds.outHeight)
    while (longestSide / sampleSize > AVATAR_MAX_SIZE * 2) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val decoded = BitmapFactory.decodeFile(path, options) ?: return null
    val currentLongestSide = max(decoded.width, decoded.height)
    if (currentLongestSide <= AVATAR_MAX_SIZE) return decoded

    val scale = AVATAR_MAX_SIZE.toFloat() / currentLongestSide
    val scaled = Bitmap.createScaledBitmap(
        decoded,
        max(1, (decoded.width * scale).toInt()),
        max(1, (decoded.height * scale).toInt()),
        true,
    )
    if (scaled !== decoded) decoded.recycle()
    return scaled
}

private const val AVATAR_MAX_SIZE = 128
