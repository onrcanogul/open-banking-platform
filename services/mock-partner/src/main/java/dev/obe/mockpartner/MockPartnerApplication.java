package dev.obe.mockpartner;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Stands in for a partner's webhook and keeps what it receives for inspection. */
@SpringBootApplication
@RestController
public class MockPartnerApplication {

    private static final Logger log = LoggerFactory.getLogger(MockPartnerApplication.class);

    private final List<Notification> received = new CopyOnWriteArrayList<>();

    /** Defined here, because a real partner sees only the JSON and shares no code with the bank. */
    record Notification(String eventNo, String disbursementNo, String status, String failureReason,
            Instant occurredAt) {
    }

    public static void main(String[] args) {
        SpringApplication.run(MockPartnerApplication.class, args);
    }

    @PostMapping("/disbursement-events")
    void receive(@RequestBody Notification notification) {
        received.add(notification);
        log.info("Disbursement {} is {}", notification.disbursementNo(), notification.status());
    }

    @GetMapping("/internal/received")
    List<Notification> received() {
        return List.copyOf(received);
    }
}
