package com.codeseek.index.segment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中英文停用词过滤器。
 *
 * <p>停用词默认从外部文件 {@code C:\JAVA\search_engine\stopwords.txt} 加载，
 * 也可以通过 {@code -Dstopwords.path=...} 或环境变量 {@code STOPWORDS_PATH}
 * 指定其他路径。文件格式为 UTF-8、每行一个词，空行和以 {@code #} 开头的
 * 注释行会被忽略。</p>
 *
 * <p>如果外部文件不存在，会尝试读取 classpath 下的 {@code /stopwords.txt}
 * 作为回退；正式运行时应保证外部文件存在。</p>
 */
public final class StopWordFilter {

    /**
     * 默认外部停用词文件。
     */
    private static final String DEFAULT_STOP_WORD_PATH = "C:\\JAVA\\search_engine\\stopwords.txt";

    /**
     * classpath 回退文件。
     */
    private static final String CLASSPATH_STOP_WORD_RESOURCE = "/stopwords.txt";

    private static final String STOP_WORD_PATH = resolveStopWordPath();

    /**
     * 中英文停用词集合，类加载时从外部文件读取。
     */
    private static final Set<String> STOP_WORDS = loadStopWords();

    private StopWordFilter() {
        // 工具类禁止实例化。
    }

    /**
     * 解析停用词文件路径。
     *
     * <p>优先级：系统属性 {@code stopwords.path}、环境变量
     * {@code STOPWORDS_PATH}、默认路径。</p>
     *
     * @return 停用词文件路径
     */
    private static String resolveStopWordPath() {
        String systemPath = System.getProperty("stopwords.path");
        if (systemPath != null && !systemPath.isBlank()) {
            return systemPath.trim();
        }

        String environmentPath = System.getenv("STOPWORDS_PATH");
        if (environmentPath != null && !environmentPath.isBlank()) {
            return environmentPath.trim();
        }

        return DEFAULT_STOP_WORD_PATH;
    }

    /**
     * 从外部文件加载停用词；外部文件不存在时回退到 classpath。
     *
     * @return 小写化后的停用词集合
     */
    private static Set<String> loadStopWords() {
        Path path = Path.of(STOP_WORD_PATH);
        if (Files.isRegularFile(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return readStopWords(reader);
            } catch (IOException exception) {
                throw new IllegalStateException("停用词文件读取失败：" + path, exception);
            }
        }

        try (InputStream input = StopWordFilter.class.getResourceAsStream(CLASSPATH_STOP_WORD_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "停用词文件不存在：" + path + "，classpath 中也没有 "
                                + CLASSPATH_STOP_WORD_RESOURCE
                );
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            )) {
                return readStopWords(reader);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "classpath 停用词文件读取失败：" + CLASSPATH_STOP_WORD_RESOURCE,
                    exception
            );
        }
    }

    /**
     * 读取停用词文件内容。
     *
     * @param reader 字符流
     * @return 小写化后的停用词集合
     */
    private static Set<String> readStopWords(BufferedReader reader) {
        return reader.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .map(line -> line.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
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
