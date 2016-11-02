package eu.crg.ega.workerservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rabbitMQ")
public class RabbitMqProperties {
  @NonNull
  private String host;

  @NonNull
  private String port;

  @NonNull
  private String virtualhost;

  @NonNull
  private String user;

  @NonNull
  private String password;

  @NonNull
  private String workerWorkflowEventsQueue;

  @NonNull
  private String workflowEventsExchange;

  @NonNull
  private String workerWorkflowCommandsQueue;

  @NonNull
  private String workflowCommandsExchange;

  @NonNull
  private String useSsl;

  @NonNull
  private String sslKeypassphrasePassword;

  @NonNull
  private String sslTrustpassphrasePassword;

  @NonNull
  private String sslKeypath;

  @NonNull
  private String sslTrustpath;
}
