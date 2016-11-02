package eu.crg.ega.workerservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

import eu.crg.ega.microservice.dto.message.CommandMessage;
import eu.crg.ega.microservice.dto.message.ServiceMessage;
import eu.crg.ega.microservice.dto.message.WorkFlowCommandMessage;
import eu.crg.ega.microservice.messaging.receiver.RabbitMessageReceiverImpl;
import eu.crg.ega.microservice.utils.JsonMessageUtils;
import eu.crg.ega.workerservice.service.RabbitCommandProcessorService;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.log4j.Log4j;

@Log4j
public class RabbitWorkflowCommandsListener extends RabbitMessageReceiverImpl
    implements ChannelAwareMessageListener {

  @Autowired
  private RabbitCommandProcessorService commandProcessorService;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public void onMessage(Message message, Channel channel) throws Exception {

    ServiceMessage command = null;
    try {
      log.debug("user added to context");
      log.debug("is message redelivered? :" + message.getMessageProperties().getRedelivered());
      command = convertContent(message);

      log.trace("Going to process command: " + command + " in channel " + channel + "with delivert tag: " + message.getMessageProperties().getDeliveryTag());
      commandProcessorService.processAndSave(command, channel, message.getMessageProperties().getDeliveryTag());

      //ACK will be sent by the job when it's sucessfully finished
      //that is why we don't do it here
    } catch (Exception e) {
      log.error("Exception processing message", e);
      //Explicitely reject message not requeing it. Will be send to dead letter
      channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
    }
  }

  private CommandMessage convertContent(Message message) {
    message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
    Jackson2JsonMessageConverter jsonMessageConverter =
        JsonMessageUtils.jsonMessageConverter(WorkFlowCommandMessage.class, objectMapper);
    WorkFlowCommandMessage
        workFlowCommandMessage =
        (WorkFlowCommandMessage) jsonMessageConverter.fromMessage(message);
    return workFlowCommandMessage;
  }

}
