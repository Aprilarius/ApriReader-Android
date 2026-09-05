package com.aprireader.app

import androidx.compose.ui.graphics.Color
import com.aprireader.app.data.prefs.DesignStyle
import com.aprireader.app.ui.theme.AccentPresets
import com.aprireader.app.ui.theme.ApriBrassSeed
import com.aprireader.app.ui.theme.buildColorScheme
import org.junit.Assert.assertNotNull
import org.junit.Test

class ColorSchemeTest {

    @Test
    fun `all design styles and theme modes generate valid color schemes`() {
        val seeds = AccentPresets.map { it.color } + listOf(ApriBrassSeed, Color.Red, Color.Blue, Color.Black, Color.White, Color(0xFF000000), Color(0xFFFFFFFF))
        for (designStyle in DesignStyle.entries) {
            for (dark in listOf(true, false)) {
                for (pureBlack in listOf(true, false)) {
                    for (seed in seeds) {
                        val scheme = buildColorScheme(seed, dark, pureBlack, designStyle)
                        assertNotNull(scheme.primary)
                        assertNotNull(scheme.surface)
                        assertNotNull(scheme.background)
                        assertNotNull(scheme.onSurface)
                        assertNotNull(scheme.onBackground)
                    }
                }
            }
        }
    }
}
