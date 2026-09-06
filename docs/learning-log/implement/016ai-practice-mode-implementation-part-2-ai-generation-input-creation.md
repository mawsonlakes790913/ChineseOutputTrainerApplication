# 016 AI問題生成モードの実装その2 AI生成した問題セットを取得する①プロンプトの作成

## 1. AI生成元問題の取得

AI問題生成メニューでユーザーが指定した、

- 難易度
- 理解度
- お気に入り
- 構文
- 学習対象言語

の条件を基に、AI問題生成の元となる`Question`を取得する処理を実装した。

前チャプターでは、同じ検索条件を使用して「AI生成対象となる問題が何問存在するか」を取得していた。

今回は実際にAIへ問題生成を依頼するため、

```java
List<Question>
```

として条件に一致する`Question`そのものを取得する。

また、AI APIへ一度に送信する問題数は最大50問とするため、Repositoryでは、

```sql
ORDER BY RANDOM()
LIMIT 50
```

を指定し、条件に一致する問題からランダムに最大50問を取得するようにした。

処理としては、

```text
ユーザーがAI問題生成条件を指定
        ↓
AiPracticeService
        ↓
検索条件をRepositoryへ渡せる形式へ変換
        ↓
QuestionRepository
        ↓
条件に一致するQuestionをランダムに最大50問取得
        ↓
List<Question> sourceQuestions
```

という流れになる。

### 工夫した点

前チャプターで実装したAI生成対象問題の件数取得と同じ検索条件を利用し、実際にAIへ送信する`Question`を取得するRepositoryを実装した。

特に、AIへ送信する問題数を最大50問に制限するため、

```sql
ORDER BY RANDOM()
LIMIT 50
```

を追加した。

これにより、条件に一致するすべての問題をJava側へ取得してから50問へ絞るのではなく、DBから取得する時点でランダムな50問へ絞ることができる。

また、構文が指定されていない場合は、

```java
if (structureIds == null || structureIds.isEmpty()) {
    structureIds = structureRepository.findAllStructureIds();
}
```

として、すべての`structureId`を検索条件へ渡す既存の仕組みを流用した。

---

## 2. AIとのデータ受け渡しに使用するDTOの設計

AI問題生成では、

- AIへ送るデータ
- AIから返ってくるデータ
- 最終的にアプリケーションで使用するデータ

で必要な情報が異なる。

そこで、それぞれの役割に応じて複数のDTOを作成した。

AIへ生成元問題を送るためのDTOとして、

```java
AiGenerationSourceDto
```

AIから返された1問分の生成結果を一時的に受け取るDTOとして、

```java
TemporaryGeneratedQuestionDto
```

複数の生成結果をまとめて受け取るDTOとして、

```java
TemporaryGeneratedQuestionListDto
```

最終的にAI問題生成モードで使用するDTOとして、

```java
AiGeneratedQuestionDto
```

を用意した。

全体として、

```text
Question
    ↓
AiGenerationSourceDto
    ↓
AI
    ↓
TemporaryGeneratedQuestionListDto
    └─ List<TemporaryGeneratedQuestionDto>
    ↓
AiGeneratedQuestionDto
```

という形でデータが変換されていく。

### 工夫した点

`Question`をそのままAIへ送るのではなく、

```java
@Data
public class AiGenerationSourceDto {

    private int sourceIndex;

    private String japaneseText;

    private String chineseText;

    private String template;

    private SubjectType subjectType;

    private VerbVariation verbVariation;
}
```

として、AI問題生成に必要な情報だけを持つDTOを作成した。

`Question`にはAIが問題を生成するうえで必要のない情報も含まれているため、外部APIへ渡す情報を必要なものだけに限定した。

また、APIから返された直後のデータと、その後アプリケーション内で使用するデータについても別のDTOを用意した。

### 勉強になった点

これまでDTOは主にEntityから画面表示用のデータを作るために使用していたが、今回の実装ではDTOの別の使い方を学んだ。

```text
AiGenerationSourceDto
→ 外部APIへ送信するためのDTO

TemporaryGeneratedQuestionDto
→ 外部APIから受信するためのDTO

AiGeneratedQuestionDto
→ アプリケーション内で使用するためのDTO
```

