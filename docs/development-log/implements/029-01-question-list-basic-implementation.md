# 029-01 問題リスト機能①｜DB設計と基本機能の実装

## 概要

これまで、ユーザーが後から確認したい問題を保存する機能として「お気に入り」を実装していた。

しかし、お気に入りでは問題を保存することはできても、目的や学習内容ごとに分類して管理することはできない。

そこで今回、ユーザーが任意のリストを作成し、問題を好きなリストへ登録できる**リスト機能**を実装する。

例えば、

-   苦手な問題
-   復習したい問題
-   旅行で使いたい表現

など、ユーザー自身が用途に応じてリストを作成し、問題を分類できるようにする。

また、1つの問題を複数のリストへ登録できる構成とし、リストごとに登録された問題を確認・管理できるようにする。

---

## このチャプターの対象範囲

問題リスト機能の実装を3部に分けて記録する。

- **029-01（本章）**：DB設計、Entity、Repository、Service、画面表示用DTO、基本的なControllerとリスト管理画面
- **029-02**：JavaScriptによる初期の詳細表示、学習画面からのリスト操作、モーダルを使った機能拡張
- **029-03**：リスト詳細を独立したページへ変更し、Spring MVCとThymeleafで表示する再設計

本章では、詳細表示方式を変更した後も共通して利用するデータ構造と基本操作を中心に記録する。開発当初のJavaScriptによる詳細表示と、その後の設計変更については029-02・029-03で扱う。

---

# DB設計

```text
git commit -m "Add database schema and entities for question lists"
```

「リストそのもの」と「リストに登録された問題」は別の概念なので、2つのテーブルを用意する。

## QUESTION_LIST --- リストそのもの

  -----------------------------------------------------------------------
  カラム         PostgreSQL型     制約
  -------------- ---------------- ---------------------------------------
  `list_id`      `BIGSERIAL`      `PRIMARY KEY`

  `user_id`      `BIGINT`         `NOT NULL`, `FOREIGN KEY → USERS(id)`

  `list_name`    `VARCHAR(100)`   `NOT NULL`

  `created_at`   `TIMESTAMP`      `NOT NULL DEFAULT CURRENT_TIMESTAMP`

  `updated_at`   `TIMESTAMP`      `NOT NULL DEFAULT CURRENT_TIMESTAMP`
  -----------------------------------------------------------------------

関係は、

```text
USERS 1 → N QUESTION_LIST
```

となる。

## QUESTION_LIST_ITEM --- リストに入っている問題

こちらは中間テーブルとする。

`list_id`と`question_id`による複合主キーとし、同じ問題を同じリストへ二重登録できないことをDB側でも保証する。

  ----
  カラム          PostgreSQL型   制約
  --------------- -------------- ------------------------------------------
  `list_id`       `BIGINT`       `NOT NULL`,
                                 `FOREIGN KEY → QUESTION_LIST(list_id)`

  `question_id`   `BIGINT`       `NOT NULL`, `FOREIGN KEY → QUESTION(id)`

  `added_at`      `TIMESTAMP`    `NOT NULL DEFAULT CURRENT_TIMESTAMP`
  ----

### 中間テーブル

1つのリストには複数の問題を入れられる。

また、1つの問題を複数のリストへ登録できるようにする必要がある。

そのため、

```text
QUESTION_LIST
      1
      |
      N
QUESTION_LIST_ITEM
      N
      |
      1
QUESTION
```

という中間テーブルを利用した構成にする。

### テーブル定義

```sql
CREATE TABLE question_list (
    list_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    list_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_question_list_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE question_list_item (
    list_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (list_id, question_id),

    CONSTRAINT fk_question_list_item_list
        FOREIGN KEY (list_id)
        REFERENCES question_list(list_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_question_list_item_question
        FOREIGN KEY (question_id)
        REFERENCES question(question_id)
        ON DELETE CASCADE
);
```

---

## Entityクラス追加

### QuestionList

```java
@Data
@Entity
@Table(name = "question_list")
public class QuestionList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "list_id")
    private Long listId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "list_name", nullable = false, length = 100)
    private String listName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

`QuestionList`では`user_id`は主キーではないため、Favoriteの複合主キーで利用しているような`@MapsId`は必要ない。

### QuestionListItem

```java
@Data
@Entity
@Table(name = "question_list_item")
public class QuestionListItem {

    @EmbeddedId
    private QuestionListItemKey id;

    @ManyToOne
    @MapsId("listId")
    @JoinColumn(name = "list_id")
    private QuestionList questionList;

