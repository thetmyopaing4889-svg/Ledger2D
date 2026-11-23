package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.R
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
                .shadow(8.dp, shape, ambientColor = Color(0x55880E4F), spotColor = Color(0x55880E4F))
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
        if(onPick != null) {
            Box(Modifier.align(Alignment.BottomEnd).size(40.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFED3B78), cherryRed)))
                .clickable(onClick=onPick),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery မှ ပုံရွေးရန်",
                    tint=Color.White,modifier=Modifier.size(22.dp))
            }
            if (onRemove != null && (bitmap != null || pickedUri != null)) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(29.dp)
                        .clip(CircleShape).background(Color(0xEAFFFFFF))
                        .border(1.dp, Color(0xE0FFFFFF), CircleShape)
                        .clickable(onClick=onRemove),
                    contentAlignment=Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription="Profile ပုံဖယ်ရှားရန်",
                        tint=cherryWine,modifier=Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Only the two profile forms use this header. Shared AppScaffold remains untouched. */
@Composable
internal fun CherryProfileFormPage(
    title: String, onBack: () -> Unit,
    photos: ProfilePhotoStore, kind: ProfileKind, id: Long,
    pickedUri: String?, removed: Boolean,
    onPick: () -> Unit, onRemove: () -> Unit,
    canSave: Boolean, saving: Boolean = false, onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val quickEntry = LocalQuickEntryAction.current
    // Android system Back performs the same draft cleanup as the visible Cancel button.
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = Color(0xFFFCEEF3),
        floatingActionButton = { quickEntry?.let { QuickEntryFab(it) } },
        topBar = {
            Box(Modifier.fillMaxWidth().height(274.dp)) {
                Image(painter = androidx.compose.ui.res.painterResource(R.drawable.cherry_header_art),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(213.dp)
                        .clip(RoundedCornerShape(bottomStart=54.dp, bottomEnd=54.dp)),
                    contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxWidth().height(213.dp)
                    .clip(RoundedCornerShape(bottomStart=54.dp,bottomEnd=54.dp))
                    .background(Brush.verticalGradient(listOf(Color(0x44000000),Color.Transparent))))
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(top=18.dp,start=16.dp,end=19.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(11.dp),
                ) {
                    IconButton(
                        onClick=onBack,
                        modifier=Modifier.size(50.dp)
                            .shadow(6.dp,CircleShape,
                                ambientColor=Color(0x66880E4F),spotColor=Color(0x66880E4F))
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(
                                Color(0xE8FFFFFF),Color(0x92FFEAF2))))
                            .border(1.5.dp,Color.White,CircleShape),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription="နောက်သို့",tint=cherryWine,
                            modifier=Modifier.size(27.dp))
                    }
                    // The title gets its own bounded right-aligned area instead of
                    // spilling back over the back button and cherry artwork.
                    Text(
                        LocalLanguage.current.translate(title),
                        modifier=Modifier.weight(1f).padding(start=24.dp),
                        color=Color.White,
                        style=MaterialTheme.typography.titleLarge.copy(
                            fontSize=21.sp,lineHeight=29.sp),
                        fontWeight=FontWeight.Black,
                        textAlign=TextAlign.End,
                        maxLines=2,
                        overflow=TextOverflow.Ellipsis,
                    )
                }
                CherryProfileAvatar(photos,kind,id,Modifier.align(Alignment.BottomCenter).size(136.dp),
                    pickedUri,removed,onPick,onRemove)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(gentleBlush, Color(0xFFFCEEF3))))
                .verticalScroll(rememberScrollState())
                .padding(horizontal=17.dp,vertical=12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // An actual layered, translucent frosted pane: a softly lit rose
            // backdrop shows THROUGH the glass instead of a flat white Card.
            val paneShape=RoundedCornerShape(28.dp)
            Box(
                Modifier.fillMaxWidth()
                    .homeRubyShadow(radius=28.dp,blur=20.dp,offsetY=8.dp,
                        tint=Color(0x43880E4F))
                    .clip(paneShape)
                    .background(Brush.linearGradient(listOf(
                        Color(0xFFE6A8C4),Color(0xFFF3D4DF),Color(0xFFDFA2BD))))
                    .border(1.5.dp,Color.White,paneShape),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xC5ED6D9F),Color.Transparent),
                            center=Offset(size.width*.16f,size.height*.23f),
                            radius=size.width*.66f),
                        radius=size.width*.66f,center=Offset(size.width*.16f,size.height*.23f))
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0x8DF4B57C),Color.Transparent),
                            center=Offset(size.width*.72f,size.height*.46f),
                            radius=size.width*.51f),
                        radius=size.width*.51f,center=Offset(size.width*.72f,size.height*.46f))
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xA7A965A0),Color.Transparent),
                            center=Offset(size.width*.86f,size.height*.83f),
                            radius=size.width*.60f),
                        radius=size.width*.60f,center=Offset(size.width*.86f,size.height*.83f))
                }
                Box(Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(
                        Color(0xC6FFFFFF),Color(0x9EFFFFFF),Color(0xB8FFFFFF)))))
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .padding(horizontal=9.dp,vertical=2.dp).height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(
                        Color.Transparent,Color(0xD9FFFFFF),Color.Transparent))))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=20.dp),
                    verticalArrangement=Arrangement.spacedBy(15.dp),
                    content=content,
                )
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick=onBack,
                    modifier=Modifier.weight(1f).height(54.dp),
                    shape=RoundedCornerShape(28.dp),
                    border=androidx.compose.foundation.BorderStroke(1.5.dp,cherryWine),
                ) {
                    Text(LocalLanguage.current.text("မလုပ်တော့ပါ","Cancel"),color=cherryWine,
                        fontWeight=FontWeight.Bold)
                }
                val saveEnabled=canSave&&!saving
                val saveShape=RoundedCornerShape(29.dp)
                Box(
                    Modifier.weight(1f).height(58.dp)
                        .homeRubyShadow(radius=29.dp,blur=13.dp,offsetY=6.dp,
                            tint=if(saveEnabled)Color(0x72880E4F)else Color(0x12880E4F))
                        .clip(saveShape)
                        .background(Brush.verticalGradient(
                            if(saveEnabled)listOf(
                                Color(0xFFFF5E93),Color(0xFFBD064B),Color(0xFF78072E))
                            else listOf(
                                Color(0xFFE2D7DC),Color(0xFFD4C8CE),Color(0xFFCDC1C7))))
                        .border(1.2.dp,Color(0xE6FFFFFF),saveShape),
                ) {
                    // A real specular highlight creates the raised lacquered pill.
                    Box(Modifier.fillMaxWidth().fillMaxHeight(.43f)
                        .padding(horizontal=10.dp,vertical=4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.verticalGradient(listOf(
                            Color(0xB9FFFFFF),Color(0x26FFFFFF),Color.Transparent))))
                    Button(
                        onClick=onSave,enabled=saveEnabled,
                        modifier=Modifier.fillMaxSize(),
                        shape=saveShape,
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
            }
            Spacer(Modifier.height(104.dp)) // Keeps the intentionally global + reachable.
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
    val fieldShape=RoundedCornerShape(28.dp)
    OutlinedTextField(
        value=value,onValueChange=onValue,
        modifier=Modifier.fillMaxWidth().heightIn(min=70.dp)
            .shadow(2.dp,fieldShape,
                ambientColor=Color(0x21880E4F),spotColor=Color(0x21880E4F))
            .clip(fieldShape)
            .background(Brush.linearGradient(listOf(
                Color(0xDFFFFFFF),Color(0x9EFFFFFF),Color(0xB7FFE9F1))),fieldShape)
            .border(1.dp,Color(0xE6FFFFFF),fieldShape),
        singleLine=label!="မှတ်ချက်",
        label={Text(language.translate(label)+(if(required)" *" else ""))},
        leadingIcon={Icon(icon,contentDescription=null,tint=cherryWine)},
        keyboardOptions=KeyboardOptions(keyboardType=keyboard),
        shape=fieldShape,
        colors=OutlinedTextFieldDefaults.colors(
            focusedBorderColor=cherryRed,unfocusedBorderColor=Color(0xCBE29EBC),
            focusedContainerColor=Color.Transparent,
            unfocusedContainerColor=Color.Transparent,
            focusedLabelColor=cherryWine,unfocusedLabelColor=Color(0xFF7E6270),
            focusedTextColor=cherryWine,unfocusedTextColor=cherryWine,
        )
    )
}
