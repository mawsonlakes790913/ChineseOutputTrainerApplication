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