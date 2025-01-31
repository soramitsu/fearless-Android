if (!window.__has_webview_ext) {
    window.__has_webview_ext = true;
    (function () {
        window.addEventListener('scroll', function(e) {
            var scrollX = e.target.scrollLeft || window.pageXOffset || document.documentElement.scrollLeft;
            var scrollY = e.target.scrollTop || window.pageYOffset || document.documentElement.scrollTop;
            window.AndroidWebViewBridge.onScroll(scrollX, scrollY);
        }, true);

        document.addEventListener('focusin', function(event) {
            if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
                const info = event.target.getBoundingClientRect();
                window.AndroidWebViewBridge.onElementFocused(JSON.stringify(info));
            }
        });

        document.addEventListener('focusout', function(event) {
            if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
                window.AndroidWebViewBridge.onElementBlurred();
            }
        });
    })();
}