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

    private static final Map<String, String> CUSTOMER_TYPE = Map.ofEntries(
            Map.entry("ME", "Meat"),
            Map.entry("MA", "Marina"),
            Map.entry("CA", "Catering"),
            Map.entry("CO", "Concrete"),
            Map.entry("SC", "Eng Subcontractor"),
            Map.entry("GOV", "Government"),
            Map.entry("NF", "NF"),
            Map.entry("PIV", "Pkg Ice Vending"),
            Map.entry("DI", "Competitive Distributor"),
            Map.entry("PIM", "Pkg Ice - Manufacturer"),
            Map.entry("PID", "Pkg Ice - Distributor"),
            Map.entry("DA", "Dairy"),
            Map.entry("PIB", "Pkg Ice Big 3 HCAGREDDY"),
            Map.entry("RE", "Refrigeration Contractor"),
            Map.entry("OT", "Other"),
            Map.entry("CS", "Convenience Store"),
            Map.entry("FO", "Restaurant/Food"),
            Map.entry("COM", "Commercial"),
            Map.entry("AG", "Agriculture"),
            Map.entry("DF", "Default"),
            Map.entry("BE", "Beverage Distributor"),
            Map.entry("FI", "Fish/Seafood"),
            Map.entry("CG", "Camp Ground"),
            Map.entry("GS", "Grocery Store")
    );

    public String map(String mappingType, String sourceValue) {
        if (sourceValue == null) {
            return null;
        }

        return switch (mappingType) {
            case "ERP_TERRITORY" -> ERP_TERRITORY.getOrDefault(sourceValue, sourceValue);
            case "CUSTOMER_TYPE" -> CUSTOMER_TYPE.getOrDefault(sourceValue, sourceValue);
            default -> sourceValue;
        };
    }

    public String unmap(String mappingType, String targetValue) {
        if (targetValue == null) {
            return null;
        }
        Map<String, String> mapping = switch (mappingType) {
            case "ERP_TERRITORY" -> ERP_TERRITORY;
            case "CUSTOMER_TYPE" -> CUSTOMER_TYPE;
            default -> Map.of();
        };
        return mapping.entrySet().stream()
                .filter(entry -> entry.getValue().equals(targetValue))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(targetValue);
    }
}
