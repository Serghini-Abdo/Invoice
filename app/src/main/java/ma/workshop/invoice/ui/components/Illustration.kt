package ma.workshop.invoice.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma.workshop.invoice.domain.catalog.PhotoKey
import ma.workshop.invoice.platform.PhotoStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ma.workshop.invoice.domain.catalog.Illustration
import ma.workshop.invoice.domain.catalog.Illustrations

/** Builds a Compose vector from the shared illustration data (the PDF/PNG renderer reads the same data). */
fun Illustration.toImageVector(): ImageVector {
    val size = Illustrations.VIEWPORT.toFloat()
    val builder = ImageVector.Builder(
        name = key,
        defaultWidth = 100.dp,
        defaultHeight = 100.dp,
        viewportWidth = size,
        viewportHeight = size,
    )
    for (part in parts) {
        builder.addPath(
            pathData = addPathNodes(part.pathData),
            pathFillType = if (part.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
            fill = part.fill?.let { SolidColor(Color(it)) },
            stroke = part.stroke?.let { SolidColor(Color(it)) },
            strokeLineWidth = part.strokeWidth,
        )
    }
    return builder.build()
}

/** A product picture. Unknown keys fall back to a generic picture instead of failing. */
@Composable
fun IllustrationImage(key: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (PhotoKey.isPhoto(key)) {
        val context = LocalContext.current
        val photo by produceState<ImageBitmap?>(null, key) {
            value = withContext(Dispatchers.IO) {
                PhotoStore.file(context, key)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
            }
        }
        photo?.let {
            Image(bitmap = it, contentDescription = contentDescription, modifier = modifier.clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            return
        }
        // A missing file (for example after a restore on another phone) shows the generic picture.
    }
    val vector = remember(key) { Illustrations.get(key).toImageVector() }
    Image(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
