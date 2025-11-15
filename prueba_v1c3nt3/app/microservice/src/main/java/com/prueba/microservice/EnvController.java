package com.prueba.microservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EnvController {

    // Esta vendrá de variable de entorno (inyectada desde Vault via Jenkins)
    @Value("${app.secret:undefined}")
    private String appSecret;

    // Esta simula config, luego podemos moverla a ConfigMap
    @Value("${app.config.value:default-config}")
    private String configValue;

    @GetMapping("/env-secret")
    public Map<String, String> getEnvSecret() {
        return Map.of("secret", appSecret);
    }

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        return Map.of("config", configValue);
    }
}
