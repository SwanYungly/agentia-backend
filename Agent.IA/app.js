// --- FUNÇÃO UTILITÁRIA PARA SUBSTITUIR OS POP-UPS DO NAVEGADOR ---
function mostrarAlertaSite(titulo, mensagem, tipo = 'primary') {
    const idModal = 'modal-alerta-dinamico';
    const existente = document.getElementById(idModal);
    if (existente) existente.remove();

    const modalHTML = `
        <div class="modal fade" id="${idModal}" tabindex="-1" aria-hidden="true">
            <div class="modal-dialog modal-dialog-centered">
                <div class="modal-content custom-card">
                    <div class="modal-header border-0">
                        <h5 class="modal-title fw-bold text-${tipo}">${titulo}</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <p class="mb-0">${mensagem}</p>
                    </div>
                    <div class="modal-footer border-0">
                        <button type="button" class="btn btn-${tipo} px-4" data-bs-dismiss="modal">Ok</button>
                    </div>
                </div>
            </div>
        </div>
    `;
    
    document.body.insertAdjacentHTML('beforeend', modalHTML);
    const modalElement = document.getElementById(idModal);
    const modalBS = new bootstrap.Modal(modalElement);
    modalBS.show();
}

const loginContent = document.getElementById('login-content');
const cadastroContent = document.getElementById('cadastro-content');
const btnIrParaCadastro = document.getElementById('btn-ir-para-cadastro');
const btnIrParaLogin = document.getElementById('btn-ir-para-login');

// --- TROCA DE TELAS ---
btnIrParaCadastro.addEventListener('click', function(evento) {
    evento.preventDefault(); 
    loginContent.classList.add('hidden');
    cadastroContent.classList.remove('hidden');
});

btnIrParaLogin.addEventListener('click', function(evento) {
    evento.preventDefault(); 
    cadastroContent.classList.add('hidden');
    loginContent.classList.remove('hidden');
});

// --- LÓGICA DE CADASTRO (CONECTADA AO JAVA) ---
document.getElementById('form-cadastro').addEventListener('submit', function(evento) {
    evento.preventDefault(); 

    const nomeInput = document.getElementById('nome').value;
    const emailInput = document.getElementById('email-cadastro').value; 
    const senhaInput = document.getElementById('senha-cadastro').value;
    const confirmarSenhaInput = document.getElementById('confirmar-senha-cadastro').value;
    const aceitouTermos = document.getElementById('termos-lgpd').checked;

    if (senhaInput !== confirmarSenhaInput) {
        mostrarAlertaSite("Validação", "As senhas não coincidem. Verifique e tente novamente.", "warning");
        return; 
    }

    const dadosUsuario = {
        nome: nomeInput,
        email: emailInput,
        senha: senhaInput,
        aceitouTermosLgpd: aceitouTermos
    };

    fetch('http://localhost:8080/api/usuarios/cadastrar', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(dadosUsuario)
    })
    .then(resposta => {
        if (resposta.ok) {
            mostrarAlertaSite("Sucesso", "Cadastro realizado! Faça login para continuar.", "success");
            cadastroContent.classList.add('hidden');
            loginContent.classList.remove('hidden');
        } else {
            return resposta.text().then(textoErro => { throw new Error(textoErro); });
        }
    })
    .catch(erro => {
        mostrarAlertaSite("Erro de Conexão", erro.message || "Erro ao conectar com o servidor. O Java está rodando?", "danger");
    });
});

// --- LÓGICA DE LOGIN ---
document.getElementById('form-login').addEventListener('submit', function(evento) {
    evento.preventDefault(); 

    const emailInput = document.getElementById('email-login').value;
    const senhaInput = document.getElementById('senha-login').value;

    const credenciais = {
        email: emailInput,
        senha: senhaInput
    };

    fetch('http://localhost:8080/api/usuarios/login', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(credenciais)
    })
    .then(resposta => {
        if (resposta.ok) {
            return resposta.json().then(dados => {
                localStorage.setItem('usuarioLogadoId', dados.id);
                localStorage.setItem('usuarioLogado', dados.nome);
                window.location.href = "index.html";
            });
        } else {
            return resposta.text().then(textoErro => { throw new Error(textoErro); });
        }
    })
    .catch(erro => {
        mostrarAlertaSite("Erro de Conexão", erro.message || "Erro ao conectar com o servidor. O Java está rodando?", "danger");
    });
});

// --- LÓGICA DE MOSTRAR/OCULTAR SENHA ---
const botoesToggleSenha = document.querySelectorAll('.toggle-senha');

botoesToggleSenha.forEach(function(botao) {
    botao.addEventListener('click', function() {
        const inputSenha = this.previousElementSibling;
        const icone = this.querySelector('i');

        if (inputSenha.type === 'password') {
            inputSenha.type = 'text'; 
            icone.classList.remove('bi-eye');
            icone.classList.add('bi-eye-slash'); 
        } else {
            inputSenha.type = 'password'; 
            icone.classList.remove('bi-eye-slash');
            icone.classList.add('bi-eye'); 
        }
    });
});