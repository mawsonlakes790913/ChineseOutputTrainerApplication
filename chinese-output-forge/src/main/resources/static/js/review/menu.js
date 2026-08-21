document.addEventListener("DOMContentLoaded", () => {

    // 検索条件
    const searchConditions = document.querySelectorAll(
        "input[name='evaluations'], " +
        "input[name='difficulties'], " +
        "input[name='conditions'], " +
        "input[name='favoriteCondition']"
    );

    // 出題数表示
    const countArea =
        document.getElementById("countReviewQuestions");

    // 件数取得
    async function updateCount() {

        const params = new URLSearchParams();

        // 理解度
        document
            .querySelectorAll("input[name='evaluations']:checked")
            .forEach(cb => {
                params.append("evaluations", cb.value);
            });

        // 難易度
        document
            .querySelectorAll("input[name='difficulties']:checked")
            .forEach(cb => {
                params.append("difficulties", cb.value);
            });

        // condition
        document
            .querySelectorAll("input[name='conditions']:checked")
            .forEach(cb => {
                params.append("conditions", cb.value);
            });

        // お気に入り条件
        params.append(
            "favoriteCondition",
            document.querySelector(
                "input[name='favoriteCondition']:checked"
            ).value
        );

        const response =
            await fetch("/review/count?" + params);

        const count =
            await response.text();

        countArea.textContent =
            count + "問";
    }

    // 検索条件変更時
    searchConditions.forEach(input => {
        input.addEventListener("change", updateCount);
    });

    // 初回表示時にも全復習対象問題数を取得
    updateCount();
});