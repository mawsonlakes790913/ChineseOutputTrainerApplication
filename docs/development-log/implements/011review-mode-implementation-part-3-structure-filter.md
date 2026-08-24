# 011 復習モードの実装 その3 - Structureによる出題条件の追加

復習モードに、文法・構造（Structure）による出題条件を追加する。

あわせて、これまで `Question` の文字列フィールドとして管理していた `structure` を独立したマスタテーブルへ変更し、復習メニューから各文法・構造の説明を確認できるようにする。

---

## 1. 復習メニューにStructureによる絞り込みを追加

`/review/menu` に文法・構造による絞り込み条件を追加する。

### QuestionRepository

登録されている文法・構造を取得するため、`findDistinctStructures()` を追加した。

```java
@Query(value = """
        SELECT DISTINCT structure
        FROM question
        ORDER BY structure
        """, nativeQuery = true)
List<String> findDistinctStructures();
```

### ReviewService

```java
public List<String> findStructures() {
    return questionRepository.findDistinctStructures();
}
```

### ReviewController

`/review/menu` 表示時に文法・構造一覧をModelへ追加する。

```java
model.addAttribute(
        "structures",
        reviewService.findStructures());
```

### /review/menu.html

文法・構造を複数選択できるチェックボックスを追加した。

あわせて、

- すべて選択
- すべて解除

の操作を追加した。

### /review/menu.js

文法・構造のチェック状態を `/review/count` へ送信し、選択状態が変更された場合に問題数を再取得するようにした。

また、「すべて選択」「すべて解除」操作にも `updateCount()` を追加した。

### 実行確認

`/review/menu` に文法・構造の選択欄が表示され、選択状態に応じて問題数が変化することを確認した。

![](../../images/0011-01.png)

![](../../images/0011-02.png)

![](../../images/0011-03.png)

---

## 2. 文法・構造欄を折りたたみ可能にする

文法・構造の選択肢が増えても復習メニューが縦に長くなりすぎないよう、文法・構造欄を折りたたみ可能にした。

### /static/css/review/menu.css

```css
.structure-list {
    max-height: 100px;
    overflow: hidden;
}

.structure-list.expanded {
    max-height: none;
}
```

### /review/menu.html

文法・構造一覧に `structureList` を設定する。

```html
<div id="structureList"
     class="d-flex flex-wrap gap-2 structure-list">
```

一覧の下に表示切替ボタンを追加する。

```html
<div class="text-center mt-3">

    <button type="button"
            id="toggleStructures"
            class="btn btn-link btn-sm"
            th:data-show-text="#{review.menu.structure.showAll}"
            th:data-hide-text="#{review.menu.structure.collapse}"
            th:text="#{review.menu.structure.showAll}">
        すべて表示
    </button>

</div>
```

### /review/menu.js

```javascript
const structureList =
    document.getElementById("structureList");

const toggleStructuresButton =
    document.getElementById("toggleStructures");

toggleStructuresButton.addEventListener("click", () => {

    const expanded =
        structureList.classList.toggle("expanded");

    toggleStructuresButton.textContent =
        expanded
            ? toggleStructuresButton.dataset.hideText
            : toggleStructuresButton.dataset.showText;
});
```

### 実行確認

初期状態では文法・構造欄が折りたたまれ、「すべて表示」を押すことで全件表示できることを確認した。

![](../../images/0011-04.png)

![](../../images/0011-05.png)

---

## 3. Structureをマスタテーブル化

`Question.structure` に文字列として保持していた文法・構造を、独立した `Structure` Entityとして管理するように変更する。

設計変更に伴い、実装前にrequirementsフォルダ内の関連設計書を更新した。

設計変更の詳細についてはrequirementsフォルダ内の各ファイルに記載する。

### Structure.java

```java
@Entity
@Table(name = "structure")
@Getter
@Setter
public class Structure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "structure_id")
    private Long structureId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description_zh_cn", nullable = false)
    private String descriptionZhCn;

    @Column(name = "description_zh_tw", nullable = false)
    private String descriptionZhTw;
}
```

### StructureRepository

```java
public interface StructureRepository
        extends JpaRepository<Structure, Long> {
}
```

### Structureマスタデータの登録

既存の `question.structure` に登録されている20種類の文法・構造を `structure` テーブルへ登録した。

```sql
INSERT INTO structure
    (name, description_zh_cn, description_zh_tw)
VALUES
...
;
```

各Structureについて、

- 文法・構造名
- 大陸普通話向け説明
- 台湾華語向け説明

を保持する。

### Question.java

変更前：

```java
@Column(name = "structure", nullable = false)
private String structure;
```

変更後：

```java
@ManyToOne
@JoinColumn(name = "structure_id")
private Structure structure;
```

この時点では既存データ移行のため `nullable = false` は設定しない。

### 既存データの移行

旧 `structure` カラムの文字列と `structure.name` を対応させ、既存60問へ `structure_id` を設定した。

```sql
UPDATE question q
SET structure_id = s.structure_id
FROM structure s
WHERE q.structure = s.name;
```

移行結果を確認する。

```sql
SELECT
    q.question_id,
    q.structure AS old_structure,
    q.structure_id,
    s.name AS new_structure
FROM question q
LEFT JOIN structure s
    ON q.structure_id = s.structure_id
ORDER BY q.question_id;
```

