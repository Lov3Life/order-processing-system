package pl.bartek.orders.outbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import pl.bartek.orders.outbox.publisher.OutboxPublisher;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import pl.bartek.orders.outbox.service.OutboxService;
import pl.bartek.orders.outbox.service.OutboxStatusService;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class OutboxConfiguration {

  @Bean
  public OutboxService outboxService(OutboxEventRepository repository,
                                     JsonMapper jsonMapper) {
    return new OutboxService(repository, jsonMapper);
  }

  @Bean
  public OutboxStatusService outboxStatusService(OutboxEventRepository repository) {
    return new OutboxStatusService(repository);
  }

  @Bean
  public OutboxPublisher outboxPublisher(OutboxEventRepository repository,
                                         OutboxStatusService statusService,
                                         KafkaTemplate<String, String> kafkaTemplate) {
    return new OutboxPublisher(repository, statusService, kafkaTemplate);
  }
}
