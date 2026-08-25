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
    // 検索条件制御
    // ======================

    const studyCondition =
        document.getElementById("studyCondition");

    const evaluations =
        document.querySelectorAll(
            "input[name='evaluations']"
        );

    if (studyCondition) {

        function updateEvaluationState() {

            const unlearned =
                studyCondition.value ===
                "UNLEARNED_ONLY";

            evaluations.forEach(function (cb) {

                if (unlearned) {

                    cb.checked = false;
                    cb.disabled = true;

                } else {

                    cb.disabled = false;

                }

            });

        }

        studyCondition.addEventListener(
            "change",
            updateEvaluationState
        );

        updateEvaluationState();

    }
    
	// =========================
	// 文法・構造の一括選択
	// =========================
	
	const selectAllStructuresButton =
	    document.getElementById("selectAllStructures");
	
	const clearAllStructuresButton =
	    document.getElementById("clearAllStructures");
	
	const structureCheckboxes =
	    document.querySelectorAll("input[name='structureIds']");
	
	
	// すべて選択
	selectAllStructuresButton.addEventListener("click", () => {
	
	    structureCheckboxes.forEach(checkbox => {
	        checkbox.checked = true;
	    });
	
	});
	
	
	// すべて解除
	clearAllStructuresButton.addEventListener("click", () => {
	
	    structureCheckboxes.forEach(checkbox => {
	        checkbox.checked = false;
	    });
	
	});
	
	
	// =========================
	// 文法・構造欄表示
	// =========================
	
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

});