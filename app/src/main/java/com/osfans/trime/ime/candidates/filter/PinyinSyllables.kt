/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

/**
 * Closed set of standard Mandarin pinyin syllables, plus schema-style
 * single-letter and zh/ch/sh initial abbreviations. ü is stored as v.
 */
object PinyinSyllables {
    const val MAX_LENGTH = 6

    /** Consonant initials that may prefix a longer syllable when locked. */
    val ABBREVIATIONS: Set<String> = setOf(
        "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x",
        "z", "c", "s", "r", "y", "w",
        "zh", "ch", "sh",
    )

    val ALL: Set<String> = setOf(
        "a", "o", "e", "ai", "ei", "ao", "ou", "an", "en", "ang", "eng", "er",
        "yi", "ya", "ye", "yao", "you", "yan", "yin", "yang", "ying", "yong",
        "yu", "yue", "yuan", "yun", "yo",
        "wu", "wa", "wo", "wai", "wei", "wan", "wen", "wang", "weng",
        "i", "ia", "ie", "iao", "iu", "ian", "in", "iang", "ing", "iong",
        "u", "ua", "uo", "uai", "ui", "uan", "un", "uang", "ueng",
        "v", "ve", "van", "vn",
        "ba", "bo", "bai", "bei", "bao", "ban", "ben", "bang", "beng",
        "bi", "bie", "biao", "bian", "bin", "bing", "bu",
        "pa", "po", "pai", "pei", "pao", "pou", "pan", "pen", "pang", "peng",
        "pi", "pie", "piao", "pian", "pin", "ping", "pu",
        "ma", "mo", "me", "mai", "mei", "mao", "mou", "man", "men", "mang", "meng",
        "mi", "mie", "miao", "miu", "mian", "min", "ming", "mu",
        "fa", "fo", "fei", "fou", "fan", "fen", "fang", "feng", "fu",
        "da", "de", "dai", "dei", "dao", "dou", "dan", "den", "dang", "deng", "dong",
        "di", "dia", "die", "diao", "diu", "dian", "ding",
        "du", "duo", "dui", "duan", "dun",
        "ta", "te", "tai", "tei", "tao", "tou", "tan", "tang", "teng", "tong",
        "ti", "tie", "tiao", "tian", "ting",
        "tu", "tuo", "tui", "tuan", "tun",
        "na", "ne", "nai", "nei", "nao", "nou", "nan", "nen", "nang", "neng", "nong",
        "ni", "nie", "niao", "niu", "nian", "nin", "niang", "ning",
        "nu", "nuo", "nuan", "nun", "nv", "nve",
        "la", "lo", "le", "lai", "lei", "lao", "lou", "lan", "lang", "leng", "long",
        "li", "lia", "lie", "liao", "liu", "lian", "lin", "liang", "ling",
        "lu", "luo", "luan", "lun", "lv", "lve",
        "ga", "ge", "gai", "gei", "gao", "gou", "gan", "gen", "gang", "geng", "gong",
        "gua", "guo", "guai", "gui", "guan", "gun", "guang", "gu",
        "ka", "ke", "kai", "kei", "kao", "kou", "kan", "ken", "kang", "keng", "kong",
        "kua", "kuo", "kuai", "kui", "kuan", "kun", "kuang", "ku",
        "ha", "he", "hai", "hei", "hao", "hou", "han", "hen", "hang", "heng", "hong",
        "hua", "huo", "huai", "hui", "huan", "hun", "huang", "hu",
        "ji", "jia", "jie", "jiao", "jiu", "jian", "jin", "jiang", "jing", "jiong",
        "ju", "jue", "juan", "jun",
        "qi", "qia", "qie", "qiao", "qiu", "qian", "qin", "qiang", "qing", "qiong",
        "qu", "que", "quan", "qun",
        "xi", "xia", "xie", "xiao", "xiu", "xian", "xin", "xiang", "xing", "xiong",
        "xu", "xue", "xuan", "xun",
        "zha", "zhe", "zhi", "zhai", "zhei", "zhao", "zhou", "zhan", "zhen",
        "zhang", "zheng", "zhong", "zhua", "zhuo", "zhuai", "zhui", "zhuan",
        "zhun", "zhuang", "zhu",
        "cha", "che", "chi", "chai", "chao", "chou", "chan", "chen",
        "chang", "cheng", "chong", "chua", "chuo", "chuai", "chui", "chuan",
        "chun", "chuang", "chu",
        "sha", "she", "shi", "shai", "shei", "shao", "shou", "shan", "shen",
        "shang", "sheng", "shua", "shuo", "shuai", "shui", "shuan", "shun",
        "shuang", "shu",
        "re", "ri", "rao", "rou", "ran", "ren", "rang", "reng", "rong",
        "rua", "ruo", "rui", "ruan", "run", "ru",
        "za", "ze", "zi", "zai", "zei", "zao", "zou", "zan", "zen",
        "zang", "zeng", "zong", "zuo", "zui", "zuan", "zun", "zu",
        "ca", "ce", "ci", "cai", "cao", "cou", "can", "cen",
        "cang", "ceng", "cong", "cuo", "cui", "cuan", "cun", "cu",
        "sa", "se", "si", "sai", "sao", "sou", "san", "sen",
        "sang", "seng", "song", "suo", "sui", "suan", "sun", "su",
    ) + ABBREVIATIONS

    fun isAbbreviation(syllable: String): Boolean = syllable in ABBREVIATIONS
}
