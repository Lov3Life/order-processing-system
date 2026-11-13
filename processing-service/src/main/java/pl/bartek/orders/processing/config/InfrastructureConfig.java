package pl.bartek.orders.processing.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.bartek.orders.outbox.config.KafkaRetryConfig;
import pl.bartek.orders.outbox.config.OutboxConfiguration;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;
import pl.bartek.orders.processing.entity.Product;
import pl.bartek.orders.processing.repository.ProductRepository;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EntityScan(basePackageClasses = {Product.class, OutboxEvent.class})
@EnableJpaRepositories(basePackageClasses = {ProductRepository.class, OutboxEventRepository.class})
@Import({OutboxConfiguration.class, KafkaRetryConfig.class})
public class InfrastructureConfig {
}
