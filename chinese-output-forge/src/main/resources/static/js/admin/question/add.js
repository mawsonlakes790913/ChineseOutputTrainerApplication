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
	
	// =========================
	// プレースホルダ入力
	// =========================
	
	const placeholderButtons =
	    document.querySelectorAll(".placeholder-button");
	
	placeholderButtons.forEach(button => {
	
	    button.addEventListener("click", () => {
	
	        // テンプレート入力不可の場合は何もしない
	        if (template.disabled) {
	            return;
	        }
	
	        const placeholder =
	            button.dataset.placeholder;
	
	        // 現在のカーソル位置
	        const start =
	            template.selectionStart;
	
	        const end =
	            template.selectionEnd;
	
	        // カーソル位置にプレースホルダを挿入
	        template.value =
	            template.value.substring(0, start)
	            + placeholder
	            + template.value.substring(end);
	
	        // カーソルを挿入した文字の後ろへ移動
	        const cursorPosition =
	            start + placeholder.length;
	
	        template.setSelectionRange(
	            cursorPosition,
	            cursorPosition
	        );
	
	        template.focus();
	    });
	});	 
    
});