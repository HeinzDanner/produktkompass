(() => {
    function showPlaceholder(image) {
        const container = image.closest('[data-product-image]');
        if (!container) return;
        image.hidden = true;
        const placeholder = container.querySelector('.product-image-placeholder');
        if (placeholder) placeholder.hidden = false;
    }

    document.addEventListener('error', event => {
        if (event.target instanceof HTMLImageElement) showPlaceholder(event.target);
    }, true);

    function checkImages() {
        document.querySelectorAll('[data-product-image] img').forEach(image => {
            if (image.complete && image.naturalWidth === 0) showPlaceholder(image);
        });
    }
    document.addEventListener('product-images:refresh', checkImages);
    checkImages();
})();
