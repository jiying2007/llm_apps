from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} drifted")
    return text.replace(old, new, 1)


# 1) Route volume keys at dispatchKeyEvent, before platform volume handling can consume them.
activity_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/MainActivity.kt")
activity = activity_path.read_text()
old_field = "    private var lastProgressPersistPosition = -1L\n"
new_field = old_field + "    private var consumedReaderVolumeKey = KeyEvent.KEYCODE_UNKNOWN\n"
activity = replace_once(activity, old_field, new_field, "volume dispatch state")
old_keys = '''    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (uiState.screen == AppScreen.READER && uiState.busyLabel == null &&
            motionController.state == ReaderMotionState.IDLE && ReaderInteractionRuntime.shouldUseVolumeKeysForPaging(uiState.settings, uiState.tts.active)
        ) {
            val nextKey = if (uiState.settings.reverseVolumeKeys) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
            val previousKey = if (uiState.settings.reverseVolumeKeys) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP
            if (keyCode == nextKey) { navigateNext(userInitiated = true); return true }
            if (keyCode == previousKey) { navigatePrevious(userInitiated = true); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
'''
new_keys = '''    private fun handleReaderVolumeKey(keyCode: Int): Boolean {
        if (uiState.screen != AppScreen.READER || uiState.busyLabel != null ||
            motionController.state != ReaderMotionState.IDLE ||
            !ReaderInteractionRuntime.shouldUseVolumeKeysForPaging(uiState.settings, uiState.tts.active)
        ) return false
        val nextKey = if (uiState.settings.reverseVolumeKeys) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
        val previousKey = if (uiState.settings.reverseVolumeKeys) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP
        return when (keyCode) {
            nextKey -> { navigateNext(userInitiated = true); true }
            previousKey -> { navigatePrevious(userInitiated = true); true }
            else -> false
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (consumedReaderVolumeKey == keyCode) return true
                    if (event.repeatCount == 0 && handleReaderVolumeKey(keyCode)) {
                        consumedReaderVolumeKey = keyCode
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> if (consumedReaderVolumeKey == keyCode) {
                    consumedReaderVolumeKey = KeyEvent.KEYCODE_UNKNOWN
                    return true
                }
                KeyEvent.ACTION_CANCEL -> if (consumedReaderVolumeKey == keyCode) {
                    consumedReaderVolumeKey = KeyEvent.KEYCODE_UNKNOWN
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
'''
activity = replace_once(activity, old_keys, new_keys, "volume key dispatch")
activity_path.write_text(activity)


# 2) Put paged gesture observation outside SelectionContainer. The outer gesture layer never
# consumes selection input, but it sees completed short taps even when selection owns the child.
screen_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderScreenV3.kt")
screen = screen_path.read_text()
old_start = '''    SelectionContainer(state = selectionState) {
        Box(
            Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures),
            contentAlignment = Alignment.TopCenter,
        ) {
'''
new_start = '''    Box(
        Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures),
        contentAlignment = Alignment.TopCenter,
    ) {
        SelectionContainer(state = selectionState) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
'''
screen = replace_once(screen, old_start, new_start, "paged gesture ownership")
old_end = '''            }
        }
    }
}

@Composable
private fun ContinuousReaderPageV3(
'''
new_end = '''            }
            }
        }
    }
}

@Composable
private fun ContinuousReaderPageV3(
'''
screen = replace_once(screen, old_end, new_end, "paged gesture nesting")
screen_path.write_text(screen)


