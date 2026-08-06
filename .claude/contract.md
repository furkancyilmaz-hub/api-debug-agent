# Projeler arası sözleşme

Üç proje var. Aralarındaki tek bağ HTTP. Bu dosya **tam olarak o sınırları**
tanımlar; üç repo'da da aynı kopya bulunur, değişirse üçü birden güncellenir.

| Proje | Port | Rol |
|---|---|---|
| `demo-api` | 8080 | Hedef servis. UI yok. |
| `api-debug-agent` | 8081 | N+1 analizi. UI yok. |
| `debug-console` | 5173 | Projedeki tek arayüz. React SPA, iki ekran: trafik ve analiz. |

```
kullanıcı ──► debug-console (:5173)      tek arayüz
debug-console ──► demo-api               trafik üretir
debug-console ──► api-debug-agent        analizi başlatır, raporu alır
api-debug-agent ──► demo-api             log, istek bilgisi, şema metadata
```

`api-debug-agent`, `demo-api`'nin veritabanına bağlanmaz.

---

## 1. Correlation

- Header adı: `X-Correlation-Id`
- `demo-api` her cevaba yazar
- İstekle bir değer geldiyse onu kullanır

---

## 2. `demo-api` → agent'ın eriştiği uçlar

### `GET /internal/logs`

| Parametre | Not |
|---|---|
| `correlationId` | tek istek için |
| `from`, `to` | ISO-8601, zaman aralığı için |
| `minLevel` | opsiyonel |
| `limit` | varsayılan 1000, üst sınır 5000 |

`correlationId` ya da `from`/`to` çiftinden biri zorunlu.
Sıralama: `timestamp, id`.

Satır alanları: `id`, `correlationId`, `timestamp`, `thread`, `level`,
`logger`, `message`.

`thread` ve `id` zorunlu: bind satırları SQL satırına thread üzerinden bağlanır,
aynı milisaniyedeki satırlar `id` ile sıralanır.

### `GET /internal/requests?from=&to=`

O aralıkta tamamlanmış istekler: `correlationId`, `method`, `path`, `status`,
`durationMs`, `timestamp`.

`app_log`'daki sabit formatlı satırdan üretilir:
```
REQUEST_COMPLETED method=GET path=/customers status=200 durationMs=1180
```
Bu format değişmez; agent bunu ayrıştırıyor.

### `GET /internal/schema/foreign-keys`

```json
[ { "childTable":"payment", "childColumn":"customer_id",
    "parentTable":"customer", "parentColumn":"id" } ]
```

Agent bunu **açılışta bir kez** çeker ve cache'ler.

---

## 2b. `demo-api` → console'un eriştiği uçlar

### İş endpoint'leri

Console iş endpoint'lerini doğrudan çağırır ve süreyi kendisi ölçer.
Yollar TE01'de tanımlı.

Başka uç yok. **Bug flag'i, toggle ya da davranış değiştiren bir kontrol
bulunmuyor** — N+1 problemleri servisin doğal akışında oluşuyor. Bazı
endpoint'ler naif yazılmış, bazıları optimize; ikisi de kalıcı.

---

## 3. Log formatı

Agent'ın ayrıştırdığı iki satır tipi:

| Logger | Örnek |
|---|---|
| `org.hibernate.SQL` | `select p1_0.id,p1_0.amount from payment p1_0 where p1_0.customer_id=?` |
| `org.hibernate.orm.jdbc.bind` | `binding parameter (1:BIGINT) <- [42]` |

Bind satırları SQL satırından **sonra** gelir, aynı thread'de, bir sonraki SQL
satırına kadar o sorguya aittir.

---

## 4. `api-debug-agent` → console'un eriştiği uçlar

### `POST /api/analyze`

```json
{ "from": "2026-08-03T10:00:00Z", "to": "2026-08-03T10:15:00Z" }
```
Cevap: `{ "analysisId": "…" }`

### `GET /api/analyze/{id}/stream`

SSE. `AnalysisEvent`:

| Alan | İçerik |
|---|---|
| `type` | `STAGE_STARTED` \| `STAGE_FINISHED` \| `REPORT` \| `ERROR` |
| `stage` | `loglar` \| `ayrıştırma` \| `tespit` \| `zenginleştirme` |
| `kind` | `LOCAL` \| `MODEL` |
| `durationMs` | |
| `payload` | özet sayılar |

### `GET /api/analyze/{id}`

Tamamlanmış `AnalysisReport`.

---

## 5. `Finding`

| Alan | İçerik |
|---|---|
| `findingId` | bulgunun kimliği; `correlationId` + `foreignKey` + `normalizedQuery` hash'i |
| `correlationId` | |
| `endpoint` | `GET /customers?withPayments=true` |
| `parentTable` / `childTable` | |
| `foreignKey` | `payment.customer_id -> customer.id` |
| `normalizedQuery` | tekrarlayan child şablonu |
| `repeatCount` | |
| `distinctBindCount` | benzersiz bind değeri sayısı |
| `confidence` | `HIGH` \| `MEDIUM` |

Yukarıdakilerin hepsi **deterministik** olarak Java'da üretilir; rapor bu
alanlarla eksiksizdir.

Model yalnızca iki **nullable** alan ekler:

| Alan | İçerik |
|---|---|
| `explanation` | bulgunun insan diliyle açıklaması |
| `suggestion` | `action`, `rationale`, `expectedResult`, `risk`, `alternatives` |

`suggestion`'ın alanları da tek tek nullable'dır; `action` ya da `expectedResult` yoksa
`suggestion` hiç üretilmez.

Model bulgu ekleyemez, çıkaramaz, ölçülmüş değerleri değiştiremez. Model
katmanı kapalıyken bu iki alan boş gelir, rapor yine geçerlidir.
