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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

private fun decodeThumbnail(context: Context, uri: Uri?, file: File?): ImageBitmap? {
    fun open() = if (uri != null) context.contentResolver.openInputStream(uri)
        else file?.takeIf { it.isFile }?.inputStream()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
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
                IconButton(
                    modifier=Modifier.align(Alignment.TopEnd).size(31.dp)
                        .clip(CircleShape).background(Color(0xE6FFFFFF)),
                    onClick=onRemove,
                ) {
                    Icon(Icons.Default.Close, contentDescription="Profile ပုံဖယ်ရှားရန်",
                        tint=cherryWine,modifier=Modifier.size(17.dp))
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
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top=18.dp,start=18.dp,end=16.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(
                        onClick=onBack,
                        modifier=Modifier.size(52.dp).clip(CircleShape)
                            .background(Color(0xBBFFFFFF))
                            .border(1.dp,Color.White,CircleShape),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription="နောက်သို့",tint=cherryWine,modifier=Modifier.size(29.dp))
                    }
                    Text(LocalLanguage.current.translate(title),color=Color.White,
                        style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black)
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
            Column(Modifier.fillMaxWidth()
                .shadow(14.dp,RoundedCornerShape(28.dp),
                    ambientColor=Color(0x2E880E4F),spotColor=Color(0x2E880E4F))
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xE8FFFFFF))
                .border(1.5.dp,Color.White,RoundedCornerShape(28.dp))
                .padding(horizontal=16.dp,vertical=20.dp),
                verticalArrangement=Arrangement.spacedBy(15.dp),
                content=content,
            )
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
                Button(
                    onClick=onSave, enabled=canSave&&!saving,
                    modifier=Modifier.weight(1f).height(54.dp)
                        .shadow(if(canSave&&!saving) 7.dp else 0.dp,RoundedCornerShape(28.dp),
                            ambientColor=Color(0x55880E4F),spotColor=Color(0x55880E4F)),
                    shape=RoundedCornerShape(28.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=cherryRed),
                ) {
                    if(saving) CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,
                        color=Color.White)
                    else Text(LocalLanguage.current.text("သိမ်းမည်","Save"),
                        fontWeight=FontWeight.Bold)
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
    OutlinedTextField(
        value=value,onValueChange=onValue,
        modifier=Modifier.fillMaxWidth().heightIn(min=70.dp),
        singleLine=label!="မှတ်ချက်",
        label={Text(language.translate(label)+(if(required)" *" else ""))},
        leadingIcon={Icon(icon,contentDescription=null,tint=cherryWine)},
        keyboardOptions=KeyboardOptions(keyboardType=keyboard),
        shape=RoundedCornerShape(28.dp),
        colors=OutlinedTextFieldDefaults.colors(
            focusedBorderColor=cherryRed,unfocusedBorderColor=Color(0xFFF2B6CA),
            focusedContainerColor=Color(0xDFFFF8FA),
            unfocusedContainerColor=Color(0xDFFFF8FA),
            focusedLabelColor=cherryWine,unfocusedLabelColor=Color(0xFF7E6270),
            focusedTextColor=cherryWine,unfocusedTextColor=cherryWine,
        )
    )
}
