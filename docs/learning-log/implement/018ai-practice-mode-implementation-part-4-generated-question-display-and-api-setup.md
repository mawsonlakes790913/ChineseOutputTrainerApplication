# 018 AI問題生成モードの実装その4 生成問題の画面表示とAPI利用準備

## 1. AIの生成結果を画面表示用DTOへ変換

前チャプターまでで、ChatGPTまたはGeminiから生成結果を、

```java
TemporaryGeneratedQuestionListDto
```

として取得できるようになった。

しかし、このDTOはAPIから生成結果を受け取るための一時的なDTOであり、そのままAI問題生成モードの問題画面で使用するものではない。

そこで、

```java
List<AiGeneratedQuestionDto> generatedQuestions =
        convertToGeneratedQuestions(
                temporaryGeneratedQuestionListDto,
                sourceQuestions);
```

として、APIから取得した生成結果と生成元の`Question`を組み合わせ、

```java
List<AiGeneratedQuestionDto>
```

へ変換する処理を実装した。

変換処理は長くなるため、

```java
private List<AiGeneratedQuestionDto> convertToGeneratedQuestions(
        TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto,
        List<Question> sourceQuestions)
```

というprivateメソッドへ分離した。

### 工夫した点

APIとのやり取りに使用するDTOと、画面表示で使用するDTOを分けた。

APIから受け取る、

```java
TemporaryGeneratedQuestionDto
```

にはAIに生成させた、

- 日本語文
- 中国語文
- 拼音
- 注音
- `sourceIndex`

が格納されている。

一方、画面表示用の、

```java
AiGeneratedQuestionDto
```

には生成元問題の、

- `questionId`
- 難易度

も必要になる。

そのため、

```text
AIから取得する情報
+
DBの生成元Questionから取得する情報
↓
AiGeneratedQuestionDto
```

という形で、必要な情報を最後にまとめる構造にした。

### 勉強になった点

DTOは単にEntityを画面へ渡すためだけのものではなく、処理の段階ごとに必要なデータを表すためにも使用できる。

今回の場合は、

```text
AiGenerationSourceDto
→ AIへ渡す生成元問題

TemporaryGeneratedQuestionDto
→ AIから受け取る1問分の生成結果

TemporaryGeneratedQuestionListDto
→ APIから複数問題を受け取るためのラッパー

AiGeneratedQuestionDto
→ アプリケーション内で最終的に使用するAI生成問題
```

と、それぞれ役割が異なる。

すべてを1つのDTOで処理しようとするのではなく、処理の境界ごとにDTOを分けることで、それぞれのクラスが何のためのデータなのか明確になることが分かった。

---

## 2. `sourceIndex`を使って生成元問題とAI生成問題を対応付ける

`TemporaryGeneratedQuestionDto`から`AiGeneratedQuestionDto`へ変換する際には、

```java
Question sourceQuestion =
        sourceQuestions.get(
                temporaryGeneratedQuestionDto.getSourceIndex());
```

として、AIから返された`sourceIndex`を使って生成元の`Question`を取得する。

例えば、

```text
sourceIndex = 0
```

であれば、

```java
sourceQuestions.get(0)
```

を取得する。

これによって、AIが生成した問題と、その問題の生成元になった`Question`を対応付けることができる。

### 工夫した点

AIへ生成元問題を送る段階で`sourceIndex`を付けておき、その値を生成結果にも返させる構造にした。

そのため、AIが生成した問題文そのものを使って元問題を検索する必要がない。

```text
sourceQuestions
0 → 問題A
1 → 問題B
2 → 問題C

        ↓ AIへ送信

生成結果
sourceIndex=2 → 問題Cを元に生成
sourceIndex=0 → 問題Aを元に生成
```

のように、返却順が元のListと一致していることを前提にせず、`sourceIndex`から対応する問題を取得できる。

### 勉強になった点

最初は、

```java
for (int i = 0; i < temporaryGeneratedQuestionDtos.size(); i++)
```

の`i`と、

```java
sourceQuestions
```

のIndexをそのまま対応させればよいようにも見える。

