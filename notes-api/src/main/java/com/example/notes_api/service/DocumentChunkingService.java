package com.example.notes_api.service;

import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 文档切分服务 —— 把长文本切成语义完整的短片段。
 * <p>
 * 核心职责：
 * <ol>
 *   <li><b>TokenTextSplitter</b>：基于 token 数切分，在标点处断裂（优先保证语义完整）</li>
 *   <li><b>滑动窗口重叠</b>：相邻 chunk 有 100 token 重叠，防止关键信息卡在边界</li>
 *   <li><b>元数据传递</b>：源文件名、chunk 序号等元数据注入每个 chunk</li>
 * </ol>
 * <p>
 * 面试重点：chunk size 为什么是 800？overlap 为什么是 100？
 * → 800 tokens ≈ 600 汉字，是一个"完整语义单元"的黄金区间；
 *   overlap 100 保证"退款流程如下…"不会被切到两个 chunk 里。
 *
 * @see TokenTextSplitter Spring AI 的 token 级别文本切分器
 */
@Service
public class DocumentChunkingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkingService.class);

    /** 中文句子分隔正则：在句号、问号、感叹号、分号、换行处断开 */
    private static final Pattern CHINESE_SENTENCE_PATTERN =
            Pattern.compile("(?<=[。！？；\\n])(?=\\S)");

    private final TokenTextSplitter tokenSplitter;

    /** 默认 chunk 大小（tokens） */
    static final int DEFAULT_CHUNK_SIZE = 800;

    /** 默认重叠大小（tokens） */
    static final int DEFAULT_OVERLAP = 100;

    /** 最小 chunk 字符数（短于此值的碎片直接丢弃） */
    static final int MIN_CHUNK_CHARS = 50;

    /** 中文每个 token 约等于的字符数（粗略估计） */
    static final double CHARS_PER_TOKEN_CN = 0.75;

    public DocumentChunkingService() {
        this.tokenSplitter = TokenTextSplitter.builder()
                .withChunkSize(DEFAULT_CHUNK_SIZE)
                .withMinChunkSizeChars(MIN_CHUNK_CHARS)
                .withMinChunkLengthToEmbed(10)   // 短于 10 token 的碎片不灌向量库
                .withMaxNumChunks(10_000)        // 上限防护
                .withKeepSeparator(true)         // 保留标点（语义更完整）
                .withEncodingType(EncodingType.CL100K_BASE) // GPT-4 同款编码，兼容性好
                .build();
        log.info("TokenTextSplitter 初始化: chunkSize={}, encoding=CL100K_BASE",
                DEFAULT_CHUNK_SIZE);
    }

    // ================================================================
    // 公开 API
    // ================================================================

    /**
     * 基础切分（无重叠）—— 直接委托 TokenTextSplitter。
     * <p>
     * 适用于：对重叠不敏感的场景，或作为管线第一刀粗切。
     *
     * @param text     原始文本
     * @param metadata 要注入每个 chunk 的元数据（文件名、来源等）
     * @return 切分后的文档片段列表
     */
    public List<Document> chunk(String text, Map<String, Object> metadata) {
        if (text == null || text.isBlank()) {
            log.warn("输入文本为空，跳过切分");
            return List.of();
        }

        Document inputDoc = new Document(text, new HashMap<>(metadata));
        List<Document> chunks = tokenSplitter.split(inputDoc);

        // 给每个 chunk 注入序号
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("chunk_index", i);
            chunks.get(i).getMetadata().put("total_chunks", chunks.size());
        }

        log.info("切分完成: 输入 {} 字符 → {} 个 chunk（平均 {} 字符/chunk）",
                text.length(), chunks.size(),
                chunks.isEmpty() ? 0 : text.length() / chunks.size());
        return chunks;
    }

    /**
     * 带滑动窗口重叠的切分 —— 相邻 chunk 间有 {@code overlapTokens} 的重叠区。
     * <p>
     * 实现思路：
     * <ol>
     *   <li>先按标点符号（。！？；\\n）把文本拆成句子</li>
     *   <li>贪心窗口：从位置 i 起，逐句加入直到 token 数接近 chunkSize</li>
     *   <li>下一窗口起点：从上一窗口末尾往回退，让重叠区 token 数 ≈ overlap</li>
     * </ol>
     * <p>
     * 为什么要自己实现？Spring AI 2.0.0 的 TokenTextSplitter 没有内置 overlap 机制。
     * 这个实现是"工程中必须自己补的胶水代码"。
     *
     * @param text          原始文本
     * @param metadata      要注入每个 chunk 的元数据
     * @param chunkTokens   目标 chunk 大小（tokens）
     * @param overlapTokens 相邻 chunk 重叠大小（tokens）
     * @return 带重叠的切分结果
     */
    public List<Document> chunkWithOverlap(String text, Map<String, Object> metadata,
                                           int chunkTokens, int overlapTokens) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 1. 按中文标点拆句子
        List<String> sentences = Arrays.asList(
                CHINESE_SENTENCE_PATTERN.split(text));
        sentences = sentences.stream()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();

        if (sentences.isEmpty()) {
            return List.of();
        }

        // 2. 贪心滑动窗口
        List<Document> chunks = new ArrayList<>();
        int windowStart = 0;

        while (windowStart < sentences.size()) {
            // 从 windowStart 开始，逐句加入直到 token 数接近 chunkTokens
            int estimatedTokens = 0;
            int windowEnd = windowStart;

            while (windowEnd < sentences.size()) {
                int nextTokens = estimateTokenCount(sentences.get(windowEnd));
                if (estimatedTokens + nextTokens > chunkTokens && estimatedTokens > 0) {
                    break; // 再加这句会超，停在这里
                }
                estimatedTokens += nextTokens;
                windowEnd++;
            }

            // 组装当前 chunk
            String chunkText = String.join("", sentences.subList(windowStart, windowEnd));
            if (!chunkText.isBlank() && chunkText.length() >= MIN_CHUNK_CHARS) {
                Map<String, Object> chunkMeta = new HashMap<>(metadata);
                chunkMeta.put("chunk_index", chunks.size());
                chunks.add(new Document(chunkText, chunkMeta));
            }

            // 3. 计算下一窗口起点：从 windowEnd 往回退，凑够 overlapTokens
            if (windowEnd >= sentences.size()) {
                break; // 已经到末尾
            }

            int overlapSoFar = 0;
            int newStart = windowEnd - 1;
            while (newStart > windowStart && overlapSoFar < overlapTokens) {
                overlapSoFar += estimateTokenCount(sentences.get(newStart));
                newStart--;
            }
            windowStart = Math.max(windowStart + 1, newStart + 1);
        }

        // 注入 total_chunks
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("total_chunks", chunks.size());
        }

        log.info("切分完成（带重叠 {} tokens）: {} 字符 → {} 个句子 → {} 个 chunk",
                overlapTokens, text.length(), sentences.size(), chunks.size());
        return chunks;
    }

    /**
     * 便捷方法：使用默认 chunkSize=800 / overlap=100。
     */
    public List<Document> chunkWithOverlap(String text, Map<String, Object> metadata) {
        return chunkWithOverlap(text, metadata, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    // ================================================================
    // 内部工具
    // ================================================================

    /**
     * 估算中文文本的 token 数。
     * <p>
     * CL100K_BASE 编码下，1 个中文字符 ≈ 1.3-2.0 个 token。
     * 这里用 1.33 (即 charsPerToken=0.75 的倒数) 作为粗略估计。
     * 精确值需要调 JTokkit 的 Encoding.encode()，但开销太大，chunk 大小本身也不需要精确到个位数。
     */
    private int estimateTokenCount(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / CHARS_PER_TOKEN_CN));
    }
}