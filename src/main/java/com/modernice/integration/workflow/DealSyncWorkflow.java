package com.modernice.integration.workflow;

import com.modernice.integration.connector.hubspot.SalesHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import com.modernice.integration.connector.hubspot.mapper.HubSpotDealMapper;
import com.modernice.integration.connector.infor.business.InforBusinessClient;
import com.modernice.integration.connector.infor.business.mapper.InforBusinessMapper;
import com.modernice.integration.domain.model.Deal;
import com.modernice.integration.identity.BusinessIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DealSyncWorkflow {
    private static final String ENTITY = "DEAL";
    private static final String SOURCE = "INFOR_CRM";
    private final InforBusinessClient inforClient;
    private final InforBusinessMapper inforMapper;
    private final HubSpotDealMapper hubSpotMapper;
    private final SalesHubSpotClient hubSpotClient;
    private final BusinessIdentityService identity;

    public Mono<Void> syncInforOpportunityToHubSpot(String opportunityId) {
        return inforClient.getOpportunity(opportunityId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "INFOR opportunity not found: " + opportunityId)))
                .map(inforMapper::toCanonical)
                .flatMap(deal -> {
                    var request = new HubSpotObjectRequest(hubSpotMapper.toHubSpot(deal));
                    Optional<String> id = identity.findTargetId(ENTITY, SOURCE, opportunityId)
                            .or(() -> Optional.ofNullable(deal.hubSpotDealId()));
                    return id.map(existing -> hubSpotClient.update("deals", existing, request)
                                    .thenReturn(existing))
                            .orElseGet(() -> hubSpotClient.create("deals", request))
                            .flatMap(hubSpotId -> {
                                identity.save(ENTITY, SOURCE, opportunityId, "HUBSPOT", hubSpotId, opportunityId);
                                return inforClient.upsertOpportunity(inforMapper.toInfor(
                                        new Deal(deal.opportunityId(), hubSpotId, deal.opportunityName(),
                                                deal.accountId(), deal.accountManager(), deal.estimatedCloseDate(),
                                                deal.actualCloseDate(), deal.potentialAmount(), deal.actualAmount(),
                                                deal.probability(), deal.stage(), deal.status(), deal.reasonWon(),
                                                deal.leadSource(), deal.owner(), deal.opportunityType(), deal.description(),
                                                deal.contactIds(), deal.productSkus(), deal.salesOrderIds())));
                            });
                });
    }

    public Mono<Void> syncHubSpotDealToInfor(String hubSpotDealId) {
        return hubSpotClient.get("deals", hubSpotDealId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "HubSpot deal not found: " + hubSpotDealId)))
                .flatMap(properties -> {
                    String opportunityId = Optional.ofNullable(properties.get("infor_opportunity_id"))
                            .map(String::valueOf)
                            .filter(value -> !value.isBlank())
                            .or(() -> identity.findSourceId(ENTITY, "HUBSPOT", hubSpotDealId))
                            .orElse(null);
                    if (opportunityId == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "HubSpot deal must include infor_opportunity_id or have a saved identity mapping"));
                    }
                    Deal updates = hubSpotMapper.fromHubSpot(properties, hubSpotDealId);
                    return inforClient.getOpportunity(opportunityId)
                            .map(inforMapper::toCanonical)
                            .defaultIfEmpty(emptyDeal(opportunityId, hubSpotDealId))
                            .flatMap(current -> {
                                Deal merged = new Deal(opportunityId, hubSpotDealId,
                                        choose(updates.opportunityName(), current.opportunityName()),
                                        current.accountId(), current.accountManager(), current.estimatedCloseDate(),
                                        current.actualCloseDate(),
                                        choose(updates.potentialAmount(), current.potentialAmount()),
                                        choose(updates.actualAmount(), current.actualAmount()),
                                        choose(updates.probability(), current.probability()),
                                        current.stage(), current.status(), current.reasonWon(), current.leadSource(),
                                        current.owner(), current.opportunityType(), current.description(),
                                        current.contactIds(), current.productSkus(), current.salesOrderIds());
                                return inforClient.upsertOpportunity(inforMapper.toInfor(merged))
                                        .then(Mono.fromRunnable(() -> identity.save(ENTITY, SOURCE, opportunityId,
                                                "HUBSPOT", hubSpotDealId, opportunityId)));
                            });
                });
    }

    private Deal emptyDeal(String opportunityId, String hubSpotDealId) {
        return new Deal(opportunityId, hubSpotDealId, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private static <T> T choose(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
