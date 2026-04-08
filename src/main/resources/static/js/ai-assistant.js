/**
 * AI Assistant Client-side Logic for StrongWine
 * Professionalized with auto-scroll, bottom-up flow, and suggested questions.
 */
document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.getElementById('aiToggle');
    const panel = document.getElementById('aiPanel');
    const closeBtn = document.getElementById('aiClose');
    const quickPrompt = document.getElementById('aiPrompt');
    const form = document.getElementById('aiForm');
    const feed = document.getElementById('aiFeed');

    if (!toggle || !panel) return;

    // Toggle Panel visibility
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

    // Handle suggested questions
    const handleSuggestion = (btn) => {
        const question = btn.getAttribute('data-ai-question');
        if (question && quickPrompt) {
            quickPrompt.value = question;
            quickPrompt.focus();
            // Optional: Auto-submit on suggestion click? 
            // Better to let user see it first, but for UX sometimes auto-submit is nice.
            // Let's just focus for now.
        }
    };

    // Use event delegation for suggestions
    document.addEventListener('click', (e) => {
        const btn = e.target.closest('.suggestion-btn') || e.target.closest('[data-ai-question]');
        if (btn) {
            handleSuggestion(btn);
        }
    });

    if (form && feed) {
        const submitBtn = form.querySelector('button[type="submit"]');

        const scrollToBottom = () => {
            feed.scrollTop = feed.scrollHeight;
        };

        const appendMessage = (role, text, isThinking = false) => {
            const messageDiv = document.createElement('div');
            messageDiv.className = `ai-message ai-message-${role === 'Bạn' ? 'user' : 'bot'}`;
            if (isThinking) messageDiv.classList.add('ai-thinking-row');

            const bubble = document.createElement('div');
            bubble.className = 'ai-bubble';
            
            if (isThinking) {
                bubble.innerHTML = '<span class="ai-thinking">Đang suy nghĩ</span>';
                messageDiv.id = 'ai-thinking-temp';
            } else {
                bubble.textContent = text;
            }

            messageDiv.appendChild(bubble);
            feed.appendChild(messageDiv);
            scrollToBottom();
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

            // 1. Add User Message
            appendMessage('Bạn', question);
            quickPrompt.value = '';

            if (submitBtn) submitBtn.disabled = true;

            // 2. Add Floating Loading State
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
                // Remove thinking
                const thinking = document.getElementById('ai-thinking-temp');
                if (thinking) thinking.remove();

                // 3. Add AI Response
                appendMessage('AI', payload.answer || 'Mình chưa có gợi ý phù hợp, bạn thử hỏi cụ thể hơn nhé.');
            })
            .catch((error) => {
                const thinking = document.getElementById('ai-thinking-temp');
                if (thinking) thinking.remove();

                const errorMsg = document.createElement('div');
                errorMsg.className = 'small text-danger text-center mb-2 px-3';
                errorMsg.textContent = error.message || 'Đã xảy ra lỗi kết nối.';
                feed.appendChild(errorMsg);
                scrollToBottom();
            })
            .finally(() => {
                if (submitBtn) submitBtn.disabled = false;
            });
        });
    }
});
