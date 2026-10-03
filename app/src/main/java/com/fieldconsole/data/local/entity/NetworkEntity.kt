package com.fieldconsole.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_entities")
data class NetworkEntity(@PrimaryKey val id: String, val displayName: String, val searchText: String, val json: String)

@Entity(tableName = "exchange_reports")
data class ExchangeReport(@PrimaryKey val id: String, val searchText: String, val json: String)
