package com.codeseek.index.segment;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结巴分词封装。
 *
 * <p>统一使用 {@link JiebaSegmenter.SegMode#SEARCH} 搜索引擎模式：
 * 对长词再次切分，提高召回率，索引构建和查询都复用同一套分词规则。</p>
 */
@Component
public class JiebaTextSegmenter {

    /**
     * 结巴分词器，词典为只读数据，可被多个请求共享。
     */
    private final JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();

    /**
     * 对文本进行搜索引擎模式分词。
     *
     * @param text 原始文本，可以为空
     * @return 分词结果；空文本返回空列表
     */
    public List<String> segment(String text) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<SegToken> tokens = jiebaSegmenter.process(normalized, JiebaSegmenter.SegMode.SEARCH);
        List<String> words = new ArrayList<>(tokens.size());
        for (SegToken token : tokens) {
            if (token.word == null) {
                continue;
            }
            String word = token.word.trim();
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }
}
