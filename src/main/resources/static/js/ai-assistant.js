(function (window, document) {
    "use strict";

    function getCsrfHeaders() {
        var headerMeta = document.querySelector('meta[name="_csrf_header"]');
        var tokenMeta = document.querySelector('meta[name="_csrf"]');
        var headers = { "Content-Type": "application/json" };
        if (tokenMeta && tokenMeta.content) {
            headers[(headerMeta && headerMeta.content) || "X-CSRF-TOKEN"] = tokenMeta.content;
        }
        return headers;
    }

    function formatCurrency(value) {
        var n = Number(value);
        return new Intl.NumberFormat("vi-VN").format(Number.isFinite(n) ? n : 0) + " ₫";
    }

    function initAiAssistant() {
        var toggle = document.getElementById("aiToggle");
        var panel = document.getElementById("aiPanel");
        var closeBtn = document.getElementById("aiClose");
        var quickPrompt = document.getElementById("aiPrompt");
        var form = document.getElementById("aiForm");
        var feed = document.getElementById("aiFeed");

        if (!toggle || !panel) {
            return;
        }

        function openPanel() {
            if (window.SwDialog && typeof window.SwDialog.open === "function") {
                window.SwDialog.open(panel, toggle);
            } else {
                panel.classList.add("show");
            }
        }

        function closePanel() {
            if (window.SwDialog && typeof window.SwDialog.close === "function") {
                window.SwDialog.close(panel);
            } else {
                panel.classList.remove("show");
            }
        }

        toggle.addEventListener("click", function () {
            var isOpen = panel.hasAttribute("open") || panel.classList.contains("show");
            if (isOpen) {
                closePanel();
            } else {
                openPanel();
            }
        });

        if (closeBtn) {
            closeBtn.addEventListener("click", closePanel);
        }

        panel.querySelectorAll("[data-ai-question]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                if (quickPrompt) {
                    quickPrompt.value = btn.getAttribute("data-ai-question") || "";
                    quickPrompt.focus();
                }
            });
        });

        if (!form || !feed) {
            return;
        }

        var submitBtn = form.querySelector('button[type="submit"], button:not([type])');

        function appendBubble(roleLabel, text, className, recommendations) {
            var wrap = document.createElement("div");
            wrap.className = className || "mb8";
            wrap.style.padding = "8px 10px";
            wrap.style.borderRadius = "8px";
            wrap.style.background = "var(--surface-elevated)";
            wrap.style.border = "1px solid var(--border-hairline)";
            wrap.style.fontSize = "13px";

            var title = document.createElement("strong");
            title.textContent = roleLabel + ": ";
            title.style.color = "var(--accent-gold-ink)";

            var content = document.createElement("span");
            content.style.whiteSpace = "pre-wrap";
            content.textContent = text;

            wrap.appendChild(title);
            wrap.appendChild(content);

            if (Array.isArray(recommendations) && recommendations.length > 0) {
                var list = document.createElement("div");
                list.className = "stack mt8";
                list.style.gap = "8px";

                recommendations.forEach(function (wine) {
                    if (!wine || wine.id == null) {
                        return;
                    }
                    var card = document.createElement("div");
                    card.className = "panel-soft p10";
                    card.style.display = "grid";
                    card.style.gridTemplateColumns = "44px minmax(0, 1fr) auto";
                    card.style.gap = "10px";
                    card.style.alignItems = "center";

                    var thumb = document.createElement("div");
                    thumb.style.width = "44px";
                    thumb.style.height = "56px";
                    thumb.style.borderRadius = "6px";
                    thumb.style.overflow = "hidden";
                    thumb.style.display = "grid";
                    thumb.style.placeItems = "center";
                    thumb.style.background = "var(--surface-sunken)";
                    thumb.style.border = "1px solid var(--border-hairline)";

                    if (wine.imageUrl) {
                        var img = document.createElement("img");
                        img.src = wine.imageUrl;
                        img.alt = wine.name || "Rượu vang";
                        img.style.width = "100%";
                        img.style.height = "100%";
                        img.style.objectFit = "contain";
                        img.addEventListener("error", function () {
                            img.remove();
                            var fallback = document.createElement("span");
                            fallback.textContent = "S";
                            fallback.style.fontFamily = "var(--font-display)";
                            fallback.style.color = "var(--accent-gold-ink)";
                            thumb.appendChild(fallback);
                        });
                        thumb.appendChild(img);
                    } else {
                        var mono = document.createElement("span");
                        mono.textContent = "S";
                        mono.style.fontFamily = "var(--font-display)";
                        mono.style.color = "var(--accent-gold-ink)";
                        thumb.appendChild(mono);
                    }
                    card.appendChild(thumb);

                    var info = document.createElement("div");
                    var link = document.createElement("a");
                    link.href = "/wines/" + encodeURIComponent(String(wine.id));
                    link.textContent = wine.name || "Chai vang đề xuất";
                    link.style.fontWeight = "700";
                    link.style.display = "block";
                    link.style.color = "var(--text-primary)";

                    var sub = document.createElement("div");
                    sub.className = "muted";
                    sub.style.fontSize = "11.5px";
                    var metaParts = [];
                    if (wine.type) metaParts.push(wine.type);
                    if (wine.year) metaParts.push(String(wine.year));
                    if (wine.country) metaParts.push(wine.country);
                    metaParts.push(wine.inStock ? ("Còn " + (wine.availableStock || 0) + " chai") : "Hết hàng");
                    sub.textContent = metaParts.join(" · ");

                    var price = document.createElement("div");
                    price.className = "tabular-nums";
                    price.style.fontWeight = "700";
                    price.style.color = "var(--accent-gold-ink)";
                    price.style.fontSize = "12.5px";
                    price.textContent = formatCurrency(wine.price);

                    info.appendChild(link);
                    info.appendChild(sub);
                    info.appendChild(price);
                    card.appendChild(info);

                    if (wine.inStock && Number(wine.availableStock) > 0) {
                        var addBtn = document.createElement("button");
                        addBtn.type = "button";
                        addBtn.className = "btn btn-sm js-add-to-cart";
                        addBtn.textContent = "+ Giỏ";
                        addBtn.setAttribute("data-cart-add", "true");
                        addBtn.setAttribute("data-cart-product-id", String(wine.id));
                        addBtn.setAttribute("data-cart-name", wine.name || "");
                        addBtn.setAttribute("data-cart-price", String(wine.price || 0));
                        addBtn.setAttribute("data-cart-image", wine.imageUrl || "");
                        addBtn.setAttribute("data-cart-stock", String(wine.availableStock));
                        card.appendChild(addBtn);
                    }

                    list.appendChild(card);
                });

                if (list.children.length > 0) {
                    wrap.appendChild(list);
                }
            }

            feed.appendChild(wrap);
            feed.scrollTop = feed.scrollHeight;
            return wrap;
        }

        function buildContext() {
            var title = document.title || "";
            var path = window.location.pathname || "";
            return (title + " | " + path).trim();
        }

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var question = quickPrompt && quickPrompt.value ? quickPrompt.value.trim() : "";
            if (!question) {
                return;
            }
            appendBubble("Bạn", question, "mb8");
            quickPrompt.value = "";

            if (submitBtn) {
                submitBtn.disabled = true;
            }

            var thinkingNode = appendBubble("Sommelier AI", "Đang tra cứu hầm rượu...", "mb8 ai-thinking");

            fetch("/api/assistant/chat", {
                method: "POST",
                headers: getCsrfHeaders(),
                credentials: "same-origin",
                body: JSON.stringify({
                    prompt: question,
                    context: buildContext()
                })
            })
                .then(function (response) {
                    return response.json().catch(function () { return {}; }).then(function (payload) {
                        if (!response.ok) {
                            throw new Error(payload.error || "Không thể kết nối Sommelier AI");
                        }
                        return payload;
                    });
                })
                .then(function (payload) {
                    if (thinkingNode && thinkingNode.parentNode) {
                        thinkingNode.remove();
                    }
                    var replyText = payload.answer || payload.reply || "Mình chưa có gợi ý phù hợp, bạn thử mô tả món ăn hoặc ngân sách nhé.";
                    var recs = Array.isArray(payload.recommendations) ? payload.recommendations : [];
                    appendBubble("Sommelier AI", replyText, "mb8", recs);
                })
                .catch(function (error) {
                    if (thinkingNode && thinkingNode.parentNode) {
                        thinkingNode.remove();
                    }
                    appendBubble("Sommelier AI", error.message || "Đã xảy ra lỗi khi gọi Sommelier AI.", "mb8");
                })
                .finally(function () {
                    if (submitBtn) {
                        submitBtn.disabled = false;
                    }
                    if (quickPrompt) {
                        quickPrompt.focus();
                    }
                });
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initAiAssistant, { once: true });
    } else {
        initAiAssistant();
    }
})(window, document);
