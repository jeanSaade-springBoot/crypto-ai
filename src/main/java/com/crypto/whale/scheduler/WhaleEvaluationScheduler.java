package com.crypto.whale.scheduler;

import com.crypto.whale.config.WhaleProperties;
import com.crypto.whale.service.WhaleEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhaleEvaluationScheduler {
    private final WhaleProperties properties;
    private final WhaleEvaluationService service;

    @Scheduled(fixedDelayString = "${whale.evaluation.fixed-delay-ms:60000}")
    public void evaluate() { if (properties.enabled()) service.evaluateDueActivities(); }
}
