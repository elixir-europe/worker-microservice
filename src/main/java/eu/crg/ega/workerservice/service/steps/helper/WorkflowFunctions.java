package eu.crg.ega.workerservice.service.steps.helper;


import eu.crg.ega.microservice.dto.message.WorkFlowEventMessage;
import eu.crg.ega.microservice.messaging.sender.RabbitMessageSender;
import eu.crg.ega.microservice.helper.WorkflowHelper;
import eu.crg.ega.workerservice.properties.RabbitMqProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j;

@Log4j
@Service
public class WorkflowFunctions extends WorkflowHelper {

  @Autowired
  RabbitMessageSender rabbitMessageSender;

  @Autowired
  private RabbitMqProperties rabbitProperties;

  public void sendMessage(String routingKey, WorkFlowEventMessage wfMessage) {
    rabbitMessageSender.prepareMessageAndSend(rabbitProperties.getWorkflowEventsExchange(),
        routingKey, wfMessage);
  }
}