しかし、実際に生成元を特定するために使用しているのは、

```java
temporaryGeneratedQuestionDto.getSourceIndex()
```

である。

つまり、

```java
i
```

は「生成された問題を順番に処理するためのIndex」であり、

```java
sourceIndex
```

は「どの生成元問題に対応しているのかを示すIndex」である。

同じIndexという言葉でも役割が異なることを整理できた。

---

## 3. 生成元QuestionとAI生成結果から`AiGeneratedQuestionDto`を作成

生成元の`Question`を取得した後、

```java
AiGeneratedQuestionDto generatedQuestion =
        new AiGeneratedQuestionDto();
```

として画面表示用DTOを作成した。

```java
generatedQuestion.setSourceQuestionId(
        sourceQuestion.getQuestionId());

generatedQuestion.setJapaneseText(
        temporaryGeneratedQuestionDto.getJapaneseText());

generatedQuestion.setChineseText(
        temporaryGeneratedQuestionDto.getChineseText());

generatedQuestion.setPinyin(
        temporaryGeneratedQuestionDto.getPinyin());

generatedQuestion.setZhuyin(
        temporaryGeneratedQuestionDto.getZhuyin());

generatedQuestion.setDifficulty(
        sourceQuestion.getDifficulty());
```

として、生成元問題とAI生成結果の両方から必要な情報を取得する。

### 気づいた点

今回のDTO変換では、データの取得元が明確に2つに分かれている。

```text
生成元Question
├─ questionId
└─ difficulty

AI生成結果
├─ japaneseText
├─ chineseText
├─ pinyin
└─ zhuyin
```

AIには生成させる必要のない情報まで生成させず、DBにすでに存在する情報は元の`Question`から引き継ぐ形になっている。

例えば難易度はAIに判定させるのではなく、生成元問題の、

```java
sourceQuestion.getDifficulty()
```

をそのまま使用する。

AIに任せる部分と、アプリケーション側で確定できる部分を分けることが重要だと分かった。

---

## 4. AI生成問題をSessionへ保存して問題画面へ渡す

AI生成が完了すると、

```java
List<AiGeneratedQuestionDto> aiPracticeQuestions;
```

として問題セットを受け取り、

```java
session.setAttribute(
        "aiPracticeQuestions",
        aiPracticeQuestions);

session.setAttribute(
        "aiPracticeQuestionsCurrentPage",
        0);
```

としてSessionへ保存する。

その後、

```java
return "redirect:/ai-practice/question?page=0";
```

として問題画面へ移動する。

### 工夫した点

通常学習モードや復習モードと同様に、問題セットそのものと現在ページをSessionへ保存する構造にした。

```text
aiPracticeQuestions
→ AI生成問題セット

aiPracticeQuestionsCurrentPage
→ 現在表示している問題番号
```

をSessionに保持することで、Controllerへのリクエストが変わっても同じ問題セットを継続して使用できる。

### 勉強になった点

AI APIから取得した問題は、その場でHTMLへ渡して終わりではなく、

```text
AI API
↓
Service
↓
Controller
↓
Session
↓
問題画面
```

という流れでアプリケーション内に保持される。

今回の実装によって、APIから取得した外部データを既存のWebアプリケーションの画面遷移へ組み込む流れを確認できた。

---

## 5. AI生成問題の前後移動・中断・再開を実装

AI問題生成モードでも通常学習モードや復習モードと同様に、

- 前の問題
- 次の問題
- 完了
- 中断
- 再開
- 終了

を実装した。

問題画面へアクセスすると、

```java
List<AiGeneratedQuestionDto> questions =
        (List<AiGeneratedQuestionDto>) session.getAttribute(
                "aiPracticeQuestions");
```

として問題セットを取得し、

```java
AiGeneratedQuestionDto question =
        questions.get(page);
```

として現在の問題を取得する。

また、

```java
session.setAttribute(
        "aiPracticeQuestionsCurrentPage",
        page);
```

によって現在ページをSessionへ保存する。

### 工夫した点

