package com.hospital.crm.knowledge.application;

import com.hospital.crm.chat.api.ChatDtos;
import com.hospital.crm.chat.application.ChatService;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.knowledge.api.KnowledgeDtos;
import com.hospital.crm.knowledge.domain.AiQueryLog;
import com.hospital.crm.knowledge.domain.AiQueryLogRepository;
import com.hospital.crm.knowledge.domain.KbChunk;
import com.hospital.crm.knowledge.domain.KbChunkRepository;
import com.hospital.crm.knowledge.domain.KbDocument;
import com.hospital.crm.knowledge.domain.KbDocumentRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.CurrentUser;
import com.hospital.crm.system.user.SysUser;
import com.hospital.crm.system.user.SysUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 数字人问答：基于已发布知识的关键词加权 RAG 检索，答案必须携带可追溯引用来源；
 * 置信度低于阈值或无命中时判定需转人工，若携带会话号则自动以 low_confidence 原因进入人工队列。
 * AI 答案仅由公开知识切片拼装，不读取任何患者/客户数据。
 */
@Service
public class AiAnswerService {

    private static final String RESOURCE = "CRM_AI_KB";

    public static final BigDecimal HANDOFF_THRESHOLD = new BigDecimal("0.3500");
    private static final BigDecimal MIN_REF_SCORE = new BigDecimal("0.1000");
    private static final int DEFAULT_TOP_K = 3;
    private static final int MAX_TOP_K = 10;

    private static final Pattern ASCII_WORD = Pattern.compile("[a-zA-Z0-9]{2,}");

    private final AiQueryLogRepository queryLogRepository;
    private final KbChunkRepository chunkRepository;
    private final KbDocumentRepository documentRepository;
    private final SysUserRepository userRepository;
    private final ChatService chatService;
    private final AuditService auditService;

    public AiAnswerService(AiQueryLogRepository queryLogRepository,
                           KbChunkRepository chunkRepository,
                           KbDocumentRepository documentRepository,
                           SysUserRepository userRepository,
                           ChatService chatService,
                           AuditService auditService) {
        this.queryLogRepository = queryLogRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.auditService = auditService;
    }

