package com.clouddisk.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${offline.download.rabbitmq.exchange}")
    private String exchange;

    @Value("${offline.download.rabbitmq.queue}")
    private String queue;

    @Value("${offline.download.rabbitmq.routing-key}")
    private String routingKey;

    @Bean
    public DirectExchange offlineDownloadExchange() {
        return ExchangeBuilder.directExchange(exchange)
                .durable(true)
                .build();
    }

    @Bean
    public Queue offlineDownloadQueue() {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(exchange + ".dlx")
                .deadLetterRoutingKey(routingKey + ".dlx")
                .build();
    }

    @Bean
    public Binding offlineDownloadBinding() {
        return BindingBuilder.bind(offlineDownloadQueue())
                .to(offlineDownloadExchange())
                .with(routingKey);
    }

    @Bean
    public DirectExchange offlineDownloadDlxExchange() {
        return ExchangeBuilder.directExchange(exchange + ".dlx")
                .durable(true)
                .build();
    }

    @Bean
    public Queue offlineDownloadDlxQueue() {
        return QueueBuilder.durable(queue + ".dlx")
                .build();
    }

    @Bean
    public Binding offlineDownloadDlxBinding() {
        return BindingBuilder.bind(offlineDownloadDlxQueue())
                .to(offlineDownloadDlxExchange())
                .with(routingKey + ".dlx");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.err.println("消息发送失败: " + cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            System.err.println("消息被退回: " + returned.getMessage());
        });
        return rabbitTemplate;
    }
}
