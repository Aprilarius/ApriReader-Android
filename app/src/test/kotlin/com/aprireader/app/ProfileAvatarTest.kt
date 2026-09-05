package com.aprireader.app

import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.profile.AvatarGender
import com.aprireader.app.data.profile.AvatarPreset
import com.aprireader.app.data.profile.ReaderTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAvatarTest {

    @Test
    fun `there are exactly 5 male and 5 female preset avatars`() {
        val all = AvatarPreset.entries
        assertEquals(10, all.size)

        val males = all.filter { it.gender == AvatarGender.MALE }
        val females = all.filter { it.gender == AvatarGender.FEMALE }

        assertEquals(5, males.size)
        assertEquals(5, females.size)
    }

    @Test
    fun `all avatar presets have unique ids and valid resources`() {
        val ids = AvatarPreset.entries.map { it.id }
        assertEquals(ids.distinct().size, ids.size)

        for (preset in AvatarPreset.entries) {
            assertTrue(preset.id.isNotBlank())
            assertTrue(preset.titleRes != 0)
            assertTrue(preset.descriptionRes != 0)
            assertTrue(preset.drawableRes != 0)
        }
    }

    @Test
    fun `avatar preset fallback resolves safely for unknown id`() {
        assertEquals(AvatarPreset.M1_SCHOLAR, AvatarPreset.fromId("unknown_avatar_id"))
        assertEquals(AvatarPreset.M1_SCHOLAR, AvatarPreset.fromId(null))
        assertEquals(AvatarPreset.F1_SORCERESS, AvatarPreset.fromId("f1_sorceress"))
        assertEquals(AvatarPreset.M3_CYBER, AvatarPreset.fromId("m3_cyber"))
    }

    @Test
    fun `reader titles have unique keys and valid badges`() {
        val titles = ReaderTitle.entries
        assertTrue(titles.size >= 6)

        val keys = titles.map { it.key }
        assertEquals(keys.distinct().size, keys.size)

        for (title in titles) {
            assertTrue(title.badgeIcon.isNotBlank())
            assertTrue(title.titleRes != 0)
        }
    }

    @Test
    fun `reader title fallback resolves safely`() {
        assertEquals(ReaderTitle.BOOK_KEEPER, ReaderTitle.fromKey("invalid_key"))
        assertEquals(ReaderTitle.BOOK_KEEPER, ReaderTitle.fromKey(null))
        assertEquals(ReaderTitle.NIGHT_READER, ReaderTitle.fromKey("night_reader"))
    }

    @Test
    fun `default app settings contain expected profile defaults`() {
        val settings = AppSettings()
        assertEquals("m1_scholar", settings.userAvatarId)
        assertEquals(null, settings.customAvatarPath)
        assertEquals("book_keeper", settings.userTitleKey)
        assertEquals("", settings.userBio)
    }
}
