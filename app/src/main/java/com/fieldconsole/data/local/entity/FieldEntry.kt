package com.fieldconsole.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FieldEntry v2 — extended core scan entity.
 *
 * New fields (v2):
 *  - [placeName]    Resolved place name from reverse geocoder.
 *  - [priority]     User-assigned priority: LOW, MEDIUM, HIGH, CRITICAL.
 *  - [missionId]    Optional mission/operation identifier.
 *  - [operator]     Name or callsign of the operator who performed the scan.
 *  - [isFavorite]   Starred/bookmarked entry.
 *  - [weatherSnap]  JSON snapshot of weather at scan time (nullable).
 *  - [photoPath]    Absolute path to attached photo (nullable).
 *  - [audioPath]    Absolute path to attached voice recording (nullable).
 */
@Entity(tableName = "field_entries")
data class FieldEntry(
    @PrimaryKey(autoGenerate = true)
    val id:          Long    = 0L,
    val latitude:    Double  = 0.0,
    val longitude:   Double  = 0.0,
    val accuracy:    Float   = 0f,
    val note:        String  = "",
    val tag:         String  = "",
    val timestamp:   Long    = System.currentTimeMillis(),
    // v2 fields
    val placeName:   String  = "",
    val priority:    String  = "MEDIUM",   // LOW | MEDIUM | HIGH | CRITICAL
    val missionId:   String  = "",
    val operator:    String  = "",
    val isFavorite:  Boolean = false,
    val weatherSnap: String  = "",         // JSON string, empty if not captured
    val photoPath:   String  = "",
    val audioPath:   String  = ""
)
