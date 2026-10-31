package dev.obe.subscription.application.port.out;

import java.util.Optional;

/** Participant data that the standard reads from the BKM-provided YOS API. */
public interface YosDirectory {

    Optional<YosProfile> find(String yosCode);
}
