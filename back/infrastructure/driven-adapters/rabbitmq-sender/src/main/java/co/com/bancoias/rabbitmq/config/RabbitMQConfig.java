package co.com.bancoias.rabbitmq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "preapproved-requests-exchange";
    public static final String QUEUE_NAME = "preapproved-requests-queue";
    public static final String ROUTING_KEY_APPROVED = "preapproved.request.approved";
    public static final String ROUTING_KEY_REJECTED = "preapproved.request.rejected";

    @Bean
    public TopicExchange preapprovedRequestsExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue preapprovedRequestsQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    public Binding bindingApproved(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_APPROVED);
    }

    @Bean
    public Binding bindingRejected(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY_REJECTED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
