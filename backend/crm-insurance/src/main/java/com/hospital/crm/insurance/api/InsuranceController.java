package com.hospital.crm.insurance.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.insurance.application.InsuranceCaseService;
import com.hospital.crm.insurance.application.InsuranceCompanyService;
import com.hospital.crm.insurance.application.InsuranceReconciliationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 商保中心接口：保险公司、保单、案件流转、材料、往来邮件与理赔对账。
 */
@RestController
@RequestMapping("/api/v1/insurance")
public class InsuranceController {

    private final InsuranceCompanyService companyService;
    private final InsuranceCaseService caseService;
    private final InsuranceReconciliationService reconciliationService;

    public InsuranceController(InsuranceCompanyService companyService,
                               InsuranceCaseService caseService,
                               InsuranceReconciliationService reconciliationService) {
        this.companyService = companyService;
        this.caseService = caseService;
        this.reconciliationService = reconciliationService;
    }

    // ---------- 保险公司 ----------

    @GetMapping("/companies")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<PageResponse<InsuranceDtos.CompanyView>> listCompanies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(companyService.listCompanies(keyword, status, page, pageSize));
    }

    @GetMapping("/companies/{id}")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<InsuranceDtos.CompanyView> companyDetail(@PathVariable UUID id) {
        return ApiResponse.ok(companyService.companyDetail(id));
    }

    @PostMapping("/companies")
    @PreAuthorize("hasAuthority('crm:insurance:manage')")
    public ApiResponse<InsuranceDtos.CompanyView> createCompany(
            @Valid @RequestBody InsuranceDtos.CreateCompanyRequest request) {
        return ApiResponse.ok(companyService.createCompany(request));
    }

    @PutMapping("/companies/{id}")
    @PreAuthorize("hasAuthority('crm:insurance:manage')")
    public ApiResponse<InsuranceDtos.CompanyView> updateCompany(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.UpdateCompanyRequest request) {
        return ApiResponse.ok(companyService.updateCompany(id, request));
    }

    @PostMapping("/companies/{id}/status")
    @PreAuthorize("hasAuthority('crm:insurance:manage')")
    public ApiResponse<InsuranceDtos.CompanyView> updateCompanyStatus(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.StatusRequest request) {
        return ApiResponse.ok(companyService.updateCompanyStatus(id, request.status()));
    }

    // ---------- 保单 ----------

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<PageResponse<InsuranceDtos.PolicyView>> listPolicies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(companyService.listPolicies(keyword, companyId, customerId, status,
                page, pageSize));
    }

    @GetMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<InsuranceDtos.PolicyView> policyDetail(@PathVariable UUID id) {
        return ApiResponse.ok(companyService.policyDetail(id));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('crm:insurance:manage')")
    public ApiResponse<InsuranceDtos.PolicyView> createPolicy(
            @Valid @RequestBody InsuranceDtos.CreatePolicyRequest request) {
        return ApiResponse.ok(companyService.createPolicy(request));
    }

    @PostMapping("/policies/{id}/status")
    @PreAuthorize("hasAuthority('crm:insurance:manage')")
    public ApiResponse<InsuranceDtos.PolicyView> updatePolicyStatus(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.StatusRequest request) {
        return ApiResponse.ok(companyService.updatePolicyStatus(id, request.status()));
    }

    // ---------- 案件 ----------

    @GetMapping("/cases")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<PageResponse<InsuranceDtos.CaseView>> listCases(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID policyId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID handlerUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(caseService.list(keyword, companyId, policyId, state, customerId,
                handlerUserId, dateFrom, dateTo, page, pageSize));
    }

    @PostMapping("/cases")
    @PreAuthorize("hasAuthority('crm:insurance:case:write')")
    public ApiResponse<InsuranceDtos.CaseDetailView> createCase(
            @Valid @RequestBody InsuranceDtos.CreateCaseRequest request) {
        return ApiResponse.ok(caseService.create(request));
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<InsuranceDtos.CaseDetailView> caseDetail(@PathVariable UUID id) {
        return ApiResponse.ok(caseService.detail(id));
    }

    @PostMapping("/cases/{id}/assign")
    @PreAuthorize("hasAuthority('crm:insurance:case:write')")
    public ApiResponse<InsuranceDtos.CaseDetailView> assignCase(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.AssignRequest request) {
        return ApiResponse.ok(caseService.assign(id, request));
    }

    @PostMapping("/cases/{id}/transition")
    @PreAuthorize("hasAuthority('crm:insurance:case:write')")
    public ApiResponse<InsuranceDtos.CaseDetailView> transitionCase(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.TransitionRequest request) {
        return ApiResponse.ok(caseService.transition(id, request));
    }

    @GetMapping("/cases/{id}/events")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<List<InsuranceDtos.EventView>> caseEvents(@PathVariable UUID id) {
        return ApiResponse.ok(caseService.events(id));
    }

    // ---------- 案件材料 ----------

    @GetMapping("/cases/{id}/materials")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<List<InsuranceDtos.MaterialView>> listMaterials(@PathVariable UUID id) {
        return ApiResponse.ok(caseService.materials(id));
    }

    @PostMapping("/cases/{id}/materials")
    @PreAuthorize("hasAuthority('crm:insurance:material:write')")
    public ApiResponse<InsuranceDtos.MaterialView> addMaterial(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.MaterialCreateRequest request) {
        return ApiResponse.ok(caseService.addMaterial(id, request));
    }

    @PostMapping("/materials/{id}/status")
    @PreAuthorize("hasAuthority('crm:insurance:material:write')")
    public ApiResponse<InsuranceDtos.MaterialView> updateMaterialStatus(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.StatusRequest request) {
        return ApiResponse.ok(caseService.updateMaterialStatus(id, request.status()));
    }

    // ---------- 邮件 ----------

    @GetMapping("/cases/{id}/emails")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<List<InsuranceDtos.EmailView>> listEmails(@PathVariable UUID id) {
        return ApiResponse.ok(caseService.emails(id));
    }

    @PostMapping("/cases/{id}/emails")
    @PreAuthorize("hasAuthority('crm:insurance:email:write')")
    public ApiResponse<InsuranceDtos.EmailView> addEmail(
            @PathVariable UUID id, @Valid @RequestBody InsuranceDtos.EmailCreateRequest request) {
        return ApiResponse.ok(caseService.addEmail(id, request));
    }

    // ---------- 对账 ----------

    @GetMapping("/reconciliations")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<PageResponse<InsuranceDtos.ReconciliationView>> listReconciliations(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(reconciliationService.list(companyId, period, status, page, pageSize));
    }

    @GetMapping("/reconciliations/{id}")
    @PreAuthorize("hasAuthority('crm:insurance:read')")
    public ApiResponse<InsuranceDtos.ReconciliationView> reconciliationDetail(@PathVariable UUID id) {
        return ApiResponse.ok(reconciliationService.detail(id));
    }

    @PostMapping("/reconciliations")
    @PreAuthorize("hasAuthority('crm:insurance:reconcile')")
    public ApiResponse<InsuranceDtos.ReconciliationView> generateReconciliation(
            @Valid @RequestBody InsuranceDtos.ReconciliationCreateRequest request) {
        return ApiResponse.ok(reconciliationService.generate(request));
    }

    @PostMapping("/reconciliations/{id}/confirm")
    @PreAuthorize("hasAuthority('crm:insurance:reconcile')")
    public ApiResponse<InsuranceDtos.ReconciliationView> confirmReconciliation(@PathVariable UUID id) {
        return ApiResponse.ok(reconciliationService.confirm(id));
    }
}
