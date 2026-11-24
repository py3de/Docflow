package com.docflow.metrics;

import com.docflow.common.Tenants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public OverviewMetrics overview(@RequestHeader(name = Tenants.HEADER, required = false) String tenantId) {
        return service.overview(Tenants.require(tenantId));
    }
}
