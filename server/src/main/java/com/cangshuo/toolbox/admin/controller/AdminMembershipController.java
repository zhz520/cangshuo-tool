package com.cangshuo.toolbox.admin.controller;

import com.cangshuo.toolbox.common.logging.TraceIdFilter;
import com.cangshuo.toolbox.common.response.ApiResponse;
import com.cangshuo.toolbox.membership.model.MembershipRequest;
import com.cangshuo.toolbox.membership.model.MembershipResponse;
import com.cangshuo.toolbox.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin membership")
public class AdminMembershipController {

    private final MembershipService service;

    public AdminMembershipController(MembershipService service) {
        this.service = service;
    }

    @PutMapping(value = "/{id}/membership", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Grant or revoke membership for one account")
    public ApiResponse<MembershipResponse> update(@PathVariable long id,
            @Valid @RequestBody MembershipRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.grant(id, body), TraceIdFilter.traceId(request));
    }

    @GetMapping(value = "/{id}/membership")
    @Operation(summary = "Read one account's membership for the admin console")
    public ApiResponse<MembershipResponse> status(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.success(service.status(id), TraceIdFilter.traceId(request));
    }
}
