package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.profile.ProfileKind
import com.myanmar.ledger2d.core.profile.ProfilePhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val cherryWine = Color(0xFF8D0C3D)
private val cherryRed = Color(0xFFB40B48)
private val gentleBlush = Color(0xFFF6DCE5)

/** Bounds-only decode returns null by design; dimensions, not bitmap, are the result. */
internal fun decodeThumbnail(context: Context, uri: Uri?, file: File?): ImageBitmap? {
    fun open() = if (uri != null) context.contentResolver.openInputStream(uri)
        else file?.takeIf { it.isFile }?.inputStream()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val dimensions = open() ?: return null
    dimensions.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 640) sample *= 2
    return open()?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }?.asImageBitmap()
}

/** One reusable avatar for both lists, information screens and editable forms. */
@Composable
internal fun CherryProfileAvatar(
    photos: ProfilePhotoStore, kind: ProfileKind, id: Long,
    modifier: Modifier = Modifier,
    pickedUri: String? = null,
    removed: Boolean = false,
    onPick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val revision by ProfilePhotoStore.revision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, kind, id, pickedUri, removed, revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (removed) null else decodeThumbnail(
                    context, null,
                    if (pickedUri != null) photos.draftFile(pickedUri)
                    else if (id > 0L) photos.photoFile(kind, id) else null,
                )
            }.getOrNull()
        }
    }
    val shape = CircleShape
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                // Reserve a narrow rim for form-only edit controls; list avatars stay unchanged.
                .then(if (onPick != null) Modifier.padding(9.dp) else Modifier)
                .shadow(6.dp, shape, ambientColor = Color(0x44880E4F), spotColor = Color(0x44880E4F))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(Color.White, gentleBlush)))
                .border(2.dp, Color.White, shape)
                .then(if(onPick != null) Modifier.clickable(onClick=onPick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) Image(bitmap!!, contentDescription = "Profile Photo",
                modifier = Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, contentDescription = "Profile Photo",
                tint = Color(0xB6AE6C83), modifier = Modifier.fillMaxSize(.67f))
        }
        if (onPick != null) {
            // 48dp tap target; only the inner 34dp circle is painted.
            Box(
                Modifier.align(Alignment.BottomEnd).size(48.dp)
                    .clickable(onClick=onPick),
                contentAlignment=Alignment.Center,
            ) {
                Box(
                    Modifier.size(34.dp).shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFFED3B78),cherryRed))),
                    contentAlignment=Alignment.Center,
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription="Gallery မှ ပုံရွေးရန်",
                        tint=Color.White, modifier=Modifier.size(18.dp))
                }
            }
            if (onRemove != null && (bitmap != null || pickedUri != null)) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(44.dp)
                        .clickable(onClick=onRemove),
                    contentAlignment=Alignment.Center,
                ) {
                    Box(
                        Modifier.size(27.dp).clip(CircleShape)
                            .background(Color(0xF5FFFFFF))
                            .border(1.dp,Color(0xFFE8BECE),CircleShape),
                        contentAlignment=Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription="Profile ပုံဖယ်ရှားရန်",
                            tint=cherryWine,modifier=Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** The same centered title and left Back action as the other inner screens.
 * Keep the Home cherry artwork exclusive to HomeScreen.
 */
@Composable
internal fun CherryProfileFormPage(
    title: String, onBack: () -> Unit,
    photos: ProfilePhotoStore, kind: ProfileKind, id: Long,
    pickedUri: String?, removed: Boolean,
    onPick: () -> Unit, onRemove: () -> Unit,
    canSave: Boolean, saving: Boolean = false, onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    AppScaffold(title=title, onBack=onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(gentleBlush, Color(0xFFFCEEF3))))
                .imePadding(),
            horizontalAlignment=Alignment.CenterHorizontally,
        ) {
            // Pinned above the field scroller: scrolling cannot cut the portrait in half.
            // Hide it while the keyboard is open so the editable area stays usable.
            if (!keyboardVisible) {
                Box(
                    Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),
                    contentAlignment=Alignment.Center,
                ) {
                    CherryProfileAvatar(
                        photos, kind, id, Modifier.size(128.dp),
                        pickedUri, removed, onPick, onRemove,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start=17.dp,end=17.dp,top=10.dp,bottom=9.dp),
            ) {
                val paneShape=RoundedCornerShape(24.dp)
                Box(
                    Modifier.fillMaxWidth()
                        .homeRubyShadow(radius=24.dp,blur=14.dp,offsetY=5.dp,
                            tint=Color(0x28880E4F))
                        .clip(paneShape)
                        .background(Brush.verticalGradient(listOf(
                            Color(0xFFFEF8FB),Color(0xFFF4DFE9))))
                        .border(1.dp,Color(0xE6FFFFFF),paneShape),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal=15.dp,vertical=16.dp),
                        verticalArrangement=Arrangement.spacedBy(11.dp),
                        content=content,
                    )
                }
            }
            // Keep both actions at one height, outside the scroller; one Save surface.
            Row(
                Modifier.fillMaxWidth().padding(horizontal=17.dp,vertical=10.dp),
                horizontalArrangement=Arrangement.spacedBy(12.dp),
            ) {
                val actionShape=RoundedCornerShape(27.dp)
                OutlinedButton(
                    onClick=onBack,
                    modifier=Modifier.weight(1f).height(54.dp),
                    shape=actionShape,
                    border=androidx.compose.foundation.BorderStroke(1.3.dp,cherryWine),
                ) {
                    Text(LocalLanguage.current.text("မလုပ်တော့ပါ","Cancel"),
                        color=cherryWine,fontWeight=FontWeight.Bold)
                }
                val saveEnabled=canSave&&!saving
                Button(
                    onClick=onSave,enabled=saveEnabled,
                    modifier=Modifier.weight(1f).height(54.dp)
                        .homeRubyShadow(radius=27.dp,blur=10.dp,offsetY=4.dp,
                            tint=if(saveEnabled) Color(0x52880E4F) else Color(0x16880E4F))
                        .clip(actionShape)
                        .background(Brush.verticalGradient(
                            if(saveEnabled) listOf(
                                Color(0xFFF44982),Color(0xFFBC0C50),Color(0xFF820A38))
                            else listOf(
                                Color(0xFFE9E1E5),Color(0xFFDAD1D6),Color(0xFFCAC0C7))))
                        .border(1.dp,if(saveEnabled) Color(0xBBFFD7E5)
                            else Color(0xA6FFFFFF),actionShape),
                    shape=actionShape,
                    colors=ButtonDefaults.buttonColors(
                        containerColor=Color.Transparent,
                        disabledContainerColor=Color.Transparent,
                        contentColor=Color.White,
                        disabledContentColor=Color(0xFF857782)),
                    elevation=null,
                ) {
                    if(saving) CircularProgressIndicator(Modifier.size(18.dp),
                        strokeWidth=2.dp,color=Color.White)
                    else Text(LocalLanguage.current.text("သိမ်းမည်","Save"),
                        style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.Black)
                }
            }
            // Small FAB clearance, not an extra 104dp inside the form scroller.
            if (!keyboardVisible) Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
