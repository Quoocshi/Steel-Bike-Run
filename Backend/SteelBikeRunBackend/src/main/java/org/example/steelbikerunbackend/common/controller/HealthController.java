package org.example.steelbikerunbackend.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Tag(name = "Health", description = "Health Check API for Infrastructure")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint for Azure/AWS")
    public Map<String, String> health() {
        return Map.of("status", "UP", "message", "Steel Bike Run Backend is running");
    }
}
