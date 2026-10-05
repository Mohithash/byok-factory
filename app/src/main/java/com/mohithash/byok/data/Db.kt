package com.mohithash.byok.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** The tool inputs as a JSON string map (for regenerate / edit & rerun). "__photo" = "yes" when a photo was attached. */
    val inputsJson: String = "",
    /** 0 for a tool result; for a follow-up, the id of the result that started the thread. */
    val rootId: Long = 0,
    /** The user's own note on this result. */
    val note: String = "",
) {
    /** Id of the first result in this row's thread. */
    val threadId: Long get() = if (rootId == 0L) id else rootId
    val isFollowUp: Boolean get() = rootId != 0L
}

@Dao
interface ResultDao {
    @Query("SELECT * FROM results ORDER BY favorite DESC, createdAt DESC") fun all(): Flow<List<ResultRow>>
    @Query("SELECT * FROM results ORDER BY createdAt DESC LIMIT :n") fun recent(n: Int): Flow<List<ResultRow>>
    @Query("SELECT * FROM results WHERE favorite = 1 ORDER BY createdAt DESC") fun favorites(): Flow<List<ResultRow>>
    @Query("SELECT * FROM results WHERE id = :root OR rootId = :root ORDER BY createdAt ASC, id ASC") fun thread(root: Long): Flow<List<ResultRow>>
    @Query("SELECT * FROM results WHERE id = :root OR rootId = :root ORDER BY createdAt ASC, id ASC") suspend fun threadOnce(root: Long): List<ResultRow>
    @Query("SELECT * FROM results ORDER BY createdAt ASC, id ASC") suspend fun allOnce(): List<ResultRow>
    @Query("SELECT * FROM results WHERE id = :id") suspend fun byId(id: Long): ResultRow?
    @Query("SELECT COUNT(*) FROM results") fun count(): Flow<Int>
    @Insert suspend fun insert(r: ResultRow): Long
    /** Re-inserts a row with its original id (undo delete). */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restore(r: ResultRow): Long
    @Update suspend fun update(r: ResultRow)
    @Query("DELETE FROM results WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM results") suspend fun deleteAll()
}

@Database(entities = [ResultRow::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun results(): ResultDao

    companion object {
        /** v2: inputs kept for regenerate, follow-up threads, notes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE results ADD COLUMN inputsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE results ADD COLUMN rootId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE results ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
