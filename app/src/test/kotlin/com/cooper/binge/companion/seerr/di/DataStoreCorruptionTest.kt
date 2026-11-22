package com.cooper.binge.companion.seerr.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val STORE_COUNT = 14

class DataStoreCorruptionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val key = stringPreferencesKey("k")

    @Test
    fun `a corrupt file is replaced and reads empty`() =
        runTest {
            val file = folder.newFile("corrupt.preferences_pb")
            file.writeBytes(byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte()))
            val store = PreferenceDataStoreFactory.create(corruptionHandler = corruptionHandler(), scope = backgroundScope) { file }

            val read = store.data.first()

            assertTrue(read.asMap().isEmpty())
        }

    @Test
    fun `a store that was reset keeps what is written to it afterwards`() =
        runTest {
            val file = folder.newFile("reset.preferences_pb")
            file.writeBytes(byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte()))
            val store = PreferenceDataStoreFactory.create(corruptionHandler = corruptionHandler(), scope = backgroundScope) { file }

            store.edit { it[key] = "kept" }

            assertEquals("kept", store.data.first()[key])
        }

    @Test
    fun `every preferences store in di carries the handler`() {
        val sources = File("src/main/kotlin/com/cooper/binge/companion/seerr/di").listFiles { f -> f.extension == "kt" }.orEmpty()
        val declared = sources.sumOf { occurrences(it, "preferencesDataStore(") }
        val handled = sources.sumOf { occurrences(it, "corruptionHandler = corruptionHandler()") }

        assertTrue("expected the $STORE_COUNT stores, found $declared", declared >= STORE_COUNT)
        assertEquals("a store is missing its corruption handler", declared, handled)
    }

    private fun occurrences(
        file: File,
        needle: String,
    ): Int {
        val parts = file.readText().split(needle)
        return parts.size - 1
    }
}
