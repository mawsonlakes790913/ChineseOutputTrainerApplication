# 029-02 問題リスト機能②｜JavaScriptによる操作と機能拡張

## 概要

029-01では、問題リストを管理するためのDB・Entity・Repository・Service・DTOと、リストの作成・編集・削除などの基本機能を実装した。

本章では、まず開発当初に採用した**JavaScriptによる問題リスト詳細表示**を記録する。その後、通常学習・復習・AI生成学習の各問題画面からリストを操作できるようにし、モーダル内でのリスト作成、表示順、多言語対応などを追加した過程をまとめる。

初期の詳細表示方式は動作するところまで実装したものの、画面生成をJavaScriptが広く担当する構成となったため、最終的には採用しなかった。ここでは試行錯誤の記録として残し、**Spring MVCとThymeleafによる詳細ページの再設計は029-03**で扱う。

---

# 旧設計 - JavaScriptによる問題リスト詳細表示

ここからは、問題リスト機能を最初に実装した際に採用していた詳細表示方式をまとめる。

この方式では、問題リスト一覧画面の「詳細」ボタンを押した際に、JavaScriptから収録問題を取得し、同じ画面内へ動的に表示していた。

この方式は一度動作するところまで実装したが、後に独立した詳細ページをSpring
MVCとThymeleafで表示する構成へ変更するため、**最終的には使用しない旧設計**となる。

## Controllerから収録問題をJSONで返す

初期段階では、`GET /user/question-list/detail`から`Question`の一覧をそのままJSON形式で返していた。

``` java
@GetMapping("/user/question-list/detail")
@ResponseBody
public List<Question> getUserQuestionListDetail(
        @AuthenticationPrincipal UserDetails loginUser,
        @RequestParam Long listId,
        Locale locale) {

    Users user = getLoginUser(loginUser);

    return questionListItemService
            .getQuestionListItems(
                    user,
                    listId,
                    locale);
}
```

この時点では、まずリストに登録されている問題を取得して画面へ表示できることを確認することを優先した。

## JavaScriptから収録問題を取得する

`/user/question-list/list.js`では、「詳細」ボタンが押されたリストの`listId`を使用してリクエストを送信する。

``` javascript
const response = await fetch(
    `/user/question-list/detail?listId=${listId}`
);

if (!response.ok) {
    throw new Error(
        "リストの取得に失敗しました。"
    );
}

const listItems =
    await response.json();
```

取得した問題は、JavaScriptからHTML要素を生成して詳細表示領域へ追加する。

初期段階では、

``` javascript
listItems.forEach(question => {

    const div =
        document.createElement("div");

    div.textContent =
        question.chineseText +
        " / " +
        question.japaneseText;

    detailContent.appendChild(div);
});
```

のような簡易表示としていた。

## 初期実装の動作確認

`/user/question-list/list`へアクセスすると、問題リストページが表示される。

![](../../images/0029-01.png)

新しいリストを作成できる。

![](../../images/0029-02.png)

![](../../images/0029-03.png)

同じ名前のリストを作成した場合はエラーになる。

![](../../images/0029-04.png)

収録問題が存在しない場合は、その旨を詳細表示領域へ表示する。

![](../../images/0029-05.png)

リスト名の編集・削除など、詳細表示方式とは独立した基本操作についても動作を確認した。

![](../../images/0029-06.png)

![](../../images/0029-07.png)

![](../../images/0029-08.png)

![](../../images/0029-09.png)

一般ユーザーのリスト作成数上限も確認した。

![](../../images/0029-10.png)

------------------------------------------------------------------------

## 初期詳細表示の問題点

基本的なリスト操作はできるようになったが、多数の問題を登録した状態で詳細を開くと、取得した`Question`の情報をそのまま表示しているだけで、画面表示に必要な情報が整理されていない。

動作確認のため、DBから50問を登録した。

``` sql
INSERT INTO question_list_item (
    list_id,
    question_id,
    added_at
)
SELECT
    3,
    q.question_id,
    CURRENT_TIMESTAMP
FROM question q
ORDER BY RANDOM()
LIMIT 50;
```

