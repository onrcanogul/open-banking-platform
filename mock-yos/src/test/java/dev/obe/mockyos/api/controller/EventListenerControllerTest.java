package dev.obe.mockyos.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.obe.contract.event.ResourceType;
import dev.obe.mockyos.application.AuthorizationCodeCollector;
import dev.obe.mockyos.application.OutageSwitch;
import dev.obe.mockyos.application.ReceivedEventStore;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EventListenerControllerTest {

    private static final String EVENT = """
            {"olayNo":"1","olayZamani":"2026-10-31T19:20:28.093Z","olayTipi":"KAYNAK_GUNCELLENDI",
             "kaynakTipi":"ODEME_EMRI","kaynakNo":"ODM-1"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReceivedEventStore store;

    @Autowired
    private OutageSwitch outage;

    @MockitoBean
    private AuthorizationCodeCollector codeCollector;

    @BeforeEach
    void clean() {
        store.clear();
        outage.set(false);
    }

    @Test
    void acceptsNotification() throws Exception {
        mvc.perform(post("/olay-dinleme").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isAccepted());

        assertThat(store.all()).hasSize(1);
        assertThat(store.all().get(0).olaylar().get(0).kaynakNo()).isEqualTo("ODM-1");
    }

    @Test
    void collectsCodeAfterSeparateAuthenticationSucceeds() throws Exception {
        String event = """
                {"olayNo":"2","olayZamani":"2026-10-31T19:20:28.093Z","olayTipi":"AYRIK_GKD_BASARILI",
                 "kaynakTipi":"ODEME_EMRI_RIZASI","kaynakNo":"consent-1"}""";

        mvc.perform(post("/olay-dinleme").contentType(MediaType.APPLICATION_JSON).content(bodyOf(event)))
                .andExpect(status().isAccepted());

        verify(codeCollector).collect("8001", ResourceType.ODEME_EMRI_RIZASI, "consent-1");
    }

    @Test
    void doesNotCollectCodeForOtherEvents() throws Exception {
        mvc.perform(post("/olay-dinleme").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isAccepted());

        verifyNoInteractions(codeCollector);
    }

    @Test
    void refusesEverythingDuringOutage() throws Exception {
        mvc.perform(post("/internal/outage").param("down", "true")).andExpect(status().isOk());

        mvc.perform(post("/olay-dinleme").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isServiceUnavailable());

        assertThat(store.all()).isEmpty();
    }

    @Test
    void rejectsMoreEventsThanTheStandardAllows() throws Exception {
        mvc.perform(post("/olay-dinleme").contentType(MediaType.APPLICATION_JSON).content(body(101)))
                .andExpect(status().isBadRequest());

        assertThat(store.all()).isEmpty();
    }

    private String body(int events) {
        return bodyOf(String.join(",", Collections.nCopies(events, EVENT)));
    }

    private String bodyOf(String events) {
        return "{\"katilimciBlg\":{\"hhsKod\":\"9999\",\"yosKod\":\"8001\"},\"olaylar\":[" + events + "]}";
    }
}