# 3) Continuous: pre-raster the bounded 4K StaticLayout off-main into bitmap tiles. Real scroll
# frames now replay cached bitmap textures instead of re-running text display lists under SwiftShader.
fast_path = Path("apps/android/app/src/main/java/com/junchen/jingdu/ReaderFastText.kt")
fast = fast_path.read_text()
fast = fast.replace("import android.graphics.RenderNode\n", "")
fast = fast.replace("import android.os.Build\n", "")
old_layout = '''internal class ReaderContinuousLayout internal constructor(internal val layout: StaticLayout) {
    val lineCount: Int get() = layout.lineCount
    val height: Int get() = layout.height
    fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset.coerceAtLeast(0))
    fun getLineTop(line: Int): Float = layout.getLineTop(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))).toFloat()
    fun getLineForVerticalPosition(y: Float): Int = layout.getLineForVertical(y.roundToInt().coerceAtLeast(0))
    fun getLineStart(line: Int): Int = layout.getLineStart(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0)))
}
'''
new_layout = '''internal class ReaderContinuousLayout internal constructor(
    internal val layout: StaticLayout,
    internal val raster: ReaderStaticLayoutBitmapTileSet,
) {
    val lineCount: Int get() = layout.lineCount
    val height: Int get() = layout.height
    fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset.coerceAtLeast(0))
    fun getLineTop(line: Int): Float = layout.getLineTop(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))).toFloat()
    fun getLineForVerticalPosition(y: Float): Int = layout.getLineForVertical(y.roundToInt().coerceAtLeast(0))
    fun getLineStart(line: Int): Int = layout.getLineStart(line.coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0)))
}
'''
fast = replace_once(fast, old_layout, new_layout, "continuous layout raster authority")
fast = replace_once(
    fast,
    "    private var tileSet: ReaderStaticLayoutTileSet? = null\n",
    "    private var tileSet: ReaderStaticLayoutBitmapTileSet? = null\n",
    "bitmap tile field",
)
old_set = '''    fun setTextLayout(layout: StaticLayout, color: Int) {
        val changed = textLayout !== layout || textColor != color
        textLayout = layout
        textColor = color
        layout.paint.color = color
        if (changed) {
            rebuildTiles()
            postInvalidateOnAnimation()
        }
    }

    private fun rebuildTiles() {
        val layout = textLayout
        tileSet = if (Build.VERSION.SDK_INT >= 29 && layout != null && width > 0 && height > 0) {
            ReaderStaticLayoutTileSet(layout, height.coerceAtLeast(1))
        } else null
    }
'''
new_set = '''    fun setTextLayout(ready: ReaderContinuousLayout, color: Int) {
        val changed = textLayout !== ready.layout || tileSet !== ready.raster || textColor != color
        textLayout = ready.layout
        tileSet = ready.raster
        textColor = color
        if (changed) postInvalidateOnAnimation()
    }
'''
fast = replace_once(fast, old_set, new_set, "worker raster publication")
old_size = '''    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) rebuildTiles()
    }

'''
fast = replace_once(fast, old_size, "", "main-thread tile rebuild removal")
fast = replace_once(
    fast,
    "        if (Build.VERSION.SDK_INT >= 29 && tileSet?.draw(canvas, scrollY, height) == true) return\n",
    "        if (tileSet?.draw(canvas, scrollY, height) == true) return\n",
    "bitmap tile replay",
)
class_start = fast.index("@android.annotation.TargetApi(29)\nprivate class ReaderStaticLayoutTileSet")
class_end = fast.index("/** Continuous keeps the 4K bounded window;", class_start)
bitmap_class = '''internal class ReaderStaticLayoutBitmapTileSet(layout: StaticLayout, requestedTileHeightPx: Int) {
    private data class Tile(val top: Int, val bitmap: android.graphics.Bitmap)
    private val tileHeightPx = requestedTileHeightPx.coerceIn(MIN_BITMAP_TILE_HEIGHT_PX, MAX_BITMAP_TILE_HEIGHT_PX)
    private val tiles: List<Tile>

    init {
        val width = layout.width.coerceAtLeast(1)
        val totalHeight = layout.height.coerceAtLeast(1)
        val built = ArrayList<Tile>((totalHeight + tileHeightPx - 1) / tileHeightPx)
        var top = 0
        while (top < totalHeight) {
            val tileHeight = minOf(tileHeightPx, totalHeight - top).coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(width, tileHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.save()
            canvas.clipRect(0, 0, width, tileHeight)
            canvas.translate(0f, -top.toFloat())
            layout.draw(canvas)
            canvas.restore()
            bitmap.prepareToDraw()
            built += Tile(top, bitmap)
            top += tileHeight
        }
        tiles = built
    }

    fun draw(canvas: android.graphics.Canvas, scrollY: Int, viewportHeight: Int): Boolean {
        if (tiles.isEmpty()) return false
        val first = (scrollY / tileHeightPx).coerceIn(0, tiles.lastIndex)
        val last = ((scrollY + viewportHeight.coerceAtLeast(1) - 1) / tileHeightPx).coerceIn(first, tiles.lastIndex)
        for (index in first..last) {
            val tile = tiles[index]
            canvas.drawBitmap(tile.bitmap, 0f, (tile.top - scrollY).toFloat(), null)
        }
        return true
    }

    private companion object {
        const val MIN_BITMAP_TILE_HEIGHT_PX = 256
        const val MAX_BITMAP_TILE_HEIGHT_PX = 4096
    }
}

'''
fast = fast[:class_start] + bitmap_class + fast[class_end:]
fast = fast.replace(
    "/** Continuous keeps the 4K bounded window; real scroll frames replay only visible RenderNode tiles. */",
    "/** Continuous keeps the 4K bounded window; real scroll frames blit only visible worker-rasterized tiles. */",
)
old_produce = '''        val layout by produceState<ReaderContinuousLayout?>(
            null,
            text,
            style,
            overflow,
            widthPx,
            density.density,
            density.fontScale,
            nativeTypeface,
            resolvedColor,
        ) {
            value = withContext(Dispatchers.Default) {
                ReaderContinuousLayout(buildFastStaticLayout(text, style, density, nativeTypeface, resolvedColor, widthPx))
            }
        }
'''
new_produce = '''        val layout by produceState<ReaderContinuousLayout?>(
            null,
            text,
            style,
            overflow,
            widthPx,
            viewportHeightPx,
            density.density,
            density.fontScale,
            nativeTypeface,
            resolvedColor,
        ) {
            value = withContext(Dispatchers.Default) {
                val staticLayout = buildFastStaticLayout(text, style, density, nativeTypeface, resolvedColor, widthPx)
                ReaderContinuousLayout(
                    staticLayout,
                    ReaderStaticLayoutBitmapTileSet(staticLayout, viewportHeightPx.coerceAtLeast(1)),
                )
            }
        }
'''
fast = replace_once(fast, old_produce, new_produce, "worker bitmap rasterization")
fast = replace_once(
    fast,
    "                    viewport.setTextLayout(ready.layout, resolvedColor.toArgb())\n",
    "                    viewport.setTextLayout(ready, resolvedColor.toArgb())\n",
    "bitmap raster publication",
)
fast_path.write_text(fast)


