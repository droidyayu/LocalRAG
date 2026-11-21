package com.ayushig.localrag.demo.platform

import com.ayushig.localrag.demo.domain.model.ModelOption
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

/** The iOS actuals of the platform interfaces, run on the iOS simulator. */
@OptIn(ExperimentalForeignApi::class)
class IosPlatformServicesTest {

    private val suite = "localrag-test-" + NSUUID().UUIDString
    private val directory = NSTemporaryDirectory() + "localrag-models-" + NSUUID().UUIDString

    @AfterTest
    fun cleanUp() {
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suite)
        NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
    }

    @Test
    fun theSelectionStartsEmptyAndRoundTrips() {
        val selection = IosModelSelection(suiteName = suite)
        assertNull(selection.selected())

        selection.select(ModelOption.GEMMA_270M)

        assertEquals(ModelOption.GEMMA_270M, selection.selected())
    }

    @Test
    fun theSelectionSurvivesANewInstance() {
        IosModelSelection(suiteName = suite).select(ModelOption.E2B)

        assertEquals(ModelOption.E2B, IosModelSelection(suiteName = suite).selected())
    }

    @Test
    fun anUnknownStoredNameReadsAsNoSelection() {
        NSUserDefaults(suiteName = suite).setObject("NOT_A_MODEL", forKey = "selected_model")

        assertNull(IosModelSelection(suiteName = suite).selected())
    }

    @Test
    fun thePathIsUnderTheDirectoryAndCarriesTheFileName() {
        val files = IosModelFiles(directory = directory)

        assertEquals("$directory/${ModelOption.E2B.fileName}", files.pathFor(ModelOption.E2B))
    }

    @Test
    fun aMissingFileIsNotPresent() {
        assertFalse(IosModelFiles(directory = directory).isPresent(ModelOption.E2B))
    }

    @Test
    fun aZeroByteFileIsNotPresentAndANonEmptyOneIs() {
        val files = IosModelFiles(directory = directory)
        NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)

        NSFileManager.defaultManager.createFileAtPath(files.pathFor(ModelOption.GEMMA_270M), contents = null, attributes = null)
        assertFalse(files.isPresent(ModelOption.GEMMA_270M))

        val bytes = "x".encodeToByteArray()
        val data = bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
        NSFileManager.defaultManager.createFileAtPath(files.pathFor(ModelOption.GEMMA_270M), contents = data, attributes = null)
        assertTrue(files.isPresent(ModelOption.GEMMA_270M))
        assertEquals(setOf(ModelOption.GEMMA_270M), files.present())
    }

    @Test
    fun theDefaultDirectoryIsTheAppDocumentsDirectory() {
        val path = IosModelFiles().pathFor(ModelOption.E2B)

        assertTrue(path.contains("/Documents/"), path)
        assertTrue(path.endsWith(ModelOption.E2B.fileName), path)
    }
}
