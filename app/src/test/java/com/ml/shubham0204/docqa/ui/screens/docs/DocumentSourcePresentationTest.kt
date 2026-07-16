package com.ml.shubham0204.docqa.ui.screens.docs

import com.ml.shubham0204.docqa.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSourcePresentationTest {
    @Test
    fun labelsKnownMultimodalSources() {
        assertEquals(R.string.document_source_document, DocumentSourcePresentation.labelRes("DOCUMENT"))
        assertEquals(R.string.document_source_image, DocumentSourcePresentation.labelRes("IMAGE"))
        assertEquals(R.string.document_source_audio, DocumentSourcePresentation.labelRes("AUDIO"))
    }

    @Test
    fun treatsLegacyOrUnknownValuesAsDocuments() {
        assertEquals(R.string.document_source_document, DocumentSourcePresentation.labelRes(""))
        assertEquals(R.string.document_source_document, DocumentSourcePresentation.labelRes("UNKNOWN"))
    }
}