![](../../images/0029-11.png)

最終的に詳細表示で必要なのは、

``` text
中国語
日本語
難易度
理解度
お気に入り
生成元
発音情報
別解
構文
```

などである。

理解度は`StudyHistory`、お気に入りは`Favorite`に存在するため、`Question`だけでは情報が不足する。

そこで、前項で作成した`QuestionListItemDto`を利用する構成へ変更した。

------------------------------------------------------------------------

## DTOをJSONとして返す方式へ変更

DTO導入後も、この時点では詳細表示そのものはJavaScriptで行う設計を維持していた。

Controllerでは`QuestionListItemDto`の一覧をJSONとして返す。

``` java
@GetMapping("/user/question-list/detail")
@ResponseBody
public List<QuestionListItemDto>
        getUserQuestionListDetail(
                @AuthenticationPrincipal
                UserDetails loginUser,
                @RequestParam Long listId,
                Locale locale) {

    Users user =
            getLoginUser(loginUser);

    return questionListItemService
            .getQuestionListItems(
                    user,
                    listId,
                    locale);
}
```

これにより、EntityそのものをJSONへ変換するのではなく、画面表示に必要な情報をDTOとして取得できるようになった。

ここで導入したDTO・Repository・Serviceによるデータ取得部分は後の設計でも利用できるが、**DTOをJSONとしてJavaScriptへ渡し、JavaScriptが画面を生成する部分は旧設計となる**。

------------------------------------------------------------------------

## JavaScriptによるテーブル表示・操作

取得した`QuestionListItemDto`を利用して、JavaScriptから問題リスト詳細のテーブルを生成するようにした。

テーブルには、

``` text
中国語
日本語
難易度
理解度
お気に入り
生成元
詳細
削除
```

を表示し、さらに、

``` text
理解度変更
お気に入り登録・解除
問題詳細表示
問題リストから削除
```

を同じ画面から操作できるようにした。

POST処理を`fetch()`から行うため、HTMLへCSRF情報を設定する。

``` html
<meta name="_csrf"
      th:content="${_csrf.token}">

<meta name="_csrf_header"
      th:content="${_csrf.headerName}">
```

JavaScript側ではこれらを取得し、理解度変更、お気に入り切り替え、削除などのリクエストに利用する。

この方式では、テーブル、各行、各セル、ボタンなども`document.createElement()`を使用してJavaScript側で生成していた。

------------------------------------------------------------------------

## JavaScriptで使用するメッセージの多言語対応

動的に生成するテーブルの見出しやボタンもJavaScript側で作成するため、それらの文言についても`messages.properties`で管理した。

``` properties
questionList.detail.chinese=中国語
questionList.detail.japanese=日本語
questionList.detail.difficulty=難易度
questionList.detail.evaluation=理解度
questionList.detail.favorite=お気に入り
questionList.detail.source=生成元

questionList.detail.difficulty.beginner=初級
questionList.detail.difficulty.intermediate=中級
questionList.detail.difficulty.advanced=上級

questionList.detail.evaluation.unlearned=未学習
questionList.detail.evaluation.change=理解度を変更
questionList.detail.evaluation.hard=Hard
questionList.detail.evaluation.good=Good
questionList.detail.evaluation.easy=Easy

questionListItem.delete=削除
questionListItem.delete.confirm=この問題をリストから削除しますか？
questionListItem.delete.error=問題の削除に失敗しました。
```

外部JavaScriptからThymeleafの`#{...}`を直接利用できないため、HTMLの`data-*`へメッセージを設定し、JavaScriptから取得する構成としていた。

------------------------------------------------------------------------

## 旧設計の動作確認

収録問題をテーブル形式で表示できるようになった。

![](../../images/0029-12.png)

理解度を変更できる。

![](../../images/0029-13.png)

問題詳細を確認できる。

![](../../images/0029-14.png)

