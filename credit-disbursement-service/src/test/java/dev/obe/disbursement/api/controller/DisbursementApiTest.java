package dev.obe.disbursement.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.obe.disbursement.domain.model.Disbursement;
import dev.obe.disbursement.domain.model.DisbursementStatus;
import dev.obe.disbursement.domain.repository.DisbursementRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_BAAS_USER=unused", "ORACLE_BAAS_PASSWORD=unused"})
@AutoConfigureMockMvc
@Testcontainers
class DisbursementApiTest {

    private static final String PARTNER = "PARTNER1";
    private static final String OTHER_PARTNER = "PARTNER2";
    private static final String BODY = """
            {"customerNo":"C-1","accountNo":"TR000000000000000000000001","amount":1500.50,"currency":"TRY"}""";
    private static final String OTHER_BODY = """
            {"customerNo":"C-1","accountNo":"TR000000000000000000000001","amount":9999.99,"currency":"TRY"}""";

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DisbursementRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void acceptsAndWaitsAtTheFirstStep() throws Exception {
        create(PARTNER, "key-1", BODY)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.disbursementNo").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ALINDI"))
                .andExpect(jsonPath("$.amount").value(1500.50));
    }

    @Test
    void repeatedRequestReturnsTheSameDisbursement() throws Exception {
        String first = disbursementNo(create(PARTNER, "key-1", BODY));

        String second = disbursementNo(create(PARTNER, "key-1", BODY));

        assertThat(second).isEqualTo(first);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void reusedKeyWithAnotherRequestIsRefused() throws Exception {
        create(PARTNER, "key-1", BODY);

        create(PARTNER, "key-1", OTHER_BODY).andExpect(status().isConflict());

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void keysBelongToOnePartner() throws Exception {
        create(PARTNER, "key-1", BODY).andExpect(status().isAccepted());
        create(OTHER_PARTNER, "key-1", BODY).andExpect(status().isAccepted());

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void concurrentRetriesStartOnlyOneDisbursement() throws Exception {
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return create(PARTNER, "key-1", BODY).andReturn().getResponse();
                }));
            }
            start.countDown();
            List<String> numbers = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                MockHttpServletResponse response = future.get();
                assertThat(response.getStatus()).isEqualTo(202);
                numbers.add(JsonPath.read(response.getContentAsString(), "$.disbursementNo"));
            }
            assertThat(numbers.stream().distinct()).hasSize(1);
        } finally {
            pool.shutdown();
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void slowerOfTwoUpdatesFailsInsteadOfOverwriting() throws Exception {
        String disbursementNo = disbursementNo(create(PARTNER, "key-1", BODY));
        Disbursement first = repository.findById(disbursementNo).orElseThrow();
        Disbursement second = repository.findById(disbursementNo).orElseThrow();

        first.moveTo(DisbursementStatus.ONAYLANDI, Instant.now());
        repository.save(first);
        second.moveTo(DisbursementStatus.BASARISIZ, Instant.now());

        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void rejectsAnInvalidAmount() throws Exception {
        create(PARTNER, "key-1", BODY.replace("1500.50", "-5")).andExpect(status().isBadRequest());
        create(PARTNER, "key-2", BODY.replace("1500.50", "10.123")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnotherCurrency() throws Exception {
        create(PARTNER, "key-1", BODY.replace("TRY", "USD")).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAnIdempotencyKey() throws Exception {
        mvc.perform(post("/disbursements")
                        .header("X-Partner-Code", PARTNER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void partnerReadsItsOwnDisbursement() throws Exception {
        String disbursementNo = disbursementNo(create(PARTNER, "key-1", BODY));

        read(PARTNER, disbursementNo).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALINDI"));
    }

    @Test
    void hidesDisbursementOfAnotherPartner() throws Exception {
        String disbursementNo = disbursementNo(create(PARTNER, "key-1", BODY));

        read(OTHER_PARTNER, disbursementNo).andExpect(status().isNotFound());
    }

    private ResultActions create(String partner, String key, String body) throws Exception {
        return mvc.perform(post("/disbursements")
                .header("X-Partner-Code", partner)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions read(String partner, String disbursementNo) throws Exception {
        return mvc.perform(get("/disbursements/" + disbursementNo).header("X-Partner-Code", partner));
    }

    private String disbursementNo(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.disbursementNo");
    }
}
