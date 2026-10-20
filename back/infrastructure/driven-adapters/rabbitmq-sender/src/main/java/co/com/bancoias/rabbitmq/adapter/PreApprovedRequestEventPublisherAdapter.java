package co.com.bancoias.rabbitmq.adapter;

import co.com.bancoias.model.preapprovedusage.event.PreApprovedRequestEvent;
import co.com.bancoias.model.preapprovedusage.gateways.PreApprovedRequestEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
public class PreApprovedRequestEventPublisherAdapter implements PreApprovedRequestEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PreApprovedRequestEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public Mono<Void> publish(PreApprovedRequestEvent event) {
        return Mono.fromRunnable(() -> {
            try {
                String routingKey = mapToRoutingKey(event.getEventType().name());
                log.info("Publishing event {} with routing key {}", event.getEventId(), routingKey);
                rabbitTemplate.convertAndSend(
                    "preapproved-requests-exchange",
                    routingKey,
                    event
                );
                log.info("Event {} published successfully", event.getEventId());
            } catch (Exception e) {
                log.error("Error publishing event {} to RabbitMQ", event.getEventId(), e);
            }
        });
    }

    private String mapToRoutingKey(String eventType) {
        return switch (eventType) {
            case "PRE_APPROVED_REQUEST_APPROVED" -> "preapproved.request.approved";
            case "PRE_APPROVED_REQUEST_REJECTED" -> "preapproved.request.rejected";
            default -> "preapproved.request.unknown";
        };
    }
}
