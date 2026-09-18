document.querySelectorAll(".password-toggle").forEach(button => {

    button.addEventListener("click", () => {

        const passwordInput =
                document.getElementById(button.dataset.target);

        const icon = button.querySelector("i");

        if (passwordInput.type === "password") {
            passwordInput.type = "text";

            icon.classList.remove("bi-eye");
            icon.classList.add("bi-eye-slash");

        } else {
            passwordInput.type = "password";

            icon.classList.remove("bi-eye-slash");
            icon.classList.add("bi-eye");
        }
    });
});