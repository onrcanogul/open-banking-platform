package dev.obe.mockhhs.domain.repository;

import dev.obe.mockhhs.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
}
