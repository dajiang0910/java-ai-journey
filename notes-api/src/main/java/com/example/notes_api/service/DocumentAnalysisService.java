package com.example.notes_api.service;

import com.example.notes_api.dto.DocumentAnalysisResponse;
import com.example.notes_api.dto.NoteMetadata;
import com.example.notes_api.dto.TextStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档智能分析服务 —— Week 4 Day 5 核心，Week 4 收网之作。
 * <p>
 * 相比 Day 3-4 的 {@link DocumentParseService}，本服务做了三件增强：
 * <ol>
 *   <li><b>文本统计</b>：程序计算字符数/句子数/段落数/阅读时间（非 AI，零成本）</li>
 *   <li><b>AI 关键句提取</b>：让 LLM 从长文中提炼 3-5 句最关键的句子</li>
 *   <li><b>SimpleLoggerAdvisor</b>：记录每次 LLM 调用的 request/response 到日志
 *       （开启 DEBUG 日志后可见，是生产环境可观测性的第一步）</li>
 * </ol>
 * <p>
 * <b>Advisor 是什么？</b>
 * Advisor 就像 Spring MVC 的 Interceptor 或 Servlet Filter，
 * 在 ChatClient 调用 LLM 之前/之后插入自定义逻辑。
 * SimpleLoggerAdvisor 是 Spring AI 内置的最简单的 Advisor，
 * 它在 DEBUG 日志中输出完整的 prompt 和 response，方便调试和问题排查。
 * <p>
 * 用法：
 * <pre>
 * chatClient.prompt()
 *     .advisors(a -> a.advisor(new SimpleLoggerAdvisor()))  // ← 一行挂上
 *     .user(text)
 *     .call()
 *     .content();
 * </pre>
 *
 * @see DocumentParseService 上游：Tika 文档解析
 * @see StructuredExtractService 下游：结构化元数据提取
 */
