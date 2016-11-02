package eu.crg.ega.workerservice.config;

import eu.crg.ega.microservice.config.RabbitMq;
import eu.crg.ega.workerservice.messaging.RabbitWorkflowCommandsListener;
import eu.crg.ega.workerservice.properties.RabbitMqProperties;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

  @Autowired
  Environment env;

  @Autowired
  private RabbitMqProperties rabbitProperties;

  @Autowired
  private RabbitMq rabbitMq;

  @Bean
  public ConnectionFactory connectionFactory() {
    com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();

    rabbitMq.configureSSL(factory, rabbitProperties.getUseSsl(),
        rabbitProperties.getSslKeypassphrasePassword(),
        rabbitProperties.getSslKeypath(),
        rabbitProperties.getSslTrustpassphrasePassword(),
        rabbitProperties.getSslTrustpath());

    factory.setHost(rabbitProperties.getHost());
    factory.setPort(Integer.parseInt(rabbitProperties.getPort()));
    factory.setVirtualHost(rabbitProperties.getVirtualhost());
    factory.setUsername(rabbitProperties.getUser());
    factory.setPassword(rabbitProperties.getPassword());

    CachingConnectionFactory connectionFactory = new CachingConnectionFactory(factory);
    return connectionFactory;
  }

  @Bean
  public ChannelAwareMessageListener rabbitListener() {
    return new RabbitWorkflowCommandsListener();
  }

  @Bean
  public SimpleMessageListenerContainer listenerContainer() {
    SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
    container.setConnectionFactory(connectionFactory());
    //container.setQueues(workflowCommandQueue());
    container.setQueueNames(rabbitProperties.getWorkerWorkflowCommandsQueue());
    container.setDefaultRequeueRejected(false);// do not requeue messages,
    // will be send to
    // dead-letter queue
    container.setMessageListener(rabbitListener());
    container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    container.setConcurrentConsumers(Integer.valueOf(env.getProperty("service.worker.workers.number")));
    container.setMaxConcurrentConsumers(Integer.valueOf(env.getProperty("service.worker.workers.number")));
    //Change prefetch settings:
    container.setPrefetchCount(1);
    return container;
  }

  @Bean
  public RabbitTemplate rabbitTemplate() {
    RabbitTemplate template = new RabbitTemplate(connectionFactory());
    template.setMessageConverter(rabbitMq.jsonMessageConverter());
    return template;
  }
}