    @ManyToOne
    @MapsId("questionId")
    @JoinColumn(name = "question_id")
    private Question question;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;
}
```

`QuestionListItemKey`の`listId`と`questionId`は複合主キーの値を保持するためのものであり、それだけではJPA上で`QuestionList`や`Question`との関連を表現できない。

そのため、

```java
private QuestionList questionList;
private Question question;
```

を`@ManyToOne`で定義する。

`@MapsId`によって、それぞれのEntityのIDと`QuestionListItemKey`内のIDを対応付ける。

つまり、

```text
QuestionListItemKey
    → 主キーの管理

questionList / question
    → Entity間の関連付け
```

を担当する。

### QuestionListItemKey

```java
@Embeddable
@Data
public class QuestionListItemKey implements Serializable {

    private Long listId;

    private Long questionId;
}
```

その後、`LocalDateTime`型のフィールドにHibernateが日時を自動設定するアノテーションを追加した。

```text
git commit -m "Add automatic timestamp handling to question list entities"
```

---

# リスト基本操作の実装

```text
git commit -m "Implement basic question list management"
```

まずは**「リストを使える状態にする」ことに集中**し、その後に「リストを学習条件として活用する」段階へ進む。

今回実装する基本操作は、

```text
List作成
List名編集
List削除
問題をListへ追加
問題をListから削除
同一Listへの同一問題の重複登録防止
出題ページからの操作
ユーザーメニューの問題一覧からの操作
モーダルによる操作
```

とする。

---

## 準備編 - Repositoryクラスを作成

### QuestionListRepository

```java
public interface QuestionListRepository
        extends JpaRepository<QuestionList, Long> {
}
```

### QuestionListItemRepository

```java
public interface QuestionListItemRepository
        extends JpaRepository<QuestionListItem, QuestionListItemKey> {
}
```

---

## QuestionListService

リストの新規作成、リスト名変更、リスト削除を担当する。

### createQuestionList - リストを新規作成するメソッド

```java
public void createQuestionList(
        Users user,
        String listName,
        Locale locale) {

    // リスト作成数上限に達していないか確認
    boolean isAdmin =
            user.getRole() == Role.ADMIN;

    int maxLists =
            isAdmin ? 1000 : 30;

    if (questionListRepository
            .countByUserId(user.getId()) >= maxLists) {

        throw new IllegalArgumentException(
                messageSource.getMessage(
                        "questionList.error.limitExceeded",
                        null,
                        locale));
    }

    // 同じ名前のリストがあるか確認
    if (questionListRepository
            .existsByUserIdAndListName(
                    user.getId(),
                    listName)) {

        throw new IllegalArgumentException(
                messageSource.getMessage(
                        "questionList.error.duplicateName",
                        null,
                        locale));
    }

    QuestionList questionList =
            new QuestionList();

    questionList.setUser(user);
    questionList.setListName(listName);

    questionListRepository.save(questionList);

    log.debug(
            "リスト追加 listId={}, userId={}",
            questionList.getListId(),
            questionList.getUser().getId());
}
```

一般ユーザーが作成できるリスト数には上限を設ける。

また、そのユーザーが同じ名前のリストを複数作成することも禁止する。

### editQuestionList - リストを編集するメソッド

```java
public void editQuestionList(
        Users user,
        Long listId,
        String listName,
        Locale locale) {

    QuestionList questionList =
            questionListRepository
                    .findByListIdAndUserId(
                            listId,
                            user.getId())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    messageSource.getMessage(
                                            "questionList.error.notFound",
                                            null,
                                            locale)));

    // 元の実装では、現在のリスト自身も重複判定の対象になる。
    // 同じ名前での保存を許可する場合は、自身のlistIdを除外する必要がある。
    if (questionListRepository
            .existsByUserIdAndListName(
                    user.getId(),
                    listName)) {

        throw new IllegalArgumentException(
                messageSource.getMessage(
                        "questionList.error.duplicateName",
                        null,
                        locale));
    }

    questionList.setListName(listName);

    questionListRepository.save(questionList);
}
```

`listId`だけではなく`userId`も検索条件に含めることで、他ユーザーが所有するリストを編集できないようにする。

### deleteQuestionList - リストを削除するメソッド

```java
public void deleteQuestionList(
        Users user,
        Long listId,
        Locale locale) {

    QuestionList questionList =
            questionListRepository
                    .findByListIdAndUserId(
                            listId,
                            user.getId())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    messageSource.getMessage(
                                            "questionList.error.notFound",
                                            null,
                                            locale)));

    questionListRepository.delete(questionList);
}
```

---

## QuestionListItemService

> **確認事項：** 以下の追加処理では`questionRepository.findByQuestionId(questionId)`で問題を取得している。AI生成問題には所有者の概念があるため、実際のコードで問題の閲覧・登録可否を検証しているかは別途確認が必要である。ここでは元の実装手順を記録し、未確認のチェックを実装済みとは記さない。


問題をリストへ追加・削除する処理を担当する。

### addQuestionToList - 問題をリストへ追加する

```java
public void addQuestionToList(
        Users user,
        Long listId,
        Long questionId,
        Locale locale) {

    // ユーザーが所有するリストを取得
    QuestionList questionList =
            questionListRepository
                    .findByListIdAndUserId(
                            listId,
                            user.getId())
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    messageSource.getMessage(
                                            "questionList.error.notFound",
                                            null,
                                            locale)));

    // リスト内の問題数が上限に達していないか確認
    if (questionListItemRepository
            .countByQuestionListItemKeyListId(listId) >= 1000) {

        throw new IllegalArgumentException(
                messageSource.getMessage(
                        "questionListItem.error.limitExceeded",
                        null,
                        locale));
    }

    // 追加対象の問題を取得
    Question question =
            questionRepository
                    .findByQuestionId(questionId)
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    messageSource.getMessage(
                                            "question.error.notFound",
                                            null,
                                            locale)));

    // 同じ問題がリストに登録済みでないか確認
    if (questionListItemRepository
            .existsByQuestionListItemKeyListIdAndQuestionListItemKeyQuestionId(
                    listId,
                    questionId)) {

        throw new IllegalArgumentException(
                messageSource.getMessage(
                        "questionListItem.error.duplicateItem",
                        null,
                        locale));
    }

    // リストIDと問題IDから複合主キーを作成
    QuestionListItemKey key = new QuestionListItemKey();
    key.setListId(listId);
    key.setQuestionId(questionId);

    // リストと問題の紐付けを作成
    QuestionListItem questionListItem =
            new QuestionListItem();

    questionListItem.setId(key);
    questionListItem.setQuestionList(questionList);
    questionListItem.setQuestion(question);

    // リストに問題を追加
    questionListItemRepository.save(questionListItem);

    log.debug(
            "問題をリストへ追加 listId={}, userId={}, questionId={}",
            questionList.getListId(),
            questionList.getUser().getId(),
            question.getQuestionId());
}
```

まず`listId`と`userId`を利用して、操作対象がログインユーザー自身のリストであることを確認する。

その後、1つのリストに登録できる問題数の上限である1000問に達していないか確認し、追加対象の`Question`を取得する。

同じ問題がすでに登録されている場合は、複合主キーによるDB側の重複防止に任せるのではなく、Service側で事前に確認して適切なエラーメッセージを返す。

最後に`listId`と`questionId`から`QuestionListItemKey`を作成し、`QuestionListItem`を保存する。

### deleteQuestionFromList - リストから問題を削除する

```java
public void deleteQuestionFromList(
        Users user,
        Long listId,
        Long questionId,
        Locale locale) {

    // ユーザーが所有するリストか確認
    questionListRepository
            .findByListIdAndUserId(
                    listId,
                    user.getId())
            .orElseThrow(
                    () -> new IllegalArgumentException(
                            messageSource.getMessage(
                                    "questionList.error.notFound",
                                    null,
                                    locale)));

    // リストIDと問題IDから複合主キーを作成
    QuestionListItemKey key =
            new QuestionListItemKey();

    key.setListId(listId);
    key.setQuestionId(questionId);

    // リストから問題を削除
    questionListItemRepository
            .deleteByQuestionListItemKey(key);

    log.debug(
            "問題をリストから削除 listId={}, userId={}, questionId={}",
            listId,
            user.getId(),
            questionId);
}
```

削除時も`listId`だけで処理せず、`listId`と`userId`からリストを検索することで、ログインユーザーが所有するリストからのみ削除できるようにする。

ここで取得した`QuestionList`自体はその後の削除処理では利用しない。

目的は、

```text
listIdが存在する
    +
