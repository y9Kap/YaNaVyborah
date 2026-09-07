package org.yanavybori.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledResourcesTest {
    @Test
    fun shared_guide_and_election_pack_are_packaged_in_android_assets() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val guide = assets.open("app-guide.md").bufferedReader().use { it.readText() }
        assertTrue(guide.startsWith("# Гайд по приложению"))
        val manifest = assets.open("demo-election-pack/manifest.json").bufferedReader().use { it.readText() }
        assertTrue(manifest.contains("\"schemaVersion\""))
        assets.open("demo-election-pack/reference_documents/originals/roadmap.pdf").use {
            val header = ByteArray(5)
            assertTrue(it.read(header) == header.size)
            assertTrue(header.decodeToString() == "%PDF-")
        }
    }
}
