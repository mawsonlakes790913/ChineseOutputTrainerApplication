document.querySelectorAll('.language-variant-form').forEach(form => {
    form.addEventListener('submit', function(event) {

        const message = this.querySelector('button').dataset.confirmMessage;

        if (!confirm(message)) {
            event.preventDefault();
        }
    });
});