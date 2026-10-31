package dev.obe.mockhhs.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.model.OutboxStatus;
import dev.obe.mockhhs.domain.model.PaymentStatus;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
import dev.obe.mockhhs.domain.repository.PaymentOrderRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_HHS_USER=unused", "ORACLE_HHS_PASSWORD=unused"})
@AutoConfigureMockMvc
@Testcontainers
class PaymentOrderApiTest {

    private static final String YOS = "8001";

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void creatingOrderProducesNoEvent() throws Exception {
        createOrder();

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void statusChangeWritesPendingEvent() throws Exception {
        String orderNo = createOrder();

        changeStatus(orderNo, "GERCEKLESTI")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GERCEKLESTI"));

        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("KAYNAK_GUNCELLENDI");
        assertThat(event.getResourceType()).isEqualTo("ODEME_EMRI");
        assertThat(event.getResourceNo()).isEqualTo(orderNo);
        assertThat(event.getYosCode()).isEqualTo(YOS);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void repeatingSameStatusWritesNoEvent() throws Exception {
        String orderNo = createOrder();

        changeStatus(orderNo, "GONDERILDI").andExpect(status().isOk());

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void eachRealChangeWritesItsOwnEvent() throws Exception {
        String orderNo = createOrder();

        changeStatus(orderNo, "GERCEKLESTI").andExpect(status().isOk());
        changeStatus(orderNo, "GERCEKLESMEDI").andExpect(status().isOk());

        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void statusIsNotChangedWhenEventCannotBeWritten() throws Exception {
        String orderNo = createOrder();
        doThrow(new IllegalStateException("outbox unavailable")).when(outboxRepository).save(any());
        doThrow(new IllegalStateException("outbox unavailable")).when(outboxRepository).saveAndFlush(any());

        assertThatThrownBy(() -> changeStatus(orderNo, "GERCEKLESTI"))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.findById(orderNo).orElseThrow().getStatus()).isEqualTo(PaymentStatus.GONDERILDI);
    }

    @Test
    void eventIsNotKeptWhenStatusCannotBeWritten() throws Exception {
        String orderNo = createOrder();
        // Makes the database itself refuse the update, whenever the code flushes it
        jdbc.execute("ALTER TABLE payment_order ADD CONSTRAINT ck_test_status CHECK (status <> 'GERCEKLESTI')");
        try {
            assertThatThrownBy(() -> changeStatus(orderNo, "GERCEKLESTI"));
        } finally {
            jdbc.execute("ALTER TABLE payment_order DROP CONSTRAINT ck_test_status");
        }

        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        changeStatus("olmayan-emir", "GERCEKLESTI").andExpect(status().isNotFound());
    }

    private String createOrder() throws Exception {
        String json = mvc.perform(post("/internal/payment-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yosCode\":\"%s\",\"amount\":150.75}".formatted(YOS)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.orderNo");
    }

    private ResultActions changeStatus(String orderNo, String status) throws Exception {
        return mvc.perform(patch("/internal/payment-orders/" + orderNo + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(status)));
    }
}
