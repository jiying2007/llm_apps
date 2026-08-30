package com.junchen.jingdu

internal data class ReaderPresentedText(
    val sourceText: String,
    val displayText: String,
    val map: SourceDisplayMap,
)

/**
 * The single bounded presentation pipeline used by paged, continuous, skim and selection paths.
 * Core/source text remains immutable and authoritative; display-only edits always carry an exact
 * monotonic projection back to source coordinates.
 */
internal object ReaderPresentationPipeline {
    fun present(source: String, settings: ReaderSettings): ReaderPresentedText {
        var intermediate = source
        if (settings.compressBlankLines) {
            intermediate = intermediate.replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n")
        }
        if (settings.paragraphSpacingEm > 0f) {
            intermediate = intermediate.replace("\n\n", "\n${ReaderTypographySpec.PARAGRAPH_SPACER}\n")
        }
        val sourceToIntermediate = TextProjection.between(source, intermediate)
        val display = ChineseDisplayConverter.convert(intermediate, settings.chineseMode, settings.chineseOverrides)
        val intermediateToDisplay = TextProjection.between(intermediate, display)
        val map = SourceDisplayMap.compose(sourceToIntermediate, intermediateToDisplay)
        // All present() callers are already on bounded worker/IO paths. Build selection source ranges
        // here so normal reader frames only merge the prepared annotations with visual spans.
        ReaderSelectionController.prewarmSelectionMap(display, map)
        return ReaderPresentedText(
            sourceText = source,
            displayText = display,
            map = map,
        )
    }
}
