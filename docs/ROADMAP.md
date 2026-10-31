# Yol Haritası

Üç alanı sıfırdan, öğrenmek için kodluyoruz: Olay Bildirim, BaaS Kredi Kullandırım, SSO.
Bu tasarım kamuya açık standarda ve genel bankacılık pratiğine dayanır. Herhangi bir bankanın
gerçek mimarisini anlatmaz.

Olay Bildirim kaynağı: [ÖHVPS v2.0.1, Bölüm 12](https://ohvps.github.io/v2.0.1/contents/olay-bildirim.html).
OAS alan adları, başlıklar ve hata formatı sayfaların ham metninden doğrulandı.

## Çalışma düzeni

- Her adım küçük bir commit olur. Claude adımı yazar, ne yaptığını ve nedenini özetler.
- Sen diff'i okursun, anlamadığını sorarsın, onaylamadan sonraki adıma geçilmez.
- Her aşamanın iki etiketli listesi var:
  - **Sen yaz:** Öğrenmenin asıl değeri burada. Claude iskeleti ve testleri hazırlar, gövdeyi sen yazarsın.
  - **Kavra:** Claude yazar, sen aynı konuyu kendi cümlelerinle anlatabilene kadar geçmeyiz.
- Boilerplate (pom, DTO, compose, mapper) Claude'da kalır.

## Teknik kararlar

| Konu | Karar | Neden |
|---|---|---|
| Dil | Java 17 | Makinede kurulu |
| Framework | Spring Boot 4.1.x, Spring Cloud 2025.1.x | Destek alan güncel hat. Bankadaki sürüm büyük ihtimalle daha eskidir, görünce not ederiz |
| Build | Maven Wrapper, çok modüllü tek repo | Makinede Maven yok, wrapper kurulum gerektirmez |
| Altyapı | Docker Compose: Oracle Free, Kafka (KRaft), Redis | Tek komutla ayağa kalkar |
| Kubernetes | En sonda, Docker Desktop üzerinde | Önce davranışı öğreniyoruz, dağıtımı sonra |
| Sıra | Olay Bildirim → BaaS → SSO | Olay Bildirim'in kamuya açık spesifikasyonu var ve Kafka, Redis, Oracle'ın hepsine dokunuyor |

## 1. Olay Bildirim (HHS tarafı)

```text
mock-hhs ──(aynı transaction)──> Oracle: iş verisi + outbox
                                   │
                  outbox-relay (mock-hhs içinde)
                                   ▼
                                Kafka
                                   ▼
                       event-dispatcher ──Redis──> abonelik bilgisi
                          │         │
         POST /olay-dinleme│         │başarısız
                          ▼         ▼
                       mock-yos    Oracle: teslimat kaydı (next_attempt_at)
                          │                 │
       GET ile asıl veriyi çeker            └─ deneme bitti → İletilemeyen Olaylar

subscription-service (OAS): POST/GET/PUT/DELETE /olay-abonelik, iletilemeyen-olaylar
```

Standarttan gelen ve tasarımı belirleyen kurallar:

- Bildirim veriyi taşımaz, yalnızca `olayTipi`, `kaynakTipi`, `kaynakNo` taşır. YÖS asıl veriyi GET ile çeker.
- Teslimat push'tur: HHS, YÖS'ün `POST /olay-dinleme` ucuna yazar, başarı `202` döner, çağrı başına en fazla 100 olay.
- "Anlık" 5 saniye içinde demektir.
- Deneme kuralı olay tipine göre değişir: çoğu olayda 30 dakikada 3 deneme, `AYRIK_GKD_*` için 1 dakikada 3 deneme.
  `BAKIYE` tekrar denenmez. Bu yüzden deneme politikası koda gömülmez, olay tipine bağlı tablodan okunur.
- Denemeler bitince olay İletilemeyen Olaylar'a düşer, YÖS en sık 10 dakikada bir sorgular, sayfa başına 100 kayıt.
- YÖS başına HHS'de tek abonelik vardır. Tekrarında `TR.OHVPS.Business.EventSubscriptionAlreadyExists` döner.

Spesifikasyonda iç tutarsızlık var. `iletilemeyen-olaylar` yolu ve `/sistem-olay-dinleme` ucunun sahibi
sayfada iki farklı şekilde geçiyor. Bu uçları kodlarken seçimimizi burada kaydedeceğiz.

Spesifikasyonun açık bıraktığı yerlerde aldığımız kararlar:

- Listedeki olay ve kaynak değerleri geçerli ama çift tabloda yoksa `InvalidContent` döner.
  Liste dışı değerlerde standardın istediği `EventSubscriptionNotFound` kullanılır.
- Abonelik bulunamazsa veya başka YÖS'e aitse `404 TR.OHVPS.Resource.NotFound` döner.
- Başlıktaki kodlar gövdedeki `katilimciBlg` ile eşleşmezse `InvalidContent` döner.
- `DELETE` kaydı fiziksel olarak siler, böylece YÖS yeniden abone olabilir.
- `X-TPP-Code` şimdilik doğrulanmadan kabul edilir. Gerçek kimlik doğrulama SSO aşamasında eklenecek.
- `X-Request-ID` henüz okunmuyor, M5'teki korelasyon adımında eklenecek.

Olay akışı için aldığımız kararlar:

- Relay ayrı servis değil, `mock-hhs` içinde bir bileşen. Outbox tablosu o servisin şemasında olduğu için
  başka bir servisin bu tabloya erişmesi şema sınırını bozardı.
- Kafka anahtarı `resourceNo`. Aynı kaynağın olayları aynı partition'a düşer ve sırası korunur.
- Teslimat en az bir kez garantilidir. Mükerrer mesajlar M5'te `eventNo` ile elenecek.
- Olay tipleri ve mesaj şekli `event-contract` modülünde durur.
- Her servisin kendi Oracle kullanıcısı vardır, servisler birbirinin tablosuna erişemez.
- Dispatcher aboneliği `subscription-service`'in iç ucundan OpenFeign ile okur.
- Dispatcher hata alınca olayı atmaz, partition'ı tutarak süresiz yeniden dener. Bu geçici bir çözümdür:
  Tek bir YÖS'ün arızası diğerlerini de bekletir. M4'te teslimat kayıtları bunu çözecek.
- YÖS abone olmadan önce oluşan olaylar ona gönderilmez.

Servislerin paket yapısı, bağımlılıklar yalnızca içeriye doğru akacak şekilde:

| Paket | Sorumluluk |
|---|---|
| `api` | Controller, DTO, mapper, HTTP hata çevirisi |
| `application` | Kullanım senaryoları, command ve result tipleri, dış dünya için port arayüzleri |
| `domain` | Entity, enum, iş politikaları, repository arayüzü |
| `infrastructure` | Port implementasyonları ve yapılandırma |
| `common` | Katmanlar arası paylaşılan hata tipleri |

| Aşama | Çıktı | Sen yaz | Kavra |
|---|---|---|---|
| M0 Altyapı | git, Maven modülleri, compose, sağlık kontrolü | - | Modüller nasıl ayrılıyor, Oracle şeması nasıl yönetiliyor (Flyway) |
| M1 Abonelik | OAS uçları, hata kodları, testler | Tek abonelik kuralı ve hata eşlemesi | Controller/Service/Repository ayrımı, HHS ve YÖS rolleri |
| M2 Outbox | Durum değişimi + outbox, Kafka'ya aktarım | İş verisi ile outbox yazımını aynı transaction'a koyma | Dual-write problemi, Kafka partition key, sıralama garantisi |
| M3 Mutlu yol | Dispatcher, mock-yos, 100'lük paketleme | Abonelik filtresi ve paketleme | Consumer group, offset commit, at-least-once teslimat |
| M4 Tekrar deneme | Teslimat durum makinesi, İletilemeyen Olaylar | Durum geçişleri ve `next_attempt_at` hesabı | Neden Kafka retry topic yerine DB, `FOR UPDATE SKIP LOCKED`, exponential backoff |
| M5 Sertleştirme | Redis önbellek, tekrar eden olay koruması, Ayrık GKD, metrik | Kafka tüketicisinde idempotency | Cache invalidation, Ayrık GKD akışı, korelasyon id'li loglama |
| M6 Batch | Spring Batch ile eski kayıt arşivi ve mutabakat | Bir Reader/Processor/Writer zinciri | Chunk işleme, restart davranışı |

Bitiş ölçütü: mock-yos kapalıyken üretilen olaylar kaybolmuyor, açıldığında kurallara uygun teslim ediliyor,
denemesi bitenler İletilemeyen Olaylar'dan okunuyor. Bu senaryo otomatik test olarak koşuyor.

## 2. BaaS Kredi Kullandırım

Kamuya açık bir standardı olmadığı için kendi tasarımımız: kullandırım talebi, ön kontrol, limit rezervasyonu,
muhasebe kaydı, hesaba aktarım, bildirim.

- **Sen yaz:** Kullandırım durum makinesi, hata halinde telafi adımları, `Idempotency-Key` kontrolü.
- **Kavra:** Saga ve dağıtık transaction farkı, para tutarlarında `BigDecimal`, Spring Batch gün sonu mutabakatı,
  Spring Cloud Gateway ve servisler arası çağrılar.

## 3. SSO

Spring Authorization Server ile OIDC Authorization Code + PKCE, Redis oturumları, React (Context API, Hooks) istemci.
Önceki iki alanın uçları bu sunucunun token'larıyla korunur.

- **Sen yaz:** React tarafında `AuthContext`, korumalı route, token yenileme akışı.
- **Kavra:** Authorization Code + PKCE akışı adım adım, JWT doğrulama, oturum ve token farkı, logout'un neden zor olduğu.

## 4. Kubernetes

Servisleri imaja alıp Docker Desktop Kubernetes'e dağıtma: Deployment, Service, ConfigMap, Secret, probe'lar.
Oracle ve Kafka Compose'da kalır. 15,6 GB RAM'de hepsini aynı anda çalıştırmak zorlayabilir.

## İlk turda kapsam dışı

mTLS, istek imzalama, gerçek çekirdek bankacılık entegrasyonu ve BKM GEÇİT bağlantısı. Olay Bildirim bittiğinde
spesifikasyonun güvenlik bölümünü okuyup tekrar değerlendiririz.
