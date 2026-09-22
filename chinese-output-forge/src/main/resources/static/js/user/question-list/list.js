document.addEventListener("DOMContentLoaded", () => {

    const detailButtons =
        document.querySelectorAll(".question-list-detail-button");

    detailButtons.forEach(button => {

        button.addEventListener("click", async () => {

            const listId = button.dataset.listId;

            const detailRow =
                document.getElementById(`list-detail-${listId}`);

            const detailContent =
                document.getElementById(`list-detail-content-${listId}`);

            // 既に開いている場合は閉じる
            if (!detailRow.classList.contains("d-none")) {
                detailRow.classList.add("d-none");
                return;
            }

            try {

                // 指定したリストの問題一覧を取得
                const response = await fetch(
                    `/user/question-list/detail?listId=${listId}`
                );

                if (!response.ok) {
                    throw new Error("リストの取得に失敗しました。");
                }

                const listItems = await response.json();

                // 動作確認用
                console.log(listItems);

                // 詳細領域を初期化
                detailContent.innerHTML = "";

                if (listItems.length === 0) {

                    detailContent.textContent =
                        "このリストには問題が登録されていません。";

                } else {

					listItems.forEach(question => {
					    const div = document.createElement("div");
					
					    div.textContent =
					        question.chineseText + " / " + question.japaneseText;
					
					    detailContent.appendChild(div);
					});
                }

                // 詳細を表示
                detailRow.classList.remove("d-none");

            } catch (error) {

                console.error(error);

                detailContent.textContent =
                    "リストの取得中にエラーが発生しました。";

                detailRow.classList.remove("d-none");
            }
        });
    });
});