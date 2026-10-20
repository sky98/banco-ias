package co.com.bancoias.rabbitmq.adapter;

import co.com.bancoias.model.preapprovedusage.event.PreApprovedRequestEvent;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedRequestEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpPreApprovedRequestEventPublisherAdapter implements PreApprovedRequestEventPublisher {

    @Override
    public Mono<Void> publish(PreApprovedRequestEvent event) {
        log.info("RabbitMQ disabled. Event {} not published", event.getEventId());
        return Mono.empty();
    }
}
