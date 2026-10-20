package co.com.bancoias.model.preapprovedusage.gateways;

import co.com.bancoias.model.preapprovedusage.event.PreApprovedRequestEvent;
import reactor.core.publisher.Mono;

public interface PreApprovedRequestEventPublisher {
    Mono<Void> publish(PreApprovedRequestEvent event);
}