@Service
public class DocumentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisService.class);

    /** 中文阅读速度：约 400 字/分钟 */
    private static final double CHARS_PER_MINUTE = 400.0;

    private final ChatClient chatClient;
    private final StructuredExtractService extractService;

    /**
     * 构造器注入。
     * <p>
     * 注意：SimpleLoggerAdvisor 不在构造时挂到 Builder 上（那样会影响全局实例），
     * 而是在每次调用时通过 {@code .advisors()} 挂载，只影响当次请求。
     */
    public DocumentAnalysisService(ChatClient.Builder builder, StructuredExtractService extractService) {
        this.chatClient = builder.build();
        this.extractService = extractService;
    }

    /**
     * 文档智能分析 —— Week 4 Day 5 核心方法。
     * <p>
     * 执行流程：
     * <pre>
     * 纯文本 ─┬─▶ computeStatistics()  → TextStatistics（程序计算，零 AI 成本）
     *         ├─▶ extractV2()         → NoteMetadata  （AI 提取，Day 2 增强版）
     *         └─▶ extractKeySentences() → List&lt;String&gt;（AI 提炼，新增能力）
     *         ─────────── ───────────
     *         组装成 DocumentAnalysisResponse
     * </pre>
     *
     * @param text            Tika 解析出的纯文本
     * @param filename        原始文件名
     * @param detectedMimeType Tika 检测到的 MIME 类型
     * @return 综合分析结果
     */
    public DocumentAnalysisResponse analyze(String text, String filename, String detectedMimeType) {
        log.info("开始文档智能分析：{}（文本长度：{} 字符，类型：{}）", filename, text.length(), detectedMimeType);

        // Step 1：文本统计（纯计算，不调 AI）
        TextStatistics stats = computeStatistics(text);
        log.info("文本统计完成：{} 字，{} 句，{} 段，约 {} 分钟",
                stats.totalWords(), stats.totalSentences(), stats.totalParagraphs(), stats.estimatedMinutes());

        // Step 2：AI 提取结构化元数据（复用 Day 2 的 extractV2）
        NoteMetadata metadata = extractService.extractV2(text);
        log.info("AI 元数据提取完成：title={}, category={}", metadata.title(), metadata.category());

        // Step 3：AI 提炼关键句（新增能力，带 Advisor 日志观测）
        List<String> keySentences = extractKeySentences(text);
        log.info("关键句提取完成：共 {} 句", keySentences.size());

        // Step 4：组装响应
        String readingTimeDesc = String.format("约 %d 分钟", stats.estimatedMinutes());

        return new DocumentAnalysisResponse(
                filename,
                detectedMimeType,
                metadata,
                stats,
                keySentences,
                readingTimeDesc
        );
    }

    /**
     * 文本统计 —— 纯程序计算，不消耗 Token。
     * <p>
     * 中文统计策略：
     * <ul>
     *   <li>字符数：直接 length()（含标点空格）</li>
     *   <li>词数：中文字符 + 英文单词数估算</li>
     *   <li>句子数：按 .。!！?？ 切分</li>
     *   <li>段落数：按连续换行符分隔</li>
     * </ul>
     */
    TextStatistics computeStatistics(String text) {
        if (text == null || text.isBlank()) {
            return new TextStatistics(0, 0, 0, 0, 0);
        }

        int totalChars = text.length();

        // 句子数：按常见句末标点切分
        String[] sentences = text.split("[。！？.?!]+");
        // 过滤掉纯空白/标点的空段
        long totalSentences = Arrays.stream(sentences)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .count();

        // 段落数：按连续空行分隔
        String[] paragraphs = text.split("\\n\\s*\\n");
        long totalParagraphs = Arrays.stream(paragraphs)
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .count();

        // 词数估算：中文字符 + 英文单词
        long chineseChars = text.codePoints()
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                .count();
        String[] englishWords = text.replaceAll("[\\p{IsHan}]", " ")
                .trim().split("\\s+");
        long englishWordCount = Arrays.stream(englishWords)
                .filter(w -> !w.isEmpty() && w.matches(".*[a-zA-Z].*"))
                .count();
        int totalWords = (int) (chineseChars + englishWordCount);

        // 阅读时间：约 400 字/分钟（中英文混合）
        int estimatedMinutes = Math.max(1, (int) Math.ceil(totalWords / CHARS_PER_MINUTE));

        return new TextStatistics(
                totalChars,
                totalWords,
                (int) totalSentences,
                (int) totalParagraphs,
                estimatedMinutes
        );
    }

    /**
     * AI 关键句提取 —— 让 LLM 从全文提炼最有信息量的 3-5 个句子。
     * <p>
     * <b>Day 5 核心：SimpleLoggerAdvisor 演示</b>
     * <p>
     * 与 extractV2() 不同：extractV2 用 {@code .entity()} 做结构化输出，
     * 这里的 key sentence 提取用 {@code .content()} 返回自然语言，更适合
     * 让 LLM 直接引用原文句子而非生成新文本。
     * <p>
     * Advisor 挂载：{@code .advisors(a -> a.advisor(new SimpleLoggerAdvisor()))}
     * 只在本次调用生效，不影响全局 ChatClient 实例。
     *
     * @param text 文档全文
     * @return 3-5 个关键句子
     */
    private List<String> extractKeySentences(String text) {
        // 文本太短时，直接按句号拆分返回，省一次 AI 调用
        if (text.length() < 200) {
            return Arrays.stream(text.split("[。！？.!?]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(5)
                    .toList();
        }

        // 取前 3000 字符（避免超 Token 限制，同时够 LLM 理解全文主旨）
        String snippet = text.length() > 3000 ? text.substring(0, 3000) : text;

        String result = chatClient.prompt()
                .system("""
                        你是一个专业的文档分析助手。请从以下文本中提取 3-5 个最关键的句子。

                        规则：
                        1. 优先引用原文句子（逐字复制），不要自己概括
                        2. 如果找不到完整的原文句子，可以用冒号后的要点
                        3. 每句单独一行，用 "• " 开头
                        4. 按重要性从高到低排列
                        5. 不要输出任何其他内容（不要解释、不要总结）
                        """)
                .user(snippet)
                // ═══════════════════════════════════════════════
                // Day 5 核心：SimpleLoggerAdvisor
                // 在 DEBUG 日志中输出本次调用的完整 request/response
                // 开启方式：logback-spring.xml 中设
                //   <logger name="org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor" level="DEBUG"/>
                // ═══════════════════════════════════════════════
                .advisors(a -> a.advisors(new SimpleLoggerAdvisor()))
                .call()
                .content();

        if (result == null || result.isBlank()) {
            return List.of("（AI 未能提取关键句）");
        }

        // 解析 "• 句子1\n• 句子2" 格式
        return Arrays.stream(result.split("\\n"))
                .map(line -> line.replaceFirst("^[•\\-\\*]\\s*", "").trim())
                .filter(s -> !s.isEmpty())
                .limit(5)
                .toList();
    }
}
