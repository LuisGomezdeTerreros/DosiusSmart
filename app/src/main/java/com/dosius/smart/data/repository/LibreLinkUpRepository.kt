package com.dosius.smart.data.repository

import com.dosius.smart.data.local.dao.GlucoseReadingDao
import com.dosius.smart.data.preferences.CredentialPreferences
import com.dosius.smart.data.remote.api.LibreLinkUpApi
import com.dosius.smart.data.remote.dto.LoginRequestDto
import com.dosius.smart.data.remote.mapper.toDomain
import com.dosius.smart.domain.model.GlucoseReading
import com.dosius.smart.domain.repository.GlucoseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibreLinkUpRepository @Inject constructor(
    private val api: LibreLinkUpApi,
    private val dao: GlucoseReadingDao,
    private val prefs: CredentialPreferences
) : GlucoseRepository {

    override suspend fun getLatestReading(): GlucoseReading? {
        return dao.getLatestReading()
    }

    override suspend fun getReadingClosestTo(epochSeconds: Long): GlucoseReading? {
        return dao.getReadingClosestTo(epochSeconds)
    }

    override suspend fun getGlucoseHistory(): List<GlucoseReading> {
        val since = Clock.System.now().minus(24, DateTimeUnit.HOUR).epochSeconds
        return dao.getReadingsAfterTimestamp(since)
    }

    override fun getReadingsStream(): Flow<List<GlucoseReading>> {
        return dao.getAllReadings()
    }

    suspend fun refresh() {
        val token = getValidToken() ?: return
        val patientId = prefs.patientId.first() ?: return
        val accountId = prefs.accountId.first() ?: return
        val connectionsResponse = api.getConnections("Bearer $token", accountId)

        val graphResponse = api.getGraph(patientId, "Bearer $token", accountId)
        var readings = buildList {
            graphResponse.data?.connection?.glucoseMeasurement?.let { add(it.toDomain()) }
            addAll(graphResponse.data?.graphData?.map { it.toDomain() } ?: emptyList())
        }
        dao.insertAll(readings)

        readings = buildList {
            connectionsResponse.data.firstOrNull()?.glucoseMeasurement?.let { add(it.toDomain()) }
        }
        dao.insertAll(readings)
    }

    private suspend fun getValidToken(): String? {
        val nowSeconds = Clock.System.now().epochSeconds
        val storedToken = prefs.token.first()
        val storedExpiry = prefs.tokenExpires.first()

        if (storedToken != null && storedExpiry > nowSeconds + 3600) {
            return storedToken
        }

        val email = prefs.email.first().takeIf { it.isNotBlank() } ?: return null
        val password = prefs.password.first().takeIf { it.isNotBlank() } ?: return null

        return try {
            val loginResponse = api.login(LoginRequestDto(email, password))
            if (loginResponse.status != 0) throw Exception("Invalid credentials (status ${loginResponse.status})")
            val ticket =
                loginResponse.data?.authTicket ?: throw Exception("Unexpected: no auth ticket")
            val userId = loginResponse.data?.user?.id ?: return null
            val accountId =
                MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            val connections = api.getConnections("Bearer ${ticket.token}", accountId)
            val patientId = connections.data.firstOrNull()?.patientId ?: return null
            prefs.saveAuthTicket(ticket.token, ticket.expires, patientId, accountId)
            ticket.token
        } catch (e: Exception) {
            throw e
        }
    }
}
