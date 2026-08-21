// =========================
// 解答表示
// =========================

function showAnswer() {

    const answerButton = document.getElementById("answerButton");
    const answerArea = document.getElementById("answerArea");

    answerButton.style.display = "none";
    answerArea.style.display = "block";
}

// =========================
// お気に入り登録・解除
// =========================

const favoriteButton = document.getElementById("favoriteButton");

if (favoriteButton) {

    favoriteButton.addEventListener("click", function () {

        const questionId = favoriteButton.dataset.questionId;

        const csrfToken =
            document.querySelector('meta[name="_csrf"]').content;

        const csrfHeader =
            document.querySelector('meta[name="_csrf_header"]').content;

        fetch("/favorite/toggle", {

            method: "POST",

            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                [csrfHeader]: csrfToken
            },

            body: "questionId=" + questionId

        })
        .then(response => response.text())
		.then(result => {
		
		    const favoriteIcon =
		            document.getElementById("favoriteIcon");
		
		    if (result === "true") {
		
		        favoriteIcon.classList.remove(
		                "bi-heart",
		                "text-secondary");
		
		        favoriteIcon.classList.add(
		                "bi-heart-fill",
		                "text-danger");
		
		    } else {
		
		        favoriteIcon.classList.remove(
		                "bi-heart-fill",
		                "text-danger");
		
		        favoriteIcon.classList.add(
		                "bi-heart",
		                "text-secondary");
		
		    }
		
		});

    });

}

const favoriteIcon = document.getElementById("favoriteIcon");