すでに通常学習モードや復習モードで実装していたSessionを使った画面遷移の仕組みを、AI問題生成モードにも適用した。

AIによって問題を作成する部分は新しい処理だが、問題生成後の、

```text
問題セットをSessionへ保存
↓
pageで1問ずつ表示
↓
現在ページをSessionへ保存
↓
中断時はSessionを残す
↓
再開時に現在ページへ戻る
```

という仕組みは既存機能とほぼ同じにできた。

### 気づいた点

新しい機能だからといって、すべてを新しく実装する必要はない。

AI問題生成モード特有なのは主に、

```text
問題セットをどのように作るか
```

という部分であり、問題セットが、

```java
List<AiGeneratedQuestionDto>
```

として完成した後は、既存の学習モードと似た構造で扱える。

既存機能を参考にすると、新機能でも共通化できる部分と固有部分を分けやすいことが分かった。

---

## 6. AI生成問題では理解度・お気に入りをまだ使用できない

通常学習モードでは、表示している`Question`に対して理解度やお気に入りを登録できる。

しかし、今回表示している、

```java
AiGeneratedQuestionDto
```

はAIが生成した直後の問題であり、まだ`question`テーブルには存在しない。

そのため、お気に入り判定部分は現時点ではコメントアウトした。

```java
//    // お気に入り判定
//    if (loginUser != null) {
//        boolean isFavorite = favoriteService.isFavorite(
//                getLoginUser(loginUser),
//                question.sourceQuestionId()
//        );
//
//        model.addAttribute("isFavorite", isFavorite);
//    }
```

### 気づいた点

理解度やお気に入りは、単純に中国語文などの文字列へ紐付いているわけではなく、DBに登録された`Question`のIDに依存している。

今回のAI生成問題は、

```text
AIが生成
↓
AiGeneratedQuestionDtoとして存在
↓
まだQuestionではない
↓
questionIdを持つ保存済み問題ではない
```

という状態である。

そのため、通常学習モードの機能をそのまま流用することはできない。

AI生成問題を`question`テーブルへ保存する機能を実装して初めて、その問題に対して理解度やお気に入りを紐付けられるようになる。

画面上では同じ「問題」に見えても、DB上に永続化されているかどうかによって利用できる機能が変わることを改めて確認できた。

---

## 7. `QuestionModelUtil`にAI生成問題用の処理を追加

AI生成問題画面でも、

- 現在の問題
- 問題番号
- 前の問題が存在するか
- 次の問題が存在するか
- 表示する発音記号

などをModelへ渡す必要がある。

そこで、

```java
public void setAiQuestionModel(
        Model model,
        List<AiGeneratedQuestionDto> questions,
        int page,
        HttpSession session)
```

を追加した。

発音記号については、

```java
PronunciationType pronunciationType =
        (PronunciationType) session.getAttribute(
                "pronunciationType");
```

からユーザー設定を取得し、

```java
switch (pronunciationType)
```

によって拼音・注音・非表示を切り替える。

### 工夫した点

発音記号の表示についても既存のユーザー設定をそのまま利用した。

AIが生成した問題だから別の表示設定を作るのではなく、

```text
PINYIN
→ question.getPinyin()

ZHUYIN
→ question.getZhuyin()

NONE
→ null
```

として、通常問題と同じユーザー設定に従うようにした。

### 気づいた点

AI生成機能を追加しても、言語設定や発音記号設定など、すでにアプリケーション全体で存在しているユーザー設定はそのまま再利用できる。

AI機能だけを独立した機能として考えるのではなく、既存アプリケーションの一部として組み込む必要があることを確認できた。

---

## 8. `OpenAIClient`をSpring Beanとして登録

前チャプターでは`AiPracticeService`に、

```java
private final OpenAIClient openAIClient;
```

を定義した。

`@RequiredArgsConstructor`によってコンストラクタインジェクションの対象にはなっていたが、この時点では`OpenAIClient`自体をSpring Beanとして登録していなかった。

そこで、

```java
@Configuration
public class OpenAiConfig {

    @Bean
    OpenAIClient openAIClient() {
        return OpenAIOkHttpClient.fromEnv();
    }
}
```

