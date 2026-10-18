package org.kealohalabs.receiptbeat.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kealohalabs.receiptbeat.core.data.FileStore

/** Decrypts and decodes receipt images off the main thread, with a small memory cache. */
@Singleton
class ImageLoader @Inject constructor(private val store: FileStore) {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(path: String): Bitmap? = cache.get(path) ?: withContext(Dispatchers.IO) {
        runCatching { store.read(path).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }.getOrNull()?.also { cache.put(path, it) }
    }
}

val LocalImageLoader = staticCompositionLocalOf<ImageLoader> { error("No ImageLoader") }

@Composable
fun ReceiptImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    description: String? = null
) {
    val loader = LocalImageLoader.current
    val bmp by produceState<Bitmap?>(null, path) { value = path?.let { loader.load(it) } }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), description, modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ReceiptLong, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
