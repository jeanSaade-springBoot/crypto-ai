package com.crypto.local;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;

/** Authenticated operational endpoint; deployment supplies its own credentials. */
@RestController
@RequestMapping("/api/local")
public class LocalDrainController {
    private final LocalDrainService drain;
    public LocalDrainController(LocalDrainService drain){this.drain=drain;}
    @PostMapping("/drain") public ResponseEntity<?> requestDrain(){drain.request(Duration.ofSeconds(60));return ResponseEntity.accepted().body(java.util.Map.of("status","DRAINING"));}
}
