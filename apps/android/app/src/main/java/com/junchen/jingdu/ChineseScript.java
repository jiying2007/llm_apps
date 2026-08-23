package com.junchen.jingdu;

import java.util.LinkedHashSet;
import java.util.List;

/** Small deterministic Chinese-script bridge for search fallback; not a general-purpose converter. */
final class ChineseScript {
    private ChineseScript() {}

    // High-frequency characters found in modern long-form Chinese fiction and UI/search terms.
    // Only unambiguous one-to-one pairs are included; ambiguous conversions are deliberately omitted.
    private static final String SIMPLIFIED =
            "这为后发国书读时会里还进对从个们来着说现学与体门见风东语网无龙边开长间问听写点万两并过当应种头面马车云电话爱让给动亲声实认变战关张刘陈杨赵黄吴罗孙叶许谢钟邓郑冯陆苏吕卢蒋蔡贺顾严乔卫韩钱邱汤温龚赖谭蓝黎简繁毕宝贝尔乐业产众优传伤余侠侣侧侦债倾储儿党兰兴养内冈册军农冲决况冻净准凉减凤凯击划则刚创删别剂剑剧劝办务势劳区医华协单卖卢厅历压县参双台号叹吓吗听启员呆呜周咏响唤啸喷团园围图圆圣场坏块坚坛坝坟坠垄垒垦垫声壶处备复够梦头夹夺奋奖奥妇妈妆姬娄娱娇娘娱孙宁宝审宫宽宾对导寿将尘尝尽层岁岂岗岛岭峡币帅师帐帘带帮干庄庆库应庙废广归录彻忆怀态总怜恶恼惊惯愤愿戏战户扑执扩扫扬扰护报担拟拢拥择挂挚损换据掩揽搀摄摆摇摊撑敌数斋斗断无旧显晋晓暂术机杀杂权条来杨极构枪柜标树样桥档梦检楼欢欧欲毁毕气汇汉汤沟没泽洁浅浆润涂涛涌湾湿灭灯灵灾炉点炼热爱爷牵状独狭狮猎猫环现玛琼电画畅疗监盖盘眨矿码砖礼祸离秃种积稳穷窃竞笔笼签简粮紧纠红约级纪纯纳纸纹纺线练组细终织经绑结绕给绝绞统继绩续绳维综绿缠网罗罚罢翘翻职联聪肃胜胀胶脑脚脸腾舆舰艺节范茧荐药获莲莹营萨著虑虚虫虽蚀蚁蚕蛮补装裤见观规视览觉触誉计订认讨让议讯记讲讳讼设访证评词译试诗诚话诞询该详语误说请诸诺读课谁调谈谢谣谱贝负财责贤败账货质贩贪贫购贮贯贴贵贷费贺赁赃资赋赌赏赔赖赚赛赞赠赢赶趋跃践踪车轨转轮软轰轻载较辅辆辈辉输辖辙辞边达迁过运还进远违连迟适选递逻遗邮邻郑释里鉴钉钓钩钮钱铁铃铜铭银铺链锁锅锋锐错锦键锻镇镜长门闪闭问闲间闻阁队阳阴阵阶际陆陈险随隐难雾静顶项顺须顾顿颁预领颇频颗题颜额风飞饭饮饱饰饼馆马驭驰驱驶验骑骗骤鱼鲁鲜鸟鸡鸣鸦鸭鸿鹅鹤麦黄齐齿龄龙龟";
    private static final String TRADITIONAL =
            "這為後發國書讀時會裡還進對從個們來著說現學與體門見風東語網無龍邊開長間問聽寫點萬兩並過當應種頭面馬車雲電話愛讓給動親聲實認變戰關張劉陳楊趙黃吳羅孫葉許謝鐘鄧鄭馮陸蘇呂盧蔣蔡賀顧嚴喬衛韓錢邱湯溫龔賴譚藍黎簡繁畢寶貝爾樂業產眾優傳傷餘俠侶側偵債傾儲兒黨蘭興養內岡冊軍農沖決況凍淨準涼減鳳凱擊劃則剛創刪別劑劍劇勸辦務勢勞區醫華協單賣盧廳歷壓縣參雙臺號嘆嚇嗎聽啟員呆嗚周詠響喚嘯噴團園圍圖圓聖場壞塊堅壇壩墳墜壟壘墾墊聲壺處備復夠夢頭夾奪奮獎奧婦媽妝姬婁娛嬌娘娛孫寧寶審宮寬賓對導壽將塵嘗盡層歲豈崗島嶺峽幣帥師帳簾帶幫幹莊慶庫應廟廢廣歸錄徹憶懷態總憐惡惱驚慣憤願戲戰戶撲執擴掃揚擾護報擔擬攏擁擇掛摯損換據掩攬攙攝擺搖攤撐敵數齋鬥斷無舊顯晉曉暫術機殺雜權條來楊極構槍櫃標樹樣橋檔夢檢樓歡歐欲毀畢氣匯漢湯溝沒澤潔淺漿潤塗濤湧灣濕滅燈靈災爐點煉熱愛爺牽狀獨狹獅獵貓環現瑪瓊電畫暢療監蓋盤眨礦碼磚禮禍離禿種積穩窮竊競筆籠簽簡糧緊糾紅約級紀純納紙紋紡線練組細終織經綁結繞給絕絞統繼績續繩維綜綠纏網羅罰罷翹翻職聯聰肅勝脹膠腦腳臉騰輿艦藝節範繭薦藥獲蓮瑩營薩著慮虛蟲雖蝕蟻蠶蠻補裝褲見觀規視覽覺觸譽計訂認討讓議訊記講諱訟設訪證評詞譯試詩誠話誕詢該詳語誤說請諸諾讀課誰調談謝謠譜貝負財責賢敗賬貨質販貪貧購貯貫貼貴貸費賀賃贓資賦賭賞賠賴賺賽贊贈贏趕趨躍踐蹤車軌轉輪軟轟輕載較輔輛輩輝輸轄轍辭邊達遷過運還進遠違連遲適選遞邏遺郵鄰鄭釋裡鑒釘釣鉤鈕錢鐵鈴銅銘銀鋪鏈鎖鍋鋒銳錯錦鍵鍛鎮鏡長門閃閉問閒間聞閣隊陽陰陣階際陸陳險隨隱難霧靜頂項順須顧頓頒預領頗頻顆題顏額風飛飯飲飽飾餅館馬馭馳驅駛驗騎騙驟魚魯鮮鳥雞鳴鴉鴨鴻鵝鶴麥黃齊齒齡龍龜";

    static List<String> searchVariants(String query) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(query);
        String toTraditional = map(query, SIMPLIFIED, TRADITIONAL);
        String toSimplified = map(query, TRADITIONAL, SIMPLIFIED);
        if (!toTraditional.equals(query)) variants.add(toTraditional);
        if (!toSimplified.equals(query)) variants.add(toSimplified);
        return List.copyOf(variants);
    }

    private static String map(String source, String from, String to) {
        StringBuilder result = new StringBuilder(source.length());
        for (int offset = 0; offset < source.length();) {
            int cp = source.codePointAt(offset);
            offset += Character.charCount(cp);
            int index = from.indexOf(cp);
            if (index >= 0 && index < to.length()) result.append(to.charAt(index));
            else result.appendCodePoint(cp);
        }
        return result.toString();
    }
}
