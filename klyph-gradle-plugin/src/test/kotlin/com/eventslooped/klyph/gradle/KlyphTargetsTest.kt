package com.eventslooped.klyph.gradle

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KlyphTargetsTest {
    private val host = KlyphTargets.MACOS_ARM64

    @Test
    fun hostDetection() {
        assertEquals("macosArm64", KlyphTargets.host("Mac OS X", "aarch64"))
        assertEquals("linuxX64", KlyphTargets.host("Linux", "amd64"))
        assertEquals("linuxArm64", KlyphTargets.host("Linux", "aarch64"))
        assertEquals("mingwX64", KlyphTargets.host("Windows 11", "amd64"))
        assertFailsWith<GradleException> { KlyphTargets.host("Mac OS X", "x86_64") }
    }

    @Test
    fun defaultIsTheHost() {
        assertEquals(listOf(host), KlyphTargets.resolve(null, null, listOf("host"), host))
        assertEquals(listOf("linuxArm64"), KlyphTargets.resolve("host", null, listOf("all"), "linuxArm64"))
    }

    @Test
    fun allAsTheWholeValueSelectsEveryTarget() {
        assertEquals(KlyphTargets.all, KlyphTargets.resolve(null, null, listOf("all"), host))
        assertEquals(KlyphTargets.all, KlyphTargets.resolve(" all ", null, listOf("host"), host))
        assertEquals(KlyphTargets.all, KlyphTargets.resolve(null, "all", listOf("host"), host))
    }

    @Test
    fun allInsideAListIsUnknown() {
        val fromProperty = assertFailsWith<GradleException> { KlyphTargets.resolve("all,mingwX64", null, listOf("host"), host) }
        assertEquals(
            "Unknown klyph target all. Use host, all, or a comma-separated list of macosArm64, linuxX64, linuxArm64, mingwX64.",
            fromProperty.message,
        )
        assertFailsWith<GradleException> { KlyphTargets.resolve(null, null, listOf("all", "host"), host) }
    }

    @Test
    fun bothPropertiesFail() {
        val failure = assertFailsWith<GradleException> { KlyphTargets.resolve("macosArm64", " mingwX64", listOf("host"), host) }
        assertEquals("Set klyph.targets or klyph.target, not both: klyph.targets=macosArm64, klyph.target=mingwX64.", failure.message)
    }

    @Test
    fun unknownTargetFails() {
        val failure = assertFailsWith<GradleException> { KlyphTargets.resolve(null, null, listOf("iosArm64", "tvos"), host) }
        assertEquals(
            "Unknown klyph target iosArm64, tvos. Use host, all, or a comma-separated list of macosArm64, linuxX64, linuxArm64, mingwX64.",
            failure.message,
        )
    }

    @Test
    fun noTargetFails() {
        val failure = assertFailsWith<GradleException> { KlyphTargets.resolve(" , ", null, listOf("host"), host) }
        assertEquals(
            "No klyph target is selected. Use host, all, or a comma-separated list of macosArm64, linuxX64, linuxArm64, mingwX64.",
            failure.message,
        )
        assertFailsWith<GradleException> { KlyphTargets.resolve(null, null, emptyList(), host) }
    }

    @Test
    fun resultIsInCanonicalOrderWithoutRepeats() {
        assertEquals(
            listOf("macosArm64", "linuxX64", "mingwX64"),
            KlyphTargets.resolve("mingwX64, linuxX64,host,macosArm64,mingwX64", null, listOf("all"), host),
        )
        assertEquals(listOf("linuxArm64", "mingwX64"), KlyphTargets.resolve(null, null, listOf("mingwX64", "linuxArm64"), host))
    }

    @Test
    fun propertiesWinOverTheExtension() {
        assertEquals(listOf("mingwX64"), KlyphTargets.resolve(null, "mingwX64", listOf("host"), host))
        assertEquals(listOf("linuxX64"), KlyphTargets.resolve("linuxX64", null, listOf("all"), host))
    }

    @Test
    fun familyOfEachTarget() {
        assertEquals(listOf("OSX", "LINUX", "LINUX", "MINGW"), KlyphTargets.all.map { KlyphTargets.family(it) })
    }
}
