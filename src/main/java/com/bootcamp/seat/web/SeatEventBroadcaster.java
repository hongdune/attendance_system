package com.bootcamp.seat.web;

import com.bootcamp.seat.service.SeatChangedEvent;
import com.bootcamp.seat.web.dto.SeatMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 좌석 변경 이벤트를 트랜잭션 커밋 후 /topic/batch/{batchName} 으로 전송한다.
 * 롤백된 변경은 전송되지 않는다.
 */
@Component
public class SeatEventBroadcaster {

    public static final String TOPIC_PREFIX = "/topic/batch/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ZoneId zone;

    public SeatEventBroadcaster(SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.zone = clock.getZone();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSeatChanged(SeatChangedEvent event) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + event.batchName(), SeatMessage.from(event, zone));
    }
}
