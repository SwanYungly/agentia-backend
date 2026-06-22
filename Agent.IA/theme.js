const toggleBtn = document.getElementById('theme-toggle');
const toggleIcon = document.getElementById('theme-icon');

// 1. Ao carregar a página, verifica se o usuário já tinha salvo o modo escuro antes
const temaAtual = localStorage.getItem('agentia-tema');

if (temaAtual === 'dark') {
    document.body.setAttribute('data-theme', 'dark');
    toggleIcon.classList.replace('bi-moon-fill', 'bi-sun-fill');
    toggleBtn.classList.replace('btn-dark', 'btn-light');
}

// 2. Quando o usuário clica no botão flutuante
toggleBtn.addEventListener('click', function() {
    let tema = document.body.getAttribute('data-theme');
    
    if (tema === 'dark') {
        // Desativa o modo escuro
        document.body.removeAttribute('data-theme');
        localStorage.setItem('agentia-tema', 'light');
        toggleIcon.classList.replace('bi-sun-fill', 'bi-moon-fill');
        toggleBtn.classList.replace('btn-light', 'btn-dark');
    } else {
        // Ativa o modo escuro
        document.body.setAttribute('data-theme', 'dark');
        localStorage.setItem('agentia-tema', 'dark');
        toggleIcon.classList.replace('bi-moon-fill', 'bi-sun-fill');
        toggleBtn.classList.replace('btn-dark', 'btn-light');
    }
});