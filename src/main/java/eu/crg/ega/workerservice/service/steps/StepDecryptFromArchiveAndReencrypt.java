package eu.crg.ega.workerservice.service.steps;

import com.rabbitmq.client.Channel;

import eu.crg.ega.microservice.dto.message.WorkFlowCommandMessage;
import eu.crg.ega.microservice.dto.message.WorkFlowEventMessage;
import eu.crg.ega.microservice.enums.WorkflowType;
import eu.crg.ega.microservice.exception.WorkflowException;
import eu.crg.ega.workerservice.service.WorkFlowStepProcessor;
import eu.crg.ega.workerservice.service.jobs.DecryptFromArchiveAndReencrypt;
import eu.crg.ega.workerservice.service.steps.helper.WorkflowFunctions;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.FutureTask;

import javax.annotation.PostConstruct;

import lombok.extern.log4j.Log4j;

@Log4j
@Service("StepDecryptFromArchiveAndReencrypt")
public class StepDecryptFromArchiveAndReencrypt implements WorkFlowStepProcessor {

  private static String SECRING_PATH;
  private static String KEY_PATH;

  @Autowired
  Environment env;

  @Autowired
  WorkflowFunctions workflowFunctions;

  @Autowired
  private ThreadPoolTaskExecutor taskExecutor;

  @Autowired
  private ApplicationContext applicationContext;

  @PostConstruct
  public void init() throws Exception {
    SECRING_PATH = env.getProperty("service.worker.secring.location");
    KEY_PATH = env.getProperty("service.worker.keypath.location");
  }

  @Override
  public void process(WorkFlowCommandMessage message, Channel channel, long deliveryTag) {

    WorkFlowEventMessage wfEventMessage = workflowFunctions.createWfEventMessage(message);
    wfEventMessage.setPreviousState(message.getWorkflowSubtype());

    try {
      log.trace("We are in: " + message.getWorkflowId() + " with the state " + message
          .getWorkflowSubtype() + " and come from: " + message.getPreviousState());
      String workflowId = message.getWorkflowId();
      workflowFunctions.checkWorkflowId(workflowId);

      String user = message.getSystemUser();
      if (StringUtils.isBlank(user)) {
        log.error("user field is blank!");
        throw new WorkflowException(1, "No user information");
      }

      //Obtain task
      DecryptFromArchiveAndReencrypt fileProcessor = (DecryptFromArchiveAndReencrypt) applicationContext.getBean("decryptFromArchiveAndReencrypt");
      //Setup task
      fileProcessor.setWorkFlowCommandMessage(message);
      fileProcessor.setChannel(channel);
      fileProcessor.setDeliveryTag(deliveryTag);
      fileProcessor.setSecRingPath(SECRING_PATH);
      fileProcessor.setKeyPath(KEY_PATH);

      log.trace("file processor id =" + fileProcessor);

      //Execute task
      FutureTask futureTask = new FutureTask(fileProcessor);
      taskExecutor.execute(futureTask);
      log.debug("Active jobs: " + taskExecutor.getActiveCount() + " ,total pool size: " + taskExecutor.getPoolSize());

      //If a task sent to the taskExecutor produces an exception
      //It will NOT be caught here.
    } catch (Exception e) {
      log.error("Error", e);
      wfEventMessage.setWorkflowSubtype(WorkflowType.REENCRYPT_FAIL);
      wfEventMessage.setErrorType(e.getMessage());
      if (e instanceof WorkflowException) {
        wfEventMessage.setErrorCode(((WorkflowException) e).getErrorCode());
      } else {
        wfEventMessage.setErrorCode(-1);
      }
      try {
        workflowFunctions.sendMessage(WorkflowType.REENCRYPT_FAIL.getValue(), wfEventMessage);
      } catch (Exception ex) {
        log.fatal("Fatal: ", ex);
      } finally { //be careful when throwing exceptions inside finally block
        throw new WorkflowException(-2, "Step failed: " + e.getMessage());
      }
    }
  }
}
