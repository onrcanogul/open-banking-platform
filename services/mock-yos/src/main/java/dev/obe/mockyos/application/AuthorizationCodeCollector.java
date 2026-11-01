package dev.obe.mockyos.application;

import dev.obe.contract.event.ResourceType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches the code after AYRIK_GKD_BASARILI, since the event only says that it is ready. Runs in the
 * background so the listener can answer 202 at once instead of making the HHS wait for this call.
 */
@Component
public class AuthorizationCodeCollector {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCodeCollector.class);

    private static final Map<ResourceType, String> CONSENT_TYPE_CODES = Map.of(
            ResourceType.ODEME_EMRI_RIZASI, "O",
            ResourceType.HESAP_BILGISI_RIZASI, "H",
            ResourceType.ILERI_TARIHLI_ODEME_EMRI_RIZASI, "I",
            ResourceType.DUZENLI_ODEME_EMRI_RIZASI, "D");

    private final RestClient restClient;
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    public AuthorizationCodeCollector(RestClient.Builder builder, @Value("${obe.hhs-url}") String hhsUrl) {
        this.restClient = builder.baseUrl(hhsUrl).build();
    }

    @Async
    public void collect(String yosCode, ResourceType resourceType, String consentNo) {
        try {
            AuthorizationCode code = restClient.get()
                    .uri(uri -> uri.path("/yetkilendirme-kodu")
                            .queryParam("rizaNo", consentNo)
                            .queryParam("rizaTip", CONSENT_TYPE_CODES.get(resourceType))
                            .build())
                    .header("X-TPP-Code", yosCode)
                    .retrieve()
                    .body(AuthorizationCode.class);
            codes.put(consentNo, code.yetKod());
            log.info("Collected the authorization code of consent {}", consentNo);
        } catch (RestClientException ex) {
            log.warn("Could not collect the code of consent {}: {}", consentNo, ex.getMessage());
        }
    }

    public Map<String, String> collected() {
        return Map.copyOf(codes);
    }

    private record AuthorizationCode(String yetKod) {
    }
}
