# 017 AI問題生成モードの実装その3 AI生成した問題セットを取得する②API連携

前チャプターでは、AI問題生成の対象となる`sourceQuestions`を取得し、

- 共通プロンプト
- Language Profile
- 生成元問題のJSON

を組み合わせて、AI APIへ送信する`input`を作成した。

このチャプターでは、その`input`を実際にChatGPTまたはGeminiへ送信し、AIが生成した問題を`TemporaryGeneratedQuestionListDto`として取得する処理を実装する。

ChatGPTとGeminiのどちらも、

```text
APIリクエストを作成
        ↓
APIへリクエストを送信
        ↓
Responseを取得
        ↓
Responseから生成結果を取得
        ↓
TemporaryGeneratedQuestionListDto
```

という流れは共通している。

一方で、それぞれ異なるJava SDKを使用するため、リクエスト設定やレスポンスから生成結果を取得する方法は異なる。

---

## 1. OpenAI Java SDKの追加

JavaアプリケーションからOpenAI APIを利用するため、`pom.xml`へOpenAI Java SDKを追加した。

### `pom.xml`

```xml
<dependency>
    <groupId>com.openai</groupId>
    <artifactId>openai-java</artifactId>
    <version>4.54.0</version>
</dependency>
```

これにより、OpenAI APIとの連携に使用する、

```java
OpenAIClient
ResponseCreateParams
StructuredResponseCreateParams
StructuredResponse
```

などのクラスを利用できるようになる。

---

## 2. ChatGPTによる問題生成処理の実装

ChatGPTへ`input`を送信し、生成結果を`TemporaryGeneratedQuestionListDto`として取得する、

```java
generateQuestionsWithChatGPT()
```

を`AiPracticeService`へ追加した。

### `AiPracticeService.java`

```java
private TemporaryGeneratedQuestionListDto generateQuestionsWithChatGPT(
        String input,
        Locale locale) {

    // 3. APIリクエストを作成(ChatGPT)
    StructuredResponseCreateParams<TemporaryGeneratedQuestionListDto> params =
            ResponseCreateParams.builder()
                    .model(ChatModel.GPT_5_2)
                    .input(input)
                    .text(TemporaryGeneratedQuestionListDto.class)
                    .build();

    // 4. APIへリクエストを送信
    StructuredResponse<TemporaryGeneratedQuestionListDto> response =
            openAIClient.responses().create(params);

    // 5. responseの中からAIが実際に生成した部分を取り出す
    TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto = null;

    // responseからStructured Outputsの生成結果を取得
    // responseの出力を順番に確認
    for (int i = 0; i < response.output().size(); i++) {

        var output = response.output().get(i);

        if (output.isMessage()) {

            var message = output.asMessage();

            // messageの中身を順番に確認
            for (int j = 0; j < message.content().size(); j++) {

                var content = message.content().get(j);

                if (content.isOutputText()) {

                    // Structured Outputsによって
                    // TemporaryGeneratedQuestionListDtoへ変換済みの値を取得
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

    // AIの生成結果を取得できなかった場合はエラーを出す
    if (temporaryGeneratedQuestionListDto == null) {

        throw new IllegalStateException(
                messageSource.getMessage(
                        "ai.generation.error.response",
                        null,
                        locale));
    }

    return temporaryGeneratedQuestionListDto;
}
```

このメソッドでは、

```text
1. Structured Outputsを使用するAPIリクエスト設定を作成
2. OpenAI APIへリクエストを送信
3. StructuredResponseを取得
4. responseからStructured Outputsの生成結果を探す
5. TemporaryGeneratedQuestionListDtoを返す
```

という処理を行う。

---

## 3. ChatGPTのAPIリクエスト設定を作成

OpenAIのResponses APIへ送信するリクエスト設定を作成する。

```java
StructuredResponseCreateParams<TemporaryGeneratedQuestionListDto> params =
        ResponseCreateParams.builder()
                .model(ChatModel.GPT_5_2)
                .input(input)
                .text(TemporaryGeneratedQuestionListDto.class)
                .build();
```

今回はStructured Outputsを使用するため、

```java
StructuredResponseCreateParams<TemporaryGeneratedQuestionListDto>
```

としてリクエスト設定を作成する。

各設定は、

```java
.model(ChatModel.GPT_5_2)
```

で使用するモデル、

```java
.input(input)
```

で前チャプターで作成したAIへの入力、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

でStructured Outputsによる出力形式を指定している。

これにより、AIには最終的に、

```java
TemporaryGeneratedQuestionListDto
```

に対応する構造で回答を生成させる。

---

## 4. `TemporaryGeneratedQuestionListDto`をStructured Outputsの出力形式として使用

