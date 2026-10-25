# 029-02 問題リスト機能②｜JavaScriptによる操作と機能拡張【学習ログ】

## 概要

029-01では、問題リストと問題の多対多の関係、中間テーブル、複合主キー、表示用DTO、更新日時の扱いについて整理した。

029-02では、**ユーザーが所有するリストと問題の登録状態を画面へ反映し、JavaScriptから一括更新する方法**を中心に、SQLのJOIN条件、チェックボックスの動的生成、`URLSearchParams`、`fetch()`、Bootstrap Modalの操作を振り返る。

さらに、学習中のリスト新規作成、AI生成問題との連携、並び順の統一、JavaScriptファイルの責務整理、多言語対応についても扱う。問題リスト詳細画面をSpring MVC・Thymeleafで再設計した内容は029-03で整理する。

---

## 1. `LEFT JOIN`の`ON`と`WHERE`の違い

今回特にSQLで重要だったのが、問題をリストへ追加するモーダルの登録状態を取得する処理だった。

モーダルでは、

```text
☐ HSK6
☑ 苦手問題
☐ 後で復習
```

のように、ユーザーが所有する**すべてのリスト**を表示したうえで、その問題が登録されているリストだけチェック済みにする必要がある。

そのため、

```sql
SELECT
    ql.user_id,
    ql.list_id,
    ql.list_name,
    i.question_id

FROM question_list ql

LEFT JOIN question_list_item i
    ON ql.list_id = i.list_id
    AND i.question_id = :questionId

WHERE ql.user_id = :userId;
```

という形にした。

ここで重要だったのが、

```sql
AND i.question_id = :questionId
```

を`WHERE`ではなく`LEFT JOIN`の`ON`へ書くことだった。

例えば、

```text
question_list

1 HSK6
2 苦手問題
3 後で復習
```

に対して、

```text
question_list_item

list_id | question_id
--------+------------
1       | 50
1       | 60
2       | 100
3       | 70
```

があり、現在の問題が`questionId = 100`だった場合、

```text
list_id | list_name | question_id
--------+-----------+------------
1       | HSK6      | NULL
2       | 苦手問題   | 100
3       | 後で復習   | NULL
```

という結果が欲しい。

つまり、

```text
NULL
    → この問題は未登録

100
    → この問題は登録済み
```

として判定できる。

`questionId`の条件を`WHERE`へ置いてしまうと、`NULL`になった未登録リストまで結果から除外されてしまい、「ユーザーの全リストを表示する」という目的を達成できない。

今回の実装で、**`LEFT JOIN`ではどの行をJOIN対象にするかという条件を`ON`へ置くことと、JOIN後の結果そのものを絞り込む`WHERE`では意味が異なる**ことを確認できた。

---

---

## 2. DBの状態をチェックボックスとして表現する

今回のリスト追加モーダルでは、チェックボックスを単なる入力フォームとして使うのではなく、現在のDB状態そのものを表現するようにした。

例えば、

```text
question_list_itemに存在する
    → ☑

question_list_itemに存在しない
    → ☐
```

という関係である。

バックエンドからは、

```json
[
  {
    "listId": 1,
    "listName": "HSK6",
    "registered": false
  },
  {
    "listId": 2,
    "listName": "苦手問題",
    "registered": true
  }
]
```

のようなデータを取得する。

JavaScriptでは、

```javascript
checkbox.checked =
    questionList.registered;
```

とすることで、DBの登録状態をそのままチェックボックスへ反映できる。

また、リスト自体はユーザーによって自由に追加・削除されるため、HTMLへチェックボックスを固定で書くことはできない。

そこで、

```javascript
const checkbox =
    document.createElement("input");

checkbox.type = "checkbox";

checkbox.value =
    questionList.listId;

checkbox.checked =
    questionList.registered;
```

のようにJavaScriptから動的に生成した。

今回の実装から、**バックエンドから取得した状態をJavaScriptでUIへ変換し、DBの状態と画面上の状態を対応させる方法**を確認できた。

---

---

## 3. 複数のチェック状態を一括更新する

お気に入り機能では、

```text
お気に入り
    ON / OFF
```

という1つの状態を更新すればよかった。

一方、リストでは、

```text
☑ HSK6
☐ 苦手問題
☑ 後で復習
☐ 把構文
```

のように複数の状態を同時に扱う。

そこで保存ボタンを押したときに、

```javascript
const checkedLists =
    document.querySelectorAll(
        ".question-list-checkbox:checked"
    );
```

として、チェックされているリストだけを取得する。

送信用データには`URLSearchParams`を利用した。

```javascript
const params =
    new URLSearchParams();

params.append(
    "questionId",
    questionId
);

checkedLists.forEach(checkbox => {

    params.append(
        "selectedListIds",
        checkbox.value
    );

});
```

例えばリスト2と5が選択されている場合、

```text
questionId=10
selectedListIds=2
selectedListIds=5
```

という形になる。

Spring側では、

```java
List<Long> selectedListIds
```

として複数の値を受け取ることができる。

バックエンドでは現在の登録状態と送信された状態を比較し、

