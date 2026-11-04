package com.codeseek.index.store;

import com.codeseek.index.model.PostingList;
import com.codeseek.index.model.RepositoryDocument;
import com.codeseek.index.model.RepositoryInvertedIndex;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 倒排索引二进制文件读写器。
 *
 * <p>加载优化点：</p>
 * <ul>
 *     <li>使用紧凑二进制格式，避免 JSON / Java 序列化的解析开销；</li>
 *     <li>使用 1MB 缓冲流一次性顺序读取，减少系统调用；</li>
 *     <li>根据文件头中的文档数和词项数预分配 HashMap，避免反复扩容；</li>
 *     <li>倒排项使用原始类型数组保存，不为每个词频创建对象；</li>
 *     <li>写入临时文件后原子替换，避免加载到写了一半的索引。</li>
 * </ul>
 */
@Component
public class IndexFileStore {

    private static final int MAGIC = 0x43524958;

    private static final int VERSION = 1;

    private static final int BUFFER_SIZE = 1024 * 1024;

    private static final int MAX_STRING_BYTES = 4 * 1024 * 1024;

    private static final int MAX_DOCUMENTS = 5_000_000;

    private static final int MAX_TERMS = 20_000_000;

    private static final int MAX_POSTINGS = 100_000_000;

    /**
     * 保存倒排索引。
     *
     * @param path  文件路径
     * @param index 倒排索引
     * @throws IOException 文件写入失败
     */
    public void save(Path path, RepositoryInvertedIndex index) throws IOException {
        if (path == null || index == null) {
            throw new IllegalArgumentException("索引路径和索引内容不能为空");
        }

        Path absolutePath = path.toAbsolutePath();
        Path parent = absolutePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporaryPath = absolutePath.resolveSibling(absolutePath.getFileName() + ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(
                                    temporaryPath,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING,
                                    StandardOpenOption.WRITE
                            ),
                            BUFFER_SIZE
                    )
            )) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(index.documentCount());
                output.writeInt(index.termCount());

                // 文档按 ID 排序写入，便于加载后构建稳定的文档列表。
                List<Long> documentIds = new ArrayList<>(index.documents().keySet());
                Collections.sort(documentIds);
                for (Long documentId : documentIds) {
                    RepositoryDocument document = index.documents().get(documentId);
                    output.writeLong(documentId);
                    writeString(output, document.fullName());
                    writeString(output, document.htmlUrl());
                }

                // 词项按字典序写入，文件内容稳定，便于排查和后续做二分查找扩展。
                List<String> terms = new ArrayList<>(index.postings().keySet());
                Collections.sort(terms);
                for (String term : terms) {
                    PostingList postingList = index.postings().get(term);
                    writeString(output, term);
                    output.writeInt(postingList.size());
                    for (int i = 0; i < postingList.size(); i++) {
                        output.writeLong(postingList.documentIdAt(i));
                        output.writeInt(postingList.frequencyAt(i));
                    }
                }
            }

            moveAtomically(temporaryPath, absolutePath);
        } catch (IOException exception) {
            Files.deleteIfExists(temporaryPath);
            throw exception;
        }
    }

    /**
     * 加载倒排索引。
     *
     * @param path 文件路径
     * @return 倒排索引；文件不存在时返回空索引
     * @throws IOException 文件格式错误或读取失败
     */
    public RepositoryInvertedIndex load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("索引路径不能为空");
        }
        if (!Files.exists(path)) {
            return RepositoryInvertedIndex.empty();
        }

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path), BUFFER_SIZE)
        )) {
            int magic = input.readInt();
            if (magic != MAGIC) {
                throw new IOException("不是有效的仓库倒排索引文件");
            }
            int version = input.readInt();
            if (version != VERSION) {
                throw new IOException("不支持的倒排索引版本：" + version);
            }

            int documentCount = input.readInt();
            int termCount = input.readInt();
            validateCount("文档数", documentCount, MAX_DOCUMENTS);
            validateCount("词项数", termCount, MAX_TERMS);

            Map<Long, RepositoryDocument> documents = new HashMap<>(mapCapacity(documentCount));
            for (int i = 0; i < documentCount; i++) {
                long documentId = input.readLong();
                String fullName = readString(input);
                String htmlUrl = readString(input);
                documents.put(documentId, new RepositoryDocument(documentId, fullName, htmlUrl));
            }

            Map<String, PostingList> postings = new HashMap<>(mapCapacity(termCount));
            for (int i = 0; i < termCount; i++) {
                String term = readString(input);
                int postingCount = input.readInt();
                validateCount("倒排项数", postingCount, MAX_POSTINGS);

                long[] documentIds = new long[postingCount];
                int[] frequencies = new int[postingCount];
                for (int j = 0; j < postingCount; j++) {
                    documentIds[j] = input.readLong();
                    frequencies[j] = input.readInt();
                }
                postings.put(term, new PostingList(documentIds, frequencies));
            }

            return new RepositoryInvertedIndex(documents, postings);
        } catch (EOFException exception) {
            throw new IOException("倒排索引文件不完整", exception);
        }
    }

    /**
     * 写入长度前缀字符串。
     *
     * @param output 输出流
     * @param value  字符串
     * @throws IOException 写入失败
     */
    private void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException("索引字符串长度超过限制：" + bytes.length);
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    /**
     * 读取长度前缀字符串。
     *
     * @param input 输入流
     * @return 字符串
     * @throws IOException 读取失败
     */
    private String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("倒排索引字符串长度非法：" + length);
        }
        if (length == 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 校验文件中的数量字段，避免损坏文件触发超大内存分配。
     *
     * @param name  字段名称
     * @param value 数量
     * @param max   最大值
     * @throws IOException 数量非法
     */
    private void validateCount(String name, int value, int max) throws IOException {
        if (value < 0 || value > max) {
            throw new IOException(name + "非法：" + value);
        }
    }

    /**
     * 计算 HashMap 初始容量。
     *
     * @param expectedSize 预估元素数量
     * @return 初始容量
     */
    private int mapCapacity(int expectedSize) {
        if (expectedSize < 1) {
            return 1;
        }
        long capacity = (long) (expectedSize / 0.75f) + 1;
        return (int) Math.min(capacity, 1 << 30);
    }

    /**
     * 原子替换索引文件。
     *
     * @param source 临时文件
     * @param target 目标文件
     * @throws IOException 移动失败
     */
    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
