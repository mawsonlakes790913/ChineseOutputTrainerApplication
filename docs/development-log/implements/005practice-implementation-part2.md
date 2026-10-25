# 005 通常学習の実装 その2

通常学習の解答画面に拼音・注音の表示機能を追加し、ユーザーが発音表記を選択できるようにする。あわせて別解の発音表記、学習開始時のデフォルト言語、問題ごとの難易度表示にも対応した。

## 1. 拼音・注音データの追加

通常学習の解答画面で拼音・注音を表示できるようにする。

関連する要件・設計については、以下のドキュメントを更新済み。

- `requirements.md`
- `data-design.md`
- `er-diagram.md`
- `table-definition.md`
- `screen-design.md`

---

### 2. Questionに拼音・注音フィールドを追加

### Question.java

解答文と発音表記を同じ問題データから取得できるよう、`Question`に`pinyin`と`zhuyin`を追加した。どちらも文章を保持するため、DBのカラム型には`TEXT`を指定している。

```java
@Column(name = "pinyin", columnDefinition = "TEXT")
private String pinyin;

@Column(name = "zhuyin", columnDefinition = "TEXT")
private String zhuyin;
```

既存の`question`テーブルには発音表記を持たないレコードがある。そのままNOT NULL制約を設けると既存データとの整合性に問題が生じるため、カラム追加時点では`nullable = false`を指定しない。

開発時点ではHibernateのスキーマ更新を有効にしていた。

```properties
spring.jpa.hibernate.ddl-auto=update
```

この設定のもとでEntityを変更してアプリケーションを起動し、Hibernateにカラムを追加させた。既存データへの値の設定とNOT NULL制約の適用は、その後に行う。

### 確認

pgAdmin4から `question` テーブルを確認し、`pinyin`・`zhuyin` カラムが追加されていることを確認した。

![](../../images/0005-01.png)

---

### 3. 既存データへの拼音・注音追加

追加したカラムには当初値が入っていないため、既存の問題文に対応する拼音と注音をSQLで登録した。以下は`question_id` 1〜5に対する更新例である。

```sql
UPDATE question SET
    pinyin = 'Wǒmen zuò chūzūchē qù chēzhàn ba.',
    zhuyin = 'ㄨㄛˇ ㄇㄣ˙ ㄗㄨㄛˋ ㄔㄨ ㄗㄨ ㄔㄜ ㄑㄩˋ ㄔㄜ ㄓㄢˋ ㄅㄚ˙'
WHERE question_id = 1;

UPDATE question SET
    pinyin = 'Wǒmen dā jìchéngchē qù chēzhàn ba.',
    zhuyin = 'ㄨㄛˇ ㄇㄣ˙ ㄉㄚ ㄐㄧˋ ㄔㄥˊ ㄔㄜ ㄑㄩˋ ㄔㄜ ㄓㄢˋ ㄅㄚ˙'
WHERE question_id = 2;

UPDATE question SET
    pinyin = 'Wǒ bǎ shǒujī wàng zài jiālǐ le.',
    zhuyin = 'ㄨㄛˇ ㄅㄚˇ ㄕㄡˇ ㄐㄧ ㄨㄤˋ ㄗㄞˋ ㄐㄧㄚ ㄌㄧˇ ㄌㄜ˙'
WHERE question_id = 3;

UPDATE question SET
    pinyin = 'Wǒ zuò dìtiě qù gōngsī.',
    zhuyin = 'ㄨㄛˇ ㄗㄨㄛˋ ㄉㄧˋ ㄊㄧㄝˇ ㄑㄩˋ ㄍㄨㄥ ㄙ'
WHERE question_id = 4;

UPDATE question SET
    pinyin = 'Zhè bù diànyǐng hěn yǒuyìsi.',
    zhuyin = 'ㄓㄜˋ ㄅㄨˋ ㄉㄧㄢˋ ㄧㄥˇ ㄏㄣˇ ㄧㄡˇ ㄧˋ ㄙ˙'
WHERE question_id = 5;
```

以下、既存データについて同様に更新した。

### NOT NULL制約の追加

既存レコードの発音表記を埋めた後、新しく登録する問題でも拼音・注音を必須とするため、Entityに`nullable = false`を指定した。

```java
@Column(name = "pinyin", nullable = false, columnDefinition = "TEXT")
private String pinyin;

@Column(name = "zhuyin", nullable = false, columnDefinition = "TEXT")
private String zhuyin;
```

**Commit**

