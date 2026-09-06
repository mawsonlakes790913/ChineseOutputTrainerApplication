# 017 AI問題生成モードの実装その3 AI生成した問題セットを取得する②API連携

## 1. ChatGPTのAPIリクエスト作成

前チャプターで作成した`input`をChatGPTへ送信するため、OpenAI Java SDKを使用してAPIリクエストの設定を作成した。

当初は、

```java
ResponseCreateParams params =
        ResponseCreateParams.builder()
                .model(ChatModel.GPT_5_2)
                .input(input)
                .build();
```

として通常の`ResponseCreateParams`を使用する構想だった。

しかし、今回AIから受け取りたいのは単なる文章ではなく、

```java
TemporaryGeneratedQuestionListDto
```

の構造に対応した生成結果である。

そのため、Structured Outputsを利用し、

```java
StructuredResponseCreateParams<TemporaryGeneratedQuestionListDto> params =
        ResponseCreateParams.builder()
                .model(ChatModel.GPT_5_2)
                .input(input)
                .text(TemporaryGeneratedQuestionListDto.class)
                .build();
```

へ変更した。

ここでは、

```java
.model(ChatModel.GPT_5_2)
```

で使用するモデル、

```java
.input(input)
```

でAIへ送る入力、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

でAIから受け取りたい出力構造を指定している。

### 工夫した点

プロンプトだけで「JSON形式で返してください」と指示するのではなく、OpenAI Java SDKのStructured Outputsを利用することにした。

今回必要な生成結果は、

```text
TemporaryGeneratedQuestionListDto
│
└─ questions
   │
   ├─ TemporaryGeneratedQuestionDto
   │  ├─ sourceIndex
   │  ├─ japaneseText
   │  ├─ chineseText
   │  ├─ pinyin
   │  └─ zhuyin
   │
   └─ ...
```

という決められた構造である。

そこで、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

を指定し、AIの回答をJava側で必要としている構造に対応させるようにした。

### 苦戦した点

最初は、

```java
ResponseCreateParams
```

と、

```java
StructuredResponseCreateParams
```

の違いが分かりにくかった。

どちらもResponses APIへ送るリクエスト設定を表すが、今回のようにAIの回答を特定のJavaクラスに対応した構造で取得したい場合は、Structured Outputsを利用する必要がある。

また、

```java
ResponseCreateParams.builder()
```

という書き方についても、`ResponseCreateParams`のインスタンスを先に作成しているわけではなく、`ResponseCreateParams`クラスに用意された`static`な`builder()`を呼び出していることを確認した。

### 勉強になった点

Builderは、複数の設定を持つオブジェクトを、

```java
ResponseCreateParams.builder()
        .model(...)
        .input(...)
        .text(...)
        .build();
```

のように段階的に組み立てるための仕組みである。

また、この時点で作成している`params`は、

```text
どのモデルを使用するか
何を入力するか
どの構造で回答してほしいか
```

という**APIへ送信するための設定を保持したオブジェクト**である。

したがって、

```java
StructuredResponseCreateParams<TemporaryGeneratedQuestionListDto> params
```

を作成しただけでは、まだAPI通信は発生していない。

---

## 2. `TemporaryGeneratedQuestionListDto`で複数問題をラップした理由

Structured Outputsでは、

```java
.text(○○.class)
```

として、AIから受け取りたい構造に対応するJavaクラスを指定する。

1問だけを受け取るのであれば、

```java
.text(TemporaryGeneratedQuestionDto.class)
```

と指定できる。

しかし、今回は最大50問の、

```java
List<TemporaryGeneratedQuestionDto>
```

を受け取る必要がある。

Javaでは、

```java
List<TemporaryGeneratedQuestionDto>.class
```

とは記述できないため、

```java
@Data
public class TemporaryGeneratedQuestionListDto {

    private List<TemporaryGeneratedQuestionDto> questions;
}
```

というラッパーとなるDTOを作成し、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

として指定した。

### 工夫した点

複数の`TemporaryGeneratedQuestionDto`を直接Structured Outputsへ指定しようとせず、

