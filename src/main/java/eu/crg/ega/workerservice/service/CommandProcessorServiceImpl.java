package eu.crg.ega.workerservice.service;

import com.rabbitmq.client.Channel;

import eu.crg.ega.microservice.dto.message.ServiceMessage;
import eu.crg.ega.microservice.dto.message.WorkFlowCommandMessage;
import eu.crg.ega.microservice.dto.message.WorkFlowEventMessage;
import eu.crg.ega.microservice.enums.WorkflowType;
import eu.crg.ega.microservice.exception.WorkflowException;
import eu.crg.ega.workerservice.service.steps.helper.WorkflowFunctions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.log4j.Log4j;

@Log4j
@Service
public class CommandProcessorServiceImpl implements RabbitCommandProcessorService {

  @Autowired
  @Resource(name = "StepDecryptFromArchiveAndReencrypt")
  WorkFlowStepProcessor stepDecryptFromArchiveAndReencrypt;

  @Autowired
  WorkflowFunctions workflowFunctions;

  @Override
  public void processAndSave(ServiceMessage message, Channel channel, long deliveryTag) {
    log.trace("processing message: " + message);
    Map<WorkflowType, WorkFlowStepProcessor>
        stepers = new EnumMap<WorkflowType, WorkFlowStepProcessor>(WorkflowType.class);

    stepers.put(WorkflowType.REENCRYPT, stepDecryptFromArchiveAndReencrypt);

    WorkflowType stepKey = ((WorkFlowCommandMessage) message).getWorkflowSubtype();

    if (!stepers.containsKey(stepKey)) {
      throw new RuntimeException("mapping of steps does not contain an entry for: " + stepKey.getValue());
    }
    try {
      //In this case, the step needs to know the channel and the deliveryTag for that channel from the message
      stepers.get(stepKey).process((WorkFlowCommandMessage) message, channel, deliveryTag);
    } catch (Exception e) {
      WorkFlowCommandMessage wfOriginalMessage = (WorkFlowCommandMessage) message;
      WorkFlowEventMessage wfMessage = workflowFunctions.createWfEventMessage((WorkFlowCommandMessage) message);
      wfMessage.setWorkflowSubtype(WorkflowType.WORKER_ERROR);
      wfMessage.setWorkflowId(wfOriginalMessage.getWorkflowId());
      wfMessage.setPreviousState(wfOriginalMessage.getWorkflowSubtype());
      wfMessage.setErrorType(e.getMessage());
      wfMessage.setErrorCode(1);
      log.error("Error: ", e);
      try {
        workflowFunctions.sendMessage(WorkflowType.WORKER_ERROR.getValue(), wfMessage);
      } catch (Exception ex) {
        log.fatal("Exception: ", ex);
      } finally { //be careful when throwing exceptions inside finally block
        throw new WorkflowException(-1, "Processing of the message by a step failed: " + e.getMessage());
      }
    }
    log.trace("finish");
  }
}