前チャプターでは、AIが生成した1問を受け取る、

```java
TemporaryGeneratedQuestionDto
```

に加えて、

```java
@Data
public class TemporaryGeneratedQuestionListDto {

    private List<TemporaryGeneratedQuestionDto> questions;
}
```

を作成した。

今回生成するのは1問ではなく最大50問なので、必要なのは、

```java
List<TemporaryGeneratedQuestionDto>
```

である。

しかし、

```java
List<TemporaryGeneratedQuestionDto>.class
```

という指定はできない。

そこで、`List<TemporaryGeneratedQuestionDto>`をフィールドとして持つ`TemporaryGeneratedQuestionListDto`でラップし、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

としてStructured Outputsの出力形式へ指定する。

生成結果は概念的に、

```text
TemporaryGeneratedQuestionListDto
│
└─ questions
   │
   ├─ TemporaryGeneratedQuestionDto
   ├─ TemporaryGeneratedQuestionDto
   ├─ TemporaryGeneratedQuestionDto
   │
   ...
   └─ TemporaryGeneratedQuestionDto
```

という構造になる。

---

## 5. OpenAI APIへリクエストを送信

作成した`params`を使用して、OpenAI APIへリクエストを送信する。

```java
StructuredResponse<TemporaryGeneratedQuestionListDto> response =
        openAIClient.responses().create(params);
```

`openAIClient`はOpenAI APIとの通信を担当するクライアントである。

```java
.responses()
```

でResponses APIを使用し、

```java
.create(params)
```

で作成した`params`を送信する。

APIから返されたレスポンス全体を、

```java
StructuredResponse<TemporaryGeneratedQuestionListDto>
```

として受け取る。

`AiPracticeService`では、この`openAIClient`をDIで受け取るため、

```java
private final OpenAIClient openAIClient;
```

をフィールドとして持たせる。

このチャプターではAPIリクエスト処理までを実装し、`OpenAIClient`の生成およびSpring Beanへの登録は次チャプターで実装する。

---

## 6. OpenAIのResponseから生成結果を取得

`StructuredResponse`にはAIが生成した問題だけではなく、レスポンスに関する複数の情報が含まれる。

そのため、`response`の構造をたどり、Structured Outputsによって生成された`TemporaryGeneratedQuestionListDto`を取得する。

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

処理しているレスポンスの構造は、

```text
response
│
└─ output()
   │
   └─ output
      │
      └─ Message
         │
         └─ content()
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

となる。

外側のループでは、

```java
response.output()
```

を順番に確認し、

```java
output.isMessage()
```

によって`Message`を探す。

内側のループでは、

```java
message.content()
```

を順番に確認し、

```java
content.isOutputText()
```

によってStructured Outputsの生成結果を探す。

見つかった場合は、

```java
temporaryGeneratedQuestionListDto =
        content.asOutputText();
```

として`TemporaryGeneratedQuestionListDto`を取得する。

ここで二重ループが走査しているのは生成された最大50問ではなく、`response`内部から生成結果全体を持つ`TemporaryGeneratedQuestionListDto`を探すための処理である。

---

## 7. ChatGPTから生成結果を取得できなかった場合の処理

Response内から`TemporaryGeneratedQuestionListDto`を取得できなかった場合は例外を発生させる。

```java
if (temporaryGeneratedQuestionListDto == null) {

    throw new IllegalStateException(
            messageSource.getMessage(
                    "ai.generation.error.response",
                    null,
                    locale));
}
```

### `messages.properties`

```properties
ai.generation.error.response=AIから生成結果を取得できませんでした。
```

生成結果を正常に取得できた場合は、

```java
return temporaryGeneratedQuestionListDto;
```

として呼び出し元へ返す。

---

## 8. Google Gen AI Java SDKの追加

Gemini APIをJavaアプリケーションから利用するため、Google Gen AI Java SDKを`pom.xml`へ追加した。

### `pom.xml`

```xml
<dependency>
    <groupId>com.google.genai</groupId>
    <artifactId>google-genai</artifactId>
    <version>1.67.0</version>
