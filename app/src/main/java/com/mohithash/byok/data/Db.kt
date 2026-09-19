package com.mohithash.byok.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "results")
data class ResultRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val toolTitle: String,
    val emoji: String,
    val title: String,
    val inputSummary: String,
    /** engine.Doc JSON */
    val json: String,
    /** Comma-separated "section:item" indices ticked in checklists. */
    val ticks: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface ResultDao {
    @Query("SELECT * FROM results ORDER BY favorite DESC, createdAt DESC") fun all(): Flow<List<ResultRow>>
    @Query("SELECT * FROM results ORDER BY createdAt DESC LIMIT :n") fun recent(n: Int): Flow<List<ResultRow>>
    @Insert suspend fun insert(r: ResultRow): Long
    @Update suspend fun update(r: ResultRow)
    @Query("DELETE FROM results WHERE id = :id") suspend fun delete(id: Long)
}

@Database(entities = [ResultRow::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() { abstract fun results(): ResultDao }