というように、同じ「問題」を表すデータであっても、処理の段階によって必要な情報が異なる。

DTOは単なるEntityのコピーではなく、処理ごとに必要なデータの形を定義するためにも利用できる。

### 気づいた点

外部APIを利用する場合、DBのEntityとAPIとの通信データを直接結び付けない方が役割が明確になる。

例えば将来`Question`へ別のフィールドを追加しても、それがAI生成に必要なければ`AiGenerationSourceDto`へ追加する必要はない。

逆に、AIとの通信だけで必要な`sourceIndex`は`Question`へ追加する必要がない。

EntityとAPI用DTOを分離することで、それぞれが必要とする情報だけを持たせられることが分かった。

---

## 3. `sourceIndex`による生成元Questionとの紐付け

今回は最大50問の生成元問題を1回のAPIリクエストでAIへ送信する。

そのため、AIから返ってきた各生成結果が、

```java
List<Question> sourceQuestions
```

のどの問題を基に生成されたものなのかを識別する必要がある。

そこで、送信用DTOへ、

```java
private int sourceIndex;
```

を持たせ、

```java
source.setSourceIndex(i);
```

として生成元問題のList上の位置を設定するようにした。

例えば、

```text
sourceIndex = 0
→ sourceQuestions.get(0)

sourceIndex = 1
→ sourceQuestions.get(1)
```

という対応になる。

### 工夫した点

DB上の`questionId`をAIへ渡して返してもらうのではなく、一時的な`sourceIndex`をAIとの対応付けに使用する設計にした。

AIから結果が返ってきた後は、

```text
sourceIndex
↓
sourceQuestions.get(sourceIndex)
↓
生成元Question
↓
questionId
```

とJava側で生成元Questionを特定できる。

これにより、DB上のIDをAIに扱わせる必要がなくなった。

### 気づいた点

AIに処理を依頼する場合でも、Java側だけで確実に処理できることまでAIへ任せる必要はない。

`questionId`はJava側ですでに把握しているため、AIには問題生成と対応付けに必要な最低限の`sourceIndex`だけを扱わせればよい。

```text
AI
→ 新しい問題を生成する

Java
→ DB上のQuestionとの正確な紐付けを行う
```

と役割を分けることで、AIが誤ったIDを返す、IDを欠落させるといった問題を避けられる。

---

## 4. 共通プロンプトと言語別Language Profileの分離

AI問題生成で使用するプロンプトを、

```text
ai-question-generation-common.txt
language-profile-mainland.txt
language-profile-taiwan.txt
```

の3つに分けて管理するようにした。

共通プロンプトでは、問題をどのように改変するかなどAI問題生成全体のルールを定義する。

一方、Language Profileでは、

- 字体
- 語彙
- 言い回し
- 発音

など、普通話と國語で異なるルールを定義する。

`LanguageVariant`に応じて必要なLanguage Profileを取得し、共通プロンプトと組み合わせて使用する。

### 工夫した点

普通話用・國語用にプロンプト全体をそれぞれ作成するのではなく、

```text
共通部分
+
言語ごとの差分
```

に分離した。

これによって、AI問題生成そのもののルールを普通話用と國語用で重複して管理する必要がなくなった。

また、プロンプト本文をJavaコードへ直接記述せず、`resources/prompts`配下のテキストファイルとして管理するようにした。

### 勉強になった点

Javaコードだけでなく、AIへ渡すプロンプトについても責務を分けて管理できることを学んだ。

```text
AiPromptService
→ プロンプトファイルを取得する

AiPracticeService
→ 取得したプロンプトと生成元問題を組み合わせる
```

という役割分担にしたことで、プロンプトを読み込む処理とAI問題生成全体の処理を分離できた。

### 気づいた点

プロンプトを外部ファイルとして管理しておけば、プロンプトの内容を変更するたびにJavaコード内の長い文字列を編集する必要がない。

また、将来Language Profileが増えた場合にも、言語ごとの差分を独立して管理しやすい構成になる。

AIを利用するアプリケーションでは、プロンプト自体もアプリケーションを構成する重要なリソースの一つとして考える必要があると感じた。

---

## 5. `ClassPathResource`によるプロンプトファイルの読み込み