</dependency>
```

これにより、

```java
Schema
GenerateContentConfig
GenerateContentResponse
```

など、Gemini APIとの連携に必要なクラスを使用できるようになる。

---

## 9. Geminiによる問題生成処理の実装

Geminiへ`input`を送信し、生成されたJSONを`TemporaryGeneratedQuestionListDto`へ変換する、

```java
generateQuestionsWithGemini()
```

を`AiPracticeService`へ追加した。

### `AiPracticeService.java`

```java
private TemporaryGeneratedQuestionListDto generateQuestionsWithGemini(
        String input,
        Locale locale) {

    // 3. APIリクエストを作成(Gemini)
    // 1問分の出力形式を定義する
    // Structured OutputsのJSON Schemaを作成
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

    // レスポンス全体の出力形式を定義する
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

    // APIリクエストを作成(Gemini)
    GenerateContentConfig config =
            GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .responseSchema(responseSchema)
                    .build();

    // 4. APIへリクエストを送信
    GenerateContentResponse response =
            geminiClient.models.generateContent(
                    "gemini-3.7-flash",
                    input,
                    config);

    // 5. AIが生成したJSONを取得
    String responseJson =
            response.text();

    // JSONをDTOへ変換
    TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto;

    try {
        temporaryGeneratedQuestionListDto =
                objectMapper.readValue(
                        responseJson,
                        TemporaryGeneratedQuestionListDto.class);

    } catch (JsonProcessingException e) {
        throw new IllegalStateException(
                messageSource.getMessage(
                        "ai.generation.error.response",
                        null,
                        locale),
                e);
    }

    return temporaryGeneratedQuestionListDto;
}
```

Gemini版では、

```text
1問分のSchemaを作成
        ↓
レスポンス全体のSchemaを作成
        ↓
GenerateContentConfigを作成
        ↓
Gemini APIへ送信
        ↓
生成されたJSON文字列を取得
        ↓
ObjectMapperでDTOへ変換
```

という流れになる。

---

## 10. Geminiの1問分のStructured Outputs Schemaを作成

Geminiでは、生成させるJSONの構造を`Schema`として明示的に定義する。

まず、1問分の構造を、

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

として定義する。

```java
.type(Type.Known.OBJECT)
```

によって1問分のデータがJSONオブジェクトであることを指定する。

`.properties()`では各プロパティとその型を指定し、

```text
sourceIndex   → INTEGER
japaneseText  → STRING
chineseText   → STRING
pinyin        → STRING
zhuyin        → STRING
```

という構造を定義する。

さらに、

```java
.required(List.of(
        "sourceIndex",
        "japaneseText",
        "chineseText",
        "pinyin",
        "zhuyin"))
```

によって5つのプロパティを必須にする。

---

## 11. Geminiのレスポンス全体のSchemaを作成

1問分の`questionSchema`を利用し、複数問題を格納するレスポンス全体のSchemaを作成する。

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

レスポンス全体は、

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

という構造にする。

そのため、

```java
.type(Type.Known.ARRAY)
```

で`questions`を配列として定義し、

```java
.items(questionSchema)
```

によって配列内の各要素が先ほど作成した`questionSchema`に従うことを指定する。

---

## 12. Geminiの`GenerateContentConfig`を作成

作成した`responseSchema`を使用して、Geminiの生成設定を作成する。

```java
GenerateContentConfig config =
        GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();
```

```java
.responseMimeType("application/json")
```

によってレスポンスをJSON形式で生成するよう指定する。

```java
.responseSchema(responseSchema)
```

では、生成するJSONが`responseSchema`で定義した構造に従うよう指定する。

ここまでで、

```text
input
→ 何を生成するか

config
→ どのような形式・設定で生成するか
```

という2つの情報をGemini APIへ渡せる状態になる。

---

## 13. Gemini APIへリクエストを送信

作成した`input`と`config`をGemini APIへ送信する。

```java
GenerateContentResponse response =
        geminiClient.models.generateContent(
                "gemini-3.7-flash",
                input,
                config);
```

ここでは、

```text
"gemini-3.7-flash"
→ 使用するモデル

input
→ 生成してほしい内容

config
→ 生成方法・出力形式の設定
```

を`generateContent()`へ渡している。

Gemini APIから返されたレスポンス全体は、

```java
GenerateContentResponse response
```

として取得する。

---

## 14. GeminiのResponseからJSONを取得

Geminiが生成した回答部分を、

```java
String responseJson =
        response.text();
```

として取得する。

`config`では、

```java
.responseMimeType("application/json")
.responseSchema(responseSchema)
```

を設定しているため、`response.text()`から取得する文字列は`responseSchema`に従ったJSONとなる。

この時点では、

```java
String responseJson
```

であり、まだ`TemporaryGeneratedQuestionListDto`ではない。

---

## 15. GeminiのJSONを`TemporaryGeneratedQuestionListDto`へ変換

取得したJSON文字列を、Jacksonの`ObjectMapper`を使用してJava DTOへ変換する。

```java
TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto;

try {
    temporaryGeneratedQuestionListDto =
            objectMapper.readValue(
                    responseJson,
                    TemporaryGeneratedQuestionListDto.class);

} catch (JsonProcessingException e) {
    throw new IllegalStateException(
            messageSource.getMessage(
                    "ai.generation.error.response",
                    null,
                    locale),
            e);
}
```

前チャプターでは、

```java
objectMapper.writeValueAsString(generationSources);
```

として、

```text
Javaオブジェクト
↓
JSON文字列
```

へ変換した。

今回は逆に、

```java
objectMapper.readValue(
        responseJson,
        TemporaryGeneratedQuestionListDto.class);
