# CLAUDE.md — Java / Spring çalışma standartları

Bu dosya projeye özel değil. Tüm Java/Spring projelerinde geçerli kurallar. Projeye özgü bilgi (domain, mimari,
endpoint'ler) repo'nun kendi `backend-architecture.md`'sinde.

## Çalışma şekli

- Kod yazmadan önce plan çıkar, onay bekle.
- Tek seferde tek iş. İstenmeyen refactor yapma, "yol üstü" iyileştirme önerme.
- Her değişiklikten sonra derle ve testleri çalıştır.
- Yeni bağımlılık ekleme, sürüm yükseltme, config anahtarı değiştirme: önce sor.
- Mevcut kodun stilini taklit et. Kendi tercihini dayatma.
- Emin değilsen dur ve sor. Tahmin ederek devam etme.

## Paketleme ve katmanlar

- Teknik katman entity/, service/, controller/, dto/ paketleri aç
- **Controller**: HTTP'ye özgü işler — validation, DTO dönüşümü, delegasyon. İş mantığı ve repository çağrısı yok.
- **Service**: iş mantığı. `@Transactional` bu katmanda.
- **Repository**: veri erişimi. İş kuralı taşımaz.
- Entity HTTP katmanına sızmaz. Her endpoint kendi DTO'sunu kullanır.

## Spring kullanımı

- Constructor injection. Alan üzerinde `@Autowired` kullanma.
- Bağımlılıklar `final`.
- `@Transactional` service'te; okuma metotlarında `readOnly = true`.
- Dağınık `@Value` yerine `@ConfigurationProperties` ile tipli config.
- Ortam farkları profile ile yönetilir, kod içinde `if (env.equals(...))` yazılmaz.
- `spring.jpa.open-in-view` kapalı olmalı.

## JPA ve veri erişimi

- İlişkiler lazy. Gerektiğinde açık `join fetch` veya `@EntityGraph`.
- Liste dönen sorgular her zaman sayfalanır. Sınırsız `findAll()` yok.
- N+1'e dikkat: döngü içinde repository çağrısı yapma.
- `CascadeType.REMOVE` ve `orphanRemoval` bilinçli kullanılır, varsayılan olarak eklenmez.
- Entity'de `equals`/`hashCode`'u tüm alanlar üzerinden üretme.
- Şema değişikliği migration ile yapılır; üretim yolunda `ddl-auto: update` yok.

## Hata yönetimi

- Merkezi `@RestControllerAdvice`. Her controller'da try/catch yok.
- Domain'e özgü exception tipleri tanımla, HTTP status eşlemesi tek yerde olsun.
- Exception yutma yok: yakalayıp sadece loglayıp devam etme.
- Yakalayıp aynı exception'ı loglayıp tekrar fırlatma — çift kayıt üretir.
- Stacktrace client'a dönmez.
- `catch (Exception e)` yerine daraltılmış tip.

## Logging

- SLF4J. `System.out.println` yok.
- Parametreli log: `log.debug("customer={}", id)`. String birleştirme yok.
- Log mesajları İngilizce.
- Parola, token, TCKN, kart numarası, e-posta log'a yazılmaz.
- Seviye: `error` gerçekten aksiyon gerektiren durumlar için. Beklenen iş kuralı ihlali `error` değildir.

## Test

- Mümkün olan her yerde Spring context'i olmadan düz unit test.
- `@SpringBootTest` son çare. Slice test tercih et: `@WebMvcTest`, `@DataJpaTest`.
- Veritabanı testinde in-memory taklit yerine Testcontainers.
- AssertJ kullan (`assertThat`). `assertTrue` ile karşılaştırma yapma.
- Test isimleri davranışı anlatır: `shouldRejectPolicyWhenEndDateBeforeStart`.
- `Thread.sleep` ile senkronizasyon yok.
- Testi geçirmek için üretim kodunu gevşetme; önce nedenini söyle.

## Java dili

- DTO ve value object'ler için `record`.
- Alanlar mümkün olduğunca `final`, nesneler immutable.
- `Optional` dönüş tipi olarak kullanılır; alan veya parametre olarak değil.
- Lombok'ta `@Data` ve `@Builder`'ı entity üzerinde kullanma; `@Getter` ve açık constructor yeterli.
- Stream okunabilirlik için kullanılır. Üç satırlık zincir bir `for`'dan anlaşılırsa `for` yaz.
- `null` döndürme; boş koleksiyon veya `Optional` dön.

## Güvenlik ve config

- Secret koda ve repo'ya girmez. Environment variable veya secret manager.
- Dış girdi sınırda doğrulanır (Bean Validation).
- Log ve hata mesajlarında iç detay (SQL, dosya yolu, sınıf adı) dışarı sızmaz.

## Yasaklar

- Test silme veya `@Disabled` ile devre dışı bırakma.
- Formatlama amacıyla ilgisiz dosyalara dokunma.
- `git push`, `git reset --hard`, branch silme gibi geri dönüşü olan komutlar:
  önce sor.
- Üretim config dosyalarını izinsiz değiştirme.