`resources/prompts`配下へ保存したプロンプトをJavaから読み込むため、`AiPromptService`で`ClassPathResource`を使用した。

```java
ClassPathResource resource =
        new ClassPathResource(path);
```

その後、

```java
try (InputStream inputStream =
        resource.getInputStream()) {

    return new String(
            inputStream.readAllBytes(),
            StandardCharsets.UTF_8);
}
```

として、プロンプトファイルの内容を`String`として取得する。

### 苦戦した点

今回初めて、

```java
ClassPathResource
InputStream
readAllBytes()
```

を使用したため、それぞれがどの段階の処理を担当しているのか理解するのに時間がかかった。

特に、

```java
ClassPathResource resource =
        new ClassPathResource(path);
```

を実行した時点でファイル本文が取得できているわけではない、という点を整理する必要があった。

### 勉強になった点

プロンプトが`String`になるまでには、

```text
classpath上のファイル
↓
ClassPathResource
↓
InputStream
↓
byte[]
↓
String
```

という段階がある。

まず、

```java
new ClassPathResource(path);
```

によってclasspath上の指定されたリソースを扱うオブジェクトを作る。

次に、

```java
resource.getInputStream()
```

によって、そのリソースの内容を読み込むための`InputStream`を取得する。

さらに、

```java
inputStream.readAllBytes()
```

によって内容を`byte[]`として取得する。

最後に、

```java
new String(
        inputStream.readAllBytes(),
        StandardCharsets.UTF_8);
```

としてUTF-8で解釈することで、Javaで使用できる`String`になる。

また、

```java
try (InputStream inputStream =
        resource.getInputStream()) {
```

はtry-with-resourcesであり、処理終了後に`InputStream`を自動的に閉じることも確認した。

### 気づいた点

普段Javaで文字列を扱っていると、ファイルの内容も最初から`String`として存在しているように感じるが、実際にはファイルからバイトデータを読み込み、文字コードを指定して文字列へ変換する必要がある。

今回の実装によって、

```text
ファイルそのもの
```

と、

```java
String
```

としてJavaプログラム上で扱えるデータは別のものであり、その間に読み込みと文字コード変換が存在することを理解できた。

---

## 6. `ObjectMapper`による生成元問題のJSON変換

`sourceQuestions`から作成した、

```java
List<AiGenerationSourceDto> generationSources
```

はJavaオブジェクトである。

これをAI APIへ送信できるようにするため、

```java
String generationSourcesJson;

try {

    generationSourcesJson =
            objectMapper.writeValueAsString(
                    generationSources);

} catch (JsonProcessingException e) {

    throw new IllegalStateException(
            messageSource.getMessage(
                    "ai.generation.error.json",
                    null,
                    locale),
            e);
}
```

としてJSON形式の文字列へ変換した。

### 勉強になった点

`ObjectMapper`はJacksonが提供しているクラスで、

```java
objectMapper.writeValueAsString()
```

を使用するとJavaオブジェクトをJSON形式の文字列へ変換できる。

例えば、

```java
List<AiGenerationSourceDto>
```

を渡せば、各DTOのフィールドを基にJSONへ変換される。

また、ここで生成されるJSONはJava上では、

```java
String generationSourcesJson;
```

である。

つまり、JavaにJSONという専用の型があるわけではなく、

```text
JSONの形式で記述されたString
```

として扱っていることを理解した。

### 苦戦した点

JavaのオブジェクトとJSONの関係について、最初は「JSONへ変換する」という表現から、JSONという別のJava型へ変換されるようなイメージを持っていた。

しかし実際には、

```text
List<AiGenerationSourceDto>
↓
ObjectMapper
↓
JSON形式のString
```

という変換であることを整理できた。

また、`writeValueAsString()`では`JsonProcessingException`が発生する可能性があるため、JSONへの変換にも例外処理が必要であることを学んだ。

### 気づいた点

API連携では、Java内部で扱いやすいデータ形式と、外部システムとの通信に適したデータ形式を変換する処理が必要になる。

今回の場合、

```text
Java内部
List<AiGenerationSourceDto>

        ↓ ObjectMapper

外部へ渡すデータ
JSON形式のString
```

という境界が明確に存在している。

DTOを作るだけではAPIへ送信できる状態にはならず、さらに通信で扱える形式へ変換する必要があることが分かった。