```text
TemporaryGeneratedQuestionListDto
└─ List<TemporaryGeneratedQuestionDto>
```

という構造にした。

これにより、通常のJavaクラスである、

```java
TemporaryGeneratedQuestionListDto.class
```

をStructured Outputsへ渡せるようになった。

### 勉強になった点

Javaのジェネリクスでは、

```java
TemporaryGeneratedQuestionDto.class
```

は取得できるが、

```java
List<TemporaryGeneratedQuestionDto>.class
```

というクラス情報は取得できない。

そのため、外部ライブラリへ`.class`として型情報を渡す必要がある場合に、Listを専用クラスで包む方法があることを学んだ。

今回`TemporaryGeneratedQuestionListDto`を前チャプターで作成した理由が、実際のStructured Outputsの実装まで進んだことで明確になった。

---

## 3. `OpenAIClient`を使用したAPIリクエストの送信

作成した`params`を実際にOpenAI APIへ送信するため、

```java
StructuredResponse<TemporaryGeneratedQuestionListDto> response =
        openAIClient.responses().create(params);
```

を実装した。

`params`が「APIへ何を送るか」を保持するのに対し、

```java
openAIClient
```

はJavaアプリケーションとOpenAI APIとの通信を担当する。

```java
.responses()
```

によってResponses APIを使用し、

```java
.create(params)
```

によって先ほど作成した`params`を送信する。

### 勉強になった点

今回、

```java
ResponseCreateParams
```

と、

```java
OpenAIClient
```

の役割の違いを整理できた。

```text
ResponseCreateParams
→ APIへ何を送るのかを保持する

OpenAIClient
→ 実際にOpenAI APIと通信する
```

という違いである。

つまり、

```text
paramsを作成
↓
まだ通信していない

openAIClient.responses().create(params)
↓
ここでAPI通信が行われる
```

という境界がある。

### 気づいた点

`AiPracticeService`には、

```java
private final OpenAIClient openAIClient;
```

を定義している。

`@RequiredArgsConstructor`によって、この`final`フィールドを受け取るコンストラクタは自動生成される。

しかし、フィールドを定義しただけで`OpenAIClient`そのものが生成されるわけではない。

Spring Boot起動時には、

```text
AiPracticeServiceを生成
↓
OpenAIClientが必要
↓
SpringがOpenAIClientのBeanを探す
↓
BeanがなければDIできない
```

となる。

Javaコード上でコンストラクタが成立することと、Springが実際にDIできることは別であり、最終的には`OpenAIClient`をSpring Beanとして登録する必要がある。

この設定については、次チャプターで実際にAPIへ接続する際に実装する。

---

## 4. ChatGPTのResponseからStructured Outputsを取得

OpenAI APIから返される、

```java
StructuredResponse<TemporaryGeneratedQuestionListDto>
```

には、AIが生成した問題だけが直接入っているわけではない。

そのため、

```java
TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto = null;

for (int i = 0; i < response.output().size(); i++) {

    var output = response.output().get(i);

    if (output.isMessage()) {

        var message = output.asMessage();

        for (int j = 0; j < message.content().size(); j++) {

            var content = message.content().get(j);

            if (content.isOutputText()) {

                temporaryGeneratedQuestionListDto =
                        content.asOutputText();

                break;
            }
        }
    }

    if (temporaryGeneratedQuestionListDto != null) {
        break;
    }
}
```

として、Responseの内部をたどってStructured Outputsの生成結果を取得した。

Responseは大まかに、

```text
response
│
└─ output
   │
   └─ Message
      │
      └─ content
         │
         └─ TemporaryGeneratedQuestionListDto
            │
            └─ questions
               ├─ TemporaryGeneratedQuestionDto
               ├─ TemporaryGeneratedQuestionDto
               └─ ...
```

という構造になっている。

### 苦戦した点

この二重ループを最初に見たとき、

```text
最大50問のTemporaryGeneratedQuestionDtoを
二重ループで処理している
```

ように見えた。

しかし、実際に走査しているのは生成された問題ではなく、OpenAI APIのResponse構造である。

外側では、

```java
response.output()
```

から`Message`を探し、内側では、