![](../../images/0011-06.png)

![](../../images/0011-07.png)

移行漏れがないことを確認後、旧カラムを削除した。

```sql
ALTER TABLE question
DROP COLUMN structure;
```

削除後、60問すべてについてStructureとの関連を取得できることを確認した。

```sql
SELECT
    q.question_id,
    q.structure_id,
    s.name
FROM question q
JOIN structure s
    ON q.structure_id = s.structure_id
ORDER BY q.question_id;
```

![](../../images/0011-08.png)

最後に `Question.structure` を必須関連に変更した。

```java
@ManyToOne
@JoinColumn(name = "structure_id", nullable = false)
private Structure structure;
```

---

## 4. 復習機能を新しいStructure構造へ対応

Structureのマスタ化に伴い、復習メニュー表示、問題数取得、問題取得処理を `structure_id` ベースへ変更する。

### 4.1 復習メニュー表示

`ReviewService` が `StructureRepository` を使用するように変更した。

```java
public List<Structure> findStructures() {
    return structureRepository.findAll();
}
```

不要になった `QuestionRepository.findDistinctStructures()` は削除した。

`/review/menu.html` では、Structure EntityからIDと名称を取得するよう変更した。

```html
<label th:each="structure : ${structures}"
       class="bg-light rounded px-3 py-2">

    <input class="form-check-input me-2"
           type="checkbox"
           name="structureIds"
           th:value="${structure.structureId}"
           checked>

    <span th:text="${structure.name}">
        文法・構造
    </span>

</label>
```

`/review/menu.js` についても、`structures` ではなく `structureIds` を送信するよう変更した。

### 実行確認

`/review/menu` でStructure一覧がこれまでどおり表示されることを確認した。

![](../../images/0011-09.png)

### 4.2 問題数取得

`StudyHistoryRepository.countReviewQuestions()` のStructure条件をIDベースへ変更した。

```sql
AND q.structure_id IN (:structureIds)
```

引数も変更する。

```java
@Param("structureIds") List<Long> structureIds
```

`ReviewService.countReviewQuestions()` および `ReviewController.getReviewCount()` についても `List<Long> structureIds` を受け渡すよう変更した。

### 実行確認

Structureの選択状態に応じて問題数が正しく更新されることを確認した。

![](../../images/0011-10.png)

### 4.3 復習問題取得

`StudyHistoryRepository.findReviewQuestions()` にStructure IDによる絞り込みを追加した。

```sql
AND q.structure_id IN (:structureIds)
```

引数にもStructure IDを追加した。

```java
@Param("structureIds") List<Long> structureIds
```

`ReviewService.getQuestion()` にも `structureIds` を追加し、Repositoryへ渡すよう変更した。

`ReviewController.getReviewStart()` では、

```java
@RequestParam(name = "structureIds", required = false)
List<Long> structureIds
```

として選択されたStructure IDを受け取り、`ReviewService` へ渡すよう変更した。

これにより、復習メニューで指定したStructure条件が実際に取得される復習問題にも反映されるようになった。

---

## 5. 文法・構造の説明を表示

復習メニューの各文法・構造にカーソルを合わせると、Structureに登録されている説明を表示するようにした。

### /review/menu.html

Bootstrap Tooltipを設定する。

```html
<label th:each="structure : ${structures}"
       class="bg-light rounded px-3 py-2"
       data-bs-toggle="tooltip"
       data-bs-placement="top"
       th:data-bs-title="${session.languageVariant != null
                           && session.languageVariant.name() == 'TAIWAN'
                           ? structure.descriptionZhTw
                           : structure.descriptionZhCn}">
```

現在の学習対象言語に応じて使用する説明を切り替える。

```text
MAINLAND
└── descriptionZhCn

TAIWAN
└── descriptionZhTw
```

`languageVariant` が `null` の場合は `descriptionZhCn` を使用する。

### /review/menu.js

Bootstrap Tooltipを初期化する。

```javascript
// =========================
// 文法・構造の説明
// =========================

const tooltipTriggerList =
    document.querySelectorAll('[data-bs-toggle="tooltip"]');

tooltipTriggerList.forEach(element => {
    new bootstrap.Tooltip(element);
});
```

`data-bs-toggle="tooltip"` が設定されている要素を取得し、それぞれにBootstrap Tooltipを生成する。

### /static/css/review/menu.css

Tooltipを読みやすいサイズに調整する。

```css
.tooltip-inner {
    max-width: 400px;
    padding: 12px 16px;
    font-size: 1rem;
    text-align: left;
}
```

### 実行確認

`/review/menu` の各文法・構造にカーソルを合わせると、現在の学習対象言語に対応した説明がTooltipとして表示されることを確認した。

![](../../images/0011-11.png)

---

## 実装結果

復習モードにStructureによる絞り込みを追加した。

また、Structureを独立したマスタテーブルとして管理する構造へ変更し、既存60問のデータを新しい構造へ移行した。

これに伴って復習メニュー、問題数取得、復習問題取得を `structure_id` ベースへ変更した。

さらに、復習メニューでは各Structureの説明を現在の学習対象言語に応じて表示できるようにした。

## 次回の実装

ユーザーメニューの実装を行う。

- プロフィール
- 問題検索
- 設定