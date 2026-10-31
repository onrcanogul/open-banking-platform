package dev.obe.mockyos.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.obe.mockyos.application.ReceivedEventStore;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

    @BeforeEach
    void clean() {
        store.clear();
    }

    @Test
    void acceptsNotification() throws Exception {
        mvc.perform(post("/olay-dinleme").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isAccepted());

        assertThat(store.all()).hasSize(1);
        assertThat(store.all().get(0).olaylar().get(0).kaynakNo()).isEqualTo("ODM-1");
    }

    @Test
    void rejectsMoreEventsThanTheStandardAllows() throws Exception {
        mvc.perform(post("/olay-dinleme").contentType(MediaType.APPLICATION_JSON).content(body(101)))
                .andExpect(status().isBadRequest());

        assertThat(store.all()).isEmpty();
    }

    private String body(int events) {
        String list = String.join(",", Collections.nCopies(events, EVENT));
        return "{\"katilimciBlg\":{\"hhsKod\":\"9999\",\"yosKod\":\"8001\"},\"olaylar\":[" + list + "]}";
    }
}