internal fun CherryProfileField(
    value: String, onValue: (String)->Unit, label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    required: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val language=LocalLanguage.current
    val fieldShape=RoundedCornerShape(19.dp)
    val interaction=remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // A single border encloses the always-visible label and editable text.
    Row(
        modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)
            .clip(fieldShape)
            .background(Brush.verticalGradient(listOf(
                Color(0xFFFDFBFC),Color(0xFFF8EDF2))))
            .border(
                if(focused) 1.5.dp else 1.dp,
                if(focused) cherryRed else Color(0xC8DDAABD),
                fieldShape),
        verticalAlignment=Alignment.CenterVertically,
    ) {
        Icon(icon,contentDescription=null,tint=cherryWine,
            modifier=Modifier.padding(start=15.dp).size(21.dp))
        Column(
            Modifier.weight(1f).padding(start=12.dp,end=14.dp,top=8.dp,bottom=8.dp),
            verticalArrangement=Arrangement.spacedBy(2.dp),
        ) {
            Text(
                language.translate(label)+(if(required)" *" else ""),
                color=if(focused)cherryRed else Color(0xFF806B75),
                style=MaterialTheme.typography.labelMedium,
                maxLines=2,
            )
            BasicTextField(
                value=value,
                onValueChange=onValue,
                modifier=Modifier.fillMaxWidth().heightIn(min=24.dp),
                singleLine=label!="မှတ်ချက်",
                keyboardOptions=KeyboardOptions(keyboardType=keyboard),
                interactionSource=interaction,
                cursorBrush=SolidColor(cherryWine),
                textStyle=MaterialTheme.typography.bodyLarge.copy(
                    color=cherryWine,fontWeight=FontWeight.Medium),
            )
        }
    }
}
