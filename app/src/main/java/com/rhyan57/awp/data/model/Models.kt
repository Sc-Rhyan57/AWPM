package com.rhyan57.awp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_scripts")
data class SavedScript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isAutoExec: Boolean = false
)

data class ScriptBloxScript(
    val id: String,
    val title: String,
    val game: String,
    val script: String,
    val imageUrl: String?,
    val verified: Boolean,
    val views: Int
)

data class AppSettings(
    val deleteOriginalGuiOnExec: Boolean = true,
    val autoExecEnabled: Boolean = false,
    val wsPort: Int = 8765,
    val httpPort: Int = 8080,
    val uiVisible: Boolean = true,
    val uiLocked: Boolean = false
)

sealed class ConnectionState {
    object Idle : ConnectionState()
    object WaitingForClient : ConnectionState()
    data class Connected(val clientAddress: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
