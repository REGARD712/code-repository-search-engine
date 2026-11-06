package com.codeseek.index.segment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 中英文停用词过滤器。
 *
 * <p>停用词在结巴分词完成后过滤，不参与倒排索引构建和查询词匹配。
 * 中文停用词按分词后的完整词项匹配，例如“一个”“因为”“进行”。</p>
 */
public final class StopWordFilter {

    /**
     * 中英文停用词集合。
     */
    private static final Set<String> STOP_WORDS;

    static {
        Set<String> words = new HashSet<>();

        // 图片中的基础英文停用词。
        Collections.addAll(words,
                "a", "an", "the", "and", "or", "but", "in", "on", "at", "to",
                "from", "with", "of", "for", "that", "this", "is", "are", "was",
                "were", "it", "i", "you", "he", "she", "we", "they", "be", "been",
                "have", "has", "had", "do", "does", "did", "will", "would", "should",
                "can", "could", "may", "might", "must", "shall", "not"
        );

        // 补充的英文功能词、代词、介词和连词。
        Collections.addAll(words,
                "about", "above", "after", "again", "against", "all", "am", "any",
                "as", "because", "before", "below", "between", "both", "by", "down",
                "during", "each", "few", "further", "here", "how", "if", "into",
                "just", "more", "most", "no", "nor", "now", "off", "once", "only",
                "other", "out", "over", "own", "same", "so", "some", "such", "than",
                "then", "there", "through", "too", "under", "until", "up", "very",
                "what", "when", "where", "which", "while", "who", "whom", "why",
                "her", "hers", "herself", "him", "himself", "his", "its", "itself",
                "me", "my", "myself", "our", "ours", "ourselves", "their", "theirs",
                "them", "themselves", "us", "your", "yours", "yourself", "yourselves",
                "also", "although", "always", "another", "anybody", "anyone", "anything",
                "either", "else", "even", "ever", "every", "everybody", "everyone",
                "everything", "however", "neither", "never", "often", "rather", "since",
                "still", "though", "thus", "unless", "upon", "whether", "within",
                "without", "yet"
        );

        // 中文常用虚词、代词、连词、介词和辅助词。
        Collections.addAll(words,
                "的", "地", "得", "了", "着", "过", "是", "在", "有", "和", "与",
                "及", "或", "或者", "而", "而且", "但", "但是", "然而", "因为",
                "所以", "因此", "如果", "虽然", "然后", "还是", "还有", "并且",
                "以及", "等等",
                "我", "你", "他", "她", "它", "我们", "你们", "他们", "她们",
                "它们", "自己", "大家", "别人", "这", "那", "这个", "那个",
                "这些", "那些", "这样", "那样", "这里", "那里", "什么", "怎么",
                "怎样", "为什么", "哪", "哪个", "哪些", "哪里",
                "不", "没", "没有", "就", "都", "也", "很", "太", "更", "最",
                "只", "才", "已", "已经", "正在", "将", "会", "能", "可以",
                "应该", "可能", "把", "被", "让", "从", "对", "对于", "向", "于",
                "为", "为了", "以", "因", "由", "关于", "按照", "根据", "通过",
                "进行", "一个", "一些", "一样", "一直", "一起", "一下", "一切",
                "上", "下", "中", "内", "外", "前", "后", "里", "之间", "之后",
                "之前", "以上", "以下", "方面"
        );

        STOP_WORDS = Collections.unmodifiableSet(words);
    }

    private StopWordFilter() {
        // 工具类禁止实例化。
    }

    /**
     * 过滤分词结果中的中英文停用词。
     *
     * @param words 结巴分词结果
     * @return 过滤后的词列表；入参为空时返回空列表
     */
    public static List<String> filter(List<String> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }

        List<String> filtered = new ArrayList<>(words.size());
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String normalizedWord = word.trim();
            if (!STOP_WORDS.contains(normalizedWord.toLowerCase(Locale.ROOT))) {
                filtered.add(normalizedWord);
            }
        }
        return filtered;
    }
}