リストから問題を削除できる。

![](../../images/0029-15.png)

![](../../images/0029-16.png)

------------------------------------------------------------------------

## 旧設計を廃止する理由

この方式でも機能自体は実現できていたが、問題リストの詳細表示について、

``` text
データ取得
テーブル生成
各項目の表示
ボタン生成
イベント設定
```

の多くをJavaScript側で担当する構成となっていた。

このアプリケーションではJavaとSpring
Bootを中心として実装し、画面表示にはThymeleafを利用しているため、問題リスト詳細だけをJavaScript主体で構築すると、他の画面と比較して役割分担が大きく異なる。

さらにスマートフォン対応では、

``` text
PC
    → テーブル形式

スマートフォン
    → カード形式
```

のように表示を切り替えたい。

旧設計のまま対応する場合、JavaScript側でPC用のテーブルとスマートフォン用のカードの両方を生成・管理する必要があり、表示処理がさらに複雑になる。

そのため、このJavaScriptによる動的な詳細表示方式は廃止し、以降は**問題リスト詳細を独立したページとしてSpring
MVCとThymeleafで表示する方式**へ変更する。

> ここまでが、後に廃止したJavaScript主体の問題リスト詳細表示の実装である。

------------------------------------------------------------------------


# 各学習モードのquestion画面から問題をリストに追加する

``` text
feat: add question list management to training question pages
```

これまでの実装ではリスト管理画面から問題を管理できるが、学習中に表示されている問題を直接リストへ追加することはできない。

そこで、

``` text
通常学習
復習
AI生成学習
```

の各`question.html`から現在の問題をリストへ追加できるようにする。

## どんな動きにするか

``` text
question.html
    │
    │ リストボタン
    ▼
┌───────────────────┐
│ リスト             │
│                   │
│ ☑ HSK6復習         │
│ ☐ 把構文           │
│ ☑ 苦手問題         │
│ ☐ 後で確認         │
│                   │
│ キャンセル   保存   │
└───────────────────┘
```

モーダルでは単純な「追加先」ではなく、**現在の問題がどのリストへ登録されているか**をチェック状態として表示する。

------------------------------------------------------------------------


## QuestionListSelectionDto

``` java
public interface QuestionListSelectionDto {

    Long getListId();

    String getListName();

    boolean getRegistered();
}
```

Repositoryではユーザーが所有するリストをすべて取得しつつ、現在の問題が登録されているかを判定する。

ここでは`INNER JOIN`にしてしまうと、問題が登録されていないリストが結果から消えてしまう。

そのため、ユーザー所有の全リストを残しつつ登録状態を判定できるようにする。

------------------------------------------------------------------------

## チェック状態に合わせて登録状態を一括更新

モーダルから、

``` text
questionId
selectedListIds
```

を送信する。

Service側では、

``` text
未登録 + チェックあり
    → 追加

登録済み + チェックなし
    → 削除

登録済み + チェックあり
    → 変更なし

未登録 + チェックなし
    → 変更なし
```

として差分を更新する。

------------------------------------------------------------------------

## /practice/question.html

お気に入りボタンの横へリストボタンを追加する。

``` html
<button id="questionListButton"
        type="button"
        class="btn p-0 border-0 bg-transparent"
        th:data-question-id="${question.questionId}"
        data-bs-toggle="modal"
        data-bs-target="#questionListModal">

    <i class="bi bi-folder-plus fs-4"></i>

</button>
```

さらに、リスト選択用のBootstrap Modalを追加する。

------------------------------------------------------------------------

## /practice/question.js

### リスト一覧を取得

