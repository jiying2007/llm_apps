package com.junchen.jingdu

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class JingduUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibraryExplainsValueAndImportAction() {
        composeRule.setContent {
            JingduApp(
                state = AppUiState(),
                actions = noOpActions(),
            )
        }

        composeRule.onNodeWithText("净读").assertIsDisplayed()
        composeRule.onNodeWithText("本地 TXT · 无广告 · 不上传").assertIsDisplayed()
        composeRule.onNodeWithText("把本地 TXT 变得好读").assertIsDisplayed()
        composeRule.onNodeWithText("选择 TXT 文件").assertIsDisplayed()
    }

    @Test
    fun readerKeepsPrimaryNavigationDiscoverable() {
        val book = BookCardModel(
            id = "a".repeat(64),
            name = "长篇小说.txt",
            encoding = "UTF-8",
            sizeBytes = 1024,
            progress = 500,
            charCount = 10_000,
            touchedAt = 1,
            normalizedSha256 = "b".repeat(64),
        )
        composeRule.setContent {
            JingduApp(
                state = AppUiState(
                    screen = AppScreen.READER,
                    currentBook = book,
                    pageText = "第一章\n这是一段用于验证阅读界面的正文。",
                    position = 500,
                    length = 10_000,
                ),
                actions = noOpActions(),
            )
        }

        composeRule.onNodeWithText("长篇小说").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回书架").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("全文搜索").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("目录").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("阅读进度").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("开始朗读").assertIsDisplayed()
    }

    private fun noOpActions() = JingduActions(
        onImport = {},
        onOpenBook = {},
        onDeleteLibraryBook = {},
        onBackToLibrary = {},
        onNavigatePrevious = {},
        onNavigateNext = {},
        onSeekFraction = {},
        onVisibleCharsChanged = {},
        onOpenPanel = {},
        onClosePanel = {},
        onSearchQueryChanged = {},
        onSearch = {},
        onJump = {},
        onAddBookmark = {},
        onDeleteBookmark = {},
        onAddRule = { _, _ -> },
        onDeleteRule = {},
        onClearRules = {},
        onToggleCleanPreview = {},
        onExportClean = {},
        onEncodingSelected = {},
        onSettingsChanged = {},
        onToggleTts = {},
        onToggleAutoPaging = {},
        onSleepTimer = {},
        onRequestDeleteCurrent = {},
        onDismissDelete = {},
        onConfirmDeleteCurrent = {},
        onMessageConsumed = {},
    )
}
