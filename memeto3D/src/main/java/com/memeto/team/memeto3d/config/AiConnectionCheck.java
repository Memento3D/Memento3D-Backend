package com.memeto.team.memeto3d.config;

import com.memeto.team.memeto3d.exception.AiClientException;
import com.memeto.team.memeto3d.service.AiGenerationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "app.ai.connection-check-enabled",
        havingValue = "true"
)
public class AiConnectionCheck implements ApplicationRunner {

    private final AiGenerationClient aiGenerationClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            aiGenerationClient.checkHealth();
            log.info("AI connection check succeeded");
        } catch (AiClientException ex) {
            log.warn(
                    "AI connection check failed: upstreamStatus={}, message={}",
                    ex.getUpstreamStatus(),
                    ex.getMessage()
            );
        }
    }
}