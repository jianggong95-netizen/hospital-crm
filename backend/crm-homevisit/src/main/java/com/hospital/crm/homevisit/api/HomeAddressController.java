package com.hospital.crm.homevisit.api;

import com.hospital.crm.common.api.ApiResponse;
import com.hospital.crm.homevisit.application.HomeAddressService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 客户服务地址接口：customerId 为业务必填查询参数，缺失时由框架返回 400。
 */
@RestController
@RequestMapping("/api/v1/home-addresses")
public class HomeAddressController {

    private final HomeAddressService addressService;

    public HomeAddressController(HomeAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:homevisit:read')")
    public ApiResponse<List<HomeVisitDtos.HomeAddressView>> list(@RequestParam UUID customerId) {
        return ApiResponse.ok(addressService.list(customerId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.HomeAddressView> create(
            @Valid @RequestBody HomeVisitDtos.HomeAddressCreateRequest request) {
        return ApiResponse.ok(addressService.create(request));
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.HomeAddressView> update(
            @PathVariable UUID addressId,
            @Valid @RequestBody HomeVisitDtos.HomeAddressUpdateRequest request) {
        return ApiResponse.ok(addressService.update(addressId, request));
    }

    @PutMapping("/{addressId}/status")
    @PreAuthorize("hasAuthority('crm:homevisit:write')")
    public ApiResponse<HomeVisitDtos.HomeAddressView> changeStatus(
            @PathVariable UUID addressId,
            @Valid @RequestBody HomeVisitDtos.HomeAddressStatusRequest request) {
        return ApiResponse.ok(addressService.changeStatus(addressId, request));
    }
}
