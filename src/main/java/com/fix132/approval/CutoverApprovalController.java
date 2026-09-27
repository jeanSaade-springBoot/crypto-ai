package com.fix132.approval;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;

@RestController
@RequestMapping("/api/fix132/cutover")
public class CutoverApprovalController {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CutoverApprovalController.class);
    private final CutoverApprovalService service;
    public CutoverApprovalController(CutoverApprovalService service){this.service=service;}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("headerName",token.getHeaderName(),"token",token.getToken());
    }
    public record Preview(String symbol,String operation) {}
    public record Approval(String proposalId,String reference) {}
    @PostMapping("/preview") public Map<String,Object> preview(@RequestBody Preview request,Authentication auth) {
        return service.preview(request.symbol(),request.operation(),auth);
    }
    @PostMapping("/approve") public Map<String,Object> approve(@RequestBody Approval request,Authentication auth) {
        return service.approve(request.proposalId(),request.reference(),auth);
    }
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public Map<String,String> conflict(IllegalStateException e){log.warn("[FIX-132][CUTOVER_REJECTED] {}",e.getMessage());return Map.of("error",e.getMessage());}
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(IllegalArgumentException e){return Map.of("error",e.getMessage());}
}
