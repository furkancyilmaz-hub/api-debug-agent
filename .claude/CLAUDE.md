# CLAUDE.md — api-debug-agent

## Bu proje nedir

API Debug Assistant. Kullanıcı yavaş çalışan bir HTTP request/response yapıştırır;
agent hedef servise (`demo-api`) araçlarla gider, kanıt toplar, yavaşlığın sebebini
bulur, fix önerir ve fix sonrası aynı isteği tekrar ölçerek iddiasını kanıtlar.

**Kapsam: performans analizi.** Exception / hata root cause analizi kapsam dışı.
`getLogSummary` ERROR baskını gösterirse agent analizi durdurur ve
"bu bir performans problemi değil" der.

## Temel ilke

Bu bir LLM wrapper değil. Model tek başına "N+1 olabilir" demez; tool çağırır,
kanıt toplar, kanıtla konuşur. Her sonuç bir sayıyla doğrulanır — sorgu sayısı,
sorgu planı, önce/sonra latency. Kanıt toplamadan sonuç üreten akış yazma.

## Stack

Java 21, Spring Boot 3.x, Spring AI Anthropic starter, Spring Web.
UI: tek statik sayfa, 3 kolon — Request | Response | AI Analizi.

## Paket yapısı

```
com.furkan.apidebugagent
├── analysis/     # AnalysisService, NPlusOneDetector, Finding, AnalysisReport
├── sqllog/       # DemoApiClient, StatementAssembler, SqlAnalyzer, SqlNormalizer
├── schema/       # ForeignKeyCache, SchemaClient
├── llm/          # LlmClient, PromptLoader
└── web/          # Controller + SSE
```

Feature-based paketleme. Hexagonal katmanlama yok.

## Ne yapıyor

Kapsam **yalnızca N+1**. Index önerisi yok.

Kullanıcı bir zaman aralığı seçer. Agent o aralıktaki SELECT loglarını çeker,
correlationId bazında gruplar, JSqlParser ile ayrıştırır, foreign key metadata
ile parent-child ilişkilerini bulur ve dört koşulu uygular:

1. Parent sorgu child'dan önce mi çalışmış
2. Child sorgular aynı normalize şablonda mı
3. Bind edilen FK değerleri farklı mı
4. Tekrar sayısı eşiğin üstünde mi

Bulgular deterministik olarak Java'da çıkar ve **rapor orada tamamlanır.**

Model tespit sürecinin parçası değil. Yalnızca hazır bulgunun üstüne iki alan
ekler: `explanation` (insan diliyle açıklama) ve `suggestion` (düzeltme önerisi).
Bulgu ekleyemez, çıkaramaz, ölçülmüş hiçbir değeri değiştiremez.

Analiz başına **tek model çağrısı**; model cevabını üretir ve analiz orada
biter. Bulgu yoksa ya da `llm.enabled: false` ise model hiç çağrılmaz — analiz
yine eksiksiz çalışır.

Telemetri, token sayımı ve maliyet hesabı **kapsam dışı**.

## Kurallar

- Hedefe erişim yalnızca HTTP üzerinden; `demo-api`'nin veritabanına bağlanma
- Modele giden tek yol `LlmClient`; `ChatModel`'i doğrudan çağırma
- Tespit tamamen deterministik — eşik ve koşullar Java'da, modelde değil
- Model katmanı kapatılabilir; kapalıyken rapor eksiksiz üretilmeli
- Model çıktısı merge edilirken **tek yönlü**: yalnızca boş açıklama/öneri
  alanları doldurulur, ölçüm alanlarına dokunulmaz
- SQL ayrıştırma JSqlParser ile; string işlemleriyle tablo/kolon çıkarma
- FK metadata açılışta bir kez çekilir, her analizde değil
- Modele ham log gitmez; yalnızca bulgu alanları
- Modelin dönüşü doğrulanır — uydurulmuş correlationId rapora girmez
- Prompt'lar resources altında dosya olarak

## Fix önerisi

Agent fix'i **uygulamaz**, önerir. Öneri serbest metin değil `FixProposal` DTO'su:
`action`, `rationale`, `expectedResult`, `risk`, `alternatives`.

`expectedResult` zorunlu — agent fix'ten önce bir sayıya bağlanır
("sorgu sayısı 214 → 2", "~40 ms"), sonra tutturup tutturmadığı ölçülür.

## Prompt yönetimi

Prompt'lar `src/main/resources/prompts/` altında dosya olarak. Java string
literal'ı içine prompt yazma.

## Yasaklar

- `demo-api`'nin veritabanına doğrudan bağlanma. Erişim sadece HTTP üzerinden.
- Ham log'u sınırsız prompt'a basma. Önce `getLogSummary`, gerekirse sınırlı
  `getLogs`. Token bütçesi kısıtlı.
- Yeni bağımlılık ekleme, önce sor.

## Komutlar

- Çalıştır: `./mvnw spring-boot:run` (port 8081)
- Test: `./mvnw test`
- Hedef adres: `target.base-url` property'si

## Çalışma şekli

Önce plan çıkar, onay bekle, sonra implement et. Referans: `docs/spec.md`.
`demo-api` ayakta ve log üretiyor olmadan bu projede iş yapma.
