package dev.obe.mockhhs.domain.repository;

import dev.obe.mockhhs.domain.model.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, String> {
}
