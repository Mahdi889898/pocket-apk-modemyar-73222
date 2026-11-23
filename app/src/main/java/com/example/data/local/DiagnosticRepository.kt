package com.example.data.local

import kotlinx.coroutines.flow.Flow

class DiagnosticRepository(private val dao: DiagnosticDao) {
    val allRecords: Flow<List<DiagnosticRecordEntity>> = dao.getAllRecords()

    suspend fun saveRecord(record: DiagnosticRecordEntity) {
        dao.insertRecord(record)
    }

    suspend fun deleteRecord(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