そのlistIdの所有者がログインユーザーである
    ↓
問題を削除

存在しない / 他ユーザーのリスト
    ↓
例外を投げて処理終了
```

という所有者確認である。

### getQuestionListItems - 指定したリストの問題をすべて取得する

この段階では画面表示用DTOをまだ作成していないため、一時的に`Question`の一覧として返す。

```java
public List<Question> getQuestionListItems(
        Users user,
        Long listId,
        Locale locale) {

    // ユーザーが所有するリストか確認
    questionListRepository
            .findByListIdAndUserId(
                    listId,
                    user.getId())
            .orElseThrow(
                    () -> new IllegalArgumentException(
                            messageSource.getMessage(
                                    "questionList.error.notFound",
                                    null,
                                    locale)));

    // リストに登録されている項目を取得
    List<QuestionListItem> questionListItems =
            questionListItemRepository
                    .findByQuestionListItemKeyListId(listId);

    // Questionだけを格納するリストを作成
    List<Question> questions =
            new ArrayList<>();

    // QuestionListItemからQuestionを取り出す
    for (QuestionListItem questionListItem : questionListItems) {
        Question question =
                questionListItem.getQuestion();

        questions.add(question);
    }

    return questions;
}
```

追加・削除と同様に、先に所有者確認を行ってからリスト内の問題を取得する。

### getQuestionListItem - 指定したquestionIdの問題を取得する

```java
public Question getQuestionListItem(
        Users user,
        Long listId,
        Long questionId,
        Locale locale) {

    // ユーザーが所有するリストか確認
    questionListRepository
            .findByListIdAndUserId(
                    listId,
                    user.getId())
            .orElseThrow(
                    () -> new IllegalArgumentException(
                            messageSource.getMessage(
                                    "questionList.error.notFound",
                                    null,
                                    locale)));

    // 指定した問題がリストに登録されているか確認
    if (!questionListItemRepository
            .existsByQuestionListItemKeyListIdAndQuestionListItemKeyQuestionId(
                    listId,
                    questionId)) {

        throw new IllegalArgumentException(
                messageSource.getMessage(
                        "questionListItem.error.notFound",
                        null,
                        locale));
    }

    // 指定したIDの問題を取得
    Question question =
            questionRepository
                    .findByQuestionId(questionId)
                    .orElseThrow(
                            () -> new IllegalArgumentException(
                                    messageSource.getMessage(
                                            "question.error.notFound",
                                            null,
                                            locale)));

    return question;
}
```

### QuestionListItemService実装に伴い追加したRepositoryメソッド

#### QuestionRepository

```java
Optional<Question> findByQuestionId(Long questionId);
```

#### QuestionListItemRepository

```java
boolean existsByQuestionListItemKeyListIdAndQuestionListItemKeyQuestionId(
        Long listId,
        Long questionId);

