/*
 * Focus: focus, notes and study tools for Android
 * Copyright (C) 2026 MR.English2008
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.barq.focus.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeJsonTest {
    private val base = ThemeJson.toJson(ThemePresets.DEFAULT)

    private fun bad(text: String): List<String> = (ThemeJson.parse(text) as? ThemeParse.Bad)?.problems ?: emptyList()
    private fun ok(text: String) = ThemeJson.parse(text) is ThemeParse.Ok

    @Test fun everyPresetExportsAndComesBackIdentical() {
        ThemePresets.ALL.forEach { p ->
            val r = ThemeJson.parse(ThemeJson.toJson(p))
            assertTrue("${p.name}: ${(r as? ThemeParse.Bad)?.problems}", r is ThemeParse.Ok)
            assertEquals(p, (r as ThemeParse.Ok).config)
        }
    }

    @Test fun presetNamesAreUnique() {
        assertEquals(ThemePresets.ALL.size, ThemePresets.ALL.map { it.name }.toSet().size)
    }

    @Test fun aCodeFenceAroundTheJsonIsAccepted() {
        assertTrue(ok("```json\n$base\n```"))
    }

    @Test fun notJsonIsRejectedWithAClearReason() {
        assertTrue(bad("hello").first().contains("not valid JSON"))
        assertTrue(bad("[1,2]").isNotEmpty())
    }

    @Test fun tooLongIsRejected() {
        assertTrue(bad(" ".repeat(ThemeJson.MAX_CHARS + 1)).isNotEmpty())
    }

    @Test fun unknownTopLevelFieldIsRejected() {
        assertTrue(bad(base.replaceFirst("{", "{\"script\": \"alert(1)\",")).any { it.contains("Unknown field") })
    }

    @Test fun underscoreNotesMustBePlainText() {
        assertTrue(bad(base.replaceFirst("{", "{\"_x\": 5,")).any { it.contains("plain text") })
        assertTrue(bad(base.replaceFirst("{", "{\"_x\": {\"a\": {\"b\": 1}},")).any { it.contains("plain text") })
        assertTrue(ok(base.replaceFirst("{", "{\"_x\": \"a note\",")))
    }

    @Test fun unknownNestedFieldsAreRejected() {
        assertTrue(bad(base.replace("\"cardRadius\"", "\"onClick\": 1, \"cardRadius\"")).any { it.contains("shapes.onClick") })
        assertTrue(bad(base.replace("\"showGreeting\"", "\"run\": true, \"showGreeting\"")).any { it.contains("components.home.run") })
        assertTrue(bad(base.replace("\"supportiveMessage\"", "\"blockApps\": false, \"supportiveMessage\"")).any { it.contains("components.gate.blockApps") })
    }

    @Test fun wrongSchemaVersionIsRejected() {
        assertTrue(bad(base.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")).any { it.contains("schemaVersion") })
        assertTrue(bad(base.replace("\"schemaVersion\": 1,", "")).any { it.contains("schemaVersion") })
    }

    @Test fun badThemeNamesAreRejected() {
        assertTrue(bad(base.replace("\"themeName\": \"Calm Sage\"", "\"themeName\": \"<script>x</script>\"")).any { it.contains("themeName") })
        assertTrue(bad(base.replace("\"themeName\": \"Calm Sage\"", "\"themeName\": \"\"")).any { it.contains("themeName") })
    }

    @Test fun colorsMustBeSixDigitHex() {
        assertTrue(bad(base.replaceFirst("\"#4A7360\"", "\"red\"")).any { it.contains("#RRGGBB") })
        assertTrue(bad(base.replaceFirst("\"#4A7360\"", "\"javascript:alert(1)\"")).any { it.contains("#RRGGBB") })
        assertTrue(bad(base.replaceFirst("\"#4A7360\"", "\"#4A73\"")).any { it.contains("#RRGGBB") })
    }

    @Test fun aMissingColorIsReported() {
        assertTrue(bad(base.replaceFirst("\"primary\": \"#4A7360\",", "")).any { it.contains("primary is missing") })
    }

    @Test fun faintTextIsRejected() {
        // textPrimary nearly the same as the background
        val faint = base.replaceFirst("\"textPrimary\": \"#232A25\"", "\"textPrimary\": \"#F0EFE8\"")
        assertTrue(bad(faint).any { it.contains("too faint") })
    }

    @Test fun faintDarkTextIsRejectedToo() {
        val faint = base.replaceFirst("\"textPrimary\": \"#E6ECE7\"", "\"textPrimary\": \"#1F261F\"")
        assertTrue(bad(faint).any { it.startsWith("darkColors") && it.contains("too faint") })
    }

    @Test fun numbersOutOfRangeAreRejected() {
        assertTrue(bad(base.replace("\"cardRadius\": 20", "\"cardRadius\": 99")).any { it.contains("cardRadius") })
        assertTrue(bad(base.replace("\"cardRadius\": 20", "\"cardRadius\": -1")).any { it.contains("cardRadius") })
        assertTrue(bad(base.replace("\"cardRadius\": 20", "\"cardRadius\": 2.5")).any { it.contains("cardRadius") })
        assertTrue(bad(base.replace("\"scale\": 1.00", "\"scale\": 3")).any { it.contains("typography.scale") })
        assertTrue(bad(base.replace("\"scale\": 1.00", "\"scale\": 0.2")).any { it.contains("typography.scale") })
    }

    @Test fun enumsAreChecked() {
        assertTrue(bad(base.replace("\"appearance\": \"system\"", "\"appearance\": \"neon\"")).any { it.contains("appearance") })
        assertTrue(bad(base.replace("\"spacing\": \"normal\"", "\"spacing\": \"huge\"")).any { it.contains("spacing") })
        assertTrue(bad(base.replace("\"primaryAction\": \"high\"", "\"primaryAction\": \"loud\"")).any { it.contains("primaryAction") })
        assertTrue(bad(base.replace("\"visualStyle\": \"calm\"", "\"visualStyle\": \"strobe\"")).any { it.contains("visualStyle") })
    }

    @Test fun booleansMustBeBooleans() {
        assertTrue(bad(base.replace("\"showGreeting\": true", "\"showGreeting\": \"yes\"")).any { it.contains("showGreeting") })
    }

    @Test fun optionalPartsFallBackToDefaults() {
        val min = """{"schemaVersion":1,"themeName":"Mini","appearance":"light","colors":{
            "primary":"#4A7360","secondary":"#6F8F7A","accent":"#B9803A","background":"#F3F2EA","surface":"#FBFAF5","card":"#FFFFFF",
            "textPrimary":"#232A25","textSecondary":"#566159","success":"#3F7A55","warning":"#9A6A1F","error":"#A94848"}}"""
        val c = (ThemeJson.parse(min) as ThemeParse.Ok).config
        assertEquals(20, c.cardRadius); assertEquals("normal", c.spacing); assertEquals(1.0f, c.typographyScale, 0.0001f)
        assertEquals("high", c.homePrimaryAction); assertEquals("calm", c.gateStyle); assertTrue(c.gateSupportiveMessage)
        assertNull(c.darkColors)
    }

    @Test fun darkPaletteIsUsedOnlyWhenTheDarkLookIsActive() {
        val c = ThemePresets.DEFAULT           // appearance system, has darkColors
        assertEquals(c.colors, c.palette(false))
        assertEquals(c.darkColors, c.palette(true))
        val forcedLight = c.copy(appearance = "light"); assertEquals(c.colors, forcedLight.palette(true))
        val forcedDark = c.copy(appearance = "dark"); assertEquals(c.darkColors, forcedDark.palette(false))
        val noDark = c.copy(darkColors = null); assertEquals(c.colors, noDark.palette(true))
    }

    @Test fun parseOrNullIsNullForBlankOrBroken() {
        assertNull(ThemeJson.parseOrNull(""))
        assertNull(ThemeJson.parseOrNull("{"))
        assertNotNull(ThemeJson.parseOrNull(base))
    }

    @Test fun contrastMathMatchesWcag() {
        assertEquals(21.0, ThemeJson.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ThemeJson.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 0.001)
        assertEquals(0xFFFFFFFF.toInt(), ThemeJson.onColor(0xFF222222.toInt()))
        assertEquals(0xFF1B1B1F.toInt(), ThemeJson.onColor(0xFFF5F5F5.toInt()))
    }

    @Test fun spacingStepsAreOrdered() {
        val c = ThemePresets.DEFAULT
        assertTrue(c.copy(spacing = "compact").spacingDp < c.copy(spacing = "normal").spacingDp)
        assertTrue(c.copy(spacing = "normal").spacingDp < c.copy(spacing = "comfortable").spacingDp)
    }

    @Test fun exportCarriesTheFieldGuideForHumansAndAis() {
        assertTrue(base.contains("_fields")); assertTrue(base.contains("schemaVersion"))
        assertTrue(ThemeJson.AI_PROMPT.contains("valid JSON only"))
    }
}
