package com.hospital.crm.his.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.common.api.PageResponse;
import com.hospital.crm.his.application.HisPatientService;
import com.hospital.crm.his.application.HisPatientSyncService;
import com.hospital.crm.his.application.HisSyncLogService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/his")
public class HisController {

    private final HisPatientService patientService;
    private final HisPatientSyncService patientSyncService;
    private final HisSyncLogService syncLogService;

    public HisController(HisPatientService patientService,
                         HisPatientSyncService patientSyncService,
                         HisSyncLogService syncLogService) {
        this.patientService = patientService;
        this.patientSyncService = patientSyncService;
        this.syncLogService = syncLogService;
    }

    @GetMapping("/patients/search")
    @PreAuthorize("hasAuthority('his:patient:read')")
    public ApiResponse<HisDtos.PatientSearchResult> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String identityType) {
        return ApiResponse.ok(patientService.search(keyword, identityType));
    }

    @GetMapping("/patients/{externalPatientId}/360")
    @PreAuthorize("hasAuthority('his:patient:read')")
    public ApiResponse<HisDtos.Patient360> patient360(@PathVariable String externalPatientId) {
        return ApiResponse.ok(patientService.patient360(externalPatientId));
    }

    @PostMapping("/patients/{externalPatientId}/link")
    @PreAuthorize("hasAuthority('his:patient:link')")
    public ApiResponse<HisDtos.LinkResponse> link(@PathVariable String externalPatientId,
                                                  @Valid @RequestBody HisDtos.LinkRequest request) {
        return ApiResponse.ok(patientService.link(externalPatientId, request));
    }

    @PostMapping("/patients/sync")
    @PreAuthorize("hasAuthority('his:patient:link')")
    public ApiResponse<HisDtos.SyncResult> syncPatients() {
        return ApiResponse.ok(patientSyncService.syncAll());
    }

    @GetMapping("/sync-logs")
    @PreAuthorize("hasAuthority('his:patient:read')")
    public ApiResponse<PageResponse<HisDtos.SyncLogItem>> syncLogs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operation,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(syncLogService.page(page, pageSize, status, operation));
    }
}
