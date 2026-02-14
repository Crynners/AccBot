package com.accbot.dca.domain.usecase

import com.accbot.dca.data.local.CredentialsStore
import com.accbot.dca.data.local.UserPreferences
import com.accbot.dca.domain.model.Exchange
import com.accbot.dca.domain.model.ExchangeCredentials
import com.accbot.dca.exchange.ExchangeApiFactory
import javax.inject.Inject

/**
 * Result of credential validation and save operation.
 */
sealed class CredentialValidationResult {
    data object Success : CredentialValidationResult()
    data class Error(val message: String) : CredentialValidationResult()
}

/**
 * Use case for validating and saving exchange credentials.
 * Extracts common credential validation logic from ViewModels for better testability
 * and to eliminate code duplication across AddPlanViewModel, AddExchangeViewModel,
 * and OnboardingViewModel.
 *
 * This use case:
 * 1. Creates ExchangeCredentials from input
 * 2. Validates credentials with the exchange API
 * 3. Saves valid credentials to secure storage
 */
class ValidateAndSaveCredentialsUseCase @Inject constructor(
    private val exchangeApiFactory: ExchangeApiFactory,
    private val credentialsStore: CredentialsStore,
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
     * @return CredentialValidationResult.Success if valid and saved, Error otherwise
     */
    suspend fun execute(
        exchange: Exchange,
        apiKey: String,
        apiSecret: String,
        passphrase: String? = null,
        clientId: String? = null
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

        return try {
            val isSandbox = userPreferences.isSandboxMode()

            val api = exchangeApiFactory.create(credentials)
            val isValid = api.validateCredentials()

            if (isValid) {
                credentialsStore.saveCredentials(credentials, isSandbox)
                CredentialValidationResult.Success
            } else {
                val hint = if (isSandbox) {
                    " Make sure you are using API keys generated on the exchange's sandbox/testnet (not production keys)."
                } else ""
                CredentialValidationResult.Error("Invalid API credentials.$hint")
            }
        } catch (e: Exception) {
            val isSandbox = userPreferences.isSandboxMode()
            val hint = if (isSandbox) {
                "\n\nNote: Sandbox mode requires separate API keys from the exchange's testnet environment."
            } else ""
            CredentialValidationResult.Error("${e.message ?: "Failed to validate credentials"}$hint")
        }
    }
}
