package com.ml.shubham0204.docqa.ui.screens.docs

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSourcePresentationTest {
    @Test
    fun labelsKnownMultimodalSources() {
        assertEquals("Document", DocumentSourcePresentation.label("DOCUMENT"))
        assertEquals("Image OCR", DocumentSourcePresentation.label("IMAGE"))
        assertEquals("Audio transcript", DocumentSourcePresentation.label("AUDIO"))
    }

    @Test
    fun treatsLegacyOrUnknownValuesAsDocuments() {
        assertEquals("Document", DocumentSourcePresentation.label(""))
        assertEquals("Document", DocumentSourcePresentation.label("UNKNOWN"))
    }
}
