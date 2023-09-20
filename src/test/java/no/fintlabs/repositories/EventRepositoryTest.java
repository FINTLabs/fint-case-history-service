package no.fintlabs.repositories;

import no.fintlabs.model.Event;
import no.fintlabs.model.EventType;
import no.fintlabs.model.InstanceFlowHeadersEmbeddable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static no.fintlabs.EventTopicNames.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@DirtiesContext
public class EventRepositoryTest {

    @Autowired
    EventRepository eventRepository;

    Event eventApplicableForGettingArchiveInstanceId;

    @BeforeEach
    public void setup() {
        eventApplicableForGettingArchiveInstanceId = Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .sourceApplicationId(1L)
                                .sourceApplicationIntegrationId("testSourceApplicationIntegrationId1")
                                .sourceApplicationInstanceId("testSourceApplicationInstanceId1")
                                .correlationId(UUID.fromString("2ee6f95e-44c3-11ed-b878-0242ac120002"))
                                .instanceId(2L)
                                .configurationId(3L)
                                .archiveInstanceId("testArchiveInstanceId1")
                                .build()
                )
                .name(INSTANCE_DISPATCHED)
                .type(EventType.INFO)
                .timestamp(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 12, 30), ZoneOffset.UTC))
                .build();
    }

    @Test
    public void shouldReturnOnlyLatestEventForEachSourceApplicationInstanceId() {
        Event event1 = createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30));
        Event event2 = createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 32));
        Event event3 = createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31));
        Event event4 = createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 29));
        eventRepository.saveAll(List.of(event1, event2, event3, event4));

        Page<Event> events = eventRepository.findLatestEventPerSourceApplicationInstanceId(Pageable.unpaged());

        assertEquals(2, events.getTotalElements());
        assertTrue(events.getContent().containsAll(Arrays.asList(event2, event4)));
    }

    @Test
    public void shouldReturnArchiveInstanceIdFromEventThatIsOfTypeInfoIsADispatchedEventAndHasMatchingSourceApplicationAndSourceApplicationInstanceIds() {
        eventRepository.save(eventApplicableForGettingArchiveInstanceId);

        Optional<String> archiveInstanceId = eventRepository.findArchiveInstanceId(
                1L,
                "testSourceApplicationInstanceId1"
        );

        assertTrue(archiveInstanceId.isPresent());
        assertEquals("testArchiveInstanceId1", archiveInstanceId.get());
    }

    @Test
    public void shouldNotReturnArchiveInstanceIdOfEventWithTypeERROR() {
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .type(EventType.ERROR)
                        .build()
        );

        Optional<String> result = eventRepository.findArchiveInstanceId(
                1L,
                "testSourceApplicationInstanceId1"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldNotReturnArchiveInstanceIdIfEventIsNotAnInstanceDispatchedEvent() {
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .name(INSTANCE_MAPPED)
                        .build()
        );

        Optional<String> result = eventRepository.findArchiveInstanceId(
                1L,
                "testSourceApplicationInstanceId1"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldNotReturnArchiveInstanceIdIfEventDoesNotHaveAMatchingSourceApplication() {
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .instanceFlowHeaders(
                                eventApplicableForGettingArchiveInstanceId.getInstanceFlowHeaders()
                                        .toBuilder()
                                        .sourceApplicationId(2L)
                                        .build()
                        )
                        .build()
        );

        Optional<String> result = eventRepository.findArchiveInstanceId(
                1L,
                "testSourceApplicationInstanceId1"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldNotReturnArchiveInstanceIdIfEventDoesNotHaveAMatchingSourceApplicationInstanceId() {
        eventRepository.save(
                eventApplicableForGettingArchiveInstanceId
                        .toBuilder()
                        .instanceFlowHeaders(
                                eventApplicableForGettingArchiveInstanceId.getInstanceFlowHeaders()
                                        .toBuilder()
                                        .sourceApplicationInstanceId("testSourceApplicationInstanceId2")
                                        .build()
                        )
                        .build()
        );

        Optional<String> result = eventRepository.findArchiveInstanceId(
                1L,
                "testSourceApplicationInstanceId1"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldReturnNumberOfDispatchedInstances() {
        eventRepository.saveAll(List.of(
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("1", "2", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "2", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "2", EventType.ERROR, INSTANCE_MAPPING_ERROR),

                createNamedEvent("2", "3", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("2", "4", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_DISPATCHED)
        ));

        long numberOfDispatchedInstances = eventRepository.countDispatchedInstances();

        assertEquals(4, numberOfDispatchedInstances);
    }

    @Test
    public void shouldReturnNumberOfDispatchedInstancesPerIntegrationId() {
        eventRepository.saveAll(List.of(
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
                createNamedEvent("1", "1", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("1", "2", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("1", "2", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("1", "2", EventType.ERROR, INSTANCE_MAPPING_ERROR),

                createNamedEvent("2", "3", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),
                createNamedEvent("2", "3", EventType.INFO, INSTANCE_DISPATCHED),

                createNamedEvent("2", "4", EventType.INFO, INSTANCE_RECEIVED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_REGISTERED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_MAPPED),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_READY_FOR_DISPATCH),
                createNamedEvent("2", "4", EventType.INFO, INSTANCE_DISPATCHED)
        ));

        Collection<EventRepository.IntegrationIdAndCount> numberOfDispatchedInstancesPerIntegrationId = eventRepository.countDispatchedInstancesPerIntegrationId();

        List<EventRepository.IntegrationIdAndCount> list = numberOfDispatchedInstancesPerIntegrationId.stream().toList();

        assertEquals(2, list.size());

        assertEquals("1", list.get(0).getIntegrationId());
        assertEquals(1, list.get(0).getCount());

        assertEquals("2", list.get(1).getIntegrationId());
        assertEquals(3, list.get(1).getCount());
    }

    @Test
    public void shouldReturnNumberOfCurrentInstanceErrors() {
        eventRepository.saveAll(List.of(
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30)),
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 31)),
                createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),

                createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31)),

                createUnnamedTimestampEvent("2", "3", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
                createUnnamedTimestampEvent("2", "3", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 33)),

                createUnnamedTimestampEvent("3", "4", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 36)),

                createUnnamedTimestampEvent("4", "5", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 28))
        ));

        long numberOfCurrentInstanceErrors = eventRepository.countCurrentInstanceErrors();

        assertEquals(3, numberOfCurrentInstanceErrors);
    }

    @Test
    public void shouldReturnNumberOfCurrentInstanceErrorsPerIntegrationId() {
        eventRepository.saveAll(List.of(
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 30)),
                createUnnamedTimestampEvent("1", "1", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 31)),
                createUnnamedTimestampEvent("1", "1", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),

                createUnnamedTimestampEvent("1", "2", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 31)),

                createUnnamedTimestampEvent("2", "3", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 32)),
                createUnnamedTimestampEvent("2", "3", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 33)),

                createUnnamedTimestampEvent("3", "4", EventType.INFO, LocalDateTime.of(2001, 1, 1, 13, 36)),

                createUnnamedTimestampEvent("4", "5", EventType.ERROR, LocalDateTime.of(2001, 1, 1, 13, 28))
        ));

        Collection<EventRepository.IntegrationIdAndCount> numberOfErrorsPerIntegrationId = eventRepository.countCurrentInstanceErrorsPerIntegrationId();

        List<EventRepository.IntegrationIdAndCount> list = numberOfErrorsPerIntegrationId.stream().toList();

        assertEquals("1", list.get(0).getIntegrationId());
        assertEquals(2, list.get(0).getCount());

        assertEquals("4", list.get(1).getIntegrationId());
        assertEquals(1, list.get(1).getCount());
    }

    private Event createUnnamedTimestampEvent(String sourceApplicationIntegrationId, String sourceApplicationInstanceId, EventType eventType, LocalDateTime timestamp) {
        return Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                                .sourceApplicationInstanceId(sourceApplicationInstanceId)
                                .build()
                )
                .type(eventType)
                .timestamp(OffsetDateTime.of(timestamp, ZoneOffset.UTC))
                .build();
    }

    private Event createNamedEvent(String sourceApplicationIntegrationId, String sourceApplicationInstanceId, EventType eventType, String name) {
        return Event
                .builder()
                .instanceFlowHeaders(
                        InstanceFlowHeadersEmbeddable
                                .builder()
                                .sourceApplicationIntegrationId(sourceApplicationIntegrationId)
                                .sourceApplicationInstanceId(sourceApplicationInstanceId)
                                .build()
                )
                .type(eventType)
                .timestamp(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 13, 30), ZoneOffset.UTC))
                .name(name)
                .build();
    }

}
