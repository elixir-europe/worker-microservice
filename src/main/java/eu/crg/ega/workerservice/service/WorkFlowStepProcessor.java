package eu.crg.ega.workerservice.service;

import com.rabbitmq.client.Channel;

import eu.crg.ega.microservice.dto.message.WorkFlowCommandMessage;
import eu.crg.ega.microservice.exception.PreConditionFailed;

public interface WorkFlowStepProcessor {

  public void process(WorkFlowCommandMessage message, Channel channel, long id) throws PreConditionFailed;
}
