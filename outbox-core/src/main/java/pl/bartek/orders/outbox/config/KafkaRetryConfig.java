package pl.bartek.orders.outbox.config;


import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;


@Configuration(proxyBeanMethods = false)
public class KafkaRetryConfig {

  @Bean
  public DefaultErrorHandler kafkaErrorHandler(KafkaProperties kafkaProperties) {
    Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
    serializers.put(byte[].class, new ByteArraySerializer());
    serializers.put(Object.class, new JacksonJsonSerializer<>());
    var producerFactory = new DefaultKafkaProducerFactory<>(
            kafkaProperties.buildProducerProperties(),
            new StringSerializer(),
            new DelegatingByTypeSerializer(serializers, true));
    var dltTemplate = new KafkaTemplate<>(producerFactory);
    var recoverer = new DeadLetterPublishingRecoverer(dltTemplate);
    recoverer.setFailIfSendResultIsError(true);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
  }
}
