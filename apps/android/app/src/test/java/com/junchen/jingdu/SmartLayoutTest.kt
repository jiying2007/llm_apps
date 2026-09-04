package com.junchen.jingdu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartLayoutTest {
    @Test fun joinsFixedWidthChineseHardWrapsWithoutAddingSpaces() {
        val source = listOf(
            "夜色沿着旧城的屋檐一点点沉下来，街边的灯笼刚刚亮起",
            "远处有人推开木门，风把门上的铜铃吹得轻轻响了几声",
            "她抱着刚买来的书站在桥边，看河面把灯影拉成长长一线",
            "船夫从石阶下经过，没有抬头，只把竹篙稳稳压进水里",
            "城门方向传来晚钟，声音越过瓦顶，落进安静的小巷深处",
            "她这才合上书页，沿着熟悉的路慢慢往家里的方向走去。",
        ).joinToString("\n")

        val result = SmartLayout.present(source)
        assertTrue(result.hardWrapDetected)
        assertTrue(result.joinedBreaks >= 4)
        assertFalse(result.text.contains("亮起\n远处"))
        assertTrue(result.text.endsWith("走去。"))
    }

    @Test fun preservesNormalParagraphPerLineAndDialogue() {
        val source = "第一章 夜雨\n\n她推开窗。\n\n“你终于回来了。”\n\n院子里的雨还在下。"
        val result = SmartLayout.present(source)
        assertFalse(result.hardWrapDetected)
        assertEquals(source, result.text)
    }

    @Test fun preservesIndentedParagraphBoundaryEvenInsideHardWrappedSample() {
        val source = listOf(
            "山路从村口一直向北延伸，清晨的雾还没有完全散开",
            "石阶旁的野草沾着水珠，鞋底踩过去会留下很浅的痕迹",
            "老人提着竹篮慢慢往前走，偶尔停下来听一听林中的鸟声",
            "树影随着风来回摇晃，细碎的光落在长满青苔的石墙上",
            "门前的木牌已经褪色，却还能看见当年留下来的几个旧字",
            "　　这是一个新的自然段，它不应该与上一行被智能拼接",
            "屋里传来烧水的声音，白汽很快从半开的窗缝里飘了出来。",
        ).joinToString("\n")
        val result = SmartLayout.present(source)
        assertTrue(result.text.contains("旧字\n　　这是一个新的自然段"))
    }

    @Test fun projectionRemainsMonotonicAcrossRemovedHardWraps() {
        val source = listOf(
            "旧书店门前摆着几张木桌，桌面上堆满刚从仓库搬出的书",
            "老板坐在最里面整理账本，听见脚步声才慢慢抬起头来",
            "窗外的阳光照进狭长过道，把空气里的灰尘照得清清楚楚",
            "她从书架抽出一本旧册，纸页边缘已经变成柔和的浅黄色",
            "扉页写着陌生人的名字，下面还有一行很淡的钢笔字迹",
            "她没有急着翻下去，只站在原地把那行字重新读了一遍。",
        ).joinToString("\n")
        val display = SmartLayout.present(source).text
        val projection = TextProjection.between(source, display)
        var last = 0L
        for (index in 0..display.codePointCount(0, display.length)) {
            val current = projection.sourceForDisplay(index.toLong())
            assertTrue(current >= last)
            last = current
        }
        assertEquals(source.codePointCount(0, source.length).toLong(), projection.sourceCodePoints)
    }
}