```java
message.content()
```

から`OutputText`を探している。

### 勉強になった点

この二重ループは、

```text
response
↓
outputを探す
↓
Message
↓
contentを探す
↓
TemporaryGeneratedQuestionListDto
```

という処理である。

つまり、

**50問を走査しているのではなく、「50問が入った箱」をResponseの中から探している**

と考えると理解しやすかった。

実際に1問ずつ処理するのは、この後、

```java
temporaryGeneratedQuestionListDto.getQuestions();
```

によって`List<TemporaryGeneratedQuestionDto>`を取り出してからになる。

### 気づいた点

APIから返されるResponseは、単純に「AIが生成した文字列」だけを表しているとは限らない。

Response全体には生成結果以外の情報も含まれるため、使用するSDKのResponse構造を理解して、必要な部分を取り出す必要がある。

---

## 5. GeminiのStructured Outputs用Schemaの作成

GeminiでもChatGPTと同様にStructured Outputsを利用するが、今回使用した方法では出力形式の指定方法が大きく異なった。

ChatGPTでは、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

としてJavaクラスを指定すれば、SDK側でそのクラスの内部構造をたどって出力形式を設定できる。

一方、Geminiでは、

```java
Schema
```

を使用して、どのようなJSONを生成させるのかを明示的に定義した。

まず、1問分の構造として、

```java
Schema questionSchema =
        Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "sourceIndex",
                        Schema.builder()
                                .type(Type.Known.INTEGER)
                                .build(),
                        "japaneseText",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "chineseText",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "pinyin",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .build(),
                        "zhuyin",
                        Schema.builder()
                                .type(Type.Known.STRING)
                                .build()
                ))
                .required(List.of(
                        "sourceIndex",
                        "japaneseText",
                        "chineseText",
                        "pinyin",
                        "zhuyin"))
                .build();
```

を作成した。

### 苦戦した点

GeminiではChatGPTのようにDTOクラスをそのまま指定するのではなく、

```java
Schema.builder()
.type(...)
.properties(...)
.required(...)
```

を使ってJSONの構造を自分で定義する必要があり、それぞれのメソッドが何を意味しているのか理解するのに時間がかかった。

特にJSONの型をどのようにJavaコード上で指定するのかが分かりにくかったため、Googleの公式リファレンスを参照した。

そこで、

```java
Type.Known.OBJECT
Type.Known.ARRAY
Type.Known.INTEGER
Type.Known.STRING
```

など、SDK側にJSONの型を表す値が用意されていることを確認した。

### 勉強になった点

```java
.type(Type.Known.OBJECT)
```

は、そのSchemaがJSONオブジェクトを表すことを指定する。

```java
.properties(...)
```

では、そのオブジェクトがどのプロパティを持つかを指定する。

例えば、

```java
"sourceIndex",
Schema.builder()
        .type(Type.Known.INTEGER)
        .build()
```

は、

```json
{
  "sourceIndex": 0
}
```

の`sourceIndex`が整数であることを定義している。

そのため、

```text
questionSchema
│
├─ type = OBJECT
│
└─ properties
   ├─ sourceIndex  → INTEGER
   ├─ japaneseText → STRING
   ├─ chineseText  → STRING
   ├─ pinyin       → STRING
   └─ zhuyin       → STRING
```

という構造になる。

また、

```java
.required(...)
```

は、指定したプロパティそのものを生成結果に必須とする設定であることも確認した。

---

## 6. Geminiのレスポンス全体のSchemaの作成

1問分の`questionSchema`を作成した後、それを複数格納するレスポンス全体の構造として、

```java
Schema responseSchema =
        Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "questions",
                        Schema.builder()
                                .type(Type.Known.ARRAY)
                                .items(questionSchema)
                                .build()
                ))
                .required(List.of("questions"))
                .build();
```

を作成した。

これは、

```json
{
  "questions": [
    {
      "sourceIndex": 0,
      "japaneseText": "...",
      "chineseText": "...",
      "pinyin": "...",
      "zhuyin": "..."
    }
  ]
}
```

という外側の構造を定義している。

### 勉強になった点

