document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.getElementById('aiToggle');
    const panel = document.getElementById('aiPanel');
    const quickPrompt = document.getElementById('aiPrompt');

    if (!toggle || !panel) return;

    toggle.addEventListener('click', () => panel.classList.toggle('show'));

    const closeBtn = document.getElementById('aiClose');
    if (closeBtn) {
        closeBtn.addEventListener('click', () => panel.classList.remove('show'));
    }

    panel.querySelectorAll('[data-ai-question]').forEach(btn => {
        btn.addEventListener('click', () => {
            if (quickPrompt) {
                quickPrompt.value = btn.getAttribute('data-ai-question');
                quickPrompt.focus();
            }
        });
    });

    const form = document.getElementById('aiForm');
    const feed = document.getElementById('aiFeed');
    if (form && feed) {
        const submitBtn = form.querySelector('button[type="submit"], button:not([type])');

        const appendMessage = (roleLabel, text, className) => {
            const row = document.createElement('div');
            row.className = className;

            const title = document.createElement('strong');
            title.textContent = roleLabel + ': ';

            const content = document.createElement('span');
            content.style.whiteSpace = 'pre-wrap';
            content.textContent = text;

            row.appendChild(title);
            row.appendChild(content);
            feed.prepend(row);
        };

        const buildContext = () => {
            const title = document.title || '';
            const path = window.location.pathname || '';
            return `${title} | ${path}`.trim();
        };

        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const question = quickPrompt.value?.trim();
            if (!question) return;
            appendMessage('Bạn', question, 'small mb-2');
            quickPrompt.value = '';

            if (submitBtn) {
                submitBtn.disabled = true;
            }

            appendMessage('AI', 'Đang suy nghĩ...', 'small text-muted mb-2 ai-thinking');

            fetch('/api/assistant/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    prompt: question,
                    context: buildContext()
                })
            })
                .then(async (response) => {
                    const payload = await response.json().catch(() => ({}));
                    if (!response.ok) {
                        throw new Error(payload.error || 'Không thể kết nối chatbot');
                    }
                    return payload;
                })
                .then((payload) => {
                    const thinking = feed.querySelector('.ai-thinking');
                    if (thinking) {
                        thinking.remove();
                    }
                    appendMessage('AI', payload.answer || 'Mình chưa có gợi ý phù hợp, bạn thử hỏi cụ thể hơn nhé.', 'small text-muted mb-2');
                })
                .catch((error) => {
                    const thinking = feed.querySelector('.ai-thinking');
                    if (thinking) {
                        thinking.remove();
                    }
                    appendMessage('AI', error.message || 'Đã xảy ra lỗi khi gọi chatbot.', 'small text-danger mb-2');
                })
                .finally(() => {
                    if (submitBtn) {
                        submitBtn.disabled = false;
                    }
                });
        });
    }
});
