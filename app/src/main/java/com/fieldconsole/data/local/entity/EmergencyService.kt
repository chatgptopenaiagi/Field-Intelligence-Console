package com.fieldconsole.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * EmergencyService — a nearby emergency facility discovered via Overpass API.
 *
 * Cached locally so the user can access it offline after the initial query.
 */
@Entity(tableName = "emergency_services")
data class EmergencyService(
    @PrimaryKey(autoGenerate = true)
    val id:              Long   = 0L,
    val scanLat:         Double = 0.0,   // coordinates of the scan that triggered the query
    val scanLon:         Double = 0.0,
    val name:            String = "",
    val type:            String = "",    // HOSPITAL | POLICE | FIRE | PHARMACY | AED | SHELTER
    val address:         String = "",
    val phone:           String = "",
    val lat:             Double = 0.0,
    val lon:             Double = 0.0,
    val distanceM:       Float  = 0f,
    val travelTimeMin:   Int    = 0,
    val isOpen:          Boolean = false,
    val is24h:           Boolean = false,
    val fetchedAt:       Long   = System.currentTimeMillis()
)