    @Transactional
    public KnowledgeDtos.AskResult ask(KnowledgeDtos.AskRequest request) {
        int topK = request.topK() == null || request.topK() < 1
                ? DEFAULT_TOP_K : Math.min(request.topK(), MAX_TOP_K);
        String scene = request.scene() == null || request.scene().isBlank() ? "chat" : request.scene();
        Set<String> tokens = tokenize(request.question());

        List<Scored> scored = new ArrayList<>();
        if (!tokens.isEmpty()) {
            List<KbChunk> candidates = chunkRepository.findPublishedChunks();
            Map<UUID, KbDocument> documents = loadDocuments(candidates);
            for (KbChunk chunk : candidates) {
                BigDecimal score = score(chunk.getContent(), tokens);
                if (score.compareTo(MIN_REF_SCORE) > 0) {
                    scored.add(new Scored(chunk, documents.get(chunk.getDocumentId()), score));
                }
            }
            scored.sort(Comparator.comparing((Scored item) -> item.score).reversed()
                    .thenComparing(item -> item.chunk.getCharCount()));
            if (scored.size() > topK) {
                scored = scored.subList(0, topK);
            }
        }

        BigDecimal confidence = scored.isEmpty()
                ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                : scored.get(0).score;
        boolean needsHandoff = confidence.compareTo(HANDOFF_THRESHOLD) < 0;

        List<KnowledgeDtos.RefView> refs = scored.stream()
                .filter(item -> item.document != null)
                .map(item -> new KnowledgeDtos.RefView(item.document.getId(), item.document.getDocNo(),
                        item.document.getTitle(), item.document.getCurrentVersion(),
                        item.chunk.getId(), item.chunk.getChunkIndex(), item.score))
                .toList();

        String answer = buildAnswer(scene, scored);
        String handoffMessage = null;
        if (needsHandoff) {
            handoffMessage = "AI 答案置信度不足，建议转人工客服处理。";
            if (request.sessionId() != null) {
                ChatDtos.HandoffResult handoff = chatService.requestHandoff(request.sessionId(),
                        new ChatDtos.HandoffRequest("low_confidence", "AI 答案置信度不足，自动转人工"));
                handoffMessage = handoff.message();
            }
        }

        AiQueryLog log = new AiQueryLog();
        log.setQuestion(request.question());
        log.setAnswer(answer);
        log.setConfidence(confidence);
        log.setRefs(refs.stream()
                .map(ref -> ref.docNo() + "#v" + ref.version() + "#c" + ref.chunkIndex())
                .toList());
        log.setScene(scene);
        log.setSessionId(request.sessionId());
        log.setNeedsHandoff(needsHandoff);
        log.setAskedBy(currentUserId());
        queryLogRepository.save(log);

        auditService.record("KB_AI_ASK", RESOURCE, log.getId().toString(),
                Map.of("scene", scene, "confidence", confidence.toPlainString(),
                        "refs", refs.size(), "needsHandoff", needsHandoff));

        return new KnowledgeDtos.AskResult(log.getId(), answer, confidence, refs, needsHandoff, handoffMessage);
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeDtos.QueryLogView> listLogs(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<AiQueryLog> result = queryLogRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        Set<UUID> userIds = new HashSet<>();
        result.getContent().forEach(log -> {
            if (log.getAskedBy() != null) {
                userIds.add(log.getAskedBy());
            }
        });
        Map<UUID, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user ->
                    names.put(user.getId(), displayName(user)));
        }
        List<KnowledgeDtos.QueryLogView> items = result.getContent().stream()
                .map(log -> new KnowledgeDtos.QueryLogView(log.getId(), log.getQuestion(), log.getAnswer(),
                        log.getConfidence(), log.getRefs(), log.getScene(), log.getSessionId(),
                        log.isNeedsHandoff(), log.getAskedBy(), names.get(log.getAskedBy()),
                        log.getCreatedAt()))
                .toList();
        return PageResponse.of(items, result.getTotalElements(), safePage, safePageSize);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------
    private String buildAnswer(String scene, List<Scored> scored) {
        List<Scored> hits = scored.stream().filter(item -> item.document != null).toList();
        if (hits.isEmpty()) {
            return "知识库暂无与该问题匹配的内容，建议转人工客服获取准确答复。";
        }
        if ("followup".equals(scene)) {
            String body = hits.stream()
                    .map(item -> "· " + excerpt(item.chunk.getContent()))
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            return "【随访话术草稿】\n您好，我是医院智能随访助手，想了解一下您近期的恢复情况。\n【要点】\n"
                    + body + "\n【结尾】\n如有不适请及时联系我们或前往医院就诊，祝您早日康复。";
        }
        StringBuilder builder = new StringBuilder();
        for (Scored item : hits) {
            builder.append("根据《").append(item.document.getTitle())
                    .append("》（v").append(item.document.getCurrentVersion()).append("）：")
                    .append(excerpt(item.chunk.getContent())).append('\n');
        }
        return builder.toString().trim();
    }

    private Map<UUID, KbDocument> loadDocuments(List<KbChunk> chunks) {
        Set<UUID> ids = new HashSet<>();
        chunks.forEach(chunk -> ids.add(chunk.getDocumentId()));
        Map<UUID, KbDocument> documents = new HashMap<>();
        if (!ids.isEmpty()) {
            documentRepository.findAllById(ids).forEach(document ->
                    documents.put(document.getId(), document));
        }
        return documents;
    }

    static Set<String> tokenize(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null) {
            return tokens;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Matcher matcher = ASCII_WORD.matcher(lower);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        StringBuilder chinese = new StringBuilder();
        for (char c : lower.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                chinese.append(c);
            } else {
                addBigrams(chinese.toString(), tokens);
                chinese.setLength(0);
            }
        }
        addBigrams(chinese.toString(), tokens);
        return tokens;
    }

    private static void addBigrams(String run, Set<String> tokens) {
        if (run.length() == 1) {
            tokens.add(run);
        }
        for (int i = 0; i + 2 <= run.length(); i++) {
            tokens.add(run.substring(i, i + 2));
        }
    }

    private static BigDecimal score(String content, Set<String> tokens) {
        String lower = content.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String token : tokens) {
            if (lower.contains(token)) {
                hits++;
            }
        }
        if (hits == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(hits)
                .divide(BigDecimal.valueOf(tokens.size()), 4, RoundingMode.HALF_UP);
    }

    private static String excerpt(String content) {
        return content.length() <= 160 ? content : content.substring(0, 160) + "…";
    }

    private String displayName(SysUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }

    private UUID currentUserId() {
        try {
            return CurrentUser.id();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record Scored(KbChunk chunk, KbDocument document, BigDecimal score) {
    }
}
