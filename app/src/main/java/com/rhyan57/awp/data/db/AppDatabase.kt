package com.rhyan57.awp.data.db

import android.content.Context
import androidx.room.*
import com.rhyan57.awp.data.model.SavedScript
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedScriptDao {
    @Query("SELECT * FROM saved_scripts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SavedScript>>

    @Query("SELECT * FROM saved_scripts WHERE isAutoExec = 1")
    suspend fun getAutoExecScripts(): List<SavedScript>

    @Insert
    suspend fun insert(script: SavedScript): Long

    @Update
    suspend fun update(script: SavedScript)

    @Delete
    suspend fun delete(script: SavedScript)

    @Query("DELETE FROM saved_scripts WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Database(entities = [SavedScript::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedScriptDao(): SavedScriptDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "awp_db")
                    .build().also { INSTANCE = it }
            }
    }
}