```java
.type(Type.Known.ARRAY)
```

によって`questions`が配列であることを指定し、

```java
.items(questionSchema)
```

によって、

**その配列に入る1要素の構造は`questionSchema`である**

と指定できる。

つまり、

```text
responseSchema
│
└─ questions : ARRAY
   │
   ├─ questionSchema
   ├─ questionSchema
   └─ ...
```

という構造になる。

1問分のSchemaと、それを格納する外側のSchemaを別々に定義することで、JSONの入れ子構造をJavaコード上でも段階的に表現できることを学んだ。

---

## 7. `GenerateContentConfig`によるGeminiの生成設定

作成した`responseSchema`をGeminiの生成処理で使用するため、

```java
GenerateContentConfig config =
        GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();
```

を作成した。

`GenerateContentConfig`は、Geminiにどのような条件で回答を生成させるのかを保持する設定である。

### 勉強になった点

```java
.responseMimeType("application/json")
```

によって、回答をJSONとして生成するよう指定する。

ここで使用するMIMEタイプは、データが何の種類なのかを表す識別子で、

```text
text/plain       → 通常のテキスト
text/html        → HTML
image/png        → PNG画像
application/json → JSON
```

のように使用される。

さらに、

```java
.responseSchema(responseSchema)
```

によって、生成するJSONを先ほど作成した`responseSchema`に従わせる。

そのため、

```text
input
→ 何を生成してほしいか

GenerateContentConfig
→ どのような形式・設定で生成してほしいか
```

という役割の違いがある。

### 気づいた点

ChatGPTでは、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

という1つの指定でStructured Outputsの出力構造を設定できた。

Geminiでは同じ目的のために、

```text
questionSchema
↓
responseSchema
↓
GenerateContentConfig
```

と段階的に設定する。

同じStructured Outputsでも、SDKによってその設定方法がかなり異なることが分かった。

---

## 8. Gemini APIへのリクエスト送信

作成した`input`と`config`を使用して、

```java
GenerateContentResponse response =
        geminiClient.models.generateContent(
                "gemini-3.7-flash",
                input,
                config);
```

としてGemini APIへリクエストを送信した。

ここでは、

```text
"gemini-3.7-flash"
→ 使用するモデル

input
→ 何を生成してほしいか

config
→ どのような設定で生成してほしいか
```

という3つを渡している。

### 勉強になった点

`geminiClient`はJavaアプリケーションからGemini APIを利用するためのクライアントであり、

```java
.models
```

を通してGeminiのモデルに対する処理を行う。

そのうえで、

```java
.generateContent(...)
```

を呼び出すことで、指定したモデルへコンテンツ生成を依頼する。

ChatGPTとは使用するクラスやメソッド名が異なるものの、

```text
Client
↓
使用するAPI・モデルの機能
↓
リクエスト送信
↓
Response
```

という大きな流れは共通していることが分かった。

---

## 9. GeminiのResponseをJSONからDTOへ変換

Gemini APIから取得した、

```java
GenerateContentResponse response
```

から、

```java
String responseJson =
        response.text();
```

として生成された回答部分を取得した。

今回はJSON形式で回答するよう設定しているため、`responseJson`にはStructured Outputsに従ったJSON文字列が入る。

ただし、この段階ではまだ、

```java
TemporaryGeneratedQuestionListDto
```

ではない。

そこで、

```java
temporaryGeneratedQuestionListDto =
        objectMapper.readValue(
                responseJson,
                TemporaryGeneratedQuestionListDto.class);
```

としてDTOへ変換した。

### 勉強になった点

前チャプターでは、

```java
objectMapper.writeValueAsString(generationSources);
```

を使用して、

```text
Javaオブジェクト
↓
JSON文字列
```

へ変換した。

今回は、

```java
objectMapper.readValue(
        responseJson,
        TemporaryGeneratedQuestionListDto.class);
```

なので、その逆方向になる。

```text
JSON文字列
↓
ObjectMapper
↓
Javaオブジェクト
```

である。

これによって、`ObjectMapper`はJSONを出力するだけではなく、

```text
Java → JSON
JSON → Java
```

