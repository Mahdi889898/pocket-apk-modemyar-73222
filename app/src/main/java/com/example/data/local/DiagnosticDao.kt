package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticDao {
    @Query("SELECT * FROM diagnostic_records ORDER BY timestamp DESC LIMIT 100")
    fun getAllRecords(): Flow<List<DiagnosticRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DiagnosticRecordEntity)

    @Query("DELETE FROM diagnostic_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM diagnostic_records")
    suspend fun clearAll()
}
