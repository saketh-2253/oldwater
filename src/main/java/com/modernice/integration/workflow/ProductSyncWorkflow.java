package com.modernice.integration.workflow;

import com.modernice.integration.connector.hubspot.SalesHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import com.modernice.integration.connector.hubspot.mapper.HubSpotLineItemMapper;
import com.modernice.integration.connector.infor.business.InforBusinessClient;
import com.modernice.integration.connector.infor.business.dto.InforProductDto;
import com.modernice.integration.connector.infor.business.mapper.InforBusinessMapper;
import com.modernice.integration.domain.model.LineItem;
import com.modernice.integration.identity.BusinessIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ProductSyncWorkflow {
    private final InforBusinessClient inforClient;
    private final InforBusinessMapper inforMapper;
    private final HubSpotLineItemMapper lineItemMapper;
    private final SalesHubSpotClient hubSpotClient;
    private final BusinessIdentityService identity;

    public Mono<BusinessSyncResult> syncProductToHubSpotLineItem(String productSku) {
        return inforClient.getProduct(productSku)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "INFOR product not found: " + productSku)))
                .flatMap(source -> {
                    var item = inforMapper.toCanonical(source);
                    var request = new HubSpotObjectRequest(lineItemMapper.toHubSpot(item));
                    return identity.findTargetId("PRODUCT", "INFOR_ERP", productSku)
                            .or(() -> java.util.Optional.ofNullable(source.hubSpotLineItemId()))
                            .map(id -> hubSpotClient.update("line_items", id, request).thenReturn(id))
                            .orElseGet(() -> hubSpotClient.create("line_items", request))
                            .flatMap(id -> {
                                identity.save("PRODUCT", "INFOR_ERP", productSku, "HUBSPOT", id, productSku);
                                InforProductDto updated = new InforProductDto(source.productSku(), source.name(),
                                        source.unitPrice(), source.unitCost(), source.profit(),
                                        source.productCategory(), id);
                                return inforClient.upsertProduct(updated)
                                        .thenReturn(new BusinessSyncResult(productSku, "line_items", id,
                                                java.util.List.of()));
                            });
                });
    }
}
