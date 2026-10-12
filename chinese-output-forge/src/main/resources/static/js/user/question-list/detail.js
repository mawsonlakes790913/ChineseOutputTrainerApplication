// =========================
// 問題詳細モーダル
// =========================

const detailButtons =
    document.querySelectorAll(".detailButton");

const modal =
    document.getElementById("questionDetailModal");

const pronunciationType =
    modal.dataset.pronunciationType;

// 発音表記設定を確認
console.log("pronunciationType =", pronunciationType);

detailButtons.forEach(button => {

    button.addEventListener("click", () => {

        // 詳細ボタンに設定されているデータを確認
        console.log("pinyin =", button.dataset.pinyin);
        console.log("zhuyin =", button.dataset.zhuyin);
        console.log("alternative =", button.dataset.alternative);
        console.log(
            "alternativePinyin =",
            button.dataset.alternativePinyin
        );
        console.log(
            "alternativeZhuyin =",
            button.dataset.alternativeZhuyin
        );
        console.log("structure =", button.dataset.structure);


        // =========================
        // 日本語
        // =========================

        document.getElementById("modalJapanese")
            .textContent =
                button.dataset.japanese || "";


        // =========================
        // 中国語
        // =========================

        document.getElementById("modalChinese")
            .textContent =
                button.dataset.chinese || "";


        // =========================
        // 中国語の発音
        // =========================

        const chinesePronunciationArea =
            document.getElementById(
                "modalChinesePronunciationArea"
            );

        const chinesePronunciation =
            document.getElementById(
                "modalChinesePronunciation"
            );

        if (pronunciationType === "PINYIN") {

            chinesePronunciation.textContent =
                button.dataset.pinyin || "";

            chinesePronunciationArea.style.display =
                button.dataset.pinyin
                    ? ""
                    : "none";

        } else if (pronunciationType === "ZHUYIN") {

            chinesePronunciation.textContent =
                button.dataset.zhuyin || "";

            chinesePronunciationArea.style.display =
                button.dataset.zhuyin
                    ? ""
                    : "none";

        } else {

            chinesePronunciation.textContent = "";
            chinesePronunciationArea.style.display =
                "none";
        }


        // =========================
        // 文法・構造
        // =========================

        document.getElementById("modalStructure")
            .textContent =
                button.dataset.structure || "";


        // =========================
        // 別解
        // =========================

        const alternativeArea =
            document.getElementById(
                "modalAlternativeArea"
            );

        const alternative =
            button.dataset.alternative;

        if (alternative) {

            alternativeArea.style.display = "";

            document.getElementById(
                "modalAlternative"
            ).textContent = alternative;


            // =========================
            // 別解の発音
            // =========================

            const alternativePronunciationArea =
                document.getElementById(
                    "modalAlternativePronunciationArea"
                );

            const alternativePronunciation =
                document.getElementById(
                    "modalAlternativePronunciation"
                );

            if (pronunciationType === "PINYIN") {

                alternativePronunciation.textContent =
                    button.dataset.alternativePinyin || "";

                alternativePronunciationArea.style.display =
                    button.dataset.alternativePinyin
                        ? ""
                        : "none";

            } else if (pronunciationType === "ZHUYIN") {

                alternativePronunciation.textContent =
                    button.dataset.alternativeZhuyin || "";

                alternativePronunciationArea.style.display =
                    button.dataset.alternativeZhuyin
                        ? ""
                        : "none";

            } else {

                alternativePronunciation.textContent = "";
                alternativePronunciationArea.style.display =
                    "none";
            }

        } else {

            alternativeArea.style.display = "none";
        }

    });

});