``` javascript
function loadQuestionLists() {

    const questionId =
        questionListButton.dataset.questionId;

    fetch(
        "/user/question-list/selection?questionId="
        + questionId
    )

    .then(response => response.json())

    .then(questionLists => {

        const selectionArea =
            document.getElementById(
                "questionListSelectionArea"
            );

        selectionArea.innerHTML = "";

        questionLists.forEach(questionList => {

            const div =
                document.createElement("div");

            div.classList.add(
                "form-check",
                "mb-2"
            );

            const checkbox =
                document.createElement("input");

            checkbox.type = "checkbox";

            checkbox.classList.add(
                "form-check-input",
                "question-list-checkbox"
            );

            checkbox.value =
                questionList.listId;

            checkbox.id =
                "questionList-" +
                questionList.listId;

            checkbox.checked =
                questionList.registered;

            const label =
                document.createElement("label");

            label.classList.add(
                "form-check-label"
            );

            label.htmlFor =
                checkbox.id;

            label.textContent =
                questionList.listName;

            div.appendChild(checkbox);
            div.appendChild(label);

            selectionArea.appendChild(div);
        });
    });
}
```

### チェック状態を保存

``` javascript
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

更新APIへ送信する。

``` javascript
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

更新成功後はページ遷移せずモーダルだけを閉じる。

``` javascript
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

------------------------------------------------------------------------

## 実行

通常学習画面にリストボタンが表示される。

![](../../images/0029-17.png)

モーダルから追加先を選択できる。

![](../../images/0029-18.png)

リスト1とリスト3へ登録する。

![](../../images/0029-19.png)

登録先をリスト1とリスト2へ変更する。

![](../../images/0029-20.png)

DB側でも登録状態が変更される。

![](../../images/0029-21.png)

------------------------------------------------------------------------

## 追加修正 - questionのモーダルからリストを新規作成する

既存リストへの追加だけでなく、question画面から新しいリスト自体を作成できるようにする。

通常の、

``` text
POST /user/question-list/create
```

は作成後にリスト管理画面へリダイレクトする。

question画面ではページ遷移したくないため、

``` java
@PostMapping("/user/question-list/create-modal")
@ResponseBody
public void postUserQuestionListCreateModal(
        @AuthenticationPrincipal
        UserDetails loginUser,
        @RequestParam String listName,
        Locale locale) {

    Users user =
            getLoginUser(loginUser);

    questionListService
            .createQuestionList(
                    user,
                    listName,
                    locale);
}
```

を追加する。

### JavaScript

``` javascript
const questionListCreateButton =
    document.getElementById(
        "questionListCreateButton"
    );

if (questionListCreateButton) {

    questionListCreateButton
        .addEventListener(
            "click",
            function () {

                const newQuestionListName =
                    document.getElementById(
                        "newQuestionListName"
                    );

                const listName =
                    newQuestionListName
                        .value
                        .trim();

                if (listName === "") {
                    return;
                }

                fetch(
                    "/user/question-list/create-modal",
                    {
                        method: "POST",

                        headers: {
                            "Content-Type":
                                "application/x-www-form-urlencoded",

                            [csrfHeader]:
                                csrfToken
                        },

                        body:
                            "listName=" +
                            encodeURIComponent(
                                listName
                            )
                    }
                )

                .then(response => {

                    if (!response.ok) {
                        throw new Error(
                            "リストの作成に失敗しました"
                        );
                    }

                    newQuestionListName.value =
                        "";

                    return loadQuestionLists();
                });
            }
        );
}
```

作成後に`questionListButton.click()`を実行すると、Bootstrap
Modalの開閉処理まで再実行されてしまう。

そこで、

``` javascript
function loadQuestionLists() {
    ...
}
```

として一覧取得処理を独立させ、作成後は、

``` javascript
return loadQuestionLists();
```

だけを呼び出す。

これによりモーダルを開いたまま一覧だけを再取得できる。

## 実行

モーダル内に新規リスト作成欄が表示される。

![](../../images/0029-22.png)

作成するとモーダルを閉じずに新しいリストが追加される。

![](../../images/0029-23.png)

------------------------------------------------------------------------

### /review/question.html

通常学習と同じリストボタン・モーダルを追加する。

### /ai-practice/question.html

AI生成学習にも同じリスト操作を追加する。

ただし、生成直後のAI問題はまだDBへ保存されておらず`questionId`を持っていない。

そのため保存前はリストボタンを表示せず、AI生成問題を保存した後に利用可能にする。

### /ai-practice/question.js

AI生成問題保存後に返された`questionId`を、

``` javascript
questionListButton.dataset.questionId =
    questionId;