```bash
git commit -m "feat: add pinyin and zhuyin to questions"
```

---

## 4. 発音表記の表示設定

発音表記のデータを保持できるようになったので、次に問題画面で何を表示するかを選べるようにした。選択肢は以下の3種類とする。

- 拼音
- 注音
- 表示なし

デフォルトは拼音とする。

選択値はSessionの`pronunciationType`に保持し、同じ問題でも設定に応じて表示を変える。未設定時は拼音を使用する。

---

### 5. PronunciationTypeの追加

### PronunciationType.java

選択可能な発音表記を文字列でばらばらに扱わないよう、`PINYIN`、`ZHUYIN`、`NONE`をEnumで定義した。

```java
public enum PronunciationType {

    PINYIN,
    ZHUYIN,
    NONE
}
```

---

### 6. PronunciationTypeControllerの追加

設定画面から受け取った`pronunciationType`をSessionへ保存し、設定画面へ戻すControllerを用意した。以下はこの時点の実装である。

```java
public class PronunciationTypeController {

    @GetMapping("/pronunciation-type")
    public String changePronunciationType(
            @RequestParam PronunciationType pronunciationType,
            HttpSession session) {

        session.setAttribute("pronunciationType", pronunciationType);

        return "redirect:/settings";
    }
}
```

`@RequestParam`で受け取ったEnum値を`pronunciationType`というSession属性に格納する。設定を保存した後は`/settings`へリダイレクトする。なお、ここに掲載した`@GetMapping`は当時の実装を記録したものである。

---

### 7. 設定画面の追加

### SettingsController.java

```java
@Controller
public class SettingsController {

    @GetMapping("/settings")
    public String getSettings() {

        return "settings";
    }
}
```

### settings.html

`SettingsController`から表示する`settings.html`に、3種類の発音表記を選ぶボタンを配置した。選択中の項目だけ`btn-primary`を使用し、それ以外はアウトライン表示にする。

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/layout}">

<head>
    <title>設定</title>
</head>

<body>

<div layout:fragment="content">

    <h1 class="mb-4">設定</h1>

    <h2 class="h5 mb-3">発音記号の表示</h2>

    <div class="d-flex gap-2">

        <!-- 拼音 -->
        <a th:href="@{/pronunciation-type(pronunciationType='PINYIN')}"
           class="btn"
           th:classappend="${session.pronunciationType == null
                            || session.pronunciationType.name() == 'PINYIN'}
                            ? 'btn-primary'
                            : 'btn-outline-primary'">
            拼音
        </a>

        <!-- 注音 -->
        <a th:href="@{/pronunciation-type(pronunciationType='ZHUYIN')}"
           class="btn"
           th:classappend="${session.pronunciationType != null
                            && session.pronunciationType.name() == 'ZHUYIN'}
                            ? 'btn-primary'
                            : 'btn-outline-primary'">
            注音
        </a>

        <!-- 表示なし -->
        <a th:href="@{/pronunciation-type(pronunciationType='NONE')}"
           class="btn"
           th:classappend="${session.pronunciationType != null
                            && session.pronunciationType.name() == 'NONE'}
                            ? 'btn-primary'
                            : 'btn-outline-primary'">
            表示なし
        </a>

    </div>

</div>

</body>
</html>
```

`pronunciationType` が未設定の場合は拼音を選択状態として表示する。

---

### 8. QuestionModelUtilで表示する発音表記を決定

表示用データの組み立ては`QuestionModelUtil#setQuestionModel()`に集約した。Sessionから設定値を読み取り、拼音・注音・非表示のいずれかに応じて、Modelの`pronunciation`属性を設定する。

```java
PronunciationType pronunciationType =
        (PronunciationType) session.getAttribute("pronunciationType");

if (pronunciationType == null) {
    pronunciationType = PronunciationType.PINYIN;
}

switch (pronunciationType) {

    case PINYIN ->
        model.addAttribute(
                "pronunciation",
                question.getPinyin()
        );

    case ZHUYIN ->
        model.addAttribute(
                "pronunciation",
                question.getZhuyin()
        );

    case NONE ->
        model.addAttribute(
                "pronunciation",
                null
        );
}
```

`pronunciationType`が未設定なら`PINYIN`に補正する。`NONE`の場合は`pronunciation`を`null`にし、テンプレート側で発音表記の要素自体を非表示にできるようにした。

---

### 9. PracticeControllerの修正

これまではModel・問題セット・ページ番号だけを渡していたが、発音表記の選択状態を参照する必要が生じたため、`PracticeController`から`HttpSession`も渡すようにした。

