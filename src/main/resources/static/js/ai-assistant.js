document.addEventListener('DOMContentLoaded', () => {
    const toggle = document.getElementById('aiToggle');
    const panel = document.getElementById('aiPanel');
    const quickPrompt = document.getElementById('aiPrompt');

    if (!toggle || !panel) return;

    toggle.addEventListener('click', () => panel.classList.toggle('show'));

    panel.querySelectorAll('[data-ai-question]').forEach(btn => {
        btn.addEventListener('click', () => {
            if (quickPrompt) {
                quickPrompt.value = btn.getAttribute('data-ai-question');
            }
        });
    });

    const form = document.getElementById('aiForm');
    const feed = document.getElementById('aiFeed');
    if (form && feed) {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const question = quickPrompt.value?.trim();
            if (!question) return;
            const user = document.createElement('div');
            user.className = 'small mb-2';
            user.innerHTML = `<strong>Bạn:</strong> ${question}`;

            const bot = document.createElement('div');
            bot.className = 'small text-muted mb-2';
            bot.innerHTML = `<strong>AI:</strong> Đã tạo gợi ý cho "${question}". Bạn có thể mở rộng phần này bằng endpoint AI ở backend.`;

            feed.prepend(bot);
            feed.prepend(user);
            quickPrompt.value = '';
        });
    }
});
