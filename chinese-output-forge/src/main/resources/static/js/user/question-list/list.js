document.addEventListener("DOMContentLoaded", () => {

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
    // メッセージ
    // ======================

    const messageElement =
        document.getElementById(
            "questionListMessages"
        );

    const messages = {

        empty:
            messageElement.dataset.empty,

        loadError:
            messageElement.dataset.loadError,

        chinese:
            messageElement.dataset.chinese,

        japanese:
            messageElement.dataset.japanese,

        difficulty:
            messageElement.dataset.difficulty,

        evaluation:
            messageElement.dataset.evaluation,

        favorite:
            messageElement.dataset.favorite,

        source:
            messageElement.dataset.source,

        detail:
            messageElement.dataset.detail,

        delete:
            messageElement.dataset.delete,

        beginner:
            messageElement.dataset.beginner,

        intermediate:
            messageElement.dataset.intermediate,

        advanced:
            messageElement.dataset.advanced,

        unlearned:
            messageElement.dataset.unlearned,

        hard:
            messageElement.dataset.hard,

        good:
            messageElement.dataset.good,

        easy:
            messageElement.dataset.easy,

        normal:
            messageElement.dataset.normal,

        ai:
            messageElement.dataset.ai,

        evaluationUpdateError:
            messageElement.dataset.evaluationUpdateError,

        deleteConfirm:
            messageElement.dataset.deleteConfirm,
            
        deleteSuccess: 
        	messageElement.dataset.deleteSuccess,
	
        deleteError:
            messageElement.dataset.deleteError
    };


    // ======================
    // リスト詳細表示
    // ======================

    const detailButtons =
        document.querySelectorAll(
            ".question-list-detail-button"
        );

    detailButtons.forEach(button => {

        button.addEventListener(
            "click",
            async () => {

                const listId =
                    button.dataset.listId;

                const detailRow =
                    document.getElementById(
                        `list-detail-${listId}`
                    );

                const detailContent =
                    document.getElementById(
                        `list-detail-content-${listId}`
                    );


                // 既に開いている場合は閉じる
                if (
                    !detailRow.classList.contains(
                        "d-none"
                    )
                ) {

                    detailRow.classList.add(
                        "d-none"
                    );

                    return;
                }


                try {

                    // 指定したリストの問題一覧を取得
                    const response =
                        await fetch(
                            `/user/question-list/detail?listId=${listId}`
                        );

                    if (!response.ok) {

                        throw new Error(
                            messages.loadError
                        );
                    }

                    const listItems =
                        await response.json();


                    // 詳細領域を初期化
                    detailContent.innerHTML = "";


                    // 問題が登録されていない場合
                    if (listItems.length === 0) {

                        detailContent.textContent =
                            messages.empty;

                    } else {

                        // ======================
                        // 問題一覧テーブル
                        // ======================

                        const table =
                            document.createElement(
                                "table"
                            );

                        table.className =
                            "table table-sm table-bordered align-middle mb-0";


                        // ======================
                        // テーブルヘッダー
                        // ======================

                        const thead =
                            document.createElement(
                                "thead"
                            );

                        thead.innerHTML = `
                            <tr>
                                <th>
                                    ${messages.chinese}
                                </th>
                                <th>
                                    ${messages.japanese}
                                </th>
                                <th style="min-width: 70px;">
                                    ${messages.difficulty}
                                </th>
                                <th style="min-width: 80px;">
                                    ${messages.evaluation}
                                </th>
                                <th>
                                    ${messages.favorite}
                                </th>
                                <th style="min-width: 70px;">
                                    ${messages.source}
                                </th>
                                <th class="text-nowrap">
                                    ${messages.detail}
                                </th>
                                <th class="text-nowrap">
                                    ${messages.delete}
                                </th>
                            </tr>
                        `;

                        table.appendChild(
                            thead
                        );


                        // ======================
                        // テーブル本体
                        // ======================

                        const tbody =
                            document.createElement(
                                "tbody"
                            );


                        listItems.forEach(
                            question => {

                                const tr =
                                    document.createElement(
                                        "tr"
                                    );


                                // ======================
                                // 中国語
                                // ======================

                                const chineseTd =
                                    document.createElement(
                                        "td"
                                    );

                                chineseTd.textContent =
                                    question.chineseText;


                                // ======================
                                // 日本語
                                // ======================

                                const japaneseTd =
                                    document.createElement(
                                        "td"
                                    );

                                japaneseTd.textContent =
                                    question.japaneseText;


                                // ======================
                                // 難易度
                                // ======================

                                const difficultyTd =
                                    document.createElement(
                                        "td"
                                    );

                                const difficultySpan =
                                    document.createElement(
                                        "span"
                                    );

                                difficultySpan.classList.add(
                                    "fw-bold"
                                );


                                if (
                                    question.difficulty ===
                                    "BEGINNER"
                                ) {

                                    difficultySpan.textContent =
                                        messages.beginner;

                                    difficultySpan.classList.add(
                                        "text-danger"
                                    );

                                } else if (
                                    question.difficulty ===
                                    "INTERMEDIATE"
                                ) {

                                    difficultySpan.textContent =
                                        messages.intermediate;

                                    difficultySpan.classList.add(
                                        "text-primary"
                                    );

                                } else if (
                                    question.difficulty ===
                                    "ADVANCED"
                                ) {

                                    difficultySpan.textContent =
                                        messages.advanced;

                                    difficultySpan.classList.add(
                                        "text-success"
                                    );
                                }

                                difficultyTd.appendChild(
                                    difficultySpan
                                );


                                // ======================
                                // 理解度
                                // ======================

                                const evaluationTd =
                                    document.createElement(
                                        "td"
                                    );

                                if (
                                    question.evaluation ===
                                    null
                                ) {

                                    // 未学習
                                    const unlearnedSpan =
                                        document.createElement(
                                            "span"
                                        );

                                    unlearnedSpan.textContent =
                                        messages.unlearned;

                                    unlearnedSpan.className =
                                        "text-secondary text-nowrap";

                                    evaluationTd.appendChild(
                                        unlearnedSpan
                                    );

                                } else {

                                    // 学習済み
                                    const evaluationButton =
                                        document.createElement(
                                            "button"
                                        );

                                    evaluationButton.type =
                                        "button";

                                    evaluationButton.classList.add(
                                        "btn",
                                        "btn-sm",
                                        "evaluationButton"
                                    );

                                    evaluationButton.dataset.questionId =
                                        question.questionId;


                                    evaluationButton.addEventListener(
                                        "click",
                                        function () {

                                            showEvaluationModal(
                                                question,
                                                evaluationButton,
                                                messages
                                            );
                                        }
                                    );


                                    if (
                                        question.evaluation ===
                                        "HARD"
                                    ) {

                                        evaluationButton.textContent =
                                            messages.hard;

                                        evaluationButton.classList.add(
                                            "btn-danger"
                                        );

                                    } else if (
                                        question.evaluation ===
                                        "GOOD"
                                    ) {

                                        evaluationButton.textContent =
                                            messages.good;

                                        evaluationButton.classList.add(
                                            "btn-primary"
                                        );

                                    } else if (
                                        question.evaluation ===
                                        "EASY"
                                    ) {

                                        evaluationButton.textContent =
                                            messages.easy;

                                        evaluationButton.classList.add(
                                            "btn-success"
                                        );
                                    }

                                    evaluationTd.appendChild(
                                        evaluationButton
                                    );
                                }


                                // ======================
                                // お気に入り
                                // ======================

                                const favoriteTd =
                                    document.createElement(
                                        "td"
                                    );

                                favoriteTd.className =
                                    "text-center";

                                const favoriteButton =
                                    document.createElement(
                                        "button"
                                    );

                                favoriteButton.type =
                                    "button";

                                favoriteButton.className =
                                    "btn btn-link p-0 favoriteButton";

                                favoriteButton.dataset.questionId =
                                    question.questionId;

                                const favoriteIcon =
                                    document.createElement(
                                        "i"
                                    );


                                // 現在のお気に入り状態を表示
                                if (question.favorite) {

                                    favoriteIcon.className =
                                        "bi bi-heart-fill text-danger";

                                } else {

                                    favoriteIcon.className =
                                        "bi bi-heart text-secondary";
                                }

                                favoriteButton.appendChild(
                                    favoriteIcon
                                );


                                // ======================
                                // お気に入り登録・解除
                                // ======================

                                favoriteButton.addEventListener(
                                    "click",
                                    function () {

                                        fetch(
                                            "/favorite/toggle",
                                            {

                                                method:
                                                    "POST",

                                                headers: {

                                                    "Content-Type":
                                                        "application/x-www-form-urlencoded",

                                                    [csrfHeader]:
                                                        csrfToken
                                                },

                                                body:
                                                    "questionId=" +
                                                    encodeURIComponent(
                                                        question.questionId
                                                    )
                                            }
                                        )

                                        .then(
                                            function (
                                                response
                                            ) {

                                                if (
                                                    !response.ok
                                                ) {

                                                    throw new Error(
                                                        "Favorite update failed"
                                                    );
                                                }

                                                return response.text();
                                            }
                                        )

                                        .then(
                                            function (
                                                result
                                            ) {

                                                // お気に入り登録
                                                if (
                                                    result ===
                                                    "true"
                                                ) {

                                                    favoriteIcon.classList.remove(
                                                        "bi-heart",
                                                        "text-secondary"
                                                    );

                                                    favoriteIcon.classList.add(
                                                        "bi-heart-fill",
                                                        "text-danger"
                                                    );

                                                // お気に入り解除
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
                                            }
                                        )

                                        .catch(
                                            function (
                                                error
                                            ) {

                                                console.error(
                                                    error
                                                );
                                            }
                                        );
                                    }
                                );

                                favoriteTd.appendChild(
                                    favoriteButton
                                );


                                // ======================
                                // 生成元
                                // ======================

                                const sourceTd =
                                    document.createElement(
                                        "td"
                                    );

                                sourceTd.textContent =
                                    question.aiGenerated
                                        ? messages.ai
                                        : messages.normal;


                                // ======================
                                // 詳細
                                // ======================

                                const detailTd =
                                    document.createElement(
                                        "td"
                                    );

                                const detailButton =
                                    document.createElement(
                                        "button"
                                    );

                                detailButton.type =
                                    "button";

                                detailButton.className =
                                    "btn btn-sm btn-outline-primary text-nowrap";

                                detailButton.textContent =
                                    messages.detail;


                                // 問題詳細モーダルを表示
                                detailButton.addEventListener(
                                    "click",
                                    () => {

                                        showQuestionDetail(
                                            question
                                        );
                                    }
                                );

                                detailTd.appendChild(
                                    detailButton
                                );


                                // ======================
                                // リストから削除
                                // ======================

                                const deleteTd =
                                    document.createElement(
                                        "td"
                                    );

                                const deleteButton =
                                    document.createElement(
                                        "button"
                                    );

                                deleteButton.type =
                                    "button";

                                deleteButton.className =
                                    "btn btn-sm btn-outline-danger text-nowrap";

                                deleteButton.textContent =
                                    messages.delete;


                                deleteButton.addEventListener(
                                    "click",
                                    function () {

                                        if (
                                            !confirm(
                                                messages.deleteConfirm
                                            )
                                        ) {

                                            return;
                                        }


										fetch(
										    "/user/question-list/item/delete",
										    {
										        method: "POST",
										        headers: {
										            "Content-Type":
										                "application/x-www-form-urlencoded",
										            [csrfHeader]: csrfToken
										        },
										        body: new URLSearchParams({
										            listId: listId,
										            questionId: question.questionId
										        })
										    }
										)
										.then(response => {
										
										    if (!response.ok) {
										        throw new Error(
										            messages.deleteError
										        );
										    }
										
										    // 行を削除
										    tr.remove();
										
										    // 削除成功メッセージを表示
										    const successMessage =
										        document.getElementById(
										            "questionListSuccessMessage"
										        );
										
										    successMessage.textContent =
										        messages.deleteSuccess;
										
										    successMessage.classList.remove(
										        "d-none"
										    );
										
										})
										.catch(error => {
										
										    console.error(error);
										
										    // 削除失敗メッセージを表示
										    const errorMessage =
										        document.getElementById(
										            "questionListErrorMessage"
										        );
										
										    errorMessage.textContent =
										        messages.deleteError;
										
										    errorMessage.classList.remove(
										        "d-none"
										    );
										
										});
                                    }
                                );

                                deleteTd.appendChild(
                                    deleteButton
                                );


                                // ======================
                                // 行に追加
                                // ======================

                                tr.appendChild(
                                    chineseTd
                                );

                                tr.appendChild(
                                    japaneseTd
                                );

                                tr.appendChild(
                                    difficultyTd
                                );

                                tr.appendChild(
                                    evaluationTd
                                );

                                tr.appendChild(
                                    favoriteTd
                                );

                                tr.appendChild(
                                    sourceTd
                                );

                                tr.appendChild(
                                    detailTd
                                );

                                tr.appendChild(
                                    deleteTd
                                );

                                tbody.appendChild(
                                    tr
                                );
                            }
                        );

                        table.appendChild(
                            tbody
                        );

                        detailContent.appendChild(
                            table
                        );
                    }


                    // 詳細を表示
                    detailRow.classList.remove(
                        "d-none"
                    );

                } catch (error) {

                    console.error(
                        error
                    );

                    detailContent.textContent =
                        messages.loadError;

                    detailRow.classList.remove(
                        "d-none"
                    );
                }
            }
        );
    });
});


// ==============================
// 問題詳細モーダル
// ==============================

function showQuestionDetail(
    question
) {

    // 中国語
    document.getElementById(
        "questionDetailChineseText"
    ).textContent =
        question.chineseText ?? "";


    // 日本語
    document.getElementById(
        "questionDetailJapaneseText"
    ).textContent =
        question.japaneseText ?? "";


    // ピンイン
    document.getElementById(
        "questionDetailPinyin"
    ).textContent =
        question.pinyin ?? "";


    // 注音
    document.getElementById(
        "questionDetailZhuyin"
    ).textContent =
        question.zhuyin ?? "";


    // 別解
    document.getElementById(
        "questionDetailAlternativeAnswer"
    ).textContent =
        question.alternativeAnswer ?? "";


    // 別解ピンイン
    document.getElementById(
        "questionDetailAlternativeAnswerPinyin"
    ).textContent =
        question.alternativeAnswerPinyin ?? "";


    // 別解注音
    document.getElementById(
        "questionDetailAlternativeAnswerZhuyin"
    ).textContent =
        question.alternativeAnswerZhuyin ?? "";


    // 構文
    document.getElementById(
        "questionDetailStructureName"
    ).textContent =
        question.structureName ?? "";


    // Bootstrapモーダルを表示
    const modalElement =
        document.getElementById(
            "questionDetailModal"
        );

    const modal =
        bootstrap.Modal.getOrCreateInstance(
            modalElement
        );

    modal.show();
}


// ==============================
// 理解度変更モーダル
// ==============================

function showEvaluationModal(
    question,
    evaluationButton,
    messages
) {

    const modalElement =
        document.getElementById(
            "evaluationModal"
        );

    const selectButtons =
        modalElement.querySelectorAll(
            ".evaluationSelect"
        );


    selectButtons.forEach(
        button => {

            button.onclick =
                function () {

                    const evaluation =
                        button.dataset.evaluation;

                    const csrfToken =
                        document.querySelector(
                            'meta[name="_csrf"]'
                        ).content;

                    const csrfHeader =
                        document.querySelector(
                            'meta[name="_csrf_header"]'
                        ).content;


                    fetch(
                        "/evaluation/toggle",
                        {

                            method:
                                "POST",

                            headers: {

                                "Content-Type":
                                    "application/x-www-form-urlencoded",

                                [csrfHeader]:
                                    csrfToken
                            },

                            body:
                                "questionId=" +
                                encodeURIComponent(
                                    question.questionId
                                ) +
                                "&evaluation=" +
                                encodeURIComponent(
                                    evaluation
                                )
                        }
                    )

                    .then(
                        function (
                            response
                        ) {

                            if (
                                !response.ok
                            ) {

                                throw new Error(
                                    messages.evaluationUpdateError
                                );
                            }


                            // ボタンの表示を更新
                            evaluationButton.classList.remove(
                                "btn-danger",
                                "btn-primary",
                                "btn-success"
                            );


                            if (
                                evaluation ===
                                "HARD"
                            ) {

                                evaluationButton.textContent =
                                    messages.hard;

                                evaluationButton.classList.add(
                                    "btn-danger"
                                );

                            } else if (
                                evaluation ===
                                "GOOD"
                            ) {

                                evaluationButton.textContent =
                                    messages.good;

                                evaluationButton.classList.add(
                                    "btn-primary"
                                );

                            } else if (
                                evaluation ===
                                "EASY"
                            ) {

                                evaluationButton.textContent =
                                    messages.easy;

                                evaluationButton.classList.add(
                                    "btn-success"
                                );
                            }


                            question.evaluation =
                                evaluation;


                            const modal =
                                bootstrap.Modal.getInstance(
                                    modalElement
                                );

                            modal.hide();
                        }
                    )

                    .catch(
                        function (
                            error
                        ) {

                            console.error(
                                error
                            );
                        }
                    );
                };
        }
    );


    const modal =
        bootstrap.Modal.getOrCreateInstance(
            modalElement
        );

    modal.show();
}