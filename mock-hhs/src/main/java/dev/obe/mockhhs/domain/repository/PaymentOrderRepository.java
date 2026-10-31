package dev.obe.mockhhs.domain.repository;

import dev.obe.mockhhs.domain.model.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
}
