package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.data.repository.ExchangeConnectionRepository
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.exchange.ExchangeApiFactory
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Result of credential validation and save operation.
 */
sealed class CredentialValidationResult {
    /** Credentials valid and saved. Carries the resulting connectionId so callers can navigate. */
    data class Success(val connectionId: Long) : CredentialValidationResult()
    data class Error(val message: String) : CredentialValidationResult()
    data object NetworkError : CredentialValidationResult()
}

/**
 * Use case for validating and saving exchange credentials, connection-aware.
 *
 * Workflow:
 * 1. Validates required fields (API key, secret, optional clientId for Coinmate)
 * 2. Resolves or creates a connection (envelope) for these credentials
 * 3. Calls the exchange API to verify the credentials are usable
 * 4. On success: saves credentials under the connection's id
 * 5. On failure: if a connection was just created for this call, deletes it as rollback
 *
 * Phase 7 (UI rewrite) will pass an explicit `connectionName` to differentiate envelopes
 * on the same exchange. Until then, callers omit the name and the use case uses or
 * creates the default (empty-named) connection per exchange.
 */
class ValidateAndSaveCredentialsUseCase @Inject constructor(
    private val exchangeApiFactory: ExchangeApiFactory,
    private val credentialsStore: CredentialsStore,
    private val connectionRepository: ExchangeConnectionRepository,
    private val userPreferences: UserPreferences
) {
    /**
     * Validate credentials with the exchange and save if valid.
     *
     * @param exchange The exchange to validate against
     * @param apiKey The API key
     * @param apiSecret The API secret
     * @param passphrase Optional passphrase (required for some exchanges like KuCoin)
     * @param clientId Optional client ID (required for Coinmate)
     * @param connectionName Optional connection name. When null, the use case uses or
     *  creates the default (empty-named) connection for [exchange]. Phase 7 UI will pass
     *  an explicit name to create separate envelopes.
     * @param existingConnectionId If non-null, save credentials against this existing
     *  connection (for "edit credentials" flows). When null, a new connection may be
     *  created.
     */
    suspend fun execute(
        exchange: Exchange,
        apiKey: String,
        apiSecret: String,
        passphrase: String? = null,
        clientId: String? = null,
        connectionName: String? = null,
        existingConnectionId: Long? = null
    ): CredentialValidationResult {
        // Validate required fields
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return CredentialValidationResult.Error("Please enter both API key and secret")
        }

        // Coinmate requires Client ID
        if (exchange == Exchange.COINMATE && clientId.isNullOrBlank()) {
            return CredentialValidationResult.Error("Please enter your Client ID")
        }

        // Build credentials
        val credentials = ExchangeCredentials(
            exchange = exchange,
            apiKey = apiKey.trim(),
            apiSecret = apiSecret.trim(),
            passphrase = passphrase?.trim()?.takeIf { it.isNotBlank() },
            clientId = clientId?.trim()?.takeIf { it.isNotBlank() }
        )

        // Resolve target connection. Track whether we created it ourselves so we can
        // roll back on validation failure.
        val (connectionId, createdHere) = resolveConnection(exchange, connectionName, existingConnectionId)

        return try {
            val isSandbox = userPreferences.isSandboxMode()

            val api = exchangeApiFactory.create(credentials)
            val isValid = api.validateCredentials()

            if (isValid) {
                credentialsStore.saveCredentials(connectionId, credentials, isSandbox)
                CredentialValidationResult.Success(connectionId)
            } else {
                if (createdHere) connectionRepository.delete(connectionId, deletePlans = false)
                val hint = if (isSandbox) {
                    " Make sure you are using API keys generated on the exchange's sandbox/testnet (not production keys)."
                } else ""
                CredentialValidationResult.Error("Invalid API credentials.$hint")
            }
        } catch (e: UnknownHostException) {
            if (createdHere) connectionRepository.delete(connectionId, deletePlans = false)
            CredentialValidationResult.NetworkError
        } catch (e: java.io.IOException) {
            if (createdHere) connectionRepository.delete(connectionId, deletePlans = false)
            CredentialValidationResult.NetworkError
        } catch (e: Exception) {
            if (createdHere) connectionRepository.delete(connectionId, deletePlans = false)
            val isSandbox = userPreferences.isSandboxMode()
            val hint = if (isSandbox) {
                "\n\nNote: Sandbox mode requires separate API keys from the exchange's testnet environment."
            } else ""
            CredentialValidationResult.Error("${e.message ?: "Failed to validate credentials"}$hint")
        }
    }

    /**
     * @return Pair(connectionId, createdHere) - true if this call created a new connection
     *  row that should be rolled back on validation failure.
     */
    private suspend fun resolveConnection(
        exchange: Exchange,
        connectionName: String?,
        existingConnectionId: Long?
    ): Pair<Long, Boolean> {
        if (existingConnectionId != null) {
            return existingConnectionId to false
        }
        if (connectionName == null) {
            // Legacy path: use or create default (empty-named) connection
            val existing = connectionRepository.getDefaultByExchange(exchange)
            if (existing != null) return existing.id to false
            return connectionRepository.create(exchange, "") to true
        }
        // Explicit name (Phase 7 path): always create a new connection.
        return connectionRepository.create(exchange, connectionName) to true
    }
}
