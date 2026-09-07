package com.example.screentimeguard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AppSessionDao {

    @Insert
    suspend fun insertSession(session: AppSession)

    @Query("SELECT * FROM app_sessions WHERE packageName = :packageName ORDER BY startTime DESC")
    suspend fun getSessionsForApp(packageName: String): List<AppSession>

    @Query("SELECT SUM(durationMs) FROM app_sessions WHERE packageName = :packageName AND startTime >= :since")
    suspend fun getTotalDurationSince(packageName: String, since: Long): Long?

    @Query("SELECT * FROM app_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<AppSession>
}