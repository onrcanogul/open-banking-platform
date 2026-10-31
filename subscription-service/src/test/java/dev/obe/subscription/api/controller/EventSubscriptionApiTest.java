package dev.obe.subscription.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.obe.subscription.domain.repository.EventSubscriptionRepository;
import java.time.Duration;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(properties = {"ORACLE_APP_USER=unused", "ORACLE_APP_PASSWORD=unused"})
@AutoConfigureMockMvc
@Testcontainers
class EventSubscriptionApiTest {

    private static final String ALREADY_EXISTS = "TR.OHVPS.Business.EventSubscriptionAlreadyExists";
    private static final String INVALID_CONTENT = "TR.OHVPS.Business.InvalidContent";
    private static final String UNKNOWN_TYPE = "TR.OHVPS.Business.EventSubscriptionNotFound";
    private static final String INVALID_FORMAT = "TR.OHVPS.Resource.InvalidFormat";
    private static final String NOT_FOUND = "TR.OHVPS.Resource.NotFound";

    private static final String HHS = "9999";
    private static final String FULL_YOS = "8001";
    private static final String HBH_ONLY_YOS = "8002";
    private static final String NO_LISTENER_YOS = "8003";

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EventSubscriptionRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsSubscription() throws Exception {
        mvc.perform(withHeaders(post("/olay-abonelik"), FULL_YOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(FULL_YOS, "KAYNAK_GUNCELLENDI", "ODEME_EMRI")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.olayAbonelikNo").isNotEmpty())
                .andExpect(jsonPath("$.katilimciBlg.hhsKod").value(HHS))
                .andExpect(jsonPath("$.katilimciBlg.yosKod").value(FULL_YOS))
                .andExpect(jsonPath("$.abonelikTipleri[0].kaynakTipi").value("ODEME_EMRI"));
    }

    @Test
    void rejectsSecondSubscriptionOfSameYos() throws Exception {
        createSubscription(FULL_YOS);

        mvc.perform(withHeaders(post("/olay-abonelik"), FULL_YOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(FULL_YOS, "KAYNAK_GUNCELLENDI", "BAKIYE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ALREADY_EXISTS));
    }

    @Test
    void concurrentRequestsCreateOnlyOneSubscription() throws Exception {
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        int created = 0;
        try {
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(withHeaders(post("/olay-abonelik"), FULL_YOS)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(createBody(FULL_YOS, "KAYNAK_GUNCELLENDI", "ODEME_EMRI")))
                            .andReturn().getResponse();
                }));
            }
            start.countDown();

            for (Future<MockHttpServletResponse> future : futures) {
                MockHttpServletResponse response = future.get();
                if (response.getStatus() == 201) {
                    created++;
                } else {
                    assertThat(response.getStatus()).isEqualTo(400);
                    assertThat(response.getContentAsString()).contains(ALREADY_EXISTS);
                }
            }
        } finally {
            pool.shutdown();
        }
        assertThat(created).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownEventType() throws Exception {
        expectCreateError(FULL_YOS, createBody(FULL_YOS, "OLMAYAN_OLAY", "ODEME_EMRI"), UNKNOWN_TYPE);
    }

    @Test
    void rejectsPairOutsideTheTable() throws Exception {
        expectCreateError(FULL_YOS, createBody(FULL_YOS, "AYRIK_GKD_BASARILI", "BAKIYE"), INVALID_CONTENT);
    }

    @Test
    void rejectsBkmDeliveredEvent() throws Exception {
        expectCreateError(FULL_YOS, createBody(FULL_YOS, "HHS_YOS_GUNCELLENDI", "HHS"), INVALID_CONTENT);
    }

    @Test
    void rejectsResourceOutsideYosRole() throws Exception {
        expectCreateError(HBH_ONLY_YOS, createBody(HBH_ONLY_YOS, "KAYNAK_GUNCELLENDI", "ODEME_EMRI"), INVALID_CONTENT);
    }

    @Test
    void rejectsYosWithoutListenerApi() throws Exception {
        expectCreateError(NO_LISTENER_YOS, createBody(NO_LISTENER_YOS, "KAYNAK_GUNCELLENDI", "ODEME_EMRI"),
                INVALID_CONTENT);
    }

    @Test
    void rejectsBodyThatDisagreesWithHeaders() throws Exception {
        expectCreateError(FULL_YOS, createBody(HBH_ONLY_YOS, "KAYNAK_GUNCELLENDI", "BAKIYE"), INVALID_CONTENT);
    }

    @Test
    void reportsMissingFieldInStandardFormat() throws Exception {
        mvc.perform(withHeaders(post("/olay-abonelik"), FULL_YOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"abonelikTipleri\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(INVALID_FORMAT))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void rejectsMissingHeader() throws Exception {
        mvc.perform(get("/olay-abonelik").header("X-ASPSP-Code", HHS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(INVALID_FORMAT));
    }

    @Test
    void returnsSubscriptionOfCallingYos() throws Exception {
        String subscriptionNo = createSubscription(FULL_YOS);

        mvc.perform(withHeaders(get("/olay-abonelik"), FULL_YOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.olayAbonelikNo").value(subscriptionNo));
    }

    @Test
    void returnsNotFoundWhenYosHasNoSubscription() throws Exception {
        mvc.perform(withHeaders(get("/olay-abonelik"), FULL_YOS))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(NOT_FOUND));
    }

    @Test
    void internalEndpointReturnsSubscribedTypes() throws Exception {
        createSubscription(FULL_YOS);

        mvc.perform(get("/internal/subscriptions/" + FULL_YOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yosCode").value(FULL_YOS))
                .andExpect(jsonPath("$.types[0].eventType").value("KAYNAK_GUNCELLENDI"))
                .andExpect(jsonPath("$.types[0].resourceType").value("BAKIYE"));
    }

    @Test
    void internalEndpointReturnsNotFoundWithoutSubscription() throws Exception {
        mvc.perform(get("/internal/subscriptions/" + FULL_YOS)).andExpect(status().isNotFound());
    }

    @Test
    void updateReplacesAllTypes() throws Exception {
        String subscriptionNo = createSubscription(FULL_YOS);

        mvc.perform(withHeaders(put("/olay-abonelik/" + subscriptionNo), FULL_YOS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(subscriptionNo, FULL_YOS, "KAYNAK_GUNCELLENDI", "BAKIYE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abonelikTipleri.length()").value(1))
                .andExpect(jsonPath("$.abonelikTipleri[0].kaynakTipi").value("BAKIYE"));
    }

    @Test
    void deleteRemovesSubscriptionAndAllowsNewOne() throws Exception {
        String subscriptionNo = createSubscription(FULL_YOS);

        mvc.perform(withHeaders(delete("/olay-abonelik/" + subscriptionNo), FULL_YOS))
                .andExpect(status().isNoContent());

        createSubscription(FULL_YOS);
    }

    @Test
    void hidesSubscriptionOfAnotherYos() throws Exception {
        String subscriptionNo = createSubscription(FULL_YOS);

        mvc.perform(withHeaders(delete("/olay-abonelik/" + subscriptionNo), HBH_ONLY_YOS))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(NOT_FOUND));
    }

    private String createSubscription(String yos) throws Exception {
        String json = mvc.perform(withHeaders(post("/olay-abonelik"), yos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(yos, "KAYNAK_GUNCELLENDI", "BAKIYE")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.olayAbonelikNo");
    }

    private void expectCreateError(String yos, String body, String errorCode) throws Exception {
        mvc.perform(withHeaders(post("/olay-abonelik"), yos)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(errorCode));
    }

    private MockHttpServletRequestBuilder withHeaders(MockHttpServletRequestBuilder request, String yos) {
        return request.header("X-ASPSP-Code", HHS).header("X-TPP-Code", yos);
    }

    private String createBody(String yos, String eventType, String resourceType) {
        return """
                {"katilimciBlg":{"hhsKod":"%s","yosKod":"%s"},
                 "abonelikTipleri":[{"olayTipi":"%s","kaynakTipi":"%s"}]}
                """.formatted(HHS, yos, eventType, resourceType);
    }

    private String updateBody(String subscriptionNo, String yos, String eventType, String resourceType) {
        return """
                {"olayAbonelikNo":"%s","katilimciBlg":{"hhsKod":"%s","yosKod":"%s"},
                 "abonelikTipleri":[{"olayTipi":"%s","kaynakTipi":"%s"}]}
                """.formatted(subscriptionNo, HHS, yos, eventType, resourceType);
    }
}
