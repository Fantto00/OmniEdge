package com.ml.shubham0204.docqa.domain.ingestion

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioTranscriptNormalizerTest {
    @Test
    fun removesVoskSpacingBetweenChineseCharactersAndNumbers() {
        val transcript =
            "今天 的 天气 比较 晴朗 订单 编号 是 一二三四五 六 现在 是 上午 九点 三十 分。"

        assertEquals(
            "今天的天气比较晴朗订单编号是一二三四五六现在是上午九点三十分。",
            AudioTranscriptNormalizer.normalizeForIndexing(transcript),
        )
    }

    @Test
    fun preservesSpacesBetweenEnglishWords() {
        assertEquals(
            "今天 test model 很好",
            AudioTranscriptNormalizer.normalizeForIndexing("今天 test  model 很 好"),
        )
    }
}
