package io.github.realtvop.pixelcardemulator

import org.junit.Assert.*
import org.junit.Test

class EmulationProfileTest {
    @Test fun missingOrUnknownStoredProfileKeepsUpstreamDefault() {
        for (value in listOf(null, "", "future-profile", "default")) {
            assertEquals(CardProfile.Default, CardProfile.fromStorage(value))
        }
        assertEquals(CardProfile.Sak08, CardProfile.fromStorage("sak08"))
    }

    @Test fun defaultPreservesDifferentOemAndRawProfiles() {
        assertEquals(RawProfile.Pixel, CardProfile.Default.oemProfile())
        assertEquals(RawProfile.Pixel, CardProfile.Default.rawProfile(isGoogle = true))
        assertEquals(RawProfile.CtsType4A, CardProfile.Default.rawProfile(isGoogle = false))
    }

    @Test fun sak08RequestsSameIdentityOnEveryBackend() {
        val profiles = listOf(CardProfile.Sak08.oemProfile(),
            CardProfile.Sak08.rawProfile(true), CardProfile.Sak08.rawProfile(false))
        profiles.forEach {
            assertEquals(4, it.bitFrameSdd.toInt())
            assertEquals(0, it.platformConfig.toInt())
            assertEquals(8, it.selInfo.toInt())
            assertEquals(0, it.rats.toInt())
        }
    }

    @Test fun randomUidWarningAppliesOnlyToFourByte08Prefix() {
        assertTrue(hasRandomUidPrefix(byteArrayOf(8, 1, 2, 3)))
        assertFalse(hasRandomUidPrefix(byteArrayOf(4, 1, 2, 3)))
        assertFalse(hasRandomUidPrefix(byteArrayOf(8, 1, 2, 3, 4, 5, 6)))
        assertFalse(hasRandomUidPrefix(ByteArray(0)))
    }

    @Test fun rollbackDoesNotEraseFailureFromBoundedHistory() {
        var history = prependDiagnostic("Activation failed\nRF not measured", "")
        history = prependDiagnostic("OFF completed\nRF not measured", history)
        assertTrue(history.contains("Activation failed"))
        history = prependDiagnostic("New activation", history)
        history = prependDiagnostic("Latest OFF", history)
        assertEquals(3, history.split("\n\n").size)
        assertTrue(history.startsWith("Latest OFF"))
    }
    @Test fun readbackShowsActualSakAndUidMismatchWithoutLeakingUid() {
        val uid = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val result = describeIdentityReadback(mapOf(0x32 to byteArrayOf(0), 0x33 to uid), byteArrayOf(1, 2, 3, 4))
        assertTrue(result.contains("SAK=00"))
        assertTrue(result.contains("DIFFERS"))
        assertTrue(result.contains("RF not measured"))
        assertFalse(result.contains("11223344"))
    }

    @Test fun missingReadbackIsNotReportedAsMatch() {
        val result = describeIdentityReadback(emptyMap(), byteArrayOf(1, 2, 3, 4))
        assertTrue(result.contains("SAK=missing"))
        assertFalse(result.contains("matches active card"))
    }
}
