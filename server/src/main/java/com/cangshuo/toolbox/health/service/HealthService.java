package com.cangshuo.toolbox.health.service;

import com.cangshuo.toolbox.health.model.HealthResponse;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final HealthEndpoint healthEndpoint;

    public HealthService(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public HealthResponse getHealth() {
        return new HealthResponse(healthEndpoint.health().getStatus().getCode());
    }
}
