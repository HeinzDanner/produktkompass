(() => {
    const form = document.getElementById('cart-search-form');
    if (!form) return;
    const guest = form.hasAttribute('data-guest-search');
    const tagSearch = document.querySelector('.cart-tag-search');
    const chips = [...tagSearch.querySelectorAll('[data-tag-key]')];
    const tagQuery = document.getElementById('cart-tag-query');
    const notice = document.querySelector('[data-tag-notice]');
    const searchStatus = document.querySelector('[data-search-status]');
    const reset = document.querySelector('[data-tags-reset]');
    const list = document.querySelector('[data-cart-list]');
    const queryInput = document.getElementById('cart-query');
    const draftKey = `cart-search-query:${form.dataset.searchOwner}`;
    if (!guest && !new URL(location.href).searchParams.has('query')) {
        const draft = sessionStorage.getItem(draftKey);
        if (draft !== null) queryInput.value = draft;
    }
    if (!guest) queryInput.addEventListener('input', () => sessionStorage.setItem(draftKey, queryInput.value));
    let request;
    let dragged;
    let dragPreview;
    let preferenceWrites = Promise.resolve();
    let pendingWrites = 0;
    let currentItems = guest ? [] : [...list.querySelectorAll('[data-cart-row]')].map(row => ({
        id: row.dataset.cartRow,
        quantity: Number(row.querySelector('[data-cart-quantity]').textContent)
    }));
    const states = ['preferred', 'neutral', 'excluded'];
    const normalizeText = text => text.normalize('NFKC').toLocaleLowerCase('de').trim();
    const tagSearchTexts = new Map(chips.map(chip => [chip, normalizeText(chip.dataset.tagSearch)]));

    function arrange() {
        const terms = normalizeText(tagQuery.value).split(/\s+/).filter(Boolean);
        const counts = { preferred: 0, neutral: 0, excluded: 0 };
        for (const chip of chips) {
            const state = chip.dataset.tagState;
            counts[state]++;
            chip.querySelector('[data-tag-select]').value = state;
            chip.querySelector('[data-tag-clear]').hidden = state === 'neutral';
            chip.hidden = state === 'neutral' && !terms.every(term => tagSearchTexts.get(chip).includes(term));
            tagSearch.querySelector(`[data-tag-zone="${state}"] .tag-zone-chips`).append(chip);
        }
        tagSearch.querySelector('summary').textContent =
            `Tag-Suche · ${counts.preferred} bevorzugt · ${counts.excluded} ausgeschlossen`;
    }

    function url(page = 0) {
        const result = new URL(form.getAttribute('action'), location.href);
        if (guest) {
            for (const [key, value] of new FormData(form)) {
                if (!key.startsWith('tag.')) result.searchParams.append(key, value);
            }
        }
        result.searchParams.set('query', document.getElementById('cart-query').value);
        if (!guest) result.searchParams.set('tagsExplicit', 'true');
        for (const chip of chips) {
            if (chip.dataset.tagState !== 'neutral') {
                result.searchParams.append(chip.dataset.tagState, chip.dataset.tagKey);
            }
        }
        if (page) result.searchParams.set('page', page);
        return result;
    }

    async function search(target = url()) {
        request?.abort();
        const current = new AbortController();
        request = current;
        const region = document.querySelector('.cart-results');
        region.setAttribute('aria-busy', 'true');
        searchStatus.textContent = 'Suche läuft …';
        searchStatus.classList.remove('field-error');
        try {
            const endpoint = new URL(form.dataset.resultsUrl, location.href);
            endpoint.search = target.search;
            const response = await fetch(endpoint, { signal: current.signal, headers: { Accept: 'text/html' } });
            if (!response.ok || response.redirected) throw new Error('Suchergebnisse konnten nicht geladen werden. Bitte erneut versuchen oder die Seite neu laden.');
            const documentResult = new DOMParser().parseFromString(await response.text(), 'text/html');
            const results = documentResult.querySelector('.cart-results');
            if (!results) throw new Error('Ungültige Suchantwort. Bitte die Seite neu laden.');
            if (current.signal.aborted) return;
            region.replaceWith(results);
            searchStatus.textContent = results.hidden ? 'Keine Produkte gefunden.'
                : results.querySelector('#cart-results-heading').textContent;
            document.dispatchEvent(new CustomEvent('cart:sync', { detail: { items: currentItems } }));
            history.replaceState(null, '', target);
            document.dispatchEvent(new CustomEvent('product-images:refresh'));
        } catch (error) {
            if (error.name !== 'AbortError' && request === current) {
                searchStatus.textContent = `${error.message} Die bisherigen Treffer wurden nicht aktualisiert.`;
                searchStatus.classList.add('field-error');
            }
        } finally {
            if (request === current) document.querySelector('.cart-results').removeAttribute('aria-busy');
        }
    }

    function assign(chip, state) {
        if (!states.includes(state)) return;
        chip.dataset.tagState = state;
        notice.textContent = state === 'excluded'
            ? 'Produkte mit diesem Tag werden aus den Suchergebnissen ausgeblendet.' : '';
        arrange();
        chip.querySelector('[data-tag-select]').focus();
        savePreferences();
    }

    function savePreferences() {
        if (guest) return Promise.resolve(true);
        const preferenceForm = document.querySelector('[data-tag-preferences]');
        const body = new URLSearchParams(new FormData(preferenceForm));
        for (const chip of chips) {
            if (chip.dataset.tagState !== 'neutral') body.append(chip.dataset.tagState, chip.dataset.tagKey);
        }
        pendingWrites++;
        preferenceWrites = preferenceWrites.then(async () => {
            try {
                const response = await fetch(preferenceForm.getAttribute('action'), {
                    method: 'POST', body, keepalive: true, headers: { Accept: 'application/json' }
                });
                if (!response.ok || response.redirected ||
                    !response.headers.get('content-type')?.includes('application/json')) {
                    throw new Error('Tag-Auswahl konnte nicht gespeichert werden. Bitte erneut anwenden oder die Seite neu laden.');
                }
                return true;
            } catch (error) {
                notice.textContent = error.message;
                return false;
            } finally {
                pendingWrites--;
            }
        });
        return preferenceWrites;
    }

    form.addEventListener('submit', event => {
        event.preventDefault();
        if (guest) {
            location.assign(url());
            return;
        }
        sessionStorage.setItem(draftKey, queryInput.value);
        savePreferences();
        search();
    });
    tagQuery.addEventListener('input', arrange);
    for (const chip of chips) {
        chip.querySelector('[data-tag-select]').addEventListener('change', event => assign(chip, event.target.value));
        chip.querySelector('[data-tag-clear]').addEventListener('click', () => assign(chip, 'neutral'));
        chip.addEventListener('dragstart', event => {
            dragged = chip;
            dragPreview?.remove();
            dragPreview = chip.cloneNode(true);
            const bounds = chip.getBoundingClientRect();
            dragPreview.classList.add('tag-drag-preview');
            dragPreview.style.width = `${bounds.width}px`;
            dragPreview.setAttribute('aria-hidden', 'true');
            tagSearch.append(dragPreview);
            event.dataTransfer.setData('text/plain', chip.dataset.tagKey);
            event.dataTransfer.effectAllowed = 'move';
            event.dataTransfer.setDragImage(dragPreview,
                Math.max(0, Math.min(event.clientX - bounds.left, bounds.width)),
                Math.max(0, Math.min(event.clientY - bounds.top, bounds.height)));
        });
        chip.addEventListener('dragend', () => {
            dragged = null;
            dragPreview?.remove();
            dragPreview = null;
        });
    }
    for (const zone of tagSearch.querySelectorAll('[data-tag-zone]')) {
        zone.addEventListener('dragover', event => {
            if (dragged) event.preventDefault();
        });
        zone.addEventListener('drop', event => {
            if (!dragged) return;
            event.preventDefault();
            dragPreview?.remove();
            dragPreview = null;
            assign(dragged, zone.dataset.tagZone);
            dragged = null;
        });
    }
    reset.hidden = false;
    const resetLink = document.querySelector('[data-tags-reset-link]');
    if (resetLink) resetLink.hidden = true;
    reset.addEventListener('click', () => {
        chips.forEach(chip => { chip.dataset.tagState = 'neutral'; });
        notice.textContent = 'Alle Tags sind wieder neutral.';
        arrange();
        savePreferences();
    });
    document.addEventListener('click', async event => {
        const link = event.target.closest('.cart-results nav a');
        if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        if (link) {
            event.preventDefault();
            search(new URL(link.href));
            return;
        }
        const navigation = event.target.closest('a[href]');
        if (!navigation || navigation.target || navigation.hasAttribute('download')
            || new URL(navigation.href).origin !== location.origin || pendingWrites === 0) return;
        event.preventDefault();
        let saved;
        do {
            const writes = preferenceWrites;
            saved = await writes;
        } while (pendingWrites > 0);
        if (saved) location.assign(navigation.href);
    });
    document.addEventListener('cart:changed', event => {
        if (guest) return;
        const items = event.detail.items;
        if (!Array.isArray(items)) return;
        currentItems = items;
        const ids = new Set(items.map(item => String(item.id)));
        for (const row of list.querySelectorAll('[data-cart-row]')) {
            if (!ids.has(row.dataset.cartRow)) row.remove();
        }
        for (const item of items) {
            let row = [...list.children].find(entry => entry.dataset.cartRow === String(item.id));
            if (!row) {
                row = document.querySelector('[data-cart-row-template]').content.firstElementChild.cloneNode(true);
                row.dataset.cartRow = item.id;
                const link = row.querySelector('[data-cart-item-link]');
                link.textContent = item.name;
                link.href = link.getAttribute('href').replace(/\/0$/, `/${item.id}`);
                const panel = row.querySelector('[data-cart-product]');
                panel.dataset.cartProduct = item.id;
                panel.dataset.cartName = item.name;
                const changeForm = panel.querySelector('form');
                changeForm.setAttribute('action', changeForm.getAttribute('action').replace(/\/0$/, `/${item.id}`));
                panel.querySelector('[data-cart-quantity]').setAttribute('aria-label', `Menge im Warenkorb: ${item.name}`);
                panel.querySelector('.cart-increase').setAttribute('aria-label', `Menge erhöhen: ${item.name}`);
                list.append(row);
            }
            list.append(row);
        }
        document.dispatchEvent(new CustomEvent('cart:sync', { detail: { items } }));
        document.querySelector('[data-cart-empty]').hidden = items.length > 0;
        document.querySelector('[data-cart-summary]').textContent = `${items.length} Artikel`;
    });
    arrange();
})();
