# 017 AI問題生成モードの実装その3 — AI API連携【実装ログ】

## 1. 実装の目的と範囲

016で作成した`input`（共通プロンプト・言語別Language Profile・生成元問題のJSON）をOpenAI Responses APIまたはGemini APIへ渡し、生成結果を共通の`TemporaryGeneratedQuestionListDto`として取得する処理を実装した。

```text
input
  ├─ OpenAI Responses API → StructuredResponse → DTO
  └─ Gemini API          → JSON文字列 → ObjectMapper → DTO
                                               ↓
                            TemporaryGeneratedQuestionListDto
```

この段階では、取得したDTOを`sourceIndex`で元の`Question`に紐付けて`AiGeneratedQuestionDto`へ変換する処理は未実装。APIクライアントのSpring Bean登録も次チャプターで扱う。

## 2. OpenAI Responses APIとの連携

### 2-1. SDKの依存関係

`pom.xml`へ当時使用したOpenAI Java SDKを追加した。

```xml
<dependency>
    <groupId>com.openai</groupId>
    <artifactId>openai-java</artifactId>
    <version>4.54.0</version>
</dependency>
```

### 2-2. `generateQuestionsWithChatGPT()`

`AiPracticeService`に以下のメソッドを追加した。**API設定・送信・レスポンスの走査・結果未取得時の例外処理**をまとめて掲載する。

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

### 2-3. リクエスト設定とStructured Outputs

- `ResponseCreateParams.builder()`でリクエスト設定を構築する。
- `.model(ChatModel.GPT_5_2)`で当時の使用モデルを指定する。
- `.input(input)`に016で作成した入力文字列を渡す。
- `.text(TemporaryGeneratedQuestionListDto.class)`で、JavaクラスをStructured Outputsの出力形式として指定する。
- `openAIClient.responses().create(params)`でResponses APIへ送信し、`StructuredResponse<TemporaryGeneratedQuestionListDto>`を受け取る。

`TemporaryGeneratedQuestionListDto`は`List<TemporaryGeneratedQuestionDto> questions`を保持するラッパーDTO。`List<TemporaryGeneratedQuestionDto>.class`のようにパラメータ化された`List`を直接クラスリテラルとして指定できないため、この形で複数問の生成結果を受け取る。

```java
@Data
public class TemporaryGeneratedQuestionListDto {
    private List<TemporaryGeneratedQuestionDto> questions;
}
```

### 2-4. レスポンスから生成結果を取得

`response.output()`には複数の出力項目が含まれ得るため、外側のループで`output.isMessage()`、内側のループで`content.isOutputText()`を確認する。該当する内容を`content.asOutputText()`でDTOとして取得する。

```text
StructuredResponse
  └─ output()（複数の出力項目）
       └─ Message
            └─ content()（複数のコンテンツ）
                 └─ OutputText → TemporaryGeneratedQuestionListDto
                                      └─ questions（生成問題の一覧）
```

**二重ループは最大50問を走査しているわけではない。** レスポンス内部を調べ、生成問題の一覧を保持するDTOを探している。結果を取得できない場合は`ai.generation.error.response`のメッセージを使って`IllegalStateException`を送出する。

この時点で`openAIClient`はDIで受け取るフィールドとして想定しているが、クライアント生成・Bean登録は後続チャプターで実装する。

## 3. Gemini APIとの連携

### 3-1. SDKの依存関係

`pom.xml`へ当時使用したGoogle Gen AI Java SDKを追加した。

```xml
<dependency>
    <groupId>com.google.genai</groupId>
    <artifactId>google-genai</artifactId>
    <version>1.67.0</version>
</dependency>
```

### 3-2. `generateQuestionsWithGemini()`

`AiPracticeService`に以下のメソッドを追加した。OpenAI版と異なり、**出力SchemaをJava側で構築し、受信したJSON文字列をJacksonでDTOへ変換する**。

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

### 3-3. 1問分とレスポンス全体のSchema

`questionSchema`は生成問題1件のJSONオブジェクトを定義する。`sourceIndex`は`INTEGER`、`japaneseText`・`chineseText`・`pinyin`・`zhuyin`は`STRING`であり、5項目すべてを`required`に指定した。

`responseSchema`は`questions`という配列プロパティを持つオブジェクト。配列の各要素は`questionSchema`に従う。

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

`GenerateContentConfig`では`responseMimeType("application/json")`と`responseSchema(responseSchema)`を設定する。`input`が「何を生成するか」、`config`が「どの形式で生成するか」を担当する。

### 3-4. 送信・JSON取得・DTOへの変換

`geminiClient.models.generateContent("gemini-3.7-flash", input, config)`で当時指定したモデルにリクエストする。`response.text()`で生成結果をJSON形式の`String`として取得し、`objectMapper.readValue(responseJson, TemporaryGeneratedQuestionListDto.class)`でJava DTOへ変換する。

