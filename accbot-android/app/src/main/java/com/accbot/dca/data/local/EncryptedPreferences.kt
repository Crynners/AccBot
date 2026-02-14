package com.accbot.dca.data.local

/**
 * @deprecated This class has been split for better Single Responsibility Principle compliance.
 * Use the following classes instead:
 * - [CredentialsStore] for exchange API credentials (security-critical)
 * - [OnboardingPreferences] for onboarding state
 * - [UserPreferences] for theme and notification settings
 *
 * This file is kept only for reference during migration.
 */
@Deprecated(
    message = "Use CredentialsStore, OnboardingPreferences, or UserPreferences instead",
    level = DeprecationLevel.ERROR
)
class EncryptedPreferences
