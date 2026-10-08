(() => {
    document.querySelectorAll("select[data-lookup-url]").forEach((select, index) => {
        const controls = document.createElement("div");
        controls.className = "lookup-controls";
        const label = select.getAttribute("aria-label") || "Einträge";
        const input = document.createElement("input");
        input.type = "search";
        input.placeholder = `${label} suchen`;
        input.setAttribute("aria-label", `${label} suchen`);
        const button = text => {
            const element = document.createElement("button");
            element.type = "button";
            element.className = "button secondary";
            element.textContent = text;
            return element;
        };
        const search = button("Suchen");
        const previous = button("Zurück");
        const next = button("Weiter");
        const status = document.createElement("span");
        status.id = `lookup-status-${index}`;
        status.setAttribute("role", "status");
        select.setAttribute("aria-describedby", status.id);
        controls.append(input, search, previous, next, status);
        select.insertAdjacentElement("afterend", controls);
        let page = 0;
        let query = "";
        let totalPages = 0;
        let loading = false;

        async function load(targetPage, targetQuery) {
            if (loading) return;
            loading = true;
            search.disabled = previous.disabled = next.disabled = true;
            status.textContent = "Einträge werden geladen …";
            try {
                const url = new URL(select.dataset.lookupUrl, window.location.origin);
                url.search = new URLSearchParams({ query: targetQuery, page: targetPage, size: 20 });
                const response = await fetch(url, { headers: { Accept: "application/json" } });
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                const result = await response.json();
                // Preserve current selections, including changes made while the request was pending.
                const retained = Array.from(select.options)
                    .filter(option => option.selected || option.value === "")
                    .map(option => {
                        const copy = option.cloneNode(true);
                        copy.selected = option.selected;
                        return copy;
                    });
                const ids = new Set(retained.map(option => option.value));
                const options = result.content.filter(item => !ids.has(String(item.id)))
                    .map(item => new Option(item.name || item.title, String(item.id)));
                const hadSelection = retained.some(option => option.selected);
                select.replaceChildren(...retained, ...options);
                if (!hadSelection) select.selectedIndex = -1;
                page = result.page;
                query = targetQuery;
                totalPages = result.totalPages;
                status.textContent = `${result.totalElements} Treffer; Seite ${totalPages ? page + 1 : 0} von ${totalPages}. Ausgewählte Einträge bleiben erhalten.`;
            } catch (error) {
                status.textContent = `Einträge konnten nicht geladen werden (${error.message}). Erneut versuchen.`;
            } finally {
                loading = false;
                search.disabled = false;
                previous.disabled = page <= 0;
                next.disabled = page + 1 >= totalPages;
            }
        }

        search.addEventListener("click", () => load(0, input.value.trim()));
        input.addEventListener("keydown", event => {
            if (event.key === "Enter") {
                event.preventDefault();
                load(0, input.value.trim());
            }
        });
        previous.addEventListener("click", () => load(page - 1, query));
        next.addEventListener("click", () => load(page + 1, query));
        load(0, "");
    });
})();