```

にも設定する。

これにより、保存されたAI生成問題を通常問題と同じようにリストへ登録できる。

## 実行

復習画面にもリストボタンが表示される。

![](../../images/0029-24.png)

モーダルからリストへ登録できる。

![](../../images/0029-25.png)

AI生成学習では保存前はリストボタンを表示しない。

![](../../images/0029-26.png)

問題を保存するとリストボタンが表示される。

![](../../images/0029-27.png)

AI生成問題もリストへ登録できる。

![](../../images/0029-28.png)

リスト管理画面から登録結果を確認できる。

![](../../images/0029-29.png)

------------------------------------------------------------------------

## ファイル名の整理

リスト機能実装によってJavaScriptの役割が分かりにくくなった。

当初は、

``` text
/static/js/

├── practice/
│   └── question.js
│
├── practice.js
│
└── ai-practice/
    └── question.js
```

となっていた。

特に`/practice.js`は実質的にAI生成学習で利用しているにもかかわらず、名前から用途を判断できない。

また、

``` text
ai-practice/question.html
    ↓
practice.js
ai-practice/question.js
```

と1画面の処理が2ファイルへ分散している。

そこで、

``` text
/static/js/

├── practice/
│   └── question.js
│       → 通常学習・復習
│
└── ai-practice/
    └── question.js
        → AI生成学習
```

へ整理する。

`/practice.js`の処理を`/ai-practice/question.js`へ統合し、`/practice.js`は削除する。

------------------------------------------------------------------------

# 追加修正 - リストの表示順を更新日時の降順で取得するようにする

``` text
git commit -m "fix: sort lists by updated date in descending order"
```

現在は`findByUserId()`を利用しているため、リストの表示順が明示されていない。

そこでRepositoryを、

``` java
List<QuestionList>
    findByUserIdOrderByUpdatedAtDesc(
        Long userId
    );
```

のように変更し、最近更新されたリストから表示する。

------------------------------------------------------------------------

# 追加修正 − 問題追加・削除時もリストをupdated扱いにする

``` text
git commit -m "fix: update list timestamp when adding or removing questions"
```

`@UpdateTimestamp`は`QuestionList`自体がUPDATEされた場合に更新される。

そのため、

``` text
question_list_itemへ問題追加
question_list_itemから問題削除
```

だけでは`question_list.updated_at`は更新されない。

問題の追加・削除も「そのリストを更新した」とみなしたいため、`QuestionListItemService`から対象リストの`updatedAt`を更新する。

## 実行

長期間更新していないリスト2は後方に表示されている。

![](../../images/0029-30.png)

![](../../images/0029-31.png)

リスト2へ問題を追加する。

![](../../images/0029-32.png)

リスト管理画面へ戻ると、更新されたリスト2が先頭に表示される。

![](../../images/0029-33.png)

リスト3から問題を削除する。

![](../../images/0029-34.png)

削除直後は画面上の並びは変わらない。

![](../../images/0029-35.png)

ページを再読み込みすると更新日時順に並び替えられる。

![](../../images/0029-36.png)

------------------------------------------------------------------------

# 追加修正 - リスト追加モーダルのリストの表示順も直す

``` text
git commit -m "fix: sort modal lists by updated date in descending order"
```

リスト管理画面の並び順は更新日時順になったが、question画面のリスト選択モーダルではまだ古い順序のままである。

![](../../images/0029-37.png)

モーダル用のリスト取得についても、

``` text
updated_at DESC
```

で取得するよう修正する。

## 実行

モーダル内でも更新日時の降順で表示されるようになった。

![](../../images/0029-38.png)

------------------------------------------------------------------------

# 追加修正 - messages.propertiesへの追加①

``` text
git commit -m "fix: add missing confirmation message for list deletion"
```

リストそのものを削除する際の確認メッセージが`messages.properties`へ設定されていなかった。

![](../../images/0029-39.png)

## messages.properties

``` properties
questionList.delete.confirm=このリストを削除しますか？
```

簡体字・繁体字についても対応する。

``` properties
questionList.delete.confirm=确定要删除这个列表吗？
```

``` properties
questionList.delete.confirm=確定要刪除這個清單嗎？
```

## 実行

リスト削除時の確認メッセージが表示されるようになった。

![](../../images/0029-40.png)

------------------------------------------------------------------------

# 追加修正 - messages.propertiesへの追加②

``` text
git commit -m "fix: localize question list modal messages"
```

question画面のリスト追加モーダルについて、表示言語を変更しても日本語のままになっている。

![](../../images/0029-41.png)

## 原因

モーダルの文言をHTMLへ直接記述していた。

例えば、

``` html
<h5 class="modal-title">
    リスト
