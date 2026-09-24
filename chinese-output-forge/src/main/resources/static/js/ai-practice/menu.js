// =========================
// AI生成対象問題件数表示
// =========================

document.addEventListener("DOMContentLoaded", () => {

    // 検索条件
    const searchConditions = document.querySelectorAll(
        "input[name='evaluations'], " +
        "input[name='difficulties'], " +
        "input[name='favoriteCondition'], " +
        "input[name='structureIds']"
    );

    // 問題件数表示
    const countArea =
        document.getElementById("countAiPracticeQuestions");


    // =========================
    // 件数取得
    // =========================

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


        // お気に入り条件
        const favoriteCondition =
            document.querySelector(
                "input[name='favoriteCondition']:checked"
            );

        if (favoriteCondition) {
            params.append(
                "favoriteCondition",
                favoriteCondition.value
            );
        }


        // 文法・構造
        document
            .querySelectorAll("input[name='structureIds']:checked")
            .forEach(cb => {
                params.append("structureIds", cb.value);
            });


        const response =
            await fetch("/ai-practice/count?" + params);

        const count =
            await response.text();

        countArea.textContent =
            count + "問";
    }


    // =========================
    // 検索条件変更時
    // =========================

    searchConditions.forEach(input => {

        input.addEventListener(
            "change",
            updateCount
        );

    });


    // 初回表示時にもAI生成対象問題数を取得
    updateCount();
    
	// =========================
	// リスト選択によるAI生成対象問題数更新
	// =========================
	
	// リスト選択
	const aiPracticeQuestionList =
	    document.getElementById("aiPracticeQuestionList");
	
	// AI生成対象問題数
	const aiPracticeListQuestionCount =
	    document.getElementById("aiPracticeListQuestionCount");
	
	// リスト変更時
	if (aiPracticeQuestionList) {
	
	    aiPracticeQuestionList.addEventListener(
	        "change",
	        async () => {
	
	            // 選択されたリストID
	            const listId =
	                aiPracticeQuestionList.value;
	
	            // 未選択の場合
	            if (!listId) {
	
	                aiPracticeListQuestionCount
	                    .textContent = "-";
	
	                return;
	            }
	
	            // 指定したリストのAI生成対象問題数を取得
	            const response =
	                await fetch(
	                    `/ai-practice/count/by-list?listId=${listId}`
	                );
	
	            const count =
	                await response.text();
	
	            // 問題数を更新
	            aiPracticeListQuestionCount
	                .textContent = count;
	        }
	    );
	}    


    // =========================
    // 文法・構造の一括選択
    // =========================

    const selectAllStructuresButton =
        document.getElementById("selectAllStructures");

    const clearAllStructuresButton =
        document.getElementById("clearAllStructures");

    const structureCheckboxes =
        document.querySelectorAll(
            "input[name='structureIds']"
        );


    // すべて選択
    selectAllStructuresButton.addEventListener("click", () => {

        structureCheckboxes.forEach(checkbox => {
            checkbox.checked = true;
        });

        updateCount();

    });


    // すべて解除
    clearAllStructuresButton.addEventListener("click", () => {

        structureCheckboxes.forEach(checkbox => {
            checkbox.checked = false;
        });

        updateCount();

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


    // =========================
    // 文法・構造の説明
    // =========================

    const tooltipTriggerList =
        document.querySelectorAll(
            '[data-bs-toggle="tooltip"]'
        );

    tooltipTriggerList.forEach(element => {
        new bootstrap.Tooltip(element);
    });

});