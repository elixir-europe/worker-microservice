package eu.crg.ega.workerservice.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolExecutorConfig {

  @Autowired
  Environment env;

  @Bean
  public ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(Integer.valueOf(env.getProperty("service.worker.workers.number")));
    pool.setMaxPoolSize(Integer.valueOf(env.getProperty("service.worker.workers.number")));
    pool.setWaitForTasksToCompleteOnShutdown(true);
    pool.setQueueCapacity(0);
    return pool;
  }

}
