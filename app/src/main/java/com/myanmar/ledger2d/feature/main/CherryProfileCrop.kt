package com.myanmar.ledger2d.feature.main

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.myanmar.ledger2d.core.profile.ProfilePhotoStore
import java.io.File

/**
 * Explicit square crop, not the implicit, unreliable Android ACTION_CROP.
 * The preview and the JPEG writer both use ProfilePhotoStore.CropMath.squareCrop,
 * so what the user sees is exactly the photo region that will be saved.
 */
@Composable
internal fun CherryProfileCropDialog(
    stagedPhoto: File,
    onCancel: () -> Unit,
    onConfirm: (zoom: Float, panX: Float, panY: Float) -> Unit,
) {
    val bitmap = remember(stagedPhoto.absolutePath, stagedPhoto.lastModified()) {
        BitmapFactory.decodeFile(stagedPhoto.absolutePath)
    }
    var zoom by remember(stagedPhoto.absolutePath, stagedPhoto.lastModified()) { mutableFloatStateOf(1f) }
    var panX by remember(stagedPhoto.absolutePath, stagedPhoto.lastModified()) { mutableFloatStateOf(0f) }
    var panY by remember(stagedPhoto.absolutePath, stagedPhoto.lastModified()) { mutableFloatStateOf(0f) }
    val displaySize = with(LocalDensity.current) { 252.dp.toPx() }
    val sourcePreview = remember(bitmap, zoom, panX, panY) {
        bitmap?.let { ProfilePhotoStore.CropMath.squareCrop(it,zoom,panX,panY).asImageBitmap() }
    }
    Dialog(onDismissRequest=onCancel) {
        Surface(shape=RoundedCornerShape(26.dp),color=Color(0xFFFFF4F7),
            tonalElevation=8.dp,shadowElevation=12.dp) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=20.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(10.dp),
            ) {
                Text("ပုံဖြတ်ညှိရန်",style=MaterialTheme.typography.titleLarge,
                    color=Color(0xFF8D0C3D),fontWeight=FontWeight.Bold)
                Text("ပုံကိုဆွဲရွှေ့ပြီး အရွယ်အစားချိန်ညှိပါ",
                    style=MaterialTheme.typography.bodySmall,color=Color(0xFF775863))
                if(sourcePreview!=null) {
                    Image(sourcePreview,contentDescription="Crop ပုံနမူနာ",
                        modifier=Modifier.size(252.dp).clip(CircleShape)
                            .border(2.dp,Color(0xFFCC7B9B),CircleShape)
                            .pointerInput(bitmap,zoom) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // Drag the picture right -> move the crop center left.
                                    panX=(panX - dragAmount.x*2f / displaySize).coerceIn(-1f,1f)
                                    panY=(panY - dragAmount.y*2f / displaySize).coerceIn(-1f,1f)
                                }
                            },
                        contentScale=ContentScale.FillBounds)
                } else {
                    Text("ပုံဖိုင် မဖတ်နိုင်ပါ",color=MaterialTheme.colorScheme.error)
                }
                Text("ချဲ့ရန် / Zoom",style=MaterialTheme.typography.labelMedium)
                Slider(value=zoom,onValueChange={zoom=it},valueRange=1f..3f)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick=onCancel,modifier=Modifier.weight(1f),
                        shape=RoundedCornerShape(24.dp)
                    ) { Text("မလုပ်တော့ပါ") }
                    Button(
                        onClick={onConfirm(zoom,panX,panY)},
                        enabled=bitmap!=null,
                        modifier=Modifier.weight(1f),
                        shape=RoundedCornerShape(24.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB40B48))
                    ) { Text("ပုံဖြတ်သိမ်းမည်") }
                }
            }
        }
    }
}
