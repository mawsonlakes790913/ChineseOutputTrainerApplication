document.addEventListener("DOMContentLoaded", () => {

    // =========================
    // AI生成問題の保存
    // =========================

    const saveQuestionButton =
        document.getElementById("saveQuestionButton");

    const saveQuestionArea =
        document.getElementById("saveQuestionArea");

    const favoriteArea =
        document.getElementById("favoriteArea");

    const favoriteButton =
        document.getElementById("favoriteButton");

    const savedQuestionActions =
        document.getElementById("savedQuestionActions");

    // CSRF
    const csrfToken =
        document.querySelector('meta[name="_csrf"]').content;

    const csrfHeader =
        document.querySelector('meta[name="_csrf_header"]').content;

    // 問題保存
    saveQuestionButton.addEventListener("click", async () => {

        const page = saveQuestionButton.dataset.page;

        const response = await fetch(
            `/ai-practice/save?page=${page}`,
            {
                method: "POST",
                headers: {
                    [csrfHeader]: csrfToken
                }
            }
        );

        // 保存失敗
        if (!response.ok) {
            return;
        }

        // 保存された問題のquestionIdを取得
        const questionId = await response.json();

        // お気に入り用のquestionIdを設定
        favoriteButton.dataset.questionId = questionId;

        // 理解度登録用のquestionIdを設定
        document.querySelectorAll(".savedQuestionId")
            .forEach(input => {
                input.value = questionId;
            });

        // 「この問題を保存する」を非表示
        saveQuestionArea.classList.add("d-none");

        // お気に入りを表示
        favoriteArea.classList.remove("d-none");

        // 理解度を表示
        savedQuestionActions.classList.remove("d-none");
    });

});