を追加した。

### 苦戦した点

前チャプターでは、

```java
private final OpenAIClient openAIClient;
```

と書いて`@RequiredArgsConstructor`を使用しているため、DIできるようなコードにはなっていた。

しかし、実際にSpring Bootを起動するには、

```text
注入される側
AiPracticeService
    ↑
OpenAIClientが必要

注入する側
OpenAIClient Bean
```

の両方が必要になる。

`private final OpenAIClient openAIClient;`を書くだけでは、Springが`OpenAIClient`を自動的に作ってくれるわけではないことを実際のAPI接続準備で確認した。

### 勉強になった点

現在使用しているOpenAI Java SDKでは、

```java
@Bean
OpenAIClient openAIClient() {
    return OpenAIOkHttpClient.fromEnv();
}
```

としてアプリケーション側で`OpenAIClient`をBean登録する構成にした。

これによって、

```text
OpenAiConfig
↓
OpenAIClient Beanを生成
↓
Spring Container
↓
AiPracticeServiceへDI
```

という流れになる。

これまで自分で作成したServiceやRepositoryをDIすることは多かったが、外部SDKのClientもBeanとしてSpringに管理させることができることを確認できた。

---

## 9. APIキーを環境変数から取得する

OpenAI APIを実際に使用するため、APIキーを作成し、Eclipseの実行構成から、

```text
OPENAI_API_KEY
```

という環境変数へ設定した。

```java
OpenAIOkHttpClient.fromEnv();
```

は、この環境変数を利用してClientを生成する。

### 勉強になった点

`OPENAI_API_KEY`という名前は今回のアプリケーションで独自に決めたものではなく、SDK側が参照する環境変数名として決められている。

そのため、

```java
System.getenv("OPENAI_API_KEY")
```

などを自分で書いてAPIキーを取得し、それを1つずつClientへ渡す必要はない。

SDKが想定している環境変数を設定しておけば、

```java
OpenAIOkHttpClient.fromEnv()
```

からClientを生成できる。

### 工夫した点

APIキーを、

```java
String apiKey = "..."
```

のようにJavaソースコードへ直接記述せず、Eclipseの実行環境に設定した。

これにより、APIキーをソースコードやGitの管理対象へ含めずにAPIを利用できる。

---

## 10. ChatGPTしか使用しなくてもGemini ClientのBeanが必要になった

OpenAI側の設定を完了してSpring Bootを起動したところ、

```text
required a bean of type 'com.google.genai.Client'
```

というエラーが発生した。

現時点では、

```java
boolean useChatGPT = true;
```

としているため、実行時にはGemini APIを呼び出さない。

しかし、`AiPracticeService`には、

```java
private final Client geminiClient;
```

も存在する。

さらに`@RequiredArgsConstructor`を使用しているため、`geminiClient`もコンストラクタの引数になる。

### 苦戦した点

最初は、

```java
boolean useChatGPT = true;
```

なのだからGeminiを使用しないため、Gemini Clientも必要ないように思えた。

しかし、

```java
useChatGPT = true
```

が評価されるのは`AiPracticeService`のメソッドを実行するときである。

その前にSpring Boot起動時には、

```text
AiPracticeServiceを生成
↓
コンストラクタを見る
↓
OpenAIClientが必要
↓
Gemini Clientも必要
↓
両方のBeanを探す
```

という処理が行われる。

そのため、実行時のif文とは関係なくGemini ClientのBeanが必要になった。

### 勉強になった点

今回、

**「Springがクラスを生成できるか」と「実行時にどの処理を通るか」は別の問題**

であることが非常に分かりやすい形で確認できた。

```java
if (useChatGPT) {
    generateQuestionsWithChatGPT(...);
} else {
    generateQuestionsWithGemini(...);
}
```

はメソッド実行時の分岐である。

一方、

```java
private final OpenAIClient openAIClient;
private final Client geminiClient;
```

は`AiPracticeService`を生成する時点で必要になる依存関係である。

