package no.fintlabs.kafka;

import no.fintlabs.EventRepository;
import no.fintlabs.kafka.common.topic.TopicCleanupPolicyParameters;
import no.fintlabs.kafka.requestreply.ReplyProducerRecord;
import no.fintlabs.kafka.requestreply.RequestConsumerFactoryService;
import no.fintlabs.kafka.requestreply.topic.RequestTopicNameParameters;
import no.fintlabs.kafka.requestreply.topic.RequestTopicService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

@Configuration
public class ArchiveInstanceIdRequestConsumerConfiguration {

    private final EventRepository eventRepository;

    public ArchiveInstanceIdRequestConsumerConfiguration(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // TODO 04/12/2024 eivindmorch: Change in services that use this topic
    @Bean
    ConcurrentMessageListenerContainer<String, ArchiveInstanceIdRequestParams> archiveInstanceIdRequestConsumer(
            RequestTopicService requestTopicService,
            RequestConsumerFactoryService requestConsumerFactoryService
    ) {
        RequestTopicNameParameters topicNameParameters = RequestTopicNameParameters.builder()
                .resource("archive.instance.id")
                .parameterName("source-application-aggregate-instance-id")
                .build();

        requestTopicService.ensureTopic(topicNameParameters, 0, TopicCleanupPolicyParameters.builder().build());

        return requestConsumerFactoryService.createRecordConsumerFactory(
                ArchiveInstanceIdRequestParams.class,
                String.class,
                consumerRecord -> {
                    String archiveInstanceId = eventRepository.findLatestArchiveInstanceId(consumerRecord.value())
                            .orElse(null);
                    return ReplyProducerRecord.<String>builder().value(archiveInstanceId).build();
                }
        ).createContainer(topicNameParameters);
    }

}