```text
未登録 + チェックあり
    → 追加

登録済み + チェックなし
    → 削除

登録済み + チェックあり
    → 何もしない

未登録 + チェックなし
    → 何もしない
```

という差分だけを更新する。

今回の実装から、**画面から「追加」「削除」を個別に送信するのではなく、ユーザーが最終的に望んでいる状態を送信し、バックエンド側で現在の状態との差分を計算する方法**を学んだ。

---

---

## 4. `URLSearchParams`とSpringの`List`パラメータ

今回初めて意識したのが、同じパラメータ名を複数回送信する方法だった。

```javascript
params.append(
    "selectedListIds",
    2
);

params.append(
    "selectedListIds",
    5
);
```

とすると、

```text
selectedListIds=2&selectedListIds=5
```

という形式になる。

Spring側ではこれを、

```java
@RequestParam
List<Long> selectedListIds
```

として受け取ることができる。

つまり、JavaScriptの配列をJSONへ変換しなくても、通常の`application/x-www-form-urlencoded`形式で複数値を送ることができる。

また、すべてのチェックを外した場合は`selectedListIds`自体が送信されないため、Controller側では空リストとして扱う必要がある。

今回の実装で、**同名のリクエストパラメータを複数送信するとSpring側で`List`として受け取れることと、0件の場合についても考慮する必要がある**ことを確認できた。

---

---

## 5. `fetch()`を利用して画面遷移せずDBを更新する

リスト登録状態の更新には`fetch()`を利用した。

```javascript
fetch("/user/question-list/item/update", {

    method: "POST",

    headers: {

        "Content-Type":
            "application/x-www-form-urlencoded",

        [csrfHeader]:
            csrfToken
    },

    body:
        params.toString()
});
```

これによって、

```text
問題画面
    ↓
リストモーダルを開く
    ↓
登録状態を変更
    ↓
保存
    ↓
DBを更新
    ↓
モーダルを閉じる
    ↓
同じ問題画面に残る
```

という操作ができる。

通常のフォーム送信でページ遷移させると、学習中の画面から移動したり再読み込みしたりする必要がある。

今回のような「現在の学習状態を維持したまま小さなデータだけ変更したい」処理では、`fetch()`による非同期更新が適していることが分かった。

また、Spring Securityを利用しているため、POSTでは、

```javascript
const csrfToken =
    document.querySelector(
        'meta[name="_csrf"]'
    ).content;

const csrfHeader =
    document.querySelector(
        'meta[name="_csrf_header"]'
    ).content;
```

としてCSRF情報を取得し、リクエストヘッダーへ設定する必要があることも再確認した。

---

---

## 6. Bootstrap ModalをJavaScriptから操作する

リスト登録が完了した後はページ遷移せず、現在開いているモーダルだけを閉じる。

そのため、

```javascript
const modalElement =
    document.getElementById(
        "questionListModal"
    );

const modal =
    bootstrap.Modal.getInstance(
        modalElement
    );

modal.hide();
```

とした。

ここでは新しいModalを作るのではなく、

```javascript
bootstrap.Modal.getInstance()
```

によって現在表示されているModalのインスタンスを取得している。

その後、

```javascript
modal.hide();
```

によって閉じる。

今回の実装で、**BootstrapのモーダルはHTML属性だけで開閉するだけでなく、JavaScriptから既存インスタンスを取得して操作できる**ことを確認できた。

---

---

## 7. モーダル内でリストを新規作成した後に再取得する

最初の実装では、リスト選択モーダルには既存リストだけを表示していた。

しかし、学習中に、

```text
この問題用に新しいリストを作りたい
```

という場合、リスト管理画面まで戻る必要がある。

そこでモーダル内からリストを新規作成できるようにした。

リスト作成後は、

```javascript
return loadQuestionLists();
```

としてリスト一覧だけを再取得する。

ここで重要だったのが、

```javascript
questionListButton.click();
```

を利用しなかったことだった。

ボタンのクリックイベントには、

```text
モーダルを開く
リストを取得する
```

という2つの意味が含まれている。

そのため、単に一覧を更新したいだけなのにボタンを再クリックすると、Bootstrap側のモーダル操作まで再実行されてしまう。

そこで、

```javascript
function loadQuestionLists() {
    ...
}
```

として「リストを取得して表示する処理」を独立した関数にした。

これによって、

```text
モーダルを開く
    → loadQuestionLists()

新しいリストを作成
    → loadQuestionLists()
```

の両方から同じ処理を呼び出せる。

今回の実装から、**イベントそのものを再実行するのではなく、イベント内で行っている本来の処理を関数として切り出し、その関数を再利用する方が副作用を防ぎやすい**ことを確認できた。

---

---

## 8. AI生成問題は保存されるまで`questionId`を持たない

通常学習や復習では、表示している問題はすでに`question`テーブルに存在するため、最初から`questionId`を利用できる。

一方、AI生成学習では、生成直後の問題はまだDBへ保存されていない。

そのため、

```text
AIが問題を生成
    ↓
まだquestionテーブルに存在しない
    ↓
questionIdがない
    ↓
リストへ登録できない
```

