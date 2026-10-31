package dev.obe.contract.listener;

import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import java.time.Instant;
import java.util.List;

/** Body of POST /olay-dinleme. Field names are the JSON names set by the standard. */
public record EventNotificationRequest(Participant katilimciBlg, List<Event> olaylar) {

    /** The most events the standard allows in one call. */
    public static final int MAX_EVENTS = 100;

    public record Participant(String hhsKod, String yosKod) {
    }

    public record Event(String olayNo, Instant olayZamani, EventType olayTipi, ResourceType kaynakTipi,
            String kaynakNo) {
    }
}
