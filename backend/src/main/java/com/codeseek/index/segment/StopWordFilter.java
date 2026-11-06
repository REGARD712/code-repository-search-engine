package com.codeseek.index.segment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中英文停用词过滤器。
 *
 * <p>停用词统一维护在 classpath 的 {@code stopwords.txt} 中，
 * 类加载时读取一次；空行和以 {@code #} 开头的注释行会被忽略。
 * 停用词在结巴分词完成后过滤，不参与倒排索引构建和查询词匹配。</p>
 */
public final class StopWordFilter {

    /**
     * 停用词文件路径。
     */
    private static final String STOP_WORD_RESOURCE = "/stopwords.txt";

    /**
     * 中英文停用词集合，类加载时从文件读取。
     */
    private static final Set<String> STOP_WORDS = loadStopWords();

    private StopWordFilter() {
        // 工具类禁止实例化。
    }

    /**
     * 从 classpath 加载停用词文件。
     *
     * @return 小写化后的停用词集合
     */
    private static Set<String> loadStopWords() {
        try (InputStream input = StopWordFilter.class.getResourceAsStream(STOP_WORD_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("找不到停用词文件：" + STOP_WORD_RESOURCE);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            )) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .filter(line -> !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("停用词文件读取失败：" + STOP_WORD_RESOURCE, exception);
        }
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