という状態になる。

そこでAI生成問題では、最初はリストボタンを表示せず、問題を保存した後に利用可能にする。

```javascript
const questionId =
    await response.json();

favoriteButton.dataset.questionId =
    questionId;

questionListButton.dataset.questionId =
    questionId;
```

保存APIから返された`questionId`を、お気に入りだけでなくリストボタンにも設定する。

これによって、

```text
AI生成
    ↓
問題をDBへ保存
    ↓
questionId取得
    ↓
お気に入り利用可能
リスト利用可能
理解度登録可能
```

という流れになった。

今回の実装で、**画面上に存在するデータとDB上に永続化されているデータは同じではなく、DBのレコードを対象とする機能では永続化後のIDが必要になる**ことを改めて確認できた。

---

---

## 9. 「最近使ったリスト」の並び順は取得箇所すべてで統一する

リスト管理画面を、

```text
updated_at DESC
```

で取得するよう変更したことで、最近変更したリストが上に表示されるようになった。

しかし、その後question画面のリスト選択モーダルを確認すると、こちらは以前の並び順のままだった。

原因は、

```text
リスト管理画面用の取得処理
リスト選択モーダル用の取得処理
```

が別々に存在していたためだった。

つまり、同じ「ユーザーのリスト一覧」であっても、Repositoryの取得処理が異なれば、一方だけ修正してももう一方には反映されない。

そこでモーダル側についても更新日時の降順で取得するよう修正した。

今回の実装から、**同じデータを複数の画面やAPIから取得している場合、仕様変更時にはそのデータを取得しているすべての経路を確認する必要がある**ことを経験できた。

---

---

## 10. JavaScriptのファイル名と責務を一致させる

リスト機能を追加したことで、各question画面のJavaScriptが増え、既存のファイル構成の問題が分かりやすくなった。

実装前は、

```text
practice/question.html
    → /practice/question.js

review/question.html
    → /practice.js

ai-practice/question.html
    → /practice.js
    → /ai-practice/question.js
```

となっていた。

特に、

```text
/practice.js
```

という名前なのに、実際には復習やAI生成学習から利用されており、ファイル名と責務が一致していなかった。

さらにAI生成学習では、

```text
/practice.js
/ai-practice/question.js
```

へ処理が分散していた。

そこでAI生成学習の処理を、

```text
/ai-practice/question.js
```

へ統合し、不要になった`/practice.js`を削除した。

これによって、

```text
/practice/question.js
    → 通常学習・復習

/ai-practice/question.js
    → AI生成学習
```

という構成になった。

今回の整理から、**ファイル名は単に動けばよいのではなく、そのファイルがどの画面・どの責務を担当しているのか分かる名前にしておくことが保守性につながる**ことを確認できた。

---

---

## 11. 外部JavaScriptへ`messages.properties`の値を渡す

問題リスト画面やリスト選択モーダルでは、日本語・簡体字・繁体字の多言語対応が必要になる。

ThymeleafのHTML内であれば、

```html
<span th:text="#{questionList.detail.chinese}">
```

のように`messages.properties`を直接参照できる。

しかし、外部JavaScriptファイルでは、

```text
#{questionList.detail.chinese}
```

のようなThymeleaf式を直接処理できない。

そこでHTML側で、

```html
<div id="questionListMessages"
     class="d-none"
     th:data-empty="#{questionList.detail.empty}"
     th:data-load-error="#{questionList.detail.loadError}">
</div>
```

のように`data-*`属性へメッセージを設定する。

JavaScriptではその要素から値を取得する。

つまり、

```text
messages.properties
        ↓
Thymeleaf
        ↓
HTMLのdata-*属性
        ↓
外部JavaScript
```

という経路でメッセージを渡す。

また、実装後にリスト選択モーダルの一部だけ日本語のまま残っていたことで、HTMLへ直接書いた文言は言語設定を変更しても切り替わらないことも確認した。

今回の実装から、**多言語対応では画面に表示される文字列をHTMLだけでなくJavaScriptが生成している部分まで確認する必要があり、外部JavaScriptへはHTMLを経由してメッセージを渡す方法がある**ことを学んだ。

---

---

## まとめ

今回の学習では、DBに保存された「問題とリストの関係」を、チェックボックスの状態として表示し、ユーザーが変更した結果をバックエンドへ送信するまでの流れを確認できた。

特に、`LEFT JOIN`の条件を`ON`へ置くことで未登録のリストも表示できること、複数のチェック状態を同名パラメータで送信できること、送信された最終状態とDBの現在状態との差分だけを更新できることが重要だった。

また、`fetch()`とBootstrap Modalを組み合わせることで学習画面を維持したまま登録状態を変更でき、モーダル内でのリスト作成やAI生成問題の保存後の連携にも対応できた。実装が広がるにつれて、リストの並び順、JavaScriptファイルの責務、多言語メッセージの受け渡しを複数画面で揃える必要があることも分かった。

029-03では、JavaScript主体だった問題リスト詳細表示を、Spring MVC・Thymeleafによる独立ページへ再設計した過程から得られた学びを整理する。