```

として、

```text
JSON文字列
↓
TemporaryGeneratedQuestionListDto
```

へ変換する。

JSONの解析またはDTOへの変換に失敗した場合は`JsonProcessingException`を捕捉し、AIの生成結果を正常に取得できなかったものとして`IllegalStateException`を発生させる。

---

## 16. ChatGPTとGeminiの生成処理を切り替える

ChatGPT版とGemini版の両方を実装したことで、同じ`generateQuestions()`内で両方を実行すると生成結果を格納する変数が競合する。

そのため、現時点では使用するAIを一時的に切り替える簡易的な分岐を追加した。

### `AiPracticeService.java`

```java
// 使用するAIを一時的に切り替える
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

// このさき最終的なAiGeneratedQuestionDtoを作成(次回で実装)
```

どちらのAPIを使用した場合でも、最終的には、

```java
TemporaryGeneratedQuestionListDto temporaryGeneratedQuestionListDto
```

を取得する。

これによって、ChatGPTとGeminiで異なる処理を、

```text
ChatGPT
generateQuestionsWithChatGPT()
        │
        ├──────────────┐
        │              ↓
        │    TemporaryGeneratedQuestionListDto
        │              ↑
        └──────────────┤
                       │
Gemini                 │
generateQuestionsWithGemini()
```

という形で同じDTOへ収束させる。

そのため、この後のAI生成問題をアプリケーション用DTOへ変換する処理については、ChatGPTとGeminiで共通化できる。

---

## 17. ChatGPTとGeminiのAPI連携処理の違い

今回、ChatGPTとGeminiでは同じ、

```text
APIリクエストを作成
↓
APIへ送信
↓
生成結果を取得
↓
TemporaryGeneratedQuestionListDto
```

という処理を実装したが、使用するSDKとStructured Outputsの扱い方が異なる。

### ChatGPT

```text
input
↓
StructuredResponseCreateParams
↓
openAIClient.responses().create()
↓
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

OpenAI Java SDKでは、

```java
.text(TemporaryGeneratedQuestionListDto.class)
```

としてJavaクラスをStructured Outputsの出力形式へ指定する。

生成結果についても、SDKによって`TemporaryGeneratedQuestionListDto`として扱えるため、`StructuredResponse`の構造をたどってDTOを取得する。

### Gemini

```text
input
↓
questionSchema
↓
responseSchema
↓
GenerateContentConfig
↓
geminiClient.models.generateContent()
↓
GenerateContentResponse
↓
JSON文字列
↓
ObjectMapper
↓
TemporaryGeneratedQuestionListDto
```

Geminiでは、`Schema`を使用して生成するJSONの構造を明示的に定義する。

APIからは生成結果をJSON文字列として取得し、

```java
objectMapper.readValue()
```

によって`TemporaryGeneratedQuestionListDto`へ変換する。

両者の実装方法は異なるが、このチャプターでは最終的にどちらからも、

```java
TemporaryGeneratedQuestionListDto
```

を取得できるところまで実装した。

---

## 18. このチャプターで実装した処理の流れ

このチャプターまでで、AI生成元問題の取得からAPIレスポンスをDTOとして取得するまでが、

```text
List<Question> sourceQuestions
        ↓
List<AiGenerationSourceDto>
        ↓
JSONへ変換
        ↓
commonPrompt
+
languageProfile
+
generationSourcesJson
        ↓
input
        ↓
使用するAIを選択
        ↓
┌──────────────────────┬──────────────────────┐
│ ChatGPT              │ Gemini               │
│                      │                      │
│ Structured Outputs   │ Schemaを作成          │
│ の設定               │                      │
│ ↓                    │ ↓                    │
│ Responses API        │ generateContent()    │
│ ↓                    │ ↓                    │
│ StructuredResponse   │ JSON                 │
│ ↓                    │ ↓                    │
│ DTOを取得            │ ObjectMapper         │
│                      │ ↓                    │
│                      │ DTOへ変換             │
└───────────┬──────────┴───────────┬──────────┘
            │                      │
            └──────────┬───────────┘
                       ↓
        TemporaryGeneratedQuestionListDto
```

という流れになった。

次チャプターでは、取得した`TemporaryGeneratedQuestionListDto`と生成元の`sourceQuestions`を`sourceIndex`によって対応付けし、実際にAI問題生成モードで使用する`AiGeneratedQuestionDto`を作成する。