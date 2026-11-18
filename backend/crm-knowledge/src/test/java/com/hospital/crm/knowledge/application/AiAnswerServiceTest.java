package com.hospital.crm.knowledge.application;

import com.hospital.crm.chat.api.ChatDtos;
import com.hospital.crm.chat.application.ChatService;
import com.hospital.crm.knowledge.api.KnowledgeDtos;
import com.hospital.crm.knowledge.domain.AiQueryLog;
import com.hospital.crm.knowledge.domain.AiQueryLogRepository;
import com.hospital.crm.knowledge.domain.KbChunk;
import com.hospital.crm.knowledge.domain.KbChunkRepository;
import com.hospital.crm.knowledge.domain.KbDocument;
import com.hospital.crm.knowledge.domain.KbDocumentRepository;
import com.hospital.crm.system.audit.AuditService;
import com.hospital.crm.system.security.UserPrincipal;
import com.hospital.crm.system.user.SysUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 数字人问答：引用来源可追溯、低置信度转人工、随访话术草稿与问答留痕。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnswerServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private AiQueryLogRepository queryLogRepository;
    @Mock
    private KbChunkRepository chunkRepository;
    @Mock
    private KbDocumentRepository documentRepository;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private ChatService chatService;
    @Mock
    private AuditService auditService;

    private AiAnswerService service;

    @BeforeEach
    void setUp() {
        service = new AiAnswerService(queryLogRepository, chunkRepository, documentRepository,
                userRepository, chatService, auditService);
        login();

        when(queryLogRepository.save(any(AiQueryLog.class))).thenAnswer(invocation -> {
            AiQueryLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
            }
            return log;
        });
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void askReturnsTraceableReferencesAndConfidence() {
        stubPublished("门诊预约指南", "KB700001", 1, "门诊预约指南：支持公众号、自助机与窗口预约");

        KnowledgeDtos.AskResult result = service.ask(
                new KnowledgeDtos.AskRequest("门诊预约", null, null, null));

        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(result.needsHandoff()).isFalse();
        assertThat(result.refs()).hasSize(1);
        assertThat(result.refs().get(0).docNo()).isEqualTo("KB700001");
        assertThat(result.refs().get(0).version()).isEqualTo(1);
        assertThat(result.answer()).contains("根据《门诊预约指南》（v1）");
        verify(queryLogRepository).save(any(AiQueryLog.class));
    }

    @Test
    void askWithoutHitsMarksHandoffAndSuggestsHuman() {
        when(chunkRepository.findPublishedChunks()).thenReturn(List.of());

        KnowledgeDtos.AskResult result = service.ask(
                new KnowledgeDtos.AskRequest("住院伙食费发票", null, null, null));

        assertThat(result.confidence()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.needsHandoff()).isTrue();
        assertThat(result.refs()).isEmpty();
        assertThat(result.answer()).contains("建议转人工");
        assertThat(result.handoffMessage()).contains("置信度不足");
    }

    @Test
    void lowConfidenceAskWithSessionTriggersChatHandoff() {
        stubPublished("门诊预约指南", "KB700001", 1, "门诊预约指南：支持公众号预约");
        when(chatService.requestHandoff(eq(SESSION_ID), any(ChatDtos.HandoffRequest.class)))
                .thenReturn(new ChatDtos.HandoffResult(false, "已进入人工队列，正在为您分配客服",
                        1, null));

        KnowledgeDtos.AskResult result = service.ask(
                new KnowledgeDtos.AskRequest("住院伙食费发票开具", null, null, SESSION_ID));

        assertThat(result.needsHandoff()).isTrue();
        ArgumentCaptor<ChatDtos.HandoffRequest> captor =
                ArgumentCaptor.forClass(ChatDtos.HandoffRequest.class);
        verify(chatService).requestHandoff(eq(SESSION_ID), captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("low_confidence");
        assertThat(result.handoffMessage()).contains("人工队列");
    }

    @Test
    void highConfidenceAskDoesNotTriggerHandoff() {
        stubPublished("门诊预约指南", "KB700001", 1, "门诊预约指南：支持公众号预约");

        service.ask(new KnowledgeDtos.AskRequest("门诊预约", null, null, SESSION_ID));

        verify(chatService, never()).requestHandoff(any(), any());
    }

    @Test
    void followupSceneBuildsScriptDraft() {
        stubPublished("术后随访要点", "KB700002", 2, "术后随访要点：观察体温与伤口，两周后复查");

        KnowledgeDtos.AskResult result = service.ask(
                new KnowledgeDtos.AskRequest("术后随访", null, "followup", null));

        assertThat(result.needsHandoff()).isFalse();
        assertThat(result.answer()).contains("【随访话术草稿】").contains("【结尾】");
        assertThat(result.refs().get(0).docNo()).isEqualTo("KB700002");
    }

    @Test
    void queryLogStoresCompactRefsAndScene() {
        stubPublished("门诊预约指南", "KB700001", 1, "门诊预约指南：支持公众号预约");

        service.ask(new KnowledgeDtos.AskRequest("门诊预约", null, "chat", null));

        ArgumentCaptor<AiQueryLog> captor = ArgumentCaptor.forClass(AiQueryLog.class);
        verify(queryLogRepository).save(captor.capture());
        AiQueryLog log = captor.getValue();
        assertThat(log.getRefs()).containsExactly("KB700001#v1#c1");
        assertThat(log.getScene()).isEqualTo("chat");
        assertThat(log.getAskedBy()).isEqualTo(USER_ID);
    }

    @Test
    void tokenizeCombinesAsciiWordsAndChineseBigrams() {
        Set<String> tokens = AiAnswerService.tokenize("预约CT检查");

        assertThat(tokens).contains("ct", "预约", "检查");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------
    private void stubPublished(String title, String docNo, int version, String chunkContent) {
        KbDocument document = new KbDocument();
        ReflectionTestUtils.setField(document, "id", DOC_ID);
        document.setDocNo(docNo);
        document.setTitle(title);
        document.setStatus("PUBLISHED");
        document.setCurrentVersion(version);

        KbChunk chunk = new KbChunk();
        ReflectionTestUtils.setField(chunk, "id", UUID.randomUUID());
        chunk.setDocumentId(DOC_ID);
        chunk.setDocVersion(version);
        chunk.setChunkIndex(1);
        chunk.setContent(chunkContent);
        chunk.setCharCount(chunkContent.length());

        when(chunkRepository.findPublishedChunks()).thenReturn(List.of(chunk));
        when(documentRepository.findAllById(any())).thenReturn(List.of(document));
    }

    private void login() {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(
                        new String[]{"crm:knowledge:read"})
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(USER_ID, "admin", "管理员", null, null, null,
                        Set.of(), Set.of(), List.of()), "", authorities));
    }
}