の両方向の変換に使用できることを実際のAPI連携の中で確認できた。

### 気づいた点

Geminiでは、

```java
response.text()
```

で取得した時点では単なるJSON形式の`String`である。

そのため、

```text
Gemini
↓
GenerateContentResponse
↓
JSON形式のString
↓
ObjectMapper
↓
TemporaryGeneratedQuestionListDto
```

という変換が必要になる。

ChatGPTではSDKから最終的にDTOとして取得するのに対し、GeminiではJSONを一度Java側で変換するという違いがある。

---

## 10. ChatGPTとGeminiの処理を同じDTOへ収束させる

ChatGPT版とGemini版ではAPI連携の方法が大きく異なるが、どちらのメソッドも最終的には、

```java
TemporaryGeneratedQuestionListDto
```

を返すようにした。

現時点では、

```java
boolean useChatGPT = true;

TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto;

if (useChatGPT) {

    temporaryGeneratedQuestionListDto =
            generateQuestionsWithChatGPT(
                    input,
                    locale);

} else {

    temporaryGeneratedQuestionListDto =
            generateQuestionsWithGemini(
                    input,
                    locale);
}
```

として一時的に使用するAIを切り替える。

### 工夫した点

ChatGPTとGeminiそれぞれのAPI固有の処理を、

```java
generateQuestionsWithChatGPT()
```

と、

```java
generateQuestionsWithGemini()
```

へ分離した。

一方、どちらも戻り値を、

```java
TemporaryGeneratedQuestionListDto
```

に統一した。

そのため、

```text
ChatGPT固有処理 ─┐
                 ├→ TemporaryGeneratedQuestionListDto
Gemini固有処理 ──┘
                           ↓
                     共通処理
```

という構造にできる。

### 気づいた点

外部APIごとにリクエストやレスポンスの形式が違っていても、アプリケーション内部で使用する形式まで同じにしてしまえば、それ以降の処理を共通化できる。

今回の場合、次の処理では、

```java
temporaryGeneratedQuestionListDto.getQuestions();
```

として生成された問題を取り出すため、ChatGPTから生成したのかGeminiから生成したのかを意識する必要がなくなる。

---

## 11. ChatGPTとGeminiのAPI連携を比較して分かったこと

今回初めて2種類の生成AI APIを実装し、同じStructured Outputsを利用する場合でもSDKによって実装方法が大きく異なることを確認した。

大まかな違いは次のようになった。

| 処理 | ChatGPT | Gemini |
|---|---|---|
| リクエスト設定 | `StructuredResponseCreateParams` | `GenerateContentConfig`など |
| API接続 | `OpenAIClient` | Gemini用のClient |
| API呼び出し | `responses().create()` | `generateContent()` |
| Structured Outputsの指定 | Javaクラスから設定 | `Schema`を明示的に定義 |
| レスポンス | `StructuredResponse` | `GenerateContentResponse` |
| 生成結果の取得 | `output → Message → content`をたどる | `response.text()`でJSONを取得 |
| DTOへの変換 | SDKからDTOとして取得 | `ObjectMapper`でJSONからDTOへ変換 |

### 気づいた点・勉強になった点

ChatGPTはStructured Outputsの**出力形式を設定する部分が非常に簡潔**だった。

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

とDTOクラスを指定することで、SDKがその内部構造まで確認して出力形式を設定してくれる。

一方、レスポンスから実際のDTOを取得するときは、

```text
StructuredResponse
↓
output
↓
Message
↓
content
↓
TemporaryGeneratedQuestionListDto
```

とResponseの構造をたどる必要があった。

Geminiはその逆で、Structured Outputsを設定する際には、

```text
questionSchema
↓
responseSchema
↓
GenerateContentConfig
```

とJSONの構造を細かく定義する必要があった。

一方、レスポンス取得後は、

```text
response
↓
String
↓
ObjectMapper
↓
TemporaryGeneratedQuestionListDto
```

という比較的分かりやすい処理だった。

同じ目的のAPIでも、SDKの設計思想によって「どこが簡単で、どこを明示的に書く必要があるのか」が大きく異なることが興味深かった。

---

