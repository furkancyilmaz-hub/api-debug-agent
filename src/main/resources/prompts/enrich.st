Sana bir N+1 analizinin bulguları veriliyor. Bu bulgular Java tarafında ölçülerek üretildi;
rapor onlarla zaten eksiksiz. Senin işin her bulguya iki şey eklemek: insan diliyle bir açıklama
ve somut bir düzeltme önerisi.

Kurallar:
- Yalnızca sana verilen alanlar üzerinden konuş. Tablo, kolon, endpoint ya da sorgu adı uydurma;
  verilmeyen hiçbir şeyden bahsetme.
- Sayıları değiştirme. repeatCount, distinctBindCount ve confidence ölçülmüş değerlerdir;
  onları yalnızca yorumlarsın.
- Bulgu ekleme, çıkarma ya da eleme. "Bu meşru bir toplu okuma olabilir" diyeceksen bunu
  explanation içinde metin olarak söyle.
- Düzeltmeyi uygulama, öner.
- expectedResult somut bir sayı içersin, örneğin "sorgu sayısı 214 -> 2" ya da "~40 ms".
- Her bulgu için tam olarak bir çıktı kalemi üret ve findingId'yi aynen geri döndür.
- Türkçe yaz.

Alanların anlamı:
- endpoint: bu sorguları çalıştıran HTTP isteği
- parentTable / childTable: ilişkinin iki ucu
- foreignKey: child kolonundan parent kolonuna giden ilişki
- normalizedQuery: tekrar eden child sorgu şablonu
- repeatCount: şablonun tek bir istek içinde kaç kez çalıştığı
- distinctBindCount: bu çalışmaların kaçının farklı bir değerle yapıldığı
- confidence: HIGH ya da MEDIUM

Bulgular:
{findings}

Yalnızca aşağıdaki şemaya uyan bir JSON dön. Markdown code fence kullanma, JSON dışında
hiçbir şey yazma. explanation dışındaki alanları kısa tut.

\{ "items": [
    \{ "findingId": "verilen findingId",
       "explanation": "bulgunun ne anlama geldiği, hangi ilişki yüzünden oluştuğu",
       "suggestion": \{
         "action": "yapılacak değişiklik, örneğin @EntityGraph(attributePaths = \"payments\")",
         "rationale": "bu değişikliğin sorunu neden çözdüğü",
         "expectedResult": "beklenen sonuç, sayıyla",
         "risk": "bu değişikliğin riski",
         "alternatives": "alternatif çözüm, örneğin @BatchSize(size = 50)"
       \}
    \}
  ]
\}
