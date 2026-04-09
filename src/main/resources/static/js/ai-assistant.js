/**
 * AI Assistant Client-side Logic for StrongWine
 * Optimized with session persistence, markdown rendering, and enhanced context.
 */
document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.getElementById('aiToggle');
    const panel = document.getElementById('aiPanel');
    const closeBtn = document.getElementById('aiClose');
    const quickPrompt = document.getElementById('aiPrompt');
    const form = document.getElementById('aiForm');
    const feed = document.getElementById('aiFeed');
    const STORAGE_KEY = 'strongwine_chat_history';

    if (!toggle || !panel) return;

    // --- UTILS ---

    const scrollToBottom = () => {
        feed.scrollTop = feed.scrollHeight;
    };

    const renderMarkdown = (text) => {
        if (!text) return '';
        let html = text
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/__(.*?)__/g, '<strong>$1</strong>')
            .replace(/^\s*[-*]\s+(.*)/gm, '<li>$1</li>')
            .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
            .replace(/\n/g, '<br>');
        return html;
    };

    const appendMessage = (role, text, isThinking = false, save = true) => {
        const messageDiv = document.createElement('div');
        messageDiv.className = `ai-message ai-message-${role === 'Bạn' ? 'user' : 'bot'}`;
        
        const bubble = document.createElement('div');
        bubble.className = 'ai-bubble';
        
        if (isThinking) {
            bubble.innerHTML = '<div class="ai-thinking"><span></span><span></span><span></span></div>';
            messageDiv.id = 'ai-thinking-temp';
        } else {
            // Use innerHTML for rendered markdown
            bubble.innerHTML = renderMarkdown(text);
        }

        messageDiv.appendChild(bubble);
        feed.appendChild(messageDiv);
        scrollToBottom();

        if (save && !isThinking) {
            const history = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '[]');
            history.push({ role, text });
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify(history.slice(-20))); // Keep last 20
        }
    };

    const loadHistory = () => {
        const history = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '[]');
        history.forEach(msg => appendMessage(msg.role, msg.text, false, false));
    };

    const buildContext = () => {
        const title = document.title || '';
        const path = window.location.pathname || '';
        let context = `Trang: ${title} (${path})`;

        // Extract Product Info if on details page
        const prodName = document.querySelector('h1')?.textContent?.trim();
        const prodPrice = document.querySelector('.price-tag')?.textContent?.trim();
        const prodDesc = document.querySelector('.text-light-emphasis.leading-relaxed')?.textContent?.trim();
        
        if (prodName) {
            context += ` | Sản phẩm đang xem: ${prodName}`;
            if (prodPrice) context += ` | Giá: ${prodPrice}`;
            if (prodDesc) context += ` | Mô tả: ${prodDesc.substring(0, 150)}...`;
        }

        return context;
    };

    // --- INTERACTION ---

    const togglePanel = (show) => {
        if (show === undefined) {
            panel.classList.toggle('show');
        } else if (show) {
            panel.classList.add('show');
        } else {
            panel.classList.remove('show');
        }
        
        if (panel.classList.contains('show')) {
            setTimeout(() => quickPrompt?.focus(), 400);
            scrollToBottom();
        }
    };

    toggle.addEventListener('click', () => togglePanel());
    if (closeBtn) {
        closeBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            togglePanel(false);
        });
    }

    // Auto-submit on suggestion
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('.suggestion-btn');
        if (btn) {
            const question = btn.getAttribute('data-ai-question');
            if (question && quickPrompt) {
                quickPrompt.value = question;
                form.dispatchEvent(new Event('submit'));
            }
        }
    });

    if (form && feed) {
        const submitBtn = form.querySelector('button[type="submit"]');

        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const question = quickPrompt.value?.trim();
            if (!question) return;

            appendMessage('Bạn', question);
            quickPrompt.value = '';

            if (submitBtn) submitBtn.disabled = true;
            appendMessage('AI', '', true);

            fetch('/api/assistant/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    prompt: question,
                    context: buildContext()
                })
            })
            .then(async (response) => {
                const payload = await response.json().catch(() => ({}));
                if (!response.ok) throw new Error(payload.error || 'Không thể kết nối chatbot');
                return payload;
            })
            .then((payload) => {
                const thinking = document.getElementById('ai-thinking-temp');
                if (thinking) thinking.remove();
                
                // If payload.answer exists, use it. If not, use generic fallback.
                const responseText = payload.answer || 'Xin lỗi, mình đang gặp khó khăn khi kết nối. Bạn thử lại nhé.';
                appendMessage('AI', responseText);
            })
            .catch((error) => {
                const thinking = document.getElementById('ai-thinking-temp');
                if (thinking) thinking.remove();

                const errorMsg = document.createElement('div');
                errorMsg.className = 'small text-danger text-center mb-2 px-3 fade-up';
                errorMsg.innerHTML = `<i class="fa-solid fa-circle-exclamation me-1"></i> ${error.message || 'Lỗi kết nối máy chủ'}`;
                feed.appendChild(errorMsg);
                scrollToBottom();
            })
            .finally(() => {
                if (submitBtn) submitBtn.disabled = false;
            });
        });

        // Initialize
        loadHistory();
    }
});