## 12. 公式リファレンスを参照しながら実装した

GeminiのStructured Outputsを実装する際には、Googleの公式リファレンスを参照した。

特に、

```java
Type.Known.OBJECT
Type.Known.ARRAY
Type.Known.INTEGER
Type.Known.STRING
```

など、Schema上でJSONの型をどのように表現するのかについて公式の実装例が参考になった。

### 勉強になった点

これまでは既知のSpring BootやJPAの機能を実装することが多かったが、今回のようなAPI連携では初めて使用するSDK固有のクラスやメソッドが多数登場する。

そのため、

```text
やりたい処理を確認
↓
公式リファレンスから該当するAPIを探す
↓
サンプルコードを確認
↓
使用されているクラス・メソッドを1つずつ調べる
↓
自分のアプリケーションへ適用する
```

という進め方が重要になると感じた。

公式リファレンスのコードをそのまま使用するだけではなく、そこで使用されているクラスやメソッドが何をしているのかを確認することで、API連携の流れそのものも理解しやすくなった。

---

## 13. このチャプター全体を通して

### 苦戦した点

今回はAPI連携という初めて本格的に扱う分野だったため、これまでの実装と比較してかなり難しかった。

特に、

- 初めて見るAPI・SDK固有の用語やクラスが多い
- APIへ送信するためのリクエスト形式が決められている
- APIから返されるResponseにもSDK固有の構造がある
- Structured Outputsの設定方法がChatGPTとGeminiで大きく異なる
- 同じ目的でも使用するクラスやメソッドがまったく異なる

という点で理解に時間がかかった。

単に、

```text
inputをAIへ送る
↓
回答が返ってくる
```

だけではなく、その前後にSDKごとの決められたデータ構造や設定が存在することを理解する必要があった。

### 気づいた点・勉強になった点

今回の実装を通して、API連携を、

```text
リクエスト設定を作る
↓
Clientを使ってAPIへ送る
↓
Responseを受け取る
↓
Responseから必要なデータを取り出す
↓
アプリケーションで使用する型へ変換する
```

という一連の処理として理解できるようになった。

ChatGPTとGeminiでは具体的な実装は異なるものの、大きな処理の流れは共通している。

また、SDKにはAPIをJavaから扱いやすくするため、

```java
StructuredResponseCreateParams
StructuredResponse
Schema
GenerateContentConfig
GenerateContentResponse
```

など、多数の専用クラスが用意されている。

これらはAPI提供側がJavaからサービスを利用しやすくするために設計したものであり、普段利用しているJavaライブラリの裏側にも、こうしたSDKを設計・実装する分野が存在することを実感した。

### 所感

今回は初めての本格的なAI API連携だったため、実装自体はかなり大変だった。

特にChatGPTとGeminiを両方実装したことで、同じ「生成AIへ入力を送り、構造化された回答を取得する」という目的でも、その実現方法が大きく異なることを実際のコードから確認できた。

ChatGPTでは、

```text
出力形式の指定 → 簡潔
ResponseからDTO取得 → 構造をたどる必要がある
```

のに対し、Geminiでは、

```text
出力形式の指定 → Schemaを細かく定義する必要がある
ResponseからDTO取得 → JSONをObjectMapperで変換できる
```

という違いがあった。

この違いがなぜSDK上でこのような形になっているのかまではまだ理解できていないが、同じ機能でもSDKによって設計や使い方が異なることを実感できた点は非常に興味深かった。

また、世の中に新しいサービスやAPIが登場すれば、それを各プログラミング言語から利用するための新しいSDK、クラス、メソッドも作られていく。

普段は用意されたクラスを使用する側だったが、今回API連携用の多数のクラスに触れたことで、それらのSDKを設計する分野や、そこに関わるエンジニアリングの奥深さも感じることができた。

実装中は公式リファレンスが非常に役立った。

記載されている内容やサンプルコードを1つずつ調べながら理解する必要はあったものの、今回の実装を通して、少なくとも「API連携についてまったく分からない」という状態から、リクエスト作成・送信・Response取得・DTOへの変換という基本的な流れを理解できる段階まで進むことができた。