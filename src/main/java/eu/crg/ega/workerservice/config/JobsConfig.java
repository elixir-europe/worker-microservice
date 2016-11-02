package eu.crg.ega.workerservice.config;

import eu.crg.ega.workerservice.service.jobs.DecryptFromArchiveAndReencrypt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class JobsConfig {

  @Bean
  @Scope(value = "prototype")
  public DecryptFromArchiveAndReencrypt decryptFromArchiveAndReencrypt() {
    return new DecryptFromArchiveAndReencrypt();
  }
}