# 4) Update hard contracts so synthetic pulses / text display-list replay cannot creep back in.
contract_path = Path("scripts/verify-reader-v3.sh")
contract = contract_path.read_text()
old_contract = '''require_literal "$fast_text" 'ReaderStaticLayoutTileSet' 'continuous viewport RenderNode tiles'
require_literal "$fast_text" 'node.beginRecording(width, tileHeight)' 'continuous tile pre-record'
require_literal "$fast_text" 'tileSet?.draw(canvas, scrollY, height)' 'visible continuous tile replay'
require_literal "$fast_text" 'canvas.drawRenderNode(tile.node)' 'continuous tile display-list replay'
'''
new_contract = '''require_literal "$fast_text" 'ReaderStaticLayoutBitmapTileSet' 'continuous worker-rasterized bitmap tiles'
require_literal "$fast_text" 'Bitmap.Config.ARGB_8888' 'continuous bounded raster format'
require_literal "$fast_text" 'bitmap.prepareToDraw()' 'continuous raster draw preparation'
require_literal "$fast_text" 'tileSet?.draw(canvas, scrollY, height)' 'visible continuous tile replay'
require_literal "$fast_text" 'canvas.drawBitmap(tile.bitmap' 'continuous cached bitmap replay'
require_literal "$fast_text" 'viewportHeightPx,' 'viewport-height raster state key'
require_literal "$fast_text" 'withContext(Dispatchers.Default)' 'off-main continuous raster build'
forbid_literal "$fast_text" 'canvas.drawRenderNode(tile.node)' 'continuous text display-list replay residue'
forbid_literal "$fast_text" 'RenderNode("ReaderContinuousTile' 'continuous RenderNode tile residue'
'''
contract = replace_once(contract, old_contract, new_contract, "continuous raster contracts")
insert_after = "require_literal \"$activity\" 'render(pageTurnDirection = -1)' 'previous page direction publication'\n"
contract = replace_once(
    contract,
    insert_after,
    insert_after + "require_literal \"$activity\" 'override fun dispatchKeyEvent(event: KeyEvent)' 'pre-system volume key dispatch'\nrequire_literal \"$activity\" 'handleReaderVolumeKey(keyCode)' 'unified volume paging route'\nforbid_literal \"$activity\" 'override fun onKeyDown(keyCode: Int' 'late volume key callback residue'\nrequire_literal \"$screen\" 'SelectionContainer(state = selectionState)' 'paged selection container'\nrequire_literal \"$screen\" 'Modifier.fillMaxSize().onSizeChanged { widthPx = it.width; heightPx = it.height }.then(semantics).then(gestures)' 'outer paged gesture observer'\n",
    "input route contracts",
)
contract_path.write_text(contract)

print("Reader V3 input/raster cut applied")