```text
Spring Boot起動
↓
AiPracticeServiceのBeanを作る
↓
コンストラクタに必要なBeanを全部解決する
↓
AiPracticeService完成
↓
その後にメソッドが呼ばれる
↓
useChatGPTによって処理を分岐
```

という順序を理解できた。

---

## 11. Gemini ClientもSpring Beanとして登録

`AiPracticeService`を生成できるようにするため、Gemini Clientについても、

```java
@Configuration
public class GeminiConfig {

    @Bean
    Client geminiClient() {
        return new Client();
    }
}
```

としてBean登録した。

Googleの公式リファレンスでも、

```java
Client client = new Client();
```

としてClientを作成しているため、それをSpringの`@Bean`として登録する形にした。

### 勉強になった点

外部SDKの公式リファレンスでは、

```java
Client client = new Client();
```

のような通常のJavaアプリケーションとしてのサンプルが掲載されていても、Spring Bootで使用する場合には、

```java
@Bean
Client geminiClient() {
    return new Client();
}
```

としてSpring Containerに管理させることができる。

つまり、

```text
公式サンプル
Client client = new Client();

        ↓ Spring Bootで使用

@Bean
Client geminiClient() {
    return new Client();
}
```

のように、SDKそのものの使い方とSpringのDIを組み合わせられることが分かった。

---

## 12. Geminiを使わなくてもAPIキーが必要になった

Gemini ClientをBean登録した後、再び起動すると、

```text
API key must either be provided or set in the environment variable
GOOGLE_API_KEY or GEMINI_API_KEY.
```

というエラーが発生した。

原因は、

```java
@Bean
Client geminiClient() {
    return new Client();
}
```

によってSpring Boot起動時に`new Client()`が実行され、その時点でGemini SDKがAPIキーを探すためだった。

処理の流れは、

```text
Spring Boot起動
↓
geminiClient Beanを生成
↓
new Client()
↓
GOOGLE_API_KEY または GEMINI_API_KEYを探す
↓
APIキーが存在しない
↓
Clientを生成できない
↓
geminiClient Bean生成失敗
↓
AiPracticeServiceを生成できない
↓
Spring Boot起動失敗
```

となる。

### 苦戦した点

ChatGPTしか使用しない設定にしているにもかかわらず、最終的にはGemini APIキーまで必要になったため、最初は理由が分かりにくかった。

しかし、実際にGemini APIを呼び出すからAPIキーが必要なのではなく、

```java
new Client()
```

を実行して**Gemini Clientそのものを生成するためにAPIキーが必要**だった。

### 気づいた点

今回の構造では、

```java
private final Client geminiClient;
```

を`AiPracticeService`の依存関係として宣言した時点で、

```text
Geminiを実際に使うか
```

とは別に、

```text
AiPracticeServiceを作るためにGemini Clientを作れる状態にする
```

必要がある。

外部APIのClientクラスをDI対象として持たせる場合、そのClientが**いつ初期化され、その初期化時に何を要求するのか**も考える必要があることが分かった。

---

## 13. 実際にAI APIから問題を生成して画面へ表示

OpenAIとGeminiのClient BeanおよびAPIキーを設定した後、実際にAI問題生成モードを実行した。

今回は、

```text
言語：國語
難易度：初級
```

で9件の生成元問題を取得した。

実際に生成された問題を確認すると、

```text
生成元：
這部電影很好看。
この映画はとても面白いです。

生成後：
這部電影很感人。
この映画はとても感動的です。
```

や、

```text
生成元：
請問，洗手間在哪裡？
すみません、お手洗いはどこですか？

生成後：
請問，服務台在哪裡？
すみません、サービスカウンターはどこですか？
```

のように、元の文法構造を維持しながら内容が変更されていた。

また、

```text
生成元：
我把手機忘在家裡了。
携帯電話を家に忘れました。

生成後：
我媽把鑰匙忘在車上了。
母は鍵を車の中に忘れました。
```

では、`把`構文を維持しながら、

```text
我 → 我媽
手機 → 鑰匙
家裡 → 車上
```

のように内容が変更されている。

