// =========================
// 解答表示
// =========================

function showAnswer() {

    const answerButton =
        document.getElementById("answerButton");

    const answerArea =
        document.getElementById("answerArea");

    answerButton.style.display = "none";

    answerArea.style.display = "block";

}


// =========================
// お気に入り登録・解除
// =========================

const favoriteButton =
    document.getElementById("favoriteButton");

if (favoriteButton) {

    favoriteButton.addEventListener("click", function () {

        const questionId =
            favoriteButton.dataset.questionId;

        const csrfToken =
            document.querySelector('meta[name="_csrf"]').content;

        const csrfHeader =
            document.querySelector('meta[name="_csrf_header"]').content;

        fetch("/favorite/toggle", {

            method: "POST",

            headers: {

                "Content-Type":
                    "application/x-www-form-urlencoded",

                [csrfHeader]: csrfToken

            },

            body: "questionId=" + questionId

        })

        .then(response => response.text())

        .then(result => {

            const favoriteIcon =
                document.getElementById("favoriteIcon");

            if (result === "true") {

                favoriteIcon.classList.remove(
                    "bi-heart",
                    "text-secondary"
                );

                favoriteIcon.classList.add(
                    "bi-heart-fill",
                    "text-danger"
                );

            } else {

                favoriteIcon.classList.remove(
                    "bi-heart-fill",
                    "text-danger"
                );

                favoriteIcon.classList.add(
                    "bi-heart",
                    "text-secondary"
                );

            }

        });

    });

}


// =========================
// リスト登録状態を取得
// =========================

const questionListButton =
    document.getElementById("questionListButton");

// リスト一覧を取得・表示
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

        // 表示内容を初期化
        selectionArea.innerHTML = "";

        // リストごとにチェックボックスを作成
        questionLists.forEach(questionList => {

            const div =
                document.createElement("div");

            div.classList.add(
                "form-check",
                "mb-2"
            );

            // チェックボックス
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
                "questionList-" + questionList.listId;

            // 登録済みならチェック
            checkbox.checked =
                questionList.registered;

            // リスト名
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


// モーダルを開いたときにリスト一覧を取得
if (questionListButton) {

    questionListButton.addEventListener(
        "click",
        loadQuestionLists
    );

}


// =========================
// リスト新規作成
// =========================

const questionListCreateButton =
    document.getElementById("questionListCreateButton");

if (questionListCreateButton) {

    questionListCreateButton.addEventListener("click", function () {

        // 入力されたリスト名を取得
        const newQuestionListName =
            document.getElementById("newQuestionListName");

        const listName =
            newQuestionListName.value.trim();

        // リスト名が未入力の場合は処理しない
        if (listName === "") {
            return;
        }

        // CSRFトークンを取得
        const csrfToken =
            document.querySelector(
                'meta[name="_csrf"]'
            ).content;

        const csrfHeader =
            document.querySelector(
                'meta[name="_csrf_header"]'
            ).content;

        // リストを新規作成
        fetch("/user/question-list/create-modal", {

            method: "POST",

            headers: {

                "Content-Type":
                    "application/x-www-form-urlencoded",

                [csrfHeader]: csrfToken

            },

            body:
                "listName=" + encodeURIComponent(listName)

        })

        .then(response => {

            if (!response.ok) {
                throw new Error(
                    "リストの作成に失敗しました"
                );
            }

            // 入力欄を空にする
            newQuestionListName.value = "";

            // リスト一覧を再取得
            return loadQuestionLists();

        });

    });

}


// =========================
// リスト登録状態を保存
// =========================

const questionListSaveButton =
    document.getElementById("questionListSaveButton");

if (questionListSaveButton) {

    questionListSaveButton.addEventListener("click", function () {

        // 保存済み問題IDを取得
        const questionId =
            questionListButton.dataset.questionId;

        // チェックされているリストを取得
        const checkedLists =
            document.querySelectorAll(
                ".question-list-checkbox:checked"
            );

        // 送信用データを作成
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

        // CSRFトークンを取得
        const csrfToken =
            document.querySelector(
                'meta[name="_csrf"]'
            ).content;

        const csrfHeader =
            document.querySelector(
                'meta[name="_csrf_header"]'
            ).content;

        // リスト登録状態を更新
        fetch("/user/question-list/item/update", {

            method: "POST",

            headers: {

                "Content-Type":
                    "application/x-www-form-urlencoded",

                [csrfHeader]: csrfToken

            },

            body: params.toString()

        })

        .then(response => {

            if (!response.ok) {
                throw new Error(
                    "リストの更新に失敗しました"
                );
            }

            // モーダルを閉じる
            const modalElement =
                document.getElementById(
                    "questionListModal"
                );

            const modal =
                bootstrap.Modal.getInstance(
                    modalElement
                );

            modal.hide();

        });

    });

}