// =========================
// 出題条件による問題数更新
// =========================

// 出題条件
const sourceConditions =
    document.querySelectorAll(
        "input[name='sourceCondition']"
    );

// 条件変更時
sourceConditions.forEach(condition => {

    condition.addEventListener("change", async () => {

        // 選択された出題条件
        const selectedCondition =
            document.querySelector(
                "input[name='sourceCondition']:checked"
            );

        // パラメータ
        const params = new URLSearchParams();

        params.append(
            "sourceCondition",
            selectedCondition.value
        );

        // 条件に一致する問題数を取得
        const response =
            await fetch(
                `/practice/count?${params.toString()}`
            );

        const data = await response.json();

        // 初級の問題数を更新
        document.getElementById("beginnerCount")
            .textContent = data.beginnerCount;

        // 中級の問題数を更新
        document.getElementById("intermediateCount")
            .textContent = data.intermediateCount;

        // 上級の問題数を更新
        document.getElementById("advancedCount")
            .textContent = data.advancedCount;
    });

});