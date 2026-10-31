package dev.obe.subscription.domain.policy;

import static dev.obe.subscription.domain.model.EventType.AYRIK_GKD_BASARILI;
import static dev.obe.subscription.domain.model.EventType.AYRIK_GKD_BASARISIZ;
import static dev.obe.subscription.domain.model.EventType.KAYNAK_GUNCELLENDI;
import static dev.obe.subscription.domain.model.ResourceType.BAKIYE;
import static dev.obe.subscription.domain.model.ResourceType.COKLU_ISLEM_TALEBI;
import static dev.obe.subscription.domain.model.ResourceType.DUZENLI_ODEME_EMRI;
import static dev.obe.subscription.domain.model.ResourceType.DUZENLI_ODEME_EMRI_RIZASI;
import static dev.obe.subscription.domain.model.ResourceType.DUZENLI_ODEME_PLANI;
import static dev.obe.subscription.domain.model.ResourceType.HESAP_BILGISI_RIZASI;
import static dev.obe.subscription.domain.model.ResourceType.ILERI_TARIHLI_ODEME_EMRI;
import static dev.obe.subscription.domain.model.ResourceType.ILERI_TARIHLI_ODEME_EMRI_RIZASI;
import static dev.obe.subscription.domain.model.ResourceType.KART_BILGILERI;
import static dev.obe.subscription.domain.model.ResourceType.ODEME_EMRI;
import static dev.obe.subscription.domain.model.ResourceType.ODEME_EMRI_RIZASI;
import static dev.obe.subscription.domain.model.YosRole.HBH;
import static dev.obe.subscription.domain.model.YosRole.OBH;

import dev.obe.subscription.domain.model.EventType;
import dev.obe.subscription.domain.model.ResourceType;
import dev.obe.subscription.domain.model.SubscriptionType;
import dev.obe.subscription.domain.model.YosRole;
import java.util.Map;
import java.util.Optional;

/**
 * Event and resource pairs a YOS may subscribe to at an HHS, with the role each pair requires.
 * Mirrors the HHS-delivered rows of the "Olay Tipleri ve Kaynak Tipleri Iliskisi" table in OHVPS
 * v2.0.1. BKM-delivered HHS_YOS_GUNCELLENDI is left out because it needs no subscription.
 */
public final class SubscriptionCatalog {

    private static final Map<SubscriptionType, YosRole> REQUIRED_ROLES = Map.ofEntries(
            entry(KAYNAK_GUNCELLENDI, ODEME_EMRI, OBH),
            entry(KAYNAK_GUNCELLENDI, ILERI_TARIHLI_ODEME_EMRI_RIZASI, OBH),
            entry(KAYNAK_GUNCELLENDI, ILERI_TARIHLI_ODEME_EMRI, OBH),
            entry(KAYNAK_GUNCELLENDI, DUZENLI_ODEME_EMRI_RIZASI, OBH),
            entry(KAYNAK_GUNCELLENDI, DUZENLI_ODEME_PLANI, OBH),
            entry(KAYNAK_GUNCELLENDI, DUZENLI_ODEME_EMRI, OBH),
            entry(KAYNAK_GUNCELLENDI, HESAP_BILGISI_RIZASI, HBH),
            entry(KAYNAK_GUNCELLENDI, BAKIYE, HBH),
            entry(KAYNAK_GUNCELLENDI, KART_BILGILERI, HBH),
            entry(KAYNAK_GUNCELLENDI, COKLU_ISLEM_TALEBI, HBH),
            entry(AYRIK_GKD_BASARILI, ODEME_EMRI_RIZASI, OBH),
            entry(AYRIK_GKD_BASARILI, HESAP_BILGISI_RIZASI, HBH),
            entry(AYRIK_GKD_BASARILI, ILERI_TARIHLI_ODEME_EMRI_RIZASI, OBH),
            entry(AYRIK_GKD_BASARILI, DUZENLI_ODEME_EMRI_RIZASI, OBH),
            entry(AYRIK_GKD_BASARISIZ, ODEME_EMRI_RIZASI, OBH),
            entry(AYRIK_GKD_BASARISIZ, HESAP_BILGISI_RIZASI, HBH),
            entry(AYRIK_GKD_BASARISIZ, ILERI_TARIHLI_ODEME_EMRI_RIZASI, OBH),
            entry(AYRIK_GKD_BASARISIZ, DUZENLI_ODEME_EMRI_RIZASI, OBH));

    private SubscriptionCatalog() {
    }

    public static Optional<YosRole> requiredRole(SubscriptionType type) {
        return Optional.ofNullable(REQUIRED_ROLES.get(type));
    }

    private static Map.Entry<SubscriptionType, YosRole> entry(EventType event, ResourceType resource, YosRole role) {
        return Map.entry(new SubscriptionType(event, resource), role);
    }
}
