package com.flowa.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CaptureUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun requireIsolatedInstall() { assertTrue("UI tests require -PisolatedUiTest=true", InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".uitest")) }
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
    @Test fun defaultSaveCreatesRecord() {
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("随心记").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextReplacement("默认保存回归记录")
        compose.onNodeWithContentDescription("保存记录").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("已保存记录").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("记录").performClick()
        compose.onNodeWithText("默认保存回归记录").assertExists()
    }
}
