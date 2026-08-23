package com.junchen.jingdu

enum class AppScreen { LIBRARY, READER }
enum class ReaderPanel { SEARCH, CHAPTERS, BOOKMARKS, CLEAN, SETTINGS, ENCODING }
enum class RepairRuleMode { LITERAL, LINE_GLOB }
enum class LibraryBookStatus { UNREAD, READING, FINISHED }
enum class LibrarySort { RECENT, NAME, PROGRESS }
enum class NoiseRisk { LOW, MEDIUM, HIGH }

data class BookCardModel(
    val id: String,
    val name: String,
    val encoding: String,
    val sizeBytes: Long,
    val progress: Long,
    val charCount: Long,
    val touchedAt: Long,
    val normalizedSha256: String,
    val favorite: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    val progressFraction: Float
        get() = if (charCount <= 0) 0f else (progress.toDouble() / charCount.toDouble()).toFloat().coerceIn(0f, 1f)

    val status: LibraryBookStatus
        get() = when {
            progress <= 0L -> LibraryBookStatus.UNREAD
            charCount > 0L && progressFraction >= 0.985f -> LibraryBookStatus.FINISHED
            else -> LibraryBookStatus.READING
        }
}

data class SearchResultModel(val offset: Long, val context: String)
data class ChapterModel(val offset: Long, val title: String)
data class BookmarkModel(val offset: Long, val progressFraction: Float)
data class RepairRule(
    val find: String,
    val replacement: String,
    val mode: RepairRuleMode = RepairRuleMode.LITERAL,
)
data class NoiseCandidateModel(
    val score: Int,
    val count: Int,
    val reason: String,
    val text: String,
    val selected: Boolean = false,
) {
    val risk: NoiseRisk
        get() = when {
            reason == "inline_fragment" || reason == "garbled_line" -> NoiseRisk.MEDIUM
            reason == "promo_repeated" || score >= 82 -> NoiseRisk.HIGH
            reason == "promo" || reason == "url" || (score >= 68 && count >= 10) -> NoiseRisk.MEDIUM
            else -> NoiseRisk.LOW
        }

    val impactChars: Long
        get() = text.codePointCount(0, text.length).toLong() * count.coerceAtLeast(0).toLong()

    val defaultSafeSelection: Boolean
        get() = when (reason) {
            "inline_fragment", "garbled_line" -> false
            else -> risk == NoiseRisk.HIGH || (risk == NoiseRisk.MEDIUM && count >= 20)
        }
}
data class TtsVoiceModel(val name: String, val label: String)

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
    val chaptersLoaded: Boolean = false,
    val bookmarks: List<BookmarkModel> = emptyList(),
    val repairRules: List<RepairRule> = emptyList(),
    val globalRules: List<RepairRule> = emptyList(),
    val noiseCandidates: List<NoiseCandidateModel> = emptyList(),
    val smartCleanAnalyzed: Boolean = false,
    val smartCleanUndoAvailable: Boolean = false,
    val proUnlocked: Boolean = false,
    val proAvailable: Boolean = false,
    val proConnected: Boolean = false,
    val proPrice: String? = null,
    val ttsVoices: List<TtsVoiceModel> = emptyList(),
    val ttsPlaying: Boolean = false,
    val autoPaging: Boolean = false,
    val sleepMinutes: Int = 0,
    val settings: ReaderSettings = ReaderSettings(),
    val deleteConfirmation: Boolean = false,
)
