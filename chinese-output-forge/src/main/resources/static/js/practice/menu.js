// =========================
// 出題条件による問題数・出題範囲更新
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

        // 条件に一致する問題数・出題範囲を取得
        const response =
            await fetch(
                `/practice/count?${params.toString()}`
            );

        const data = await response.json();

        // 初級
        document.getElementById("beginnerCount")
            .textContent = data.beginnerCount;

        updateRanges(
            "beginnerRange",
            data.beginnerRanges
        );

        // 中級
        document.getElementById("intermediateCount")
            .textContent = data.intermediateCount;

        updateRanges(
            "intermediateRange",
            data.intermediateRanges
        );

        // 上級
        document.getElementById("advancedCount")
            .textContent = data.advancedCount;

        updateRanges(
            "advancedRange",
            data.advancedRanges
        );
    });
});


// =========================
// 出題範囲更新
// =========================

function updateRanges(selectId, ranges) {

    const select =
        document.getElementById(selectId);

    // 「選択してください」以外を削除
    while (select.options.length > 1) {
        select.remove(1);
    }

    // 新しい出題範囲を追加
    ranges.forEach(range => {

        const option =
            document.createElement("option");

        option.value = range.start;
        option.textContent = range.displayText;

        select.appendChild(option);
    });

    // 選択状態を初期化
    select.selectedIndex = 0;
}

// =========================
// リスト選択による問題数更新
// =========================

// リスト選択
const practiceQuestionList =
    document.getElementById("practiceQuestionList");

// 対象問題数
const practiceListQuestionCount =
    document.getElementById("practiceListQuestionCount");

// リスト変更時
if (practiceQuestionList) {

    practiceQuestionList.addEventListener(
        "change",
        async () => {

            // 選択されたリストID
            const listId =
                practiceQuestionList.value;

            // 未選択の場合
            if (!listId) {

                practiceListQuestionCount
                    .textContent = "-";

                return;
            }

            // 指定したリストの問題数を取得
            const response =
                await fetch(
                    `/practice/menu/count/by-list?listId=${listId}`
                );

            const count =
                await response.json();

            // 問題数を更新
            practiceListQuestionCount
                .textContent = count;
        }
    );
}