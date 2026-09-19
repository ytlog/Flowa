package com.flowa.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class CaptureUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun captureClearRestoreAndNavigation() {
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("随心记").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("Compose regression draft")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("清除草稿").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("恢复草稿").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("恢复草稿").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Compose regression draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("记录").performClick()
        compose.onNodeWithContentDescription("返回").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Compose regression draft").assertExists()
    }
    @Test fun blankWorkflowShowsErrorWithoutConsumingDraft() {
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("随心记").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("保留这个草稿")
        compose.onNodeWithContentDescription("默认工作流").performClick()
        compose.onNodeWithText("还没有启用的步骤。请添加并启用至少一个步骤，再运行。").assertExists()
        compose.onNodeWithText("继续记录").performClick()
        compose.onNodeWithText("保留这个草稿").assertExists()
    }
}
