// =========================
// 問題件数表示
// =========================

document.addEventListener("DOMContentLoaded", () => {

    // 検索条件
	const searchConditions = document.querySelectorAll(
	    "input[name='evaluations'], " +
	    "input[name='difficulties'], " +
	    "input[name='conditions'], " +
	    "input[name='favoriteCondition'], " +
	    "input[name='structureIds']"
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
        
		// 文法・構造
		document
		    .querySelectorAll("input[name='structureIds']:checked")
		    .forEach(cb => {
		        params.append("structureIds", cb.value);
		    });     

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
    
});


