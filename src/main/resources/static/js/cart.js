(() => {
    const busy = new Set();
    function panels(id) {
        return [...document.querySelectorAll('[data-cart-product]')]
            .filter(panel => panel.dataset.cartProduct === id);
    }
    function render(panel, quantity) {
        panel.querySelector('.cart-add').hidden = quantity > 0;
        panel.querySelector('.cart-stepper').hidden = quantity === 0;
        panel.querySelector('[data-cart-quantity]').textContent = quantity;
        panel.querySelector('[data-cart-minus]').hidden = quantity === 1;
        panel.querySelector('[data-cart-trash]').hidden = quantity !== 1;
        panel.querySelector('.cart-decrease').setAttribute('aria-label',
            `${quantity === 1 ? 'Aus dem Warenkorb entfernen' : 'Menge verringern'}: ${panel.dataset.cartName}`);
    }
    document.addEventListener('submit', async event => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement) || !form.matches('[data-cart-form]')) return;
        event.preventDefault();
        const panel = form.closest('[data-cart-product]');
        const id = panel.dataset.cartProduct;
        if (busy.has(id)) return;
        const action = event.submitter?.value;
        if (!action) return;
        busy.add(id);
        panels(id).forEach(item => {
            item.querySelector('[data-cart-feedback]').textContent = '';
            item.querySelectorAll('button').forEach(button => button.disabled = true);
        });
        try {
            const body = new URLSearchParams(new FormData(form));
            body.set('action', action);
            body.set('ajax', 'true');
            const response = await fetch(form.getAttribute('action'), {
                method: 'POST', body, headers: { Accept: 'application/json' }
            });
            if (response.redirected || !response.ok ||
                !response.headers.get('content-type')?.includes('application/json')) {
                throw new Error(response.status === 403 || response.redirected
                    ? 'Sitzung abgelaufen. Bitte neu anmelden oder die Seite neu laden.'
                    : 'Warenkorb konnte nicht gespeichert werden. Bitte erneut versuchen.');
            }
            const result = await response.json();
            if (String(result.productId) !== id || !Number.isInteger(result.quantity) || result.quantity < 0) {
                throw new Error('Ungültige Antwort beim Speichern des Warenkorbs. Bitte Seite neu laden.');
            }
            panels(id).forEach(item => {
                render(item, result.quantity);
                item.querySelector('[data-cart-feedback]').textContent = result.quantity === 0
                    ? 'Aus dem Warenkorb entfernt.' : `Gespeichert: ${result.quantity} im Warenkorb.`;
            });
            const target = panel.querySelector(result.quantity === 0 ? '.cart-add'
                : action === 'INCREMENT' ? '.cart-increase' : '.cart-decrease');
            target.disabled = false;
            target.focus();
        } catch (error) {
            panels(id).forEach(item => item.querySelector('[data-cart-feedback]').textContent =
                error instanceof Error ? error.message : 'Warenkorb konnte nicht gespeichert werden.');
        } finally {
            busy.delete(id);
            panels(id).forEach(item => item.querySelectorAll('button').forEach(button => button.disabled = false));
        }
    });
})();
