(() => {
    const picker = document.getElementById("alternativePicker");
    if (!picker) return;

    const select = document.getElementById("alternativeProductIds");
    const input = document.getElementById("alternativeSearch");
    const search = document.getElementById("alternativeSearchButton");
    const previous = document.getElementById("alternativePrevious");
    const next = document.getElementById("alternativeNext");
    const status = document.getElementById("alternativeSearchStatus");
    let page = 0;
    let query = "";
    let totalPages = 0;

    async function load(targetPage, targetQuery) {
        search.disabled = previous.disabled = next.disabled = true;
        status.textContent = "Alternativen werden geladen ...";
        try {
            const url = new URL(picker.dataset.searchUrl, window.location.origin);
            url.search = new URLSearchParams({ name: targetQuery, page: targetPage, size: 20 });
            const response = await fetch(url, { headers: { Accept: "application/json" } });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const results = await response.json();
            // Read selections after the request, so changes made during loading are preserved.
            const selected = Array.from(select.selectedOptions).map(option => option.cloneNode(true));
            select.replaceChildren(...selected);
            const ids = new Set(selected.map(option => option.value));
            for (const product of results.content) {
                const id = String(product.id);
                if (id !== picker.dataset.productId && !ids.has(id)) {
                    select.add(new Option(product.name, id));
                    ids.add(id);
                }
            }
            page = results.page;
            query = targetQuery;
            totalPages = results.totalPages;
            status.textContent = `${results.totalElements} Treffer; Seite ${totalPages ? page + 1 : 0} von ${totalPages}.`;
        } catch (error) {
            status.textContent = `Alternativen konnten nicht geladen werden (${error.message}). Bitte erneut versuchen.`;
        } finally {
            search.disabled = false;
            previous.disabled = page <= 0;
            next.disabled = page + 1 >= totalPages;
        }
    }

    search.addEventListener("click", () => load(0, input.value.trim()));
    input.addEventListener("keydown", event => {
        if (event.key === "Enter") {
            event.preventDefault();
            if (!search.disabled) load(0, input.value.trim());
        }
    });
    previous.addEventListener("click", () => load(page - 1, query));
    next.addEventListener("click", () => load(page + 1, query));
})();
