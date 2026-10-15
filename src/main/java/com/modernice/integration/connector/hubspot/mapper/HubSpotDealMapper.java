package com.modernice.integration.connector.hubspot.mapper;

import com.modernice.integration.domain.model.Deal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HubSpotDealMapper {

    public Map<String, Object> toHubSpot(Deal deal) {
        Map<String, Object> properties = new LinkedHashMap<>();
        put(properties, "dealname", deal.opportunityName());
        put(properties, "account_id", deal.accountId());
        put(properties, "account_maanger", deal.accountManager());
        put(properties, "est_close_date", deal.estimatedCloseDate());
        put(properties, "infor_close_date", deal.actualCloseDate());
        put(properties, "potential_amount", deal.potentialAmount());
        put(properties, "actual_amount", deal.actualAmount());
        put(properties, "infor_probability", deal.probability());
        put(properties, "dealstage", deal.stage());
        put(properties, "infor_status", deal.status());
        put(properties, "closed_won_reason", deal.reasonWon());
        put(properties, "infor_opportunity_id", deal.opportunityId());
        put(properties, "lead_source", deal.leadSource());
        put(properties, "infor_owner", deal.owner());
        put(properties, "opportunity_type", deal.opportunityType());
        put(properties, "description", deal.description());
        return properties;
    }

    public Deal fromHubSpot(Map<String, Object> properties, String hubSpotDealId) {
        return new Deal(
                null,
                hubSpotDealId,
                string(properties.get("dealname")),
                null,
                null,
                null,
                null,
                decimal(properties.get("potential_amount")),
                decimal(properties.get("actual_amount")),
                decimal(properties.get("infor_probability")),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static void put(Map<String, Object> properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal decimal(Object value) {
        return value == null || value.toString().isBlank() ? null : new BigDecimal(value.toString());
    }

}
