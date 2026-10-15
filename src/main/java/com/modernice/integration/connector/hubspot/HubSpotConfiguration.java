package com.modernice.integration.connector.hubspot;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HubSpotProperties.class)
public class HubSpotConfiguration {
}
