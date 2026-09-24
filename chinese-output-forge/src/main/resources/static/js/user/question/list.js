document.addEventListener("DOMContentLoaded", function () {

    // ======================
    // CSRF情報
    // ======================

    const csrfToken =
        document.querySelector(
            'meta[name="_csrf"]'
        ).content;

    const csrfHeader =
        document.querySelector(
            'meta[name="_csrf_header"]'
        ).content;


    // ======================
    // Tooltip
    // ======================

    const tooltipTriggerList =
        document.querySelectorAll(
            '[data-bs-toggle="tooltip"]'
        );

    tooltipTriggerList.forEach(function (element) {

        new bootstrap.Tooltip(element);

    });


	// ======================
	// 詳細モーダル
	// ======================
	
	const detailButtons =
	    document.querySelectorAll(".detailButton");
	
	const modal =
	    document.getElementById("questionDetailModal");
	
	const pronunciationType =
	    modal.dataset.pronunciationType;
	
	detailButtons.forEach(function (button) {
	
	    button.addEventListener("click", function () {
	
	        // 日本語
	        document.getElementById("modalJapanese").textContent =
	            button.dataset.japanese;
	
	        // 中国語
	        document.getElementById("modalChinese").textContent =
	            button.dataset.chinese;
	
	
	        // ======================
	        // 中国語の発音記号
	        // ======================
	
	        const chinesePronunciation =
	            document.getElementById("modalChinesePronunciation");
	
	        const chinesePronunciationArea =
	            document.getElementById("modalChinesePronunciationArea");
	
	        if (pronunciationType === "PINYIN") {
	
	            chinesePronunciation.textContent =
	                button.dataset.pinyin || "";
	
	            chinesePronunciationArea.style.display = "";
	
	        } else if (pronunciationType === "ZHUYIN") {
	
	            chinesePronunciation.textContent =
	                button.dataset.zhuyin || "";
	
	            chinesePronunciationArea.style.display = "";
	
	        } else {
	
	            chinesePronunciation.textContent = "";
	
	            chinesePronunciationArea.style.display = "none";
	
	        }
	
	
	        // ======================
	        // 別解
	        // ======================
	
	        const alternativeArea =
	            document.getElementById("modalAlternativeArea");
	
	        const alternativePronunciation =
	            document.getElementById("modalAlternativePronunciation");
	
	        const alternativePronunciationArea =
	            document.getElementById("modalAlternativePronunciationArea");
	
	        if (button.dataset.alternative) {
	
	            document.getElementById("modalAlternative").textContent =
	                button.dataset.alternative;
	
	            alternativeArea.style.display = "";
	
	
	            // ======================
	            // 別解の発音記号
	            // ======================
	
	            if (pronunciationType === "PINYIN") {
	
	                alternativePronunciation.textContent =
	                    button.dataset.alternativePinyin || "";
	
	                alternativePronunciationArea.style.display = "";
	
	            } else if (pronunciationType === "ZHUYIN") {
	
	                alternativePronunciation.textContent =
	                    button.dataset.alternativeZhuyin || "";
	
	                alternativePronunciationArea.style.display = "";
	
	            } else {
	
	                alternativePronunciation.textContent = "";
	
	                alternativePronunciationArea.style.display = "none";
	
	            }
	
	        } else {
	
	            document.getElementById("modalAlternative").textContent = "";
	
	            alternativePronunciation.textContent = "";
	
	            alternativeArea.style.display = "none";
	
	        }
	
	    });
	
	});


    // ======================
    // Evaluation変更
    // ======================

    let currentQuestionId = null;

    const evaluationButtons =
        document.querySelectorAll(".evaluationButton");

    evaluationButtons.forEach(function (button) {

        button.addEventListener("click", function () {

            currentQuestionId =
                button.dataset.questionId;

        });

    });

    const evaluationSelectButtons =
        document.querySelectorAll(".evaluationSelect");

    evaluationSelectButtons.forEach(function (button) {

        button.addEventListener("click", function () {

            const evaluation =
                button.dataset.evaluation;

            if (currentQuestionId === null) {

                console.error(
                    "問題IDを取得できませんでした"
                );

                return;

            }

            fetch("/evaluation/toggle", {

                method: "POST",

                headers: {

                    "Content-Type":
                        "application/x-www-form-urlencoded",

                    [csrfHeader]:
                        csrfToken

                },

                body:
                    "questionId=" +
                    encodeURIComponent(currentQuestionId) +
                    "&evaluation=" +
                    encodeURIComponent(evaluation)

            })

            .then(function (response) {

                if (!response.ok) {

                    throw new Error(
                        "理解度の更新に失敗しました: " +
                        response.status
                    );

                }

                location.reload();

            })

            .catch(function (error) {

                console.error(error);

            });

        });

    });


    // ======================
    // お気に入り登録・解除
    // ======================

    const favoriteButtons =
        document.querySelectorAll(".favoriteButton");

    favoriteButtons.forEach(function (button) {

        button.addEventListener("click", function () {

            const questionId =
                button.dataset.questionId;

            const favoriteIcon =
                button.querySelector("i");

            fetch("/favorite/toggle", {

                method: "POST",

                headers: {

                    "Content-Type":
                        "application/x-www-form-urlencoded",

                    [csrfHeader]:
                        csrfToken

                },

                body:
                    "questionId=" +
                    encodeURIComponent(questionId)

            })

            .then(function (response) {

                if (!response.ok) {

                    throw new Error(
                        "お気に入り更新失敗"
                    );

                }

                return response.text();

            })

            .then(function (result) {

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

            })

            .catch(function (error) {

                console.error(error);

            });

        });

    });
    
    // ======================

    // 問題リスト

    // ======================

    let questionListQuestionId = null;

    const questionListButtons =
        document.querySelectorAll(".questionListButton");

    const questionListSelectionArea =
        document.getElementById("questionListSelectionArea");


    // ======================

    // リスト登録状態を取得

    // ======================

    function loadQuestionListSelection() {

        fetch(
            "/user/question-list/selection?questionId=" +
            encodeURIComponent(questionListQuestionId)
        )

        .then(function (response) {

            if (!response.ok) {

                throw new Error(
                    "リスト情報の取得に失敗しました"
                );

            }

            return response.json();

        })

        .then(function (questionLists) {

            // リスト一覧を初期化
            questionListSelectionArea.innerHTML = "";

            questionLists.forEach(function (questionList) {

                const label =
                    document.createElement("label");

                label.className =
                    "form-check mb-2";

                const checkbox =
                    document.createElement("input");

                checkbox.type = "checkbox";

                checkbox.className =
                    "form-check-input";

                checkbox.name =
                    "selectedListIds";

                checkbox.value =
                    questionList.listId;

                checkbox.checked =
                    questionList.registered;

                const span =
                    document.createElement("span");

                span.className =
                    "form-check-label ms-2";

                span.textContent =
                    questionList.listName;

                label.appendChild(checkbox);

                label.appendChild(span);

                questionListSelectionArea.appendChild(label);

            });

        })

        .catch(function (error) {

            console.error(error);

        });

    }


    // ======================

    // リスト選択モーダルを表示

    // ======================

    questionListButtons.forEach(function (button) {

        button.addEventListener("click", function () {

            // 対象の問題IDを取得
            questionListQuestionId =
                button.dataset.questionId;

            // リスト登録状態を取得
            loadQuestionListSelection();

        });

    });


    // ======================

    // リスト登録状態を保存

    // ======================

    const questionListSaveButton =
        document.getElementById("questionListSaveButton");

    questionListSaveButton.addEventListener("click", function () {

        // 問題IDを取得できない場合
        if (questionListQuestionId === null) {

            console.error(
                "問題IDを取得できませんでした"
            );

            return;

        }

        // チェックされているリストIDを取得
        const selectedListIds =
            Array.from(
                questionListSelectionArea.querySelectorAll(
                    "input[name='selectedListIds']:checked"
                )
            ).map(function (checkbox) {

                return checkbox.value;

            });

        // 送信用データを作成
        const params =
            new URLSearchParams();

        params.append(
            "questionId",
            questionListQuestionId
        );

        selectedListIds.forEach(function (listId) {

            params.append(
                "selectedListIds",
                listId
            );

        });

        // リスト登録状態を更新
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

        })

        .then(function (response) {

            if (!response.ok) {

                throw new Error(
                    "リストの更新に失敗しました"
                );

            }

			// 画面を再読み込み
			location.reload();
            // モーダルを閉じる

        })

        .catch(function (error) {

            console.error(error);

        });

    });


    // ======================

    // 新しいリストを作成

    // ======================

    const questionListCreateButton =
        document.getElementById("questionListCreateButton");

    const newQuestionListName =
        document.getElementById("newQuestionListName");

    questionListCreateButton.addEventListener("click", function () {

        const listName =
            newQuestionListName.value.trim();

        // リスト名が空の場合
        if (listName === "") {

            return;

        }

        // 送信用データを作成
        const params =
            new URLSearchParams();

        params.append(
            "listName",
            listName
        );

        // 新しいリストを作成
        fetch("/user/question-list/create-modal", {

            method: "POST",

            headers: {

                "Content-Type":
                    "application/x-www-form-urlencoded",

                [csrfHeader]:
                    csrfToken

            },

            body:
                params.toString()

        })

        .then(function (response) {

            if (!response.ok) {

                throw new Error(
                    "リストの作成に失敗しました"
                );

            }

            // 入力欄を空にする
            newQuestionListName.value = "";

            // リスト一覧を再取得
            loadQuestionListSelection();

        })

        .catch(function (error) {

            console.error(error);

        });

    });
    
});