変更前：

```java
questionModelUtil.setQuestionModel(
        model,
        questions,
        page
);
```

変更後：

```java
questionModelUtil.setQuestionModel(
        model,
        questions,
        page,
        session
);
```

---

### 10. 問題画面への発音表記追加

### practice/question.html

`QuestionModelUtil`が設定した`pronunciation`を解答部分に表示する。`th:if`を設け、表示なしを選んだ場合は空の発音表記欄を作らないようにした。

```html
<p class="mb-2"
   th:if="${pronunciation != null}">

    <span th:text="${pronunciation}">
        Pronunciation
    </span>

</p>
```

`PronunciationType.NONE` の場合は `pronunciation` が `null` となるため表示しない。

あわせて、中国語の解答を見やすくするため文字サイズを変更した。

```html
<span class="fs-3"
      th:text="${question.chineseText}">
    Chinese Answer
</span>
```

---

### 11. 発音表記切り替えの確認

デフォルト状態では、学習対象言語が普通話・國語のどちらの場合でも拼音が表示されることを確認した。

### 普通話

![](../../images/0005-02.png)

### 國語

![](../../images/0005-03.png)

---

### 12. 学習開始時のlanguageVariant未設定への対応

### 問題

発音表記を注音に切り替えた後、通常学習メニューから学習を開始すると、`GET /practice/start` でエラーが発生した。

`/practice/menu` では `languageVariant` が未設定の場合に `MAINLAND` を使用する処理を追加済みだったが、`/practice/start` の問題取得処理では同様の対応が行われていなかった。

### 修正

メニュー表示時のデフォルト処理だけでは、学習開始リクエストで問題を取得するときの`null`を防げない。そこで`PracticeController#getPracticeStart()` でもSessionから `languageVariant` を取得し、未設定の場合は `MAINLAND` を使用するよう修正した。

```java
// 学習対象言語を取得
LanguageVariant languageVariant =
        (LanguageVariant) session.getAttribute("languageVariant");

if (languageVariant == null) {
    languageVariant = LanguageVariant.MAINLAND;
}

// 問題セットを取得
List<Question> questions =
        practiceService.getPracticeQuestions(
                languageVariant,
                difficulty,
                start,
                random
        );
```

これにより、学習対象言語がSessionに保存されていない状態でも、普通話をデフォルトとして通常学習を開始できるようになった。

---

### 13. 拼音・注音・非表示の動作確認

設定画面から発音表記を変更し、通常学習画面へ反映されることを確認した。

### 注音

![](../../images/0005-04.png)

### 表示なし

![](../../images/0005-05.png)

**Commit**

```bash
git commit -m "feat: add pinyin and zhuyin display settings"
```

---

## 14. 別解への発音表記追加

本解答だけでなく、別解にも固有の読み方がある。別解の文章と発音表記を対応させるため、DBの項目追加、既存データの更新、Modelと画面の修正を行った。

---

### 15. Questionに別解用発音フィールドを追加

### Question.java

```java
@Column(name = "alternative_answer_pinyin")
private String alternativeAnswerPinyin;

@Column(name = "alternative_answer_zhuyin")
private String alternativeAnswerZhuyin;
```

---

### 16. 既存データへの別解・発音表記追加

`question_id` 1〜10に別解と対応する拼音・注音を追加した。

```sql
UPDATE question
SET alternative_answer = '我们打车去车站吧。',
    alternative_answer_pinyin = 'Wǒmen dǎchē qù chēzhàn ba.',
    alternative_answer_zhuyin = 'ㄨㄛˇ ㄇㄣ˙ ㄉㄚˇ ㄔㄜ ㄑㄩˋ ㄔㄜ ㄓㄢˋ ㄅㄚ˙'
WHERE question_id = 1;

UPDATE question
SET alternative_answer = '我們坐計程車去車站吧。',
    alternative_answer_pinyin = 'Wǒmen zuò jìchéngchē qù chēzhàn ba.',
    alternative_answer_zhuyin = 'ㄨㄛˇ ㄇㄣ˙ ㄗㄨㄛˋ ㄐㄧˋ ㄔㄥˊ ㄔㄜ ㄑㄩˋ ㄔㄜ ㄓㄢˋ ㄅㄚ˙'
WHERE question_id = 2;

UPDATE question
SET alternative_answer = '我的手机忘在家里了。',
    alternative_answer_pinyin = 'Wǒ de shǒujī wàng zài jiālǐ le.',
    alternative_answer_zhuyin = 'ㄨㄛˇ ㄉㄜ˙ ㄕㄡˇ ㄐㄧ ㄨㄤˋ ㄗㄞˋ ㄐㄧㄚ ㄌㄧˇ ㄌㄜ˙'
WHERE question_id = 3;
```

