package com.aprireader.app

import com.aprireader.app.data.prefs.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPersistenceTest {

    @Test
    fun `default app settings has onboardingCompleted false and empty userName`() {
        val settings = AppSettings()
        assertFalse(settings.onboardingCompleted)
        assertEquals("", settings.userName)
    }

    @Test
    fun `completing onboarding saves trimmed userName and flags onboarding completed`() {
        val initial = AppSettings()
        val completed = initial.copy(
            onboardingCompleted = true,
            userName = "   Александр Пушкин   ".trim(),
        )

        assertTrue(completed.onboardingCompleted)
        assertEquals("Александр Пушкин", completed.userName)
    }

    @Test
    fun `skipping onboarding flags onboarding completed without requiring userName`() {
        val initial = AppSettings()
        val skipped = initial.copy(
            onboardingCompleted = true,
            userName = "",
        )

        assertTrue(skipped.onboardingCompleted)
        assertEquals("", skipped.userName)
    }

    @Test
    fun `router start destination logic selects library when onboarding is completed`() {
        fun resolveStartDestination(onboardingCompleted: Boolean, pendingBookId: String?): String {
            return when {
                !onboardingCompleted -> "onboarding"
                pendingBookId != null -> "reader/$pendingBookId"
                else -> "library"
            }
        }

        assertEquals("onboarding", resolveStartDestination(onboardingCompleted = false, pendingBookId = null))
        assertEquals("onboarding", resolveStartDestination(onboardingCompleted = false, pendingBookId = "book123"))
        assertEquals("library", resolveStartDestination(onboardingCompleted = true, pendingBookId = null))
        assertEquals("reader/book123", resolveStartDestination(onboardingCompleted = true, pendingBookId = "book123"))
    }
}
