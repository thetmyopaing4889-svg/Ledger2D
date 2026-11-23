package com.myanmar.ledger2d.feature.main

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guards for all connected profile routes, not just the input form. */
class CherryProfileIntegrationContractTest {
    private fun source(path: String) = File("src/main/java/com/myanmar/ledger2d/" + path)
        .readText(Charsets.UTF_8)
    private val screen by lazy { source("feature/main/Screens.kt") }
    private val forms by lazy { source("feature/main/CherryProfileFormUi.kt") }
    private val lists by lazy { source("feature/main/ListWorkspaceScreens.kt") }
    private val vm by lazy { source("feature/main/LedgerViewModel.kt") }
    private val store by lazy { source("core/profile/ProfilePhotoStore.kt") }
    private val archive by lazy { source("core/profile/CherryProfileArchive.kt") }
    private val backup by lazy { source("core/backup/CherryBackupManager.kt") }
    private val db by lazy { source("core/database/LedgerDatabase.kt") }

    @Test fun bothFormsUseGalleryOnlyAndDoNotRemoveGlobalQuickEntry() {
        assertTrue(screen.contains("CherryProfileFormPage("))
        assertTrue(screen.contains("ProfileKind.AGENT,id"))
        assertTrue(screen.contains("ProfileKind.CUSTOMER,id"))
        assertTrue(screen.contains("ActivityResultContracts.PickVisualMedia()"))
        assertTrue(screen.contains("PickVisualMedia.ImageOnly"))
        assertFalse(forms.contains("TakePicture"))
        assertFalse(forms.contains("CameraPermission"))
        assertTrue(forms.contains("LocalQuickEntryAction.current"))
        assertTrue(forms.contains("QuickEntryFab(it)"))
        assertTrue(forms.contains("onRemove: (() -> Unit)? = null"))
        assertTrue(forms.contains("photos.draftFile(pickedUri)"))
        assertTrue(forms.contains("BackHandler(onBack = onBack)"))
    }

    @Test fun saveValidationIsExplicitAndBusinessRatesDoNotChange() {
        assertTrue(screen.contains("val canSave=name.trim().isNotEmpty()"))
        assertTrue(screen.contains("id>0L||validCommission"))
        assertTrue(screen.contains("val validCommission=rate!=null"))
        assertTrue(screen.contains("vm.saveAgentWithPhoto("))
        assertTrue(screen.contains("vm.saveCustomerWithPhoto("))
        assertTrue(vm.contains("saveWithRateSchedule(entity,effectiveDate,effectiveSession,forceSessionReprice)"))
        assertTrue(vm.contains("commissionRateBasisPoints=old?.commissionRateBasisPoints?:openingRate!!"))
        assertFalse(screen.contains("ကော်မရှင်ကို 0% သတ်မှတ်လိုပါက 0 ဟု ကိုယ်တိုင်ထည့်ပါ"))
        assertTrue(forms.contains("onClick=onSave,enabled=saveEnabled"))
    }

    @Test fun galleryPhotoIsBoundToRecordAndShownInAllProfileSurfaces() {
        assertTrue(store.contains("photoDirectory(context)"))
        assertTrue(store.contains("kind.stem + \"-\" + id + \".jpg\""))
        assertTrue(store.contains("512f / max(w, h)"))
        assertTrue(store.contains("StandardCopyOption.ATOMIC_MOVE"))
        assertTrue(vm.contains("saveProfileAtomically(container.database, container.profilePhotos,"))
        assertTrue(vm.contains("ProfileKind.AGENT, photoChange"))
        assertTrue(vm.contains("ProfileKind.CUSTOMER, photoChange"))
        assertTrue(store.contains("applyReversible("))
        assertTrue(store.contains("class AppliedChange"))
        assertTrue(screen.contains("stageGallery(uri,draftKey)"))
        assertTrue(screen.contains("ProfilePhotoChange.ReplaceDraft(selectedPhoto!!)"))
        assertTrue(lists.contains("ProfileKind.AGENT,agent.id"))
        assertTrue(lists.contains("ProfileKind.CUSTOMER,customer.id"))
        assertTrue(lists.contains("ProfileKind.AGENT,id"))
        assertTrue(lists.contains("ProfileKind.CUSTOMER,id"))
        assertTrue(screen.contains("ProfileKind.AGENT,agent.id"))
        assertTrue(screen.contains("ProfileKind.CUSTOMER,customer.id"))
    }

    @Test fun encryptedBackupCarriesPhotosAndRetainsLegacyFormatSupport() {
        assertTrue(backup.contains("private const val ARCHIVE_VERSION = 2"))
        assertTrue(backup.contains("version in 1..ARCHIVE_VERSION"))
        assertTrue(backup.contains("ZipEntry(\"photos.zip\")"))
        assertTrue(backup.contains("CherryProfileArchive.write(context, KeepOpen(zip))"))
        assertTrue(backup.contains("CherryProfileArchive.read(it,staging)"))
        assertTrue(backup.contains("photosMoved=true"))
        assertTrue(backup.contains("photos.deleteRecursively()"))
        assertTrue(archive.contains("MAX_TOTAL = 128L * 1024L * 1024L"))
        assertTrue(archive.contains("validName.matches(entry.name)"))
        assertTrue(db.contains("version = 12")) // No destructive Room migration.
    }

    @Test fun weekColorsAndHomeGraphicsRemainIntact() {
        assertTrue(screen.contains("date in closedSet -> Color(0xFFFFD4E0)"))
        assertTrue(screen.contains("morning && digit==\"--\" -> Color(0xFFF9DEE8)"))
        assertTrue(screen.contains("!morning && digit==\"--\" -> Color(0xFFEBDCED)"))
        assertTrue(screen.contains("morning -> Color(0xFFF6BFD3)"))
        assertTrue(screen.contains("else -> Color(0xFFE3CDE7)"))
        assertTrue(screen.contains("drawCircle(Color(0x66C2185B),radius=radius"))
        assertTrue(screen.contains("R.drawable.cherry_header_art"))
        assertTrue(screen.contains("R.drawable.btn_cherry_orb"))
        assertTrue(screen.contains("R.drawable.bg_live_wave"))
    }
}