### 気づいた点

ここまで設計してきた、

```text
生成元問題を取得
↓
AI用DTOへ変換
↓
プロンプト・JSONを作成
↓
APIへ送信
↓
Structured Outputsで取得
↓
画面表示用DTOへ変換
↓
Sessionへ保存
↓
HTMLへ表示
```

という一連の処理が、初めて実際のAI APIを通して最後まで動作した。

特に、生成結果を見ることで、これまで実装してきた`template`や生成ルールが単なるデータではなく、実際にAIの生成内容を制御するために機能していることを確認できた。

---

## 14. このチャプター全体を通して

### 苦戦した点

今回最も苦戦したのは、APIを実際に動かしたことで初めて発生したSpringのDI周辺の問題だった。

コード上では、

```java
private final OpenAIClient openAIClient;
private final Client geminiClient;
```

と書くことができ、Eclipse上でもServiceの実装自体は進められていた。

しかし、実際にSpring Bootを起動すると、

```text
OpenAIClientのBeanがない
↓
Beanを追加

Gemini ClientのBeanがない
↓
Beanを追加

Gemini APIキーがない
↓
APIキーを追加
```

と、依存関係が順番に問題として現れた。

APIを呼び出すコードが正しく書けていることと、Spring Bootアプリケーションとしてそのコードを実行できることは別であり、DIされるオブジェクトの生成まで含めて考える必要があった。

### 勉強になった点

今回の実装で、SpringのDIについてこれまでより具体的に理解できた。

特に、

```java
@RequiredArgsConstructor
```

があるからDIできるのではなく、

```text
@RequiredArgsConstructor
↓
必要な依存関係を受け取るコンストラクタを作る

@Bean
↓
そのコンストラクタへ渡せる実際のオブジェクトを
Spring Containerへ登録する
```

という役割の違いが分かった。

また、

```java
boolean useChatGPT = true;
```

のような実行時の分岐はBean生成には影響しない。

この経験によって、

```text
Bean定義
↓
依存関係解決
↓
Service生成
↓
アプリケーション起動
↓
メソッド実行
↓
if文による処理分岐
```

というSpring Bootの処理順序を意識できるようになった。

### 気づいた点

前チャプターまではAPI連携を、

```text
リクエストを作る
↓
APIへ送る
↓
Responseを受け取る
```

というコード上の処理として考えていた。

しかし、実際に動かすためには、

```text
SDKの依存関係
Client
Spring Bean
DI
APIキー
環境変数
課金設定
リクエスト
Response
DTO変換
Session
Controller
HTML
```

までがつながって初めて1つの機能として成立する。

外部API連携はServiceクラス内の数行だけで完結するものではなく、アプリケーションの設定や実行環境まで含めた実装になることを学んだ。

### 所感

今回は、AI問題生成モードを設計し始めてから初めて、

```text
DB
↓
Java
↓
AI API
↓
Java
↓
Spring MVC
↓
ブラウザ
```

という一連の流れが実際につながった。

特に、実際に自分のDBに登録していた中国語問題から、

```text
這部電影很好看。
```

が、

```text
這部電影很感人。
```

へ変わったり、

```text
我把手機忘在家裡了。
```

が、

```text
我媽把鑰匙忘在車上了。
```

へ変わった状態で自分のアプリケーションの問題画面に表示されたことで、これまで個別に実装してきたRepository、DTO、ObjectMapper、プロンプト、API、Session、Controller、Thymeleafがすべて1つの処理としてつながったことを確認できた。

また、今回は実際にAPIを動かしたことで、コードを書いているだけでは気づかなかったBeanやAPIキーの問題も発生した。

エラーを順番に確認することで、

```text
なぜOpenAIClientのBeanが必要なのか
なぜGeminiを使わないのにGemini ClientのBeanが必要なのか
なぜGemini APIを呼んでいないのにAPIキーが必要なのか
```

まで処理の流れを追って理解できた。

API連携そのものだけでなく、Spring Boot上で外部サービスを実際に動かすために必要な仕組みまで学ぶことができたチャプターになった。