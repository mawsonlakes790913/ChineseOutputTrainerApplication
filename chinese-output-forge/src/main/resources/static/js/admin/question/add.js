document.addEventListener("DOMContentLoaded", () => {

    const hasAlternativeAnswer =
        document.getElementById("hasAlternativeAnswer");

    const alternativeAnswerFields =
        document.querySelectorAll(".alternative-answer-field");

    function updateAlternativeAnswerFields() {

        alternativeAnswerFields.forEach(field => {
            field.disabled = !hasAlternativeAnswer.checked;
        });
    }

    // 初期表示
    updateAlternativeAnswerFields();

    // チェック状態変更時
    hasAlternativeAnswer.addEventListener(
        "change",
        updateAlternativeAnswerFields
    );

	// =========================
	// AI生成許可によるテンプレート制御
	// =========================
	
	const allowAiVariation =
	    document.getElementById("allowAiVariation");
	
	const template =
	    document.getElementById("template");
	
	function updateTemplateField() {
	
	    template.disabled =
	        allowAiVariation.value === "false";
	}
	
	// 初期表示
	updateTemplateField();
	
	// 選択変更時
	allowAiVariation.addEventListener(
	    "change",
	    updateTemplateField
	);    
    
});