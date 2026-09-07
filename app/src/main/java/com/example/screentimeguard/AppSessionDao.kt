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

    // --- Agrégats calculés à la volée (approche A) ---
    //
    // Pas de nouvelle table : app_sessions reste l'unique source de vérité.
    // strftime() convertit startTime (en millisecondes epoch UTC) en date
    // LOCALE au moment de la lecture. C'est important : si l'utilisateur
    // change de fuseau horaire plus tard, ça ne modifie pas rétroactivement
    // le regroupement des sessions déjà enregistrées dans le passé — seules
    // les nouvelles sessions seront regroupées selon le nouveau fuseau.
    //
    // "période" vaut "AAAA-MM-JJ" pour le jour, "AAAA-MM" pour le mois,
    // "AAAA" pour l'année. openCount = nombre d'ouvertures (une ligne
    // app_sessions = une session, déjà fusionnée si < 35s d'écart par
    // AppMonitorService avant l'insertion).

    @Query("""
        SELECT strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS period,
               SUM(durationMs) AS totalDurationMs,
               COUNT(*) AS openCount
        FROM app_sessions
        WHERE packageName = :packageName
        GROUP BY period
        ORDER BY period DESC
    """)
    suspend fun getDailyStats(packageName: String): List<UsageStat>

    @Query("""
        SELECT strftime('%Y-%m', startTime / 1000, 'unixepoch', 'localtime') AS period,
               SUM(durationMs) AS totalDurationMs,
               COUNT(*) AS openCount
        FROM app_sessions
        WHERE packageName = :packageName
        GROUP BY period
        ORDER BY period DESC
    """)
    suspend fun getMonthlyStats(packageName: String): List<UsageStat>

    @Query("""
        SELECT strftime('%Y', startTime / 1000, 'unixepoch', 'localtime') AS period,
               SUM(durationMs) AS totalDurationMs,
               COUNT(*) AS openCount
        FROM app_sessions
        WHERE packageName = :packageName
        GROUP BY period
        ORDER BY period DESC
    """)
    suspend fun getYearlyStats(packageName: String): List<UsageStat>

    // Répartition par heure de la journée (00–23), pour un jour donné
    // (format "AAAA-MM-JJ", en heure locale). Utile pour afficher à quelle(s)
    // heure(s) l'app a été ouverte ce jour-là (ex : histogramme 24h).
    @Query("""
        SELECT strftime('%H', startTime / 1000, 'unixepoch', 'localtime') AS period,
               SUM(durationMs) AS totalDurationMs,
               COUNT(*) AS openCount
        FROM app_sessions
        WHERE packageName = :packageName
          AND strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') = :date
        GROUP BY period
        ORDER BY period ASC
    """)
    suspend fun getHourlyBreakdown(packageName: String, date: String): List<UsageStat>
}

/**
 * Résultat d'une requête d'agrégation (jour, mois, année ou heure).
 *
 * `period` est une chaîne dont le format dépend de la requête utilisée :
 * "AAAA-MM-JJ" (getDailyStats), "AAAA-MM" (getMonthlyStats),
 * "AAAA" (getYearlyStats), ou "00".."23" (getHourlyBreakdown).
 * Room mappe automatiquement les colonnes de la requête vers ces champs
 * par nom, sans qu'on ait besoin d'une @Entity pour cette classe.
 */
data class UsageStat(
    val period: String,
    val totalDurationMs: Long,
    val openCount: Int
)