package com.hospital.crm.knowledge.application;

import com.hospital.crm.common.exception.BusinessException;
import com.hospital.crm.knowledge.api.KnowledgeDtos;
import com.hospital.crm.knowledge.domain.KbCategory;
import com.hospital.crm.knowledge.domain.KbCategoryRepository;
import com.hospital.crm.knowledge.domain.KbChunk;
import com.hospital.crm.knowledge.domain.KbChunkRepository;
import com.hospital.crm.knowledge.domain.KbDocument;
import com.hospital.crm.knowledge.domain.KbDocumentRepository;
import com.hospital.crm.knowledge.domain.KbReview;
import com.hospital.crm.knowledge.domain.KbReviewRepository;
import com.hospital.crm.knowledge.domain.KbVersion;
import com.hospital.crm.knowledge.domain.KbVersionRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 知识库：上传解析切片、审核状态机、版本发布、敏感脱敏拦截与下架。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    @Mock
    private KbCategoryRepository categoryRepository;
    @Mock
    private KbDocumentRepository documentRepository;
    @Mock
    private KbChunkRepository chunkRepository;
    @Mock
    private KbVersionRepository versionRepository;
    @Mock
    private KbReviewRepository reviewRepository;
    @Mock
    private SysUserRepository userRepository;
    @Mock
    private AuditService auditService;

    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeService(categoryRepository, documentRepository, chunkRepository,
                versionRepository, reviewRepository, userRepository, auditService);
        login();

        when(documentRepository.nextDocNo()).thenReturn(700001L);
        when(documentRepository.save(any(KbDocument.class))).thenAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            if (document.getId() == null) {
                ReflectionTestUtils.setField(document, "id", DOC_ID);
            }
            return document;
        });
        when(categoryRepository.save(any(KbCategory.class))).thenAnswer(invocation -> {
            KbCategory category = invocation.getArgument(0);
            if (category.getId() == null) {
                ReflectionTestUtils.setField(category, "id", CATEGORY_ID);
            }
            return category;
        });
        when(chunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkRepository.countByDocumentIdAndDocVersion(any(), anyInt())).thenReturn(2L);
        when(chunkRepository.findByDocumentIdAndDocVersionOrderByChunkIndexAsc(any(), anyInt()))
                .thenReturn(List.of());
        when(categoryRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(versionRepository.save(any(KbVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.save(any(KbReview.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------
    @Test
    void createCategoryPersistsAndReturnsView() {
        when(categoryRepository.existsByCode("faq")).thenReturn(false);

        KnowledgeDtos.CategoryView view = service.createCategory(
                new KnowledgeDtos.CategoryRequest("faq", "常见问题", 10, null));

        assertThat(view.code()).isEqualTo("faq");
        assertThat(view.status()).isEqualTo("active");
    }

    @Test
    void createCategoryRejectsDuplicateCode() {
        when(categoryRepository.existsByCode("faq")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(
                new KnowledgeDtos.CategoryRequest("faq", "常见问题", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类编码已存在");
    }

    // ------------------------------------------------------------------
    // 创建与上传
    // ------------------------------------------------------------------
    @Test
    void createDocumentChunksContent() {
        KnowledgeDtos.DocumentView view = service.createDocument(
                new KnowledgeDtos.CreateDocumentRequest("门诊预约指南", null, null,
                        "第一段内容。\n\n第二段内容。"));

        assertThat(view.docNo()).isEqualTo("KB700001");
        assertThat(view.status()).isEqualTo("DRAFT");
        assertThat(view.currentVersion()).isZero();
        ArgumentCaptor<List<KbChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getChunkIndex()).isEqualTo(1);
        assertThat(captor.getValue().get(0).getContent()).contains("第一段内容").contains("第二段内容");
    }

    @Test
    void uploadParsesTxtFileIntoDraftChunks() {
        MockMultipartFile file = new MockMultipartFile("file", "预约须知.txt",
                "text/plain", "如何预约门诊？\n\n窗口与自助机均可。".getBytes(StandardCharsets.UTF_8));

        KnowledgeDtos.DocumentView view = service.upload(file, null, null, null);

        assertThat(view.title()).isEqualTo("预约须知");
        assertThat(view.sourceType()).isEqualTo("file");
        assertThat(view.fileName()).isEqualTo("预约须知.txt");
        verify(chunkRepository).saveAll(any());
    }

    @Test
    void uploadRejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf",
                "application/pdf", "data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(file, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持上传 txt/markdown 知识文件");
    }

    @Test
    void updateContentRejectedForPublishedDocument() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document("PUBLISHED", 1)));

        assertThatThrownBy(() -> service.updateContent(DOC_ID,
                new KnowledgeDtos.UpdateContentRequest("新内容", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿或已下架知识可修改内容");
    }

    @Test
    void updateContentReplacesDraftChunksOfNextVersion() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document("DRAFT", 2)));

        service.updateContent(DOC_ID, new KnowledgeDtos.UpdateContentRequest("全新知识内容", "修订"));

        verify(chunkRepository).deleteByDocumentIdAndDocVersion(DOC_ID, 3);
        ArgumentCaptor<List<KbChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getDocVersion()).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // 审核流
    // ------------------------------------------------------------------
    @Test
    void submitRejectsWhenDraftChunksMissing() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document("DRAFT", 0)));
        when(chunkRepository.findByDocumentIdAndDocVersionOrderByChunkIndexAsc(DOC_ID, 1))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.submit(DOC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识切片为空");
    }

    @Test
    void submitBlocksPatientSensitiveIdentifiers() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document("DRAFT", 0)));
        when(chunkRepository.findByDocumentIdAndDocVersionOrderByChunkIndexAsc(DOC_ID, 1))
                .thenReturn(List.of(chunk("联系人手机 13800138000，请回访")));

        assertThatThrownBy(() -> service.submit(DOC_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("患者敏感标识");
        verify(documentRepository, never()).submitDocument(any(), any());
    }

    @Test
    void submitMovesDraftToReviewing() {
        KbDocument document = document("DRAFT", 0);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document));
        when(chunkRepository.findByDocumentIdAndDocVersionOrderByChunkIndexAsc(DOC_ID, 1))
                .thenReturn(List.of(chunk("门诊预约指南内容")));
        when(documentRepository.submitDocument(eq(DOC_ID), any())).thenAnswer(invocation -> {
            document.setStatus("REVIEWING");
            return 1;
        });

        KnowledgeDtos.DocumentView view = service.submit(DOC_ID);

        assertThat(view.status()).isEqualTo("REVIEWING");
        ArgumentCaptor<KbReview> captor = ArgumentCaptor.forClass(KbReview.class);
        verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("SUBMIT");
    }

    @Test
    void approvePublishesDraftAsNewVersion() {
        KbDocument document = document("REVIEWING", 0);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document));
        when(documentRepository.approveDocument(eq(DOC_ID), eq(0), eq(1), any())).thenAnswer(invocation -> {
            document.setStatus("PUBLISHED");
            document.setCurrentVersion(1);
            return 1;
        });

        KnowledgeDtos.DocumentView view = service.approve(DOC_ID,
                new KnowledgeDtos.ReviewActionRequest("内容准确"));

        assertThat(view.status()).isEqualTo("PUBLISHED");
        assertThat(view.currentVersion()).isEqualTo(1);
        ArgumentCaptor<KbVersion> captor = ArgumentCaptor.forClass(KbVersion.class);
        verify(versionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(captor.getValue().getChunkCount()).isEqualTo(2);
    }

    @Test
    void approveFailsWhenConcurrentlyHandled() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document("REVIEWING", 0)));
        when(documentRepository.approveDocument(any(), anyInt(), anyInt(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.approve(DOC_ID, new KnowledgeDtos.ReviewActionRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他审核人处理");
    }

    @Test
    void rejectReturnsDocumentToDraft() {
        KbDocument document = document("REVIEWING", 0);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document));
        when(documentRepository.rejectDocument(eq(DOC_ID), any())).thenAnswer(invocation -> {
            document.setStatus("DRAFT");
            return 1;
        });

        KnowledgeDtos.DocumentView view = service.reject(DOC_ID,
                new KnowledgeDtos.ReviewActionRequest("来源不完整"));

        assertThat(view.status()).isEqualTo("DRAFT");
    }

    @Test
    void offlineOnlyForPublishedAndStopsRetrieval() {
        KbDocument published = document("PUBLISHED", 1);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(published));
        when(documentRepository.offlineDocument(eq(DOC_ID), eq("政策过期"), any())).thenAnswer(invocation -> {
            published.setStatus("OFFLINE");
            published.setOfflineReason("政策过期");
            return 1;
        });

        KnowledgeDtos.DocumentView view = service.offline(DOC_ID, new KnowledgeDtos.OfflineRequest("政策过期"));

        assertThat(view.status()).isEqualTo("OFFLINE");
        assertThat(view.offlineReason()).isEqualTo("政策过期");
    }

    @Test
    void offlineRejectsDraftDocument() {
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(document("DRAFT", 0)));

        assertThatThrownBy(() -> service.offline(DOC_ID, new KnowledgeDtos.OfflineRequest("原因")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已发布知识可下架");
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------
    private void login() {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(
                        new String[]{"crm:knowledge:read", "crm:knowledge:write", "crm:knowledge:review"})
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(USER_ID, "admin", "管理员", null, null, null,
                        Set.of(), Set.of(), List.of()), "", authorities));
    }

    private KbDocument document(String status, int currentVersion) {
        KbDocument document = new KbDocument();
        ReflectionTestUtils.setField(document, "id", DOC_ID);
        document.setDocNo("KB700001");
        document.setTitle("门诊预约指南");
        document.setStatus(status);
        document.setCurrentVersion(currentVersion);
        document.setOwnerId(USER_ID);
        return document;
    }

    private KbChunk chunk(String content) {
        KbChunk chunk = new KbChunk();
        ReflectionTestUtils.setField(chunk, "id", UUID.randomUUID());
        chunk.setDocumentId(DOC_ID);
        chunk.setDocVersion(1);
        chunk.setChunkIndex(1);
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        return chunk;
    }
}