---

## 7. AIへ送信する`input`の作成

最後に、

- 共通プロンプト
- Language Profile
- JSONへ変換した生成元問題

を組み合わせ、AIへ送信する1つの`String`を作成した。

```java
String input =
        commonPrompt
        + "\n\n"
        + languageProfile
        + "\n\n"
        + "## 生成元問題\n"
        + generationSourcesJson;
```

これによって、

```text
AI問題生成の共通ルール

言語別のルール

生成元問題
```

が1つの入力としてまとまり、次チャプターで実装するAPI連携処理へ渡せる状態になった。

### 勉強になった点

AIへ渡す情報を、

```text
commonPrompt
languageProfile
generationSourcesJson
```

という3つの要素として個別に作成し、最後に結合する形にした。

それぞれの情報を作る処理を分離しているため、プロンプトの取得、言語ルールの取得、生成元問題の作成が混在しない構成になった。

AI APIへ送信する「プロンプト」は、固定された文章だけではなく、

```text
固定の共通ルール
+
条件によって変わる言語別ルール
+
DBから取得してJSON化した動的なデータ
```

を組み合わせて作ることができる。

今回の`input`は単なる固定文字列ではなく、アプリケーションの状態やユーザーの条件によって内容が変化する。

### 気づいた点

今回の`generateQuestions()`を見ると、

```text
1. 共通プロンプトを取得
2. Language Profileを取得
3. Questionを送信用DTOへ変換
4. DTOをJSONへ変換
5. すべてをinputへまとめる
6. API連携用メソッドへ渡す
```

という流れになっている。

AI APIを利用する処理も、いきなりAPIを呼び出すのではなく、その前に必要なデータを段階的に準備していく通常のアプリケーション処理として考えられることが分かった。

---

## 8. このチャプター全体を通して

### 苦戦した点

このチャプターは実際のAPI連携に入る前の段階だが、これまでの機能と比較して初めて扱う内容が多かった。

特に、

```text
Question
↓
AiGenerationSourceDto
↓
JSON
↓
AI
↓
TemporaryGeneratedQuestionDto
↓
AiGeneratedQuestionDto
```

というデータの流れを整理することに時間がかかった。

また、

- `ClassPathResource`
- `InputStream`
- `ObjectMapper`
- JSON変換
- API送信用DTO
- APIレスポンス用DTO

など、これまでの通常学習・復習機能ではあまり使用してこなかった要素が一度に登場したため、実装を細かく分けて確認する必要があった。

### 気づいた点・勉強になった点

AI機能というと特殊な仕組みに見えるが、Java側の処理を分解すると、

```text
DBからデータを取得
↓
必要なデータだけDTOへ変換
↓
外部サービスへ渡せる形式へ変換
↓
APIへ送信
↓
レスポンスをDTOで受信
↓
アプリケーションで使用する形式へ変換
```

という外部API連携の流れになっている。

また、AIを利用するからといって、すべての処理をAIへ任せる必要はない。

今回の`sourceIndex`と`sourceQuestionId`のように、

```text
AIが得意な部分
→ 問題文の生成

Javaで確実に処理できる部分
→ DB上のQuestionとの紐付け
```

と役割を分けることが重要だと分かった。

### 所感

今回のチャプターではまだChatGPTやGeminiへの実際のAPIリクエストは実装していないが、その前段階だけでも多くの処理が必要だった。

特に、これまで何となく「JavaからプロンプトをAPIへ送ればAIが返してくれる」と考えていた部分について、

```text
どのQuestionをAIへ渡すのか
↓
どの情報だけを渡すのか
↓
複数問題をどう識別するのか
↓
プロンプトファイルをどう読み込むのか
↓
JavaオブジェクトをどうJSONへ変換するのか
↓
共通ルール・言語別ルール・問題データをどう組み合わせるのか
```

という具体的な処理へ分解して理解することができた。

また、`ClassPathResource`や`InputStream`によるファイル読み込み、`ObjectMapper`によるJSON変換など、AI機能以外のJava/Spring開発でも利用できる知識を学べた点も大きかった。

次チャプターでは、今回作成した`input`を実際にChatGPTやGeminiへ送信するため、今回の実装がAPI連携の土台になる。