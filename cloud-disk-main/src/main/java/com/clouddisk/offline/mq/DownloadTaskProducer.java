package com.clouddisk.offline.mq;

import com.clouddisk.offline.dto.DownloadTaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DownloadTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${offline.download.rabbitmq.exchange}")
    private String exchange;

    @Value("${offline.download.rabbitmq.routing-key}")
    private String routingKey;

    public DownloadTaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendDownloadTask(DownloadTaskMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
            msg.getMessageProperties().setMessageId(message.getTaskId());
            msg.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
            return msg;
        });
    }
}
