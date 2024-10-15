package no.fintlabs;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.fintlabs.model.Event;
import no.fintlabs.model.EventDto;
import no.fintlabs.model.ManualEventDto;
import no.fintlabs.repositories.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static no.fintlabs.EventNames.INSTANCE_DELETED;

@Service
@AllArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;

    public void save(Event event) {
        eventRepository.save(event);
    }

    public Page<EventDto> findAll(Pageable pageable) {
        return convertPageOfEventIntoPageOfEventDto(eventRepository.findAll(pageable));
    }

    public Page<EventDto> getMergedLatestEvents(Pageable pageable) {
        Page<Event> latestEventsPage = eventRepository
                .findLatestEventPerSourceApplicationInstanceId(pageable);

        Page<Event> latestNonDeletedEventsPage = eventRepository
                .findLatestEventNotDeletedPerSourceApplicationInstanceId(pageable);

        long totalLatestEvents = eventRepository.countLatestEventPerSourceApplicationInstanceId();
        long totalLatestNonDeletedEvents = eventRepository.countLatestEventNotDeletedPerSourceApplicationInstanceId();

        List<EventDto> mergedEvents = mergeEvents(latestEventsPage.getContent(), latestNonDeletedEventsPage.getContent());

        long totalElements = totalLatestEvents + totalLatestNonDeletedEvents;

        return new PageImpl<>(mergedEvents, pageable, totalElements);
    }

    public Page<EventDto> getMergedLatestEventsWhereSourceApplicationIdIn(
            List<Long> sourceApplicationIds,
            Pageable pageable
    ) {
        List<Event> latestEvents = eventRepository
                .findLatestEventPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
                        sourceApplicationIds,
                        Pageable.unpaged()
                ).getContent();

        List<Event> latestNonDeletedEvents = eventRepository
                .findLatestEventNotDeletedPerSourceApplicationInstanceIdAndSourceApplicationIdIn(
                        sourceApplicationIds,
                        Pageable.unpaged()
                ).getContent();

        List<EventDto> mergedEvents = mergeEvents(latestEvents, latestNonDeletedEvents);

        Sort sort = pageable.getSort();
        if (sort.isSorted()) {
            mergedEvents.sort((e1, e2) -> {
                for (Sort.Order order : sort) {
                    int comparisonResult = compareEvents(e1, e2, order);
                    if (comparisonResult != 0) {
                        return order.isAscending() ? comparisonResult : -comparisonResult;
                    }
                }
                return 0;
            });
        }

        long totalElements = mergedEvents.size();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), mergedEvents.size());

        if (start >= mergedEvents.size()) {
            return new PageImpl<>(new ArrayList<>(), pageable, totalElements);
        }

        List<EventDto> paginatedList = mergedEvents.subList(start, end);
        return new PageImpl<>(paginatedList, pageable, totalElements);
    }

    private int compareEvents(EventDto e1, EventDto e2, Sort.Order order) {
        if ("timestamp".equals(order.getProperty())) {
            return e1.getTimestamp().compareTo(e2.getTimestamp());
        }
        return 0;
    }

    private List<EventDto> mergeEvents(List<Event> latestEvents, List<Event> latestNonDeletedEvents) {
        Map<String, Event> nonDeletedEventMap = latestNonDeletedEvents.stream()
                .collect(Collectors.toMap(
                        event -> event.getInstanceFlowHeaders().getSourceApplicationInstanceId(),
                        event -> event
                ));

        List<EventDto> mergedEvents = new ArrayList<>();
        for (Event latestEvent : latestEvents) {
            if (INSTANCE_DELETED.equals(latestEvent.getName())) {
                Event nonDeletedEvent = nonDeletedEventMap
                        .get(latestEvent.getInstanceFlowHeaders().getSourceApplicationInstanceId());
                if (nonDeletedEvent != null) {
                    EventDto updatedEventDto = EventToEventDto(nonDeletedEvent);
                    updatedEventDto.setStatus(INSTANCE_DELETED);
                    mergedEvents.add(updatedEventDto);
                }
            } else {
                mergedEvents.add(EventToEventDto(latestEvent));
            }
        }
        return mergedEvents;
    }

    public Optional<Event> findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
            ManualEventDto manualEventDto
    ) {
        return eventRepository.
                findFirstByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceIdAndInstanceFlowHeadersSourceApplicationIntegrationIdOrderByTimestampDesc(
                        manualEventDto.getSourceApplicationId(),
                        manualEventDto.getSourceApplicationInstanceId(),
                        manualEventDto.getSourceApplicationIntegrationId()
                );
    }

    public Page<EventDto> findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
            Long sourceApplicationId,
            String sourceApplicationInstanceId,
            Pageable pageable
    ) {
        return convertPageOfEventIntoPageOfEventDto(
                eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdAndInstanceFlowHeadersSourceApplicationInstanceId(
                        sourceApplicationId, sourceApplicationInstanceId, pageable
                )
        );
    }

    public Page<EventDto> findAllByInstanceFlowHeadersSourceApplicationIdIn(
            List<Long> sourceApplicationIds,
            Pageable pageable
    ) {
        return convertPageOfEventIntoPageOfEventDto(
                eventRepository.findAllByInstanceFlowHeadersSourceApplicationIdIn(
                        sourceApplicationIds,
                        pageable
                )
        );
    }

    private Page<EventDto> convertPageOfEventIntoPageOfEventDto(Page<Event> events) {
        return events.map(this::EventToEventDto);
    }

    private EventDto EventToEventDto(Event event) {
        return EventDto.builder()
                .instanceFlowHeaders(event.getInstanceFlowHeaders())
                .name(event.getName())
                .timestamp(event.getTimestamp())
                .type(event.getType())
                .applicationId(event.getApplicationId())
                .errors(event.getErrors())
                .build();
    }
}
