package com.ml.shubham0204.docqa.domain.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SpeechModelArchiveTest {
    @Test
    fun pinsTheOfficialHttpsModelArchive() {
        assertTrue(SpeechModelConfig.ARCHIVE_URL.startsWith("https://"))
        assertEquals(64, SpeechModelConfig.ARCHIVE_SHA256.length)
    }

    @Test
    fun resolvesArchiveEntriesInsideModelRoot() {
        val root = Files.createTempDirectory("speech-model-root").toFile()
        try {
            val output = SpeechModelArchive.outputFile(root, "model/am/final.mdl")

            assertEquals(File(root, "model/am/final.mdl").canonicalFile, output)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsArchivePathTraversal() {
        val root = Files.createTempDirectory("speech-model-root").toFile()
        try {
            SpeechModelArchive.outputFile(root, "../outside")
        } finally {
            root.deleteRecursively()
        }
    }
}
