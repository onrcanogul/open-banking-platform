package dev.obe.disbursement.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.obe.disbursement.application.port.out.CoreBankingClient;
import dev.obe.disbursement.application.port.out.CoreBankingClient.Step;
import dev.obe.disbursement.application.service.DisbursementService;
import dev.obe.disbursement.domain.model.DisbursementDetails;
import dev.obe.disbursement.domain.repository.DisbursementRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_BAAS_USER=unused", "ORACLE_BAAS_PASSWORD=unused",
        "obe.saga.worker-enabled=false", "obe.outbox.relay-enabled=false", "obe.reconciliation.scheduler-enabled=false",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"})
@AutoConfigureMockMvc
@Testcontainers
class OperationsApiTest {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DisbursementService service;

    @Autowired
    private DisbursementRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private CoreBankingClient core;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        jdbc.update("DELETE FROM disbursement_mismatch");
    }

    @Test
    void listsDisbursementsOfAllPartnersNewestFirst() throws Exception {
        String older = disbursement("PARTNER1", Duration.ofHours(1));
        String newer = disbursement("PARTNER2", Duration.ZERO);

        mvc.perform(get("/operations/disbursements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].disbursementNo").value(newer))
                .andExpect(jsonPath("$[0].partnerCode").value("PARTNER2"))
                .andExpect(jsonPath("$[1].disbursementNo").value(older));
    }

    @Test
    void keepsCustomerDetailsOutOfTheList() throws Exception {
        disbursement("PARTNER1", Duration.ZERO);

        mvc.perform(get("/operations/disbursements"))
                .andExpect(jsonPath("$[0].customerNo").doesNotExist())
                .andExpect(jsonPath("$[0].accountNo").doesNotExist());
    }

    @Test
    void splitsALongListIntoPages() throws Exception {
        IntStream.range(0, 51).forEach(i -> disbursement("PARTNER1", Duration.ZERO));

        mvc.perform(get("/operations/disbursements")).andExpect(jsonPath("$.length()").value(50));
        mvc.perform(get("/operations/disbursements").param("page", "1")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void refusesANegativePage() throws Exception {
        mvc.perform(get("/operations/disbursements").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/operations/findings").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void showsWhatAManualReconciliationFound() throws Exception {
        String number = disbursement("PARTNER1", Duration.ZERO);
        jdbc.update("UPDATE disbursement SET status = 'BASARISIZ' WHERE disbursement_no = ?", number);
        when(core.isCompleted(eq(Step.TRANSFER), eq(number))).thenReturn(true);

        mvc.perform(post("/operations/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("COMPLETED"));

        String body = mvc.perform(get("/operations/findings"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].disbursementNo").value(number))
                .andExpect(jsonPath("$[0].type").value("PAID_BUT_FAILED"))
                .andExpect(jsonPath("$[0].ourStatus").value("BASARISIZ"))
                .andExpect(jsonPath("$[0].coreTransfer").value(true))
                .andReturn().getResponse().getContentAsString();
        Instant foundAt = Instant.parse(JsonPath.read(body, "$[0].foundAt"));
        assertThat(Duration.between(foundAt, Instant.now()).abs()).isLessThan(Duration.ofMinutes(2));
    }

    @Test
    void theOldInternalPathIsGone() throws Exception {
        mvc.perform(post("/internal/reconciliation")).andExpect(status().isNotFound());
    }

    private String disbursement(String partner, Duration age) {
        DisbursementDetails details = new DisbursementDetails(partner, "C-1", "TR01", new BigDecimal("100.00"), "TRY");
        String number = service.accept(details, UUID.randomUUID().toString()).disbursementNo();
        jdbc.update("UPDATE disbursement SET created_at = SYS_EXTRACT_UTC(SYSTIMESTAMP) - ? WHERE disbursement_no = ?",
                age.toMinutes() / 1440.0, number);
        return number;
    }
}
