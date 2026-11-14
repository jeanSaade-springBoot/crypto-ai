package com.crypto.shared;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-142: endpoint opt-in/header/authenticated-reviewer guards.
 * Full ROLE_ADMIN HTTP/security-chain acceptance remains a separate release gate. */
class Fix142DiagnosticsControllerTest {
    private final SharedMarketConsumer consumer=mock(SharedMarketConsumer.class);
    private final Fix142DiagnosticsController controller=new Fix142DiagnosticsController(consumer,mock(Fix142HealthService.class));
    private final Instant start=Instant.parse("2026-10-03T09:00:00Z");
    private final Fix142DiagnosticsController.ProtectionResolution request=
        new Fix142DiagnosticsController.ProtectionResolution("BTCUSDT",1,"old-owner",start,
            "CONFIRMED_NOT_APPLIED","CONFIRMED_NO_EFFECTS","independent old owner fence proof",
            "independent verification that durable and in-process effects are absent");
    @Test void protectionResolutionIsDisabledByDefault() {
        assertThrows(IllegalStateException.class,()->controller.protection(request,()->"operator","CONFIRMED"));
        verifyNoInteractions(consumer);
    }
    @Test void protectionResolutionRequiresExplicitHeaderAndAuthenticatedReviewer() {
        ReflectionTestUtils.setField(controller,"mode","LIVE");
        ReflectionTestUtils.setField(controller,"resolutionEnabled",true);
        assertThrows(IllegalStateException.class,()->controller.protection(request,()->"operator","NO"));
        assertThrows(IllegalStateException.class,()->controller.protection(request,null,"CONFIRMED"));
        verifyNoInteractions(consumer);
    }
    @Test void protectionResolutionUsesAuthenticatedReviewerAndExactAttemptIdentity() {
        ReflectionTestUtils.setField(controller,"mode","LIVE");
        ReflectionTestUtils.setField(controller,"resolutionEnabled",true);
        assertEquals("COMMITTED",controller.protection(request,()->"authenticated-admin","CONFIRMED").get("result"));
        verify(consumer).resolveProtection("BTCUSDT",1,"old-owner",start,"CONFIRMED_NOT_APPLIED",
            "CONFIRMED_NO_EFFECTS","authenticated-admin",request.fenceProof(),request.effectEvidence());
    }
}