QuestionListItem
        findByQuestionListItemKeyListIdAndQuestionListItemKeyQuestionId(
                Long listId,
                Long questionId);

List<QuestionListItem>
        findByQuestionListItemKeyListId(Long listId);

void deleteByQuestionListItemKey(
        QuestionListItemKey key);

int countByQuestionListItemKeyListId(
        Long listId);
```

---

# 問題リスト詳細表示のDTO化

```text
git commit -m "Implement question list detail view and interactions"
```

## QuestionListItemDto

```java
public interface QuestionListItemDto {

    Long getQuestionId();

    String getChineseText();

    String getJapaneseText();

    Difficulty getDifficulty();

    Evaluation getEvaluation();

    boolean isFavorite();

    boolean isAiGenerated();

    String getPinyin();

    String getZhuyin();

    String getAlternativeAnswer();

    String getAlternativeAnswerPinyin();

    String getAlternativeAnswerZhuyin();

    String getStructureName();
}
```

今回は一覧用・詳細用でDTOを分割せず、同じ`QuestionListItemDto`を利用する。

一覧では、

```text
chineseText
japaneseText
difficulty
evaluation
favorite
aiGenerated
```

を使用し、詳細モーダルでは、

```text
pinyin
zhuyin
alternativeAnswer
alternativeAnswerPinyin
alternativeAnswerZhuyin
structureName
```

も利用する。

---

## QuestionListItemRepository

複数のEntityから必要な情報を取得する。

```java
@Query(value = """
        SELECT
            q.question_id AS questionId,
            q.chinese_text AS chineseText,
            q.japanese_text AS japaneseText,
            q.difficulty AS difficulty,
            sh.evaluation AS evaluation,

            CASE
                WHEN f.question_id IS NOT NULL
                THEN TRUE
                ELSE FALSE
            END AS favorite,

            q.ai_generated AS aiGenerated,
            q.pinyin AS pinyin,
            q.zhuyin AS zhuyin,
            q.alternative_answer AS alternativeAnswer,
            q.alternative_answer_pinyin
                AS alternativeAnswerPinyin,
            q.alternative_answer_zhuyin
                AS alternativeAnswerZhuyin,
            s.name AS structureName

        FROM question_list_item qli

        JOIN question q
            ON qli.question_id = q.question_id

        JOIN structure s
            ON q.structure_id = s.structure_id

        LEFT JOIN study_history sh
            ON q.question_id = sh.question_id
            AND sh.user_id = :userId

        LEFT JOIN favorite f
            ON q.question_id = f.question_id
            AND f.user_id = :userId

        WHERE qli.list_id = :listId
        """,
        nativeQuery = true)