</h5>

<label for="newQuestionListName"
       class="form-label">
    新しいリスト
</label>

<input type="text"
       id="newQuestionListName"
       class="form-control"
       placeholder="リスト名">

<button type="button"
        id="questionListCreateButton"
        class="btn btn-outline-primary">
    作成
</button>
```

となっている。

## html

通常学習・復習・AI生成学習の3画面で同じメッセージキーを使用する。

``` html
<h5 class="modal-title"
    th:text="#{questionList.modal.title}">
    リスト
</h5>

<label for="newQuestionListName"
       class="form-label"
       th:text="#{questionList.modal.newList}">
    新しいリスト
</label>

<input type="text"
       id="newQuestionListName"
       class="form-control"
       th:placeholder="#{questionList.modal.listName}">

<button type="button"
        id="questionListCreateButton"
        class="btn btn-outline-primary"
        th:text="#{questionList.modal.create}">
    作成
</button>
```

## messages.properties

``` properties
# 問題リスト選択モーダル
questionList.modal.title=リスト
questionList.modal.newList=新しいリスト
questionList.modal.listName=リスト名
questionList.modal.create=作成
questionList.modal.cancel=キャンセル
questionList.modal.save=保存
```

`messages_zh_CN.properties`と`messages_zh_TW.properties`にも同じキーを追加する。

## 実行

表示言語に合わせてリスト選択モーダルの文言も切り替わるようになった。

![](../../images/0029-42.png)

------------------------------------------------------------------------

# 追加修正 - 不要になった問題リスト追加用エンドポイントを削除する

各question画面から問題リストの登録状態を一括更新できるようになったことで、初期実装で追加した問題追加専用エンドポイントが使用されなくなった。

初期実装では、以下のメソッドから問題をリストへ追加していた。

``` java
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

その後、各question画面のリスト選択モーダルから、

``` text
POST /user/question-list/item/update
```

へ`questionId`と`selectedListIds`を送信し、`updateQuestionLists()`で現在の登録状態との差分を判定して追加・削除を行う構成へ変更した。

そのため、

``` text
POST /user/question-list/item/add
```

を直接呼び出す箇所は存在しなくなった。

不要になった`postQuestionListItemAdd()`を`QuestionListItemController`から削除する。

一方、

``` text
POST /user/question-list/item/delete
```

は問題リスト管理画面から問題を直接削除する際に使用しているため、こちらは残す。


# まとめ

029-01で作成したDB・Entity・Repository・Service・DTOなどの基盤を利用し、各question画面からのリスト操作と、モーダルによる登録状態の一括更新を実装した。

一方、問題リストに収録されている問題の詳細表示については、当初JavaScriptからJSONを取得し、同じ画面内へ動的にテーブルを生成する方式としていた。

この旧方式は一度動作するところまで実装したものの、アプリケーション全体の構成やスマートフォン対応を考慮し、最終的には採用しない。

次の029-03では、問題リスト詳細を独立したページとして表示する新しい設計へ変更する。

------------------------------------------------------------------------


