package pl.bartek.orders.processing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.bartek.orders.outbox.config.KafkaRetryConfig;
import pl.bartek.orders.outbox.config.OutboxConfiguration;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.repository.ProductRepository;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackageClasses = {Product.class, OutboxEvent.class})
@EnableJpaRepositories(basePackageClasses = {ProductRepository.class, OutboxEventRepository.class})
@Import({OutboxConfiguration.class, KafkaRetryConfig.class})
public class ProcessingServiceApplication {
  public static void main(String[] args) {
		SpringApplication.run(ProcessingServiceApplication.class, args);
  }
}
