package com.myanmar.ledger2d.core.profile

import androidx.room.withTransaction
import com.myanmar.ledger2d.core.database.LedgerDatabase

/**
 * One atomic logical save: if a photo write (or a later transaction step) fails,
 * Room rolls the person and rate changes back, and the previous photo is restored.
 * The Gallery draft is retained after failures so the user can retry Save.
 */
internal suspend fun saveProfileAtomically(
    db: LedgerDatabase,
    photos: ProfilePhotoStore,
    kind: ProfileKind,
    photoChange: ProfilePhotoChange,
    afterPhotoApplied: () -> Unit = {},
    saveRecord: suspend () -> Long,
): Long {
    val prepared = photos.prepare(photoChange)
    var applied: ProfilePhotoStore.AppliedChange? = null
    try {
        val id = db.withTransaction {
            val id = saveRecord()
            require(id > 0L) { "Profile record could not be saved" }
            applied = photos.applyReversible(kind, id, prepared)
            afterPhotoApplied()
            id
        }
        // No throwing operations should follow a successful database commit.
        applied?.finish()
        if (photoChange is ProfilePhotoChange.ReplaceDraft)
            photos.discardDraft(photoChange.token)
        return id
    } catch (error: Throwable) {
        try { applied?.rollback() }
        catch (rollbackFailure: Throwable) { error.addSuppressed(rollbackFailure) }
        throw error
    } finally {
        prepared.cleanup()
    }
}
