package eu.crg.ega.workerservice.service;


import com.rabbitmq.client.Channel;

import eu.crg.ega.microservice.dto.message.ServiceMessage;

public interface RabbitCommandProcessorService {

  //@PreAuthorize(value = "hasAnyRole('ROLE_ADMIN','ROLE_SUBMITTER','ROLE_REQUESTER','ROLE_SYSTEM')")

  public void processAndSave(ServiceMessage command, Channel channel, long id);
}


