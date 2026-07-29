package com.godlei.onlinesafe.vaultimport.web;

import com.godlei.onlinesafe.vaultimport.application.AiConnectivityProbeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/ai")
public class AdminAiController {

    private final AiConnectivityProbeService probeService;

    public AdminAiController(AiConnectivityProbeService probeService) {
        this.probeService = probeService;
    }

    @PostMapping("/connectivity-test")
    public AiConnectivityTestResponse connectivityTest(@RequestBody(required = false) AiConnectivityTestRequest request) {
        return probeService.probe(request == null ? new AiConnectivityTestRequest(null, null, null) : request);
    }
}
