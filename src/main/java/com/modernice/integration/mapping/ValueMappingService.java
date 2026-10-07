package com.modernice.integration.mapping;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ValueMappingService {

    private static final Map<String, String> ERP_TERRITORY = Map.ofEntries(
            Map.entry("601", "601-INDUSTRIAL"),
            Map.entry("100", "1-NORTHEAST REGION"),
            Map.entry("950", "REGION-ARCTIC GLACIER"),
            Map.entry("400", "4-SOUTHWEST REGION"),
            Map.entry("700", "7-WESTERN REGION"),
            Map.entry("200", "2-SOUTHEAST REGION"),
            Map.entry("301", "CARIBBEAN/CNTRL AMERICA"),
            Map.entry("900", "9-INTERNATIONAL"),
            Map.entry("500", "5-REGION-HCI"),
            Map.entry("502", "502-MODERN ICE STOREFRONT"),
            Map.entry("925", "925 - Government")
    );

    public String map(String mappingType, String sourceValue) {
        if (sourceValue == null) {
            return null;
        }

        return switch (mappingType) {
            case "ERP_TERRITORY" -> ERP_TERRITORY.getOrDefault(sourceValue, sourceValue);
            default -> sourceValue;
        };
    }
}
