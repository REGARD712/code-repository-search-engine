package com.codeseek.index.segment;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * README 与查询词文本归一化工具。
 *
 * <p>分词前先去掉 HTML 标签、标点符号、表情等特殊字符，只保留中文、字母和数字，
 * 并统一转为小写，保证索引和查询使用相同的词形。</p>
 */
public final class TextNormalizer {

    /**
     * HTML 标签正则，避免把 img、href 等标签名当作正文词。
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    /**
     * 特殊字符正则：只保留 Unicode 字母和数字，其余字符替换为空格。
     */
    private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+");

    private TextNormalizer() {
        // 工具类禁止实例化。
    }

    /**
     * 清洗文本。
     *
     * @param text 原始文本，可以为空
     * @return 只包含字母、数字和空格的归一化文本
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String withoutHtml = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        String withoutSpecialCharacters = SPECIAL_CHARACTER_PATTERN.matcher(withoutHtml).replaceAll(" ");
        return withoutSpecialCharacters.trim().toLowerCase(Locale.ROOT);
    }
}
