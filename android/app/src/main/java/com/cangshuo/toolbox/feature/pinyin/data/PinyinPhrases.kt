package com.cangshuo.toolbox.feature.pinyin.data

/** Curated common readings; matching is performed by the bounded conversion engine. */
internal object PinyinPhrases {
    val readings: Map<String, Array<String>> by lazy {
        val dict = LinkedHashMap<String, Array<String>>(340)
        // Common polyphone phrases sorted by frequency of misreading
        // ---- 重 (zhòng/chóng) ----
        dict["重庆"] = arrayOf("chong2", "qing4")
        dict["重复"] = arrayOf("chong2", "fu4")
        dict["重新"] = arrayOf("chong2", "xin1")
        dict["重要"] = arrayOf("zhong4", "yao4")
        dict["重量"] = arrayOf("zhong4", "liang4")
        dict["重大"] = arrayOf("zhong4", "da4")
        dict["重点"] = arrayOf("zhong4", "dian3")
        dict["重视"] = arrayOf("zhong4", "shi4")
        dict["严重"] = arrayOf("yan2", "zhong4")
        dict["体重"] = arrayOf("ti3", "zhong4")
        dict["重建"] = arrayOf("chong2", "jian4")
        dict["重叠"] = arrayOf("chong2", "die2")
        dict["重来"] = arrayOf("chong2", "lai2")
        dict["重温"] = arrayOf("chong2", "wen1")
        dict["重逢"] = arrayOf("chong2", "feng2")
        dict["重启"] = arrayOf("chong2", "qi3")
        dict["重生"] = arrayOf("chong2", "sheng1")
        dict["重组"] = arrayOf("chong2", "zu3")
        dict["重返"] = arrayOf("chong2", "fan3")
        dict["重播"] = arrayOf("chong2", "bo1")

        // ---- 长 (cháng/zhǎng) ----
        dict["长城"] = arrayOf("chang2", "cheng2")
        dict["长度"] = arrayOf("chang2", "du4")
        dict["长期"] = arrayOf("chang2", "qi1")
        dict["成长"] = arrayOf("cheng2", "zhang3")
        dict["校长"] = arrayOf("xiao4", "zhang3")
        dict["部长"] = arrayOf("bu4", "zhang3")
        dict["长大"] = arrayOf("zhang3", "da4")
        dict["长江"] = arrayOf("chang2", "jiang1")
        dict["长沙"] = arrayOf("chang2", "sha1")
        dict["长春"] = arrayOf("chang2", "chun1")
        dict["长安"] = arrayOf("chang2", "an1")
        dict["长远"] = arrayOf("chang2", "yuan3")
        dict["长寿"] = arrayOf("chang2", "shou4")
        dict["市长"] = arrayOf("shi4", "zhang3")
        dict["局长"] = arrayOf("ju2", "zhang3")
        dict["院长"] = arrayOf("yuan4", "zhang3")
        dict["队长"] = arrayOf("dui4", "zhang3")
        dict["班长"] = arrayOf("ban1", "zhang3")
        dict["厂长"] = arrayOf("chang3", "zhang3")
        dict["增长"] = arrayOf("zeng1", "zhang3")
        dict["家长"] = arrayOf("jia1", "zhang3")

        // ---- 行 (háng/xíng) ----
        dict["银行"] = arrayOf("yin2", "hang2")
        dict["行业"] = arrayOf("hang2", "ye4")
        dict["行列"] = arrayOf("hang2", "lie4")
        dict["行情"] = arrayOf("hang2", "qing2")
        dict["行为"] = arrayOf("xing2", "wei2")
        dict["行动"] = arrayOf("xing2", "dong4")
        dict["行走"] = arrayOf("xing2", "zou3")
        dict["行人"] = arrayOf("xing2", "ren2")
        dict["旅行"] = arrayOf("lv3", "xing2")
        dict["执行"] = arrayOf("zhi2", "xing2")
        dict["进行"] = arrayOf("jin4", "xing2")
        dict["举行"] = arrayOf("ju3", "xing2")
        dict["实行"] = arrayOf("shi2", "xing2")
        dict["流行"] = arrayOf("liu2", "xing2")
        dict["航行"] = arrayOf("hang2", "xing2")
        dict["通行"] = arrayOf("tong1", "xing2")
        dict["飞行"] = arrayOf("fei1", "xing2")

        // ---- 乐 (lè/yuè) ----
        dict["音乐"] = arrayOf("yin1", "yue4")
        dict["乐器"] = arrayOf("yue4", "qi4")
        dict["乐曲"] = arrayOf("yue4", "qu3")
        dict["乐谱"] = arrayOf("yue4", "pu3")
        dict["乐团"] = arrayOf("yue4", "tuan2")
        dict["快乐"] = arrayOf("kuai4", "le4")
        dict["乐观"] = arrayOf("le4", "guan1")
        dict["乐趣"] = arrayOf("le4", "qu4")
        dict["欢乐"] = arrayOf("huan1", "le4")
        dict["娱乐"] = arrayOf("yu2", "le4")

        // ---- 了 (le/liǎo) ----
        dict["了解"] = arrayOf("liao3", "jie3")
        dict["了不起"] = arrayOf("liao3", "bu4", "qi3")
        dict["了得"] = arrayOf("liao3", "de2")
        dict["了然"] = arrayOf("liao3", "ran2")
        dict["了如指掌"] = arrayOf("liao3", "ru2", "zhi3", "zhang3")

        // ---- 地 (dì/de) ----
        dict["地方"] = arrayOf("di4", "fang1")
        dict["地区"] = arrayOf("di4", "qu1")
        dict["地球"] = arrayOf("di4", "qiu2")
        dict["地址"] = arrayOf("di4", "zhi3")
        dict["地图"] = arrayOf("di4", "tu2")
        dict["地铁"] = arrayOf("di4", "tie3")
        dict["地震"] = arrayOf("di4", "zhen4")

        // ---- 都 (dū/dōu) ----
        dict["首都"] = arrayOf("shou3", "du1")
        dict["成都"] = arrayOf("cheng2", "du1")

        // ---- 干 (gān/gàn) ----
        dict["干部"] = arrayOf("gan4", "bu4")
        dict["干活"] = arrayOf("gan4", "huo2")
        dict["干燥"] = arrayOf("gan1", "zao4")
        dict["干净"] = arrayOf("gan1", "jing4")
        dict["饼干"] = arrayOf("bing3", "gan1")
        dict["干涉"] = arrayOf("gan1", "she4")
        dict["干扰"] = arrayOf("gan1", "rao3")

        // ---- 还 (hái/huán) ----
        dict["还是"] = arrayOf("hai2", "shi4")
        dict["还有"] = arrayOf("hai2", "you3")
        dict["还要"] = arrayOf("hai2", "yao4")
        dict["归还"] = arrayOf("gui1", "huan2")
        dict["偿还"] = arrayOf("chang2", "huan2")

        // ---- 会 (huì/kuài) ----
        dict["会计"] = arrayOf("kuai4", "ji4")

        // ---- 假 (jiǎ/jià) ----
        dict["假期"] = arrayOf("jia4", "qi1")
        dict["假日"] = arrayOf("jia4", "ri4")
        dict["放假"] = arrayOf("fang4", "jia4")
        dict["请假"] = arrayOf("qing3", "jia4")
        dict["假装"] = arrayOf("jia3", "zhuang1")
        dict["真假"] = arrayOf("zhen1", "jia3")

        // ---- 觉 (jué/jiào) ----
        dict["睡觉"] = arrayOf("shui4", "jiao4")
        dict["午觉"] = arrayOf("wu3", "jiao4")
        dict["觉得"] = arrayOf("jue2", "de2")
        dict["感觉"] = arrayOf("gan3", "jue2")
        dict["自觉"] = arrayOf("zi4", "jue2")
        dict["觉悟"] = arrayOf("jue2", "wu4")

        // ---- 看 (kàn/kān) ----
        dict["看守"] = arrayOf("kan1", "shou3")
        dict["看护"] = arrayOf("kan1", "hu4")

        // ---- 空 (kōng/kòng) ----
        dict["天空"] = arrayOf("tian1", "kong1")
        dict["空间"] = arrayOf("kong1", "jian1")
        dict["空气"] = arrayOf("kong1", "qi4")
        dict["有空"] = arrayOf("you3", "kong4")
        dict["空闲"] = arrayOf("kong4", "xian2")
        dict["空地"] = arrayOf("kong4", "di4")

        // ---- 难 (nán/nàn) ----
        dict["困难"] = arrayOf("kun4", "nan2")
        dict["难题"] = arrayOf("nan2", "ti2")
        dict["难道"] = arrayOf("nan2", "dao4")
        dict["灾难"] = arrayOf("zai1", "nan4")
        dict["难民"] = arrayOf("nan4", "min2")
        dict["遇难"] = arrayOf("yu4", "nan4")

        // ---- 弄 (nòng/lòng) ----
        dict["弄堂"] = arrayOf("long4", "tang2")

        // ---- 强 (qiáng/qiǎng/jiàng) ----
        dict["强烈"] = arrayOf("qiang2", "lie4")
        dict["强大"] = arrayOf("qiang2", "da4")
        dict["坚强"] = arrayOf("jian1", "qiang2")
        dict["勉强"] = arrayOf("mian3", "qiang3")
        dict["强迫"] = arrayOf("qiang3", "po4")
        dict["倔强"] = arrayOf("jue2", "jiang4")

        // ---- 少 (shǎo/shào) ----
        dict["少年"] = arrayOf("shao4", "nian2")
        dict["少数"] = arrayOf("shao3", "shu4")
        dict["多少"] = arrayOf("duo1", "shao3")
        dict["至少"] = arrayOf("zhi4", "shao3")
        dict["减少"] = arrayOf("jian3", "shao3")

        // ---- 数 (shù/shǔ) ----
        dict["数学"] = arrayOf("shu4", "xue2")
        dict["数量"] = arrayOf("shu4", "liang4")
        dict["数据"] = arrayOf("shu4", "ju4")
        dict["数字"] = arrayOf("shu4", "zi4")
        dict["数目"] = arrayOf("shu4", "mu4")
        dict["无数"] = arrayOf("wu2", "shu4")

        // ---- 说 (shuō/shuì) ----
        dict["游说"] = arrayOf("you2", "shui4")
        dict["说服"] = arrayOf("shuo1", "fu2")

        // ---- 为 (wéi/wèi) ----
        dict["为了"] = arrayOf("wei4", "le5")
        dict["因为"] = arrayOf("yin1", "wei4")
        dict["以为"] = arrayOf("yi3", "wei2")
        dict["认为"] = arrayOf("ren4", "wei2")
        dict["成为"] = arrayOf("cheng2", "wei2")
        dict["作为"] = arrayOf("zuo4", "wei2")
        dict["行为"] = arrayOf("xing2", "wei2")

        // ---- 相 (xiāng/xiàng) ----
        dict["相机"] = arrayOf("xiang4", "ji1")
        dict["相片"] = arrayOf("xiang4", "pian4")
        dict["相互"] = arrayOf("xiang1", "hu4")
        dict["相信"] = arrayOf("xiang1", "xin4")
        dict["相同"] = arrayOf("xiang1", "tong2")
        dict["真相"] = arrayOf("zhen1", "xiang4")
        dict["照相"] = arrayOf("zhao4", "xiang4")
        dict["相声"] = arrayOf("xiang4", "sheng1")

        // ---- 兴 (xīng/xìng) ----
        dict["高兴"] = arrayOf("gao1", "xing4")
        dict["兴趣"] = arrayOf("xing4", "qu4")
        dict["兴奋"] = arrayOf("xing1", "fen4")
        dict["兴旺"] = arrayOf("xing1", "wang4")
        dict["兴盛"] = arrayOf("xing1", "sheng4")
        dict["新兴"] = arrayOf("xin1", "xing1")
        dict["复兴"] = arrayOf("fu4", "xing1")

        // ---- 着 (zhe/zháo/zhuó) ----
        dict["着急"] = arrayOf("zhao2", "ji2")
        dict["着迷"] = arrayOf("zhao2", "mi2")
        dict["着凉"] = arrayOf("zhao2", "liang2")
        dict["着火"] = arrayOf("zhao2", "huo3")
        dict["着落"] = arrayOf("zhuo2", "luo4")
        dict["着重"] = arrayOf("zhuo2", "zhong4")
        dict["执着"] = arrayOf("zhi2", "zhuo2")
        dict["穿着"] = arrayOf("chuan1", "zhuo2")
        dict["着装"] = arrayOf("zhuo2", "zhuang1")

        // ---- 种 (zhǒng/zhòng) ----
        dict["种类"] = arrayOf("zhong3", "lei4")
        dict["各种"] = arrayOf("ge4", "zhong3")
        dict["品种"] = arrayOf("pin3", "zhong3")
        dict["种子"] = arrayOf("zhong3", "zi3")
        dict["种植"] = arrayOf("zhong4", "zhi2")
        dict["播种"] = arrayOf("bo1", "zhong3")
        dict["种地"] = arrayOf("zhong4", "di4")

        // ---- 朝 (cháo/zhāo) ----
        dict["朝代"] = arrayOf("chao2", "dai4")
        dict["朝廷"] = arrayOf("chao2", "ting2")
        dict["朝阳"] = arrayOf("chao2", "yang2")
        dict["朝向"] = arrayOf("chao2", "xiang4")
        dict["朝鲜"] = arrayOf("chao2", "xian3")
        dict["今朝"] = arrayOf("jin1", "zhao1")

        // ---- 参 (cān/shēn/cēn) ----
        dict["参加"] = arrayOf("can1", "jia1")
        dict["参与"] = arrayOf("can1", "yu4")
        dict["参考"] = arrayOf("can1", "kao3")
        dict["参观"] = arrayOf("can1", "guan1")
        dict["参数"] = arrayOf("can1", "shu4")
        dict["人参"] = arrayOf("ren2", "shen1")
        dict["参差"] = arrayOf("cen1", "ci1")

        // ---- 传 (chuán/zhuàn) ----
        dict["传统"] = arrayOf("chuan2", "tong3")
        dict["传说"] = arrayOf("chuan2", "shuo1")
        dict["传播"] = arrayOf("chuan2", "bo1")
        dict["传递"] = arrayOf("chuan2", "di4")
        dict["自传"] = arrayOf("zi4", "zhuan4")
        dict["传记"] = arrayOf("zhuan4", "ji4")

        // ---- 大 (dà/dài) ----
        dict["大夫"] = arrayOf("dai4", "fu5")

        // ---- 得 (dé/de/děi) ----
        dict["得到"] = arrayOf("de2", "dao4")
        dict["取得"] = arrayOf("qu3", "de2")
        dict["获得"] = arrayOf("huo4", "de2")
        dict["值得"] = arrayOf("zhi2", "de2")
        dict["舍得"] = arrayOf("she3", "de2")

        // ---- 的 (de/dí/dì) ----
        dict["目的"] = arrayOf("mu4", "di4")
        dict["的确"] = arrayOf("di2", "que4")

        // ---- 发 (fā/fà) ----
        dict["发展"] = arrayOf("fa1", "zhan3")
        dict["发现"] = arrayOf("fa1", "xian4")
        dict["头发"] = arrayOf("tou2", "fa4")
        dict["理发"] = arrayOf("li3", "fa4")
        dict["发型"] = arrayOf("fa4", "xing2")
        dict["白发"] = arrayOf("bai2", "fa4")

        // ---- 分 (fēn/fèn) ----
        dict["身分"] = arrayOf("shen1", "fen4")
        dict["成分"] = arrayOf("cheng2", "fen4")
        dict["部分"] = arrayOf("bu4", "fen4")
        dict["充分"] = arrayOf("chong1", "fen4")
        dict["分钟"] = arrayOf("fen1", "zhong1")
        dict["分析"] = arrayOf("fen1", "xi1")
        dict["分别"] = arrayOf("fen1", "bie2")

        // ---- 更 (gèng/gēng) ----
        dict["更加"] = arrayOf("geng4", "jia1")
        dict["更新"] = arrayOf("geng1", "xin1")
        dict["变更"] = arrayOf("bian4", "geng1")
        dict["更换"] = arrayOf("geng1", "huan4")

        // ---- 和 (hé/huò/huo) ----
        dict["和平"] = arrayOf("he2", "ping2")
        dict["和谐"] = arrayOf("he2", "xie2")
        dict["温和"] = arrayOf("wen1", "he2")
        dict["和面"] = arrayOf("huo2", "mian4")

        // ---- 好 (hǎo/hào) ----
        dict["好奇"] = arrayOf("hao4", "qi2")
        dict["爱好"] = arrayOf("ai4", "hao4")
        dict["好客"] = arrayOf("hao4", "ke4")

        // ---- 降 (jiàng/xiáng) ----
        dict["投降"] = arrayOf("tou2", "xiang2")
        dict["降低"] = arrayOf("jiang4", "di1")
        dict["下降"] = arrayOf("xia4", "jiang4")

        // ---- 教 (jiāo/jiào) ----
        dict["教室"] = arrayOf("jiao4", "shi4")
        dict["教育"] = arrayOf("jiao4", "yu4")
        dict["教师"] = arrayOf("jiao4", "shi1")
        dict["教学"] = arrayOf("jiao4", "xue2")
        dict["宗教"] = arrayOf("zong1", "jiao4")
        dict["教书"] = arrayOf("jiao1", "shu1")
        dict["教导"] = arrayOf("jiao4", "dao3")

        // ---- 没 (méi/mò) ----
        dict["没有"] = arrayOf("mei2", "you3")
        dict["没关系"] = arrayOf("mei2", "guan1", "xi5")
        dict["淹没"] = arrayOf("yan1", "mo4")
        dict["沉没"] = arrayOf("chen2", "mo4")
        dict["埋没"] = arrayOf("mai2", "mo4")

        // ---- 率 (lǜ/shuài) ----
        dict["效率"] = arrayOf("xiao4", "lv4")
        dict["频率"] = arrayOf("pin2", "lv4")
        dict["比率"] = arrayOf("bi3", "lv4")
        dict["概率"] = arrayOf("gai4", "lv4")
        dict["利率"] = arrayOf("li4", "lv4")
        dict["率领"] = arrayOf("shuai4", "ling3")
        dict["率先"] = arrayOf("shuai4", "xian1")

        // ---- 处 (chǔ/chù) ----
        dict["处理"] = arrayOf("chu3", "li3")
        dict["处于"] = arrayOf("chu3", "yu2")
        dict["到处"] = arrayOf("dao4", "chu4")
        dict["深处"] = arrayOf("shen1", "chu4")
        dict["好处"] = arrayOf("hao3", "chu5")
        dict["住处"] = arrayOf("zhu4", "chu4")
        dict["用处"] = arrayOf("yong4", "chu5")

        // ---- 调 (tiáo/diào) ----
        dict["调查"] = arrayOf("diao4", "cha2")
        dict["调整"] = arrayOf("tiao2", "zheng3")
        dict["协调"] = arrayOf("xie2", "tiao2")
        dict["空调"] = arrayOf("kong1", "tiao2")
        dict["格调"] = arrayOf("ge2", "diao4")
        dict["声调"] = arrayOf("sheng1", "diao4")
        dict["曲调"] = arrayOf("qu3", "diao4")

        // ---- 应 (yīng/yìng) ----
        dict["应该"] = arrayOf("ying1", "gai1")
        dict["应当"] = arrayOf("ying1", "dang1")
        dict["应用"] = arrayOf("ying4", "yong4")
        dict["反应"] = arrayOf("fan3", "ying4")
        dict["适应"] = arrayOf("shi4", "ying4")
        dict["答应"] = arrayOf("da1", "ying5")

        // ---- 只 (zhī/zhǐ) ----
        dict["只有"] = arrayOf("zhi3", "you3")
        dict["只是"] = arrayOf("zhi3", "shi4")
        dict["只好"] = arrayOf("zhi3", "hao3")
        dict["只要"] = arrayOf("zhi3", "yao4")
        dict["只能"] = arrayOf("zhi3", "neng2")
        dict["不只"] = arrayOf("bu4", "zhi3")

        // ---- 折 (zhé/zhē/shé) ----
        dict["折扣"] = arrayOf("zhe2", "kou4")
        dict["打折"] = arrayOf("da3", "zhe2")
        dict["折叠"] = arrayOf("zhe2", "die2")
        dict["折腾"] = arrayOf("zhe1", "teng5")
        dict["骨折"] = arrayOf("gu3", "zhe2")

        // ---- 奇 (qí/jī) ----
        dict["奇数"] = arrayOf("ji1", "shu4")
        dict["奇偶"] = arrayOf("ji1", "ou3")
        dict["奇怪"] = arrayOf("qi2", "guai4")
        dict["好奇"] = arrayOf("hao4", "qi2")
        dict["惊奇"] = arrayOf("jing1", "qi2")

        // ---- 薄 (báo/bó/bò) ----
        dict["薄弱"] = arrayOf("bo2", "ruo4")
        dict["刻薄"] = arrayOf("ke4", "bo2")
        dict["薄荷"] = arrayOf("bo4", "he5")

        // ---- 曾 (céng/zēng) ----
        dict["曾经"] = arrayOf("ceng2", "jing1")
        dict["曾几何时"] = arrayOf("ceng2", "ji3", "he2", "shi2")

        // ---- 差 (chā/chà/chāi/cī) ----
        dict["差别"] = arrayOf("cha1", "bie2")
        dict["差距"] = arrayOf("cha1", "ju4")
        dict["差异"] = arrayOf("cha1", "yi4")
        dict["差不多"] = arrayOf("cha4", "bu4", "duo1")
        dict["出差"] = arrayOf("chu1", "chai1")

        // ---- 藏 (cáng/zàng) ----
        dict["西藏"] = arrayOf("xi1", "zang4")
        dict["藏族"] = arrayOf("zang4", "zu2")
        dict["隐藏"] = arrayOf("yin3", "cang2")
        dict["收藏"] = arrayOf("shou1", "cang2")
        dict["躲藏"] = arrayOf("duo3", "cang2")

        // ---- 乘 (chéng/shèng) ----
        dict["乘法"] = arrayOf("cheng2", "fa3")
        dict["乘客"] = arrayOf("cheng2", "ke4")

        // ---- 供 (gōng/gòng) ----
        dict["供应"] = arrayOf("gong1", "ying4")
        dict["提供"] = arrayOf("ti2", "gong1")
        dict["供给"] = arrayOf("gong1", "ji3")
        dict["供奉"] = arrayOf("gong4", "feng4")
        dict["口供"] = arrayOf("kou3", "gong4")

        // ---- 哪 (nǎ/na/né) ----
        dict["哪里"] = arrayOf("na3", "li3")
        dict["哪个"] = arrayOf("na3", "ge4")
        dict["哪些"] = arrayOf("na3", "xie1")
        dict["哪儿"] = arrayOf("na3", "er2")

        // ---- 那 (nà/nèi) ----
        dict["那里"] = arrayOf("na4", "li3")
        dict["那个"] = arrayOf("na4", "ge4")
        dict["那些"] = arrayOf("na4", "xie1")
        dict["那么"] = arrayOf("na4", "me5")

        // ---- 便 (biàn/pián) ----
        dict["方便"] = arrayOf("fang1", "bian4")
        dict["便利"] = arrayOf("bian4", "li4")
        dict["便宜"] = arrayOf("pian2", "yi5")

        // ---- 称 (chēng/chèn/chèng) ----
        dict["称呼"] = arrayOf("cheng1", "hu1")
        dict["名称"] = arrayOf("ming2", "cheng1")
        dict["称赞"] = arrayOf("cheng1", "zan4")
        dict["称职"] = arrayOf("chen4", "zhi2")
        dict["对称"] = arrayOf("dui4", "chen4")
        dict["匀称"] = arrayOf("yun2", "chen4")
        dict["称心"] = arrayOf("chen4", "xin1")

        dict
    }
}