List<QuestionListItemDto> findQuestionListItems(
        Long listId,
        Long userId);
```

これにより、

```text
question_list_item
    ↓
question
    ↓
structure

+ study_history
+ favorite
```

から、問題リスト画面に必要な情報を1つのDTOとして取得できる。

---

## QuestionListItemService.getQuestionListItems()

戻り値を`List<Question>`から`List<QuestionListItemDto>`へ変更する。

```java
public List<QuestionListItemDto> getQuestionListItems(
        Users user,
        Long listId,
        Locale locale) {

    // ユーザーが所有するリストか確認
    questionListRepository
            .findByListIdAndUserId(
                    listId,
                    user.getId())
            .orElseThrow(
                    () -> new IllegalArgumentException(
                            messageSource.getMessage(
                                    "questionList.error.notFound",
                                    null,
                                    locale)));

    // DTOとして取得
    return questionListItemRepository
            .findQuestionListItems(
                    listId,
                    user.getId());
}
```

---

## QuestionListController

リスト管理画面では、

```text
GET  /user/question-list/list
POST /user/question-list/create
GET  /user/question-list/detail
POST /user/question-list/delete
POST /user/question-list/edit
```

を利用する。

新規作成・編集については専用画面へ遷移せず一覧画面上のBootstrap
Modalを利用するため、作成画面・編集画面表示専用のGETは作成しない。

### getUserQuestionList - ユーザーの問題リスト一覧を表示

```java
@GetMapping("/user/question-list/list")
public String getUserQuestionList(
        @AuthenticationPrincipal UserDetails loginUser,
        Model model) {

    // ログインユーザーを取得
    Users user =
            userAccountService
                    .getUserOne(loginUser.getUsername());

    // ユーザーが所有するリストをすべて取得
    List<QuestionList> questionLists =
            questionListService
                    .getQuestionLists(user);

    // リスト一覧を画面に渡す
    model.addAttribute(
            "questionLists",
            questionLists);

    return "/user/question-list/list";
}
```

### postUserQuestionListCreate - リストを新規作成する

```java
@PostMapping("/user/question-list/create")
public String postUserQuestionListCreate(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam String listName,
        RedirectAttributes redirectAttributes,
        Locale locale) {

    // ログインユーザーを取得
    Users user = getLoginUser(loginUser);

    try {
        // 新しいリストを作成
        questionListService.createQuestionList(
                user,
                listName,
                locale);

        // 作成完了メッセージを設定
        redirectAttributes.addFlashAttribute(
                "successMessage",
                messageSource.getMessage(
                        "questionList.success.create",
                        null,
                        locale));

    } catch (IllegalArgumentException e) {
        // エラーメッセージを設定
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                e.getMessage());
    }

    // リスト一覧画面へ戻る
    return "redirect:/user/question-list/list";
}
```

### postUserQuestionListDelete - リストを削除する

```java
@PostMapping("/user/question-list/delete")
public String postUserQuestionListDelete(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        RedirectAttributes redirectAttributes,
        Locale locale) {

    // ログインユーザーを取得
    Users user = getLoginUser(loginUser);

    try {
        // 指定したリストを削除
        questionListService.deleteQuestionList(
                user,
                listId,
                locale);

        // 削除完了メッセージを設定
        redirectAttributes.addFlashAttribute(
                "successMessage",
                messageSource.getMessage(
                        "questionList.success.delete",
                        null,
                        locale));

    } catch (IllegalArgumentException e) {
        // エラーメッセージを設定
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                e.getMessage());
    }

    // リスト一覧画面へ戻る
    return "redirect:/user/question-list/list";
}
```

### postUserQuestionListEdit - リスト名を変更する

```java
@PostMapping("/user/question-list/edit")
public String postUserQuestionListEdit(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        @RequestParam String listName,
        RedirectAttributes redirectAttributes,
        Locale locale) {

    // ログインユーザーを取得
    Users user = getLoginUser(loginUser);

    try {
        // 指定したリストの名前を変更
        questionListService.editQuestionList(
                user,
                listId,
                listName,
                locale);

        // 変更完了メッセージを設定
        redirectAttributes.addFlashAttribute(
                "successMessage",
                messageSource.getMessage(
                        "questionList.success.edit",
                        null,
                        locale));

    } catch (IllegalArgumentException e) {
        // エラーメッセージを設定
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                e.getMessage());
    }

    // リスト一覧画面へ戻る
    return "redirect:/user/question-list/list";
}
```

---

## QuestionListItemController

問題リスト項目については、

```text
GET  /user/question-list/item
POST /user/question-list/item/add
POST /user/question-list/item/delete
```

を利用する。

### getUserQuestionListItem - 指定した問題の詳細を取得する

```java
@GetMapping("/user/question-list/item")
@ResponseBody
public Question getUserQuestionListItem(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        @RequestParam Long questionId,
        Locale locale) {

    // ログインユーザーを取得
    Users user = getLoginUser(loginUser);

    // 指定したリスト内の問題を取得
    Question question =
            questionListItemService
                    .getQuestionListItem(
                            user,
                            listId,
                            questionId,
                            locale);

    return question;
}
```

### postQuestionListItemAdd - リストに問題を追加する

```java
@PostMapping("/user/question-list/item/add")
public String postQuestionListItemAdd(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        @RequestParam Long questionId,
        Locale locale) {

    // ログインユーザーを取得
    Users user = getLoginUser(loginUser);

    // 指定した問題をリストに追加
    questionListItemService.addQuestionToList(
            user,
            listId,
            questionId,
            locale);

    // リスト一覧画面へ戻る
    return "redirect:/user/question-list/list";
}
```

この時点では、問題を1つのリストへ直接追加するための専用POSTエンドポイントとして実装した。

### postQuestionListItemDelete - リストから問題を削除する

```java
@PostMapping("/user/question-list/item/delete")
public String postQuestionListItemDelete(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        @RequestParam Long questionId,
        Locale locale) {

    // ログインユーザーを取得
    Users user = getLoginUser(loginUser);

    // 指定した問題をリストから削除
    questionListItemService.deleteQuestionFromList(
            user,
            listId,
            questionId,
            locale);

    // リスト一覧画面へ戻る
    return "redirect:/user/question-list/list";
}
```

---

## /user/question-list/list.html

ユーザーが所有する問題リストを一覧表示する画面を追加する。

この画面から、

```text
新規作成
詳細
編集
削除
```

を行えるようにする。

リスト一覧そのものや新規作成・編集・削除の機能は、詳細表示方式を変更した後も利用する共通部分である。

---

## /user/menu.html

ユーザーメニューから問題リスト管理画面へ移動できるリンクを追加する。

```html
<a th:href="@{/user/question-list/list}"
   class="list-group-item list-group-item-action">

    <i class="bi bi-list-check me-2"></i>

    <span th:text="#{user.menu.questionList}">
        問題リスト
    </span>

