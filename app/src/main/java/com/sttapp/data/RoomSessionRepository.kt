package com.sttapp.data

import com.sttapp.core.model.Session
import com.sttapp.data.local.SessionDao
import com.sttapp.data.local.toDomain
import com.sttapp.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSessionRepository @Inject constructor(
    private val dao: SessionDao,
) : SessionRepository {

    override fun observeSessions(): Flow<List<Session>> =
        dao.observeAll().map { sessions -> sessions.map { it.toDomain() } }

    override fun observeSession(id: Long): Flow<Session?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun insert(session: Session): Long = dao.insert(session.toEntity())

    override suspend fun update(session: Session) = dao.update(session.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
