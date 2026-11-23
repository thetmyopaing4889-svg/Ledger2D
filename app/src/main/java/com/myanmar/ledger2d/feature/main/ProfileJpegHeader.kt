package com.myanmar.ledger2d.feature.main

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.myanmar.ledger2d.core.profile.ProfileKind
import com.myanmar.ledger2d.core.profile.ProfilePhotoStore

/** Read one small immutable portrait snapshot for a JPEG preview/export session.
 * Never reference a Gallery URI, change the original file or keep a full-size bitmap.
 * Caller invokes this on Dispatchers.IO and shares the result across JPEG pages.
 */
internal fun loadJpegProfilePhoto(
    photos: ProfilePhotoStore, kind: ProfileKind, id: Long,
): Bitmap? {
    if (id <= 0L) return null
    return runCatching {
        val file = photos.photoFile(kind, id)
        if (!file.isFile || file.length() <= 3L) null
        else BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = 2 // persisted portraits are max 512px
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }.getOrNull()
}

/** Header-only circle. Kept completely above the divider (y = 333) on 1080x1920 pages.
 * Both printed JPEGs and their previews use the same renderer call.
 */
internal fun drawJpegProfileAvatar(
    canvas: Canvas, portrait: Bitmap?, left: Float = 904f, top: Float = 204f,
    diameter: Float = 122f,
) {
    val cx = left + diameter / 2f
    val cy = top + diameter / 2f
    val r = diameter / 2f
    val pale = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 233, 242) }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(155, 29, 75)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(cx, cy, r, pale)
    if (portrait != null && !portrait.isRecycled && portrait.width > 0 && portrait.height > 0) {
        val shortSide = minOf(portrait.width, portrait.height)
        val src = Rect(
            (portrait.width - shortSide) / 2,
            (portrait.height - shortSide) / 2,
            (portrait.width + shortSide) / 2,
            (portrait.height + shortSide) / 2,
        )
        val circle = Path().apply { addCircle(cx, cy, r - 2f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(circle)
        canvas.drawBitmap(portrait, src, RectF(left, top, left + diameter, top + diameter),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        // No photo: draw a deterministic avatar silhouette, not an empty white circle.
        val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(173, 96, 125) }
        canvas.drawCircle(cx, cy - r * .23f, r * .23f, icon)
        canvas.drawRoundRect(
            RectF(cx - r * .46f, cy + r * .08f, cx + r * .46f, cy + r * .65f),
            r * .24f, r * .24f, icon,
        )
    }
    canvas.drawCircle(cx, cy, r - 1.5f, ring)
}
