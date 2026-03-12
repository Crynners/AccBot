package com.accbot.dca.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for onboarding-related state.
 * Tracks whether the user has completed the initial setup flow.
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if onboarding has been completed.
     */
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Set onboarding completion status.
     */
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    /**
     * Check if a DCA plan was created during onboarding.
     */
    fun isPlanCreatedDuringOnboarding(): Boolean {
        return prefs.getBoolean(KEY_PLAN_CREATED, false)
    }

    fun setPlanCreatedDuringOnboarding(created: Boolean) {
        prefs.edit().putBoolean(KEY_PLAN_CREATED, created).apply()
    }

    /**
     * Reset onboarding state (for testing or re-onboarding).
     */
    fun resetOnboarding() {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .remove(KEY_PLAN_CREATED)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "accbot_onboarding"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PLAN_CREATED = "plan_created_during_onboarding"
    }
}