以下、対象データについて同様に更新した。

---

### 17. QuestionModelUtilで別解の発音表記を設定

本解答と別解で異なる発音表記を表示できるよう、既存の`pronunciation`に加えて`alternativePronunciation`をModelへ渡す。どちらも同じ`PronunciationType`に従って切り替える。

```java
switch (pronunciationType) {

    case PINYIN -> {
        model.addAttribute(
                "pronunciation",
                question.getPinyin()
        );

        model.addAttribute(
                "alternativePronunciation",
                question.getAlternativeAnswerPinyin()
        );
    }

    case ZHUYIN -> {
        model.addAttribute(
                "pronunciation",
                question.getZhuyin()
        );

        model.addAttribute(
                "alternativePronunciation",
                question.getAlternativeAnswerZhuyin()
        );
    }

    case NONE -> {
        model.addAttribute("pronunciation", null);
        model.addAttribute("alternativePronunciation", null);
    }
}
```

---

### 18. 問題画面に別解の発音表記を追加

### practice/question.html

テンプレートでは`alternativePronunciation`が存在するときだけ、別解の発音表記を表示する。別解であることを視覚的に区別するため、本解答より小さな文字サイズを指定した。

```html
<p class="mb-2"
   th:if="${alternativePronunciation != null}">

    <span class="small"
          th:text="${alternativePronunciation}">
        Alternative Pronunciation
    </span>

</p>
```

別解の発音表記は、本解答より小さいサイズで表示する。

---

### 19. 別解の発音表記を確認

### 拼音

![](../../images/0005-06.png)

### 注音

![](../../images/0005-07.png)

別解についても、設定した発音表記に応じて拼音・注音が表示されることを確認した。

**Commit**

```bash
git commit -m "feat: add pronunciation display for alternative answers"
```

---

## 20. 追加修正（8月31日）：問題ごとの難易度表示

```bash
git commit -m "feat: display question difficulty on practice page"
```

後日の追加修正として、問題ページに現在の問題の難易度を表示するようにした。

通常学習モードでは異なる難易度の問題が同時に出題されることは少ないが、今後実装する復習モードやAI問題生成モードでは、複数の難易度を選択して問題セットを作成できるため、異なる難易度の問題が混在することがある。

その場合、現在表示されている問題の難易度が分かる方がユーザーにとって分かりやすい。

また、問題画面の仕様に一貫性を持たせるため、通常学習モードの問題画面にも難易度を表示する。

### 20.1 問題の難易度を表示する

### `/practice/question.html`

問題文の上部に難易度バッジを配置する。`Difficulty`のEnum値に応じて背景色を変え、表示文言はメッセージプロパティから取得する。

```html
<!-- 問題の難易度 -->
<span class="badge"
      th:classappend="${question.difficulty.name() == 'BEGINNER'} ? 'bg-danger' :
                      (${question.difficulty.name() == 'INTERMEDIATE'} ? 'bg-primary' : 'bg-success')"
      th:text="${question.difficulty.name() == 'BEGINNER'} ? #{difficulty.beginner} :
               (${question.difficulty.name() == 'INTERMEDIATE'} ? #{difficulty.intermediate} : #{difficulty.advanced})">
    難易度
</span>
```

`question.difficulty`の値によって、表示する難易度とバッジの背景色を切り替える。

| `Difficulty`   | 表示 | Bootstrapクラス |
| -------------- | -- | ------------ |
| `BEGINNER`     | 初級 | `bg-danger`  |
| `INTERMEDIATE` | 中級 | `bg-primary` |
| `ADVANCED`     | 上級 | `bg-success` |

難易度名については`messages.properties`から取得する。

#### 20.2 実行確認

任意の条件で通常学習を開始し、

```text
http://localhost:8080/practice/question
```

にアクセスする。

問題文の上部に、現在出題されている問題の難易度が表示されることを確認した。

![](../../images/0005-08.png)


---

## 21. 次回の実装

お気に入り登録、未学習問題のトレーニング、理解度の保存など、通常学習機能をさらに拡張する予定。

これらの機能にはユーザーを識別する仕組みが必要となるため、通常学習の拡張は一旦区切り、次にログイン機能の実装へ進む。