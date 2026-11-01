package dev.obe.mockhhs.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.obe.contract.event.EventType;
import dev.obe.contract.event.ResourceType;
import dev.obe.mockhhs.domain.model.ConsentStatus;
import dev.obe.mockhhs.domain.model.OutboxEvent;
import dev.obe.mockhhs.domain.repository.ConsentRepository;
import dev.obe.mockhhs.domain.repository.OutboxEventRepository;
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

@SpringBootTest(properties = {"ORACLE_HHS_USER=unused", "ORACLE_HHS_PASSWORD=unused",
        "spring.kafka.admin.auto-create=false", "obe.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class ConsentApiTest {

    private static final String YOS = "8001";
    private static final String OTHER_YOS = "8002";
    private static final String PAYMENT = "PAYMENT";
    private static final String ACCOUNT = "ACCOUNT_INFORMATION";

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConsentRepository consentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private OutboxEventRepository outboxRepository;

    @BeforeEach
    void clean() {
        outboxRepository.deleteAll();
        consentRepository.deleteAll();
    }

    @Test
    void newConsentWaitsAndProducesNoEvent() throws Exception {
        createConsent(PAYMENT);

        assertThat(consentRepository.findAll()).extracting(consent -> consent.getStatus())
                .containsExactly(ConsentStatus.YETKI_BEKLENIYOR);
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void authorizingWritesSuccessEventWithoutTheCode() throws Exception {
        String consentNo = createConsent(PAYMENT);

        authorize(consentNo).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("YETKILENDIRILDI"));

        OutboxEvent event = singleEvent();
        assertThat(event.getEventType()).isEqualTo(EventType.AYRIK_GKD_BASARILI);
        assertThat(event.getResourceType()).isEqualTo(ResourceType.ODEME_EMRI_RIZASI);
        assertThat(event.getResourceNo()).isEqualTo(consentNo);
    }

    @Test
    void rejectingWritesFailureEventAndKeepsCancelCode() throws Exception {
        String consentNo = createConsent(ACCOUNT);

        reject(consentNo, "13").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("YETKI_IPTAL"))
                .andExpect(jsonPath("$.cancelCode").value("13"));

        OutboxEvent event = singleEvent();
        assertThat(event.getEventType()).isEqualTo(EventType.AYRIK_GKD_BASARISIZ);
        assertThat(event.getResourceType()).isEqualTo(ResourceType.HESAP_BILGISI_RIZASI);
    }

    @Test
    void refusesCancelCodeOutsideTheAuthenticationList() throws Exception {
        String consentNo = createConsent(PAYMENT);

        reject(consentNo, "02").andExpect(status().isBadRequest());
    }

    @Test
    void customerRevocationWritesResourceUpdatedEvent() throws Exception {
        String consentNo = createConsent(ACCOUNT);
        authorize(consentNo);
        outboxRepository.deleteAll();

        revoke(consentNo).andExpect(status().isOk()).andExpect(jsonPath("$.cancelCode").value("02"));

        OutboxEvent event = singleEvent();
        assertThat(event.getEventType()).isEqualTo(EventType.KAYNAK_GUNCELLENDI);
        assertThat(event.getResourceType()).isEqualTo(ResourceType.HESAP_BILGISI_RIZASI);
    }

    @Test
    void paymentConsentCannotBeRevokedHere() throws Exception {
        String consentNo = createConsent(PAYMENT);
        authorize(consentNo);

        revoke(consentNo).andExpect(status().isConflict());
    }

    @Test
    void consentCannotBeAuthorizedTwice() throws Exception {
        String consentNo = createConsent(PAYMENT);
        authorize(consentNo);

        authorize(consentNo).andExpect(status().isConflict());

        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void yosReadsCodeOfAuthorizedConsent() throws Exception {
        String consentNo = createConsent(PAYMENT);
        authorize(consentNo);

        readCode(YOS, consentNo, "O").andExpect(status().isOk())
                .andExpect(jsonPath("$.yetKod").isNotEmpty())
                .andExpect(jsonPath("$.rizaNo").value(consentNo))
                .andExpect(jsonPath("$.rizaDrm").value("Y"));
    }

    @Test
    void hidesCodeBeforeAuthorization() throws Exception {
        String consentNo = createConsent(PAYMENT);

        readCode(YOS, consentNo, "O").andExpect(status().isNotFound());
    }

    @Test
    void hidesCodeFromAnotherYos() throws Exception {
        String consentNo = createConsent(PAYMENT);
        authorize(consentNo);

        readCode(OTHER_YOS, consentNo, "O").andExpect(status().isNotFound());
    }

    @Test
    void hidesCodeWhenConsentTypeDoesNotMatch() throws Exception {
        String consentNo = createConsent(PAYMENT);
        authorize(consentNo);

        readCode(YOS, consentNo, "H").andExpect(status().isNotFound());
    }

    @Test
    void hidesCodeAfterItExpires() throws Exception {
        String consentNo = createConsent(PAYMENT);
        authorize(consentNo);
        jdbc.update("UPDATE consent SET code_expires_at = SYSTIMESTAMP - 1 WHERE consent_no = ?", consentNo);

        readCode(YOS, consentNo, "O").andExpect(status().isNotFound());
    }

    @Test
    void revokingRemovesTheCode() throws Exception {
        String consentNo = createConsent(ACCOUNT);
        authorize(consentNo);
        revoke(consentNo);

        readCode(YOS, consentNo, "H").andExpect(status().isNotFound());
    }

    @Test
    void consentStaysWaitingWhenEventCannotBeWritten() throws Exception {
        String consentNo = createConsent(PAYMENT);
        doThrow(new IllegalStateException("outbox unavailable")).when(outboxRepository).save(any());

        assertThatThrownBy(() -> authorize(consentNo)).hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(consentRepository.findById(consentNo).orElseThrow().getStatus())
                .isEqualTo(ConsentStatus.YETKI_BEKLENIYOR);
    }

    private OutboxEvent singleEvent() {
        List<OutboxEvent> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private String createConsent(String type) throws Exception {
        String json = mvc.perform(post("/internal/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yosCode\":\"%s\",\"type\":\"%s\"}".formatted(YOS, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.consentNo");
    }

    private ResultActions authorize(String consentNo) throws Exception {
        return mvc.perform(post("/internal/consents/" + consentNo + "/authorization"));
    }

    private ResultActions reject(String consentNo, String cancelCode) throws Exception {
        return mvc.perform(post("/internal/consents/" + consentNo + "/rejection")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cancelCode\":\"%s\"}".formatted(cancelCode)));
    }

    private ResultActions revoke(String consentNo) throws Exception {
        return mvc.perform(post("/internal/consents/" + consentNo + "/revocation"));
    }

    private ResultActions readCode(String yosCode, String consentNo, String consentType) throws Exception {
        return mvc.perform(get("/yetkilendirme-kodu")
                .header("X-TPP-Code", yosCode)
                .param("rizaNo", consentNo)
                .param("rizaTip", consentType));
    }
}
