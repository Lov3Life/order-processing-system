package pl.bartek.orders.order.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.bartek.orders.order.entity.Order;
import pl.bartek.orders.order.repository.OrderRepository;
import pl.bartek.orders.outbox.config.KafkaRetryConfig;
import pl.bartek.orders.outbox.config.OutboxConfiguration;
import pl.bartek.orders.outbox.entity.OutboxEvent;
import pl.bartek.orders.outbox.repository.OutboxEventRepository;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EntityScan(basePackageClasses = {Order.class, OutboxEvent.class})
@EnableJpaRepositories(basePackageClasses = {OrderRepository.class, OutboxEventRepository.class})
@Import({OutboxConfiguration.class, KafkaRetryConfig.class})
public class InfrastructureConfig {
}
