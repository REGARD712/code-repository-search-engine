package com.codeseek.index.model;

/**
 * 一个词对应的倒排列表。
 *
 * <p>使用两个原始类型数组保存文档 ID 和词频，避免为每个倒排项创建对象，
 * 既减少内存占用，也能加快索引文件加载速度。数组按文档 ID 升序排列，
 * 为多路归并检索提供有序输入。</p>
 */
public final class PostingList {

    private final long[] documentIds;

    private final int[] frequencies;

    /**
     * 创建倒排列表。
     *
     * @param documentIds 文档 ID 数组，必须严格升序
     * @param frequencies 对应词频数组，长度必须和文档 ID 数组一致
     */
    public PostingList(long[] documentIds, int[] frequencies) {
        if (documentIds == null || frequencies == null) {
            throw new IllegalArgumentException("倒排列表数组不能为空");
        }
        if (documentIds.length != frequencies.length) {
            throw new IllegalArgumentException("文档 ID 和词频数组长度不一致");
        }
        for (int i = 0; i < documentIds.length; i++) {
            if (frequencies[i] < 0) {
                throw new IllegalArgumentException("词频不能为负数");
            }
            if (i > 0 && documentIds[i] <= documentIds[i - 1]) {
                throw new IllegalArgumentException("倒排列表必须按文档 ID 严格升序排列");
            }
        }
        this.documentIds = documentIds;
        this.frequencies = frequencies;
    }

    /**
     * 获取倒排项数量。
     *
     * @return 倒排项数量
     */
    public int size() {
        return documentIds.length;
    }

    /**
     * 获取指定位置的文档 ID。
     *
     * @param index 下标
     * @return 文档 ID
     */
    public long documentIdAt(int index) {
        return documentIds[index];
    }

    /**
     * 获取指定位置的词频。
     *
     * @param index 下标
     * @return 词频
     */
    public int frequencyAt(int index) {
        return frequencies[index];
    }

    /**
     * 获取文档 ID 数组。
     *
     * @return 文档 ID 数组
     */
    public long[] documentIds() {
        return documentIds;
    }

    /**
     * 获取词频数组。
     *
     * @return 词频数组
     */
    public int[] frequencies() {
        return frequencies;
    }
}