</a>
```

---

---

## まとめ

問題リスト機能の基盤として、リスト本体を管理する`question_list`と、リストと問題の対応を保持する`question_list_item`を作成した。後者は`list_id`と`question_id`の複合主キーにより、同じリストへの同一問題の重複登録を防ぐ。

Java側ではEntityとRepositoryを追加し、Serviceでリストの作成・名称変更・削除、問題の追加・削除・取得を実装した。操作対象のリストは`listId`とログインユーザーの`userId`を組み合わせて取得し、他ユーザーのリストを操作しないようにしている。

また、詳細表示に必要な問題情報を複数テーブルから取得する`QuestionListItemDto`とRepositoryの検索処理を用意した。これらを基盤として、次の**029-02**ではJavaScriptによる詳細表示と、各学習画面からリストを操作する機能を記録する。

## 実装上の確認事項

- `QuestionListItem`の複合主キーフィールドは掲載したEntity定義の`id`に合わせて、保存例のsetter表記を`setId(key)`に統一した。実際のソースコードが別名の場合は、EntityとServiceの双方を照合する。
- リスト名の重複判定は、編集対象自身と同じ名前で保存する場合も該当する可能性がある。仕様に応じて、自身のリストIDを除外する条件を検討する。
- AI生成問題など、ユーザーごとに閲覧権限が異なる問題をリストへ登録する場合は、リスト所有者の確認に加え、対象問題へのアクセス権限も確認する必要がある。