これは016で送信データを作成した`writeValueAsString()`とは逆方向の変換である。

```text
016: List<AiGenerationSourceDto> → writeValueAsString() → JSON文字列
017: JSON文字列 → readValue() → TemporaryGeneratedQuestionListDto
```

JSONの解析やDTOへの変換で`JsonProcessingException`が発生した場合は、`ai.generation.error.response`を使用した`IllegalStateException`として扱う。

## 4. 使用するAPIの切り替えと結果形式の統一

両方のメソッドを呼び出すのではなく、当時は`useChatGPT`フラグによって使用先を一時的に選択した。

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

どちらを使用しても戻り値は`TemporaryGeneratedQuestionListDto`である。このため、後続の`sourceIndex`による生成元Questionの特定や`AiGeneratedQuestionDto`への変換は、APIごとに別々に実装せず共通化できる。

このフラグは当時の暫定的な切り替え方式であり、設定画面等による切り替え機能を実装したことを意味しない。

## 5. OpenAI版とGemini版の違い

| 項目 | OpenAI Responses API | Gemini API |
|---|---|---|
| Java SDK | `com.openai:openai-java:4.54.0` | `com.google.genai:google-genai:1.67.0` |
| 出力形式の指定 | `.text(TemporaryGeneratedQuestionListDto.class)` | `Schema`を構築し`responseSchema()`へ指定 |
| API呼び出し | `openAIClient.responses().create(params)` | `geminiClient.models.generateContent(...)` |
| 受信後の処理 | `StructuredResponse`を走査してDTOを取得 | `response.text()`でJSONを取得して`ObjectMapper.readValue()` |
| 最終的な戻り値 | `TemporaryGeneratedQuestionListDto` | `TemporaryGeneratedQuestionListDto` |

SDKごとの実装は異なるが、**アプリケーション内部に渡すDTOを統一した**ことが今回の設計上のポイントとなる。

## 6. エラーメッセージと実装状況

`messages.properties`へ、生成結果を取得できなかった場合のメッセージを追加した。

```properties
ai.generation.error.response=AIから生成結果を取得できませんでした。
```

今回の実装範囲は、**APIへリクエストする処理と、返された生成結果を`TemporaryGeneratedQuestionListDto`として取り出す処理**まで。APIクライアントのBean登録や、生成結果と元問題の紐付け、最終的な問題DTOへの変換は後続チャプターに引き継ぐ。

### 実装後の処理の流れ

```text
List<Question> sourceQuestions
    ↓
List<AiGenerationSourceDto>
    ↓ ObjectMapper.writeValueAsString()
生成元問題JSON + 共通プロンプト + Language Profile
    ↓
input
    ↓ 使用するAPIを選択
    ├─ OpenAI: Structured Outputs → DTO取得
    └─ Gemini: Schema指定 → JSON → DTO変換
    ↓
TemporaryGeneratedQuestionListDto
    ↓
（次チャプター：sourceIndexで元問題と対応付け）
```

**記録上の留意点：** 本チャプターにはAPIリクエスト処理のコードが掲載されているが、クライアントのBean登録は次回としているため、ここでAPIの実行成功まで検証済みだったとは記載しない。また、モデル名・SDKバージョンは当時のコードを記録したもので、現在の利用可否を検証したものではない。

## まとめ

このチャプターでは、前チャプターで作成した`input`をOpenAI APIまたはGemini APIへ送信し、AIが生成した問題を`TemporaryGeneratedQuestionListDto`として取得する処理を実装した。

両APIでは使用するSDKやStructured Outputsの設定方法が異なる。

- **OpenAI API**：`TemporaryGeneratedQuestionListDto.class`を出力形式に指定し、`StructuredResponse`からDTOを取得する。
- **Gemini API**：`Schema`でJSONの出力形式を定義し、レスポンスのJSON文字列を`ObjectMapper`でDTOへ変換する。

このようにAPIごとの実装方法は異なるが、最終的な戻り値を`TemporaryGeneratedQuestionListDto`に統一することで、後続の処理を共通化できる構成とした。

また、現段階では`useChatGPT`による簡易的な切り替えを採用し、どちらか一方のAPIを呼び出す仕組みを用意した。

なお、このチャプターではAPI連携メソッドを実装した段階であり、APIクライアントのSpring Bean登録や、生成結果を実際の学習画面で使用するための変換処理はまだ完了していない。

次のチャプターでは、APIクライアントの設定に加え、取得した`TemporaryGeneratedQuestionListDto`と生成元の`sourceQuestions`を`sourceIndex`によって対応付け、最終的な`AiGeneratedQuestionDto`へ変換する処理を実装する。