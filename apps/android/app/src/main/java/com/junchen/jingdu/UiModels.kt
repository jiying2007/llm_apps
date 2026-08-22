package com.junchen.jingdu

enum class AppScreen { LIBRARY, READER }
enum class ReaderPanel { SEARCH, CHAPTERS, BOOKMARKS, CLEAN, SETTINGS, ENCODING }

data class BookCardModel(
    val id: String,
    val name: String,
    val encoding: String,
    val sizeBytes: Long,
    val progress: Long,
    val charCount: Long,
    val touchedAt: Long,
    val normalizedSha256: String,
) {
    val progressFraction: Float
        get() = if (charCount <= 0) 0f else (progress.toDouble() / charCount.toDouble()).toFloat().coerceIn(0f, 1f)
}

data class SearchResultModel(val offset: Long, val context: String)
data class ChapterModel(val offset: Long, val title: String)
data class BookmarkModel(val offset: Long, val progressFraction: Float)
data class RepairRule(val find: String, val replacement: String)

data class AppUiState(
    val screen: AppScreen = AppScreen.LIBRARY,
    val books: List<BookCardModel> = emptyList(),
    val currentBook: BookCardModel? = null,
    val pageText: String = "",
    val position: Long = 0,
    val length: Long = 0,
    val cleanMode: Boolean = false,
    val panel: ReaderPanel? = null,
    val busyLabel: String? = null,
    val message: String? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResultModel> = emptyList(),
    val chapters: List<ChapterModel> = emptyList(),
    val bookmarks: List<BookmarkModel> = emptyList(),
    val repairRules: List<RepairRule> = emptyList(),
    val ttsPlaying: Boolean = false,
    val autoPaging: Boolean = false,
    val sleepMinutes: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    val deleteConfirmation: Boolean = false,
)
