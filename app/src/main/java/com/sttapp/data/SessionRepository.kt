package com.sttapp.data

import com.sttapp.core.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {

    fun observeSessions(): Flow<List<Session>>

    fun observeSession(id: Long): Flow<Session?>

    suspend fun insert(session: Session): Long

    suspend fun update(session: Session)

    suspend fun delete(id: Long)
}
