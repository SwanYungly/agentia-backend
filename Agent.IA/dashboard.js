document.addEventListener('DOMContentLoaded', function() {
    
    // VARIÁVEIS GLOBAIS DE CONTROLE
    let listaCompromissosLocal = [];
    let idCompromissoSelecionadoParaEdicao = null;
    let mapaInstance = null; // Armazena a instância ativa do mapa Leaflet

    // INSTÂNCIAS DOS MODAIS FIXOS DO BOOTSTRAP
    const modalDetalhesBS = new bootstrap.Modal(document.getElementById('modalDetalhes'));
    const modalEditarBS = new bootstrap.Modal(document.getElementById('modalEditar'));

    // --- 0. FUNÇÕES UTILITÁRIAS PARA SUBSTITUIR OS POP-UPS DO NAVEGADOR ---
    
    function mostrarAlertaSite(titulo, message, tipo = 'primary') {
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
                            <p class="mb-0">${message}</p>
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

    function mostrarConfirmacaoSite(titulo, message, callbackConfirmado) {
        const idModal = 'modal-confirmacao-dinamico';
        const existente = document.getElementById(idModal);
        if (existente) existente.remove();

        const modalHTML = `
            <div class="modal fade" id="${idModal}" tabindex="-1" aria-hidden="true">
                <div class="modal-dialog modal-dialog-centered">
                    <div class="modal-content custom-card">
                        <div class="modal-header border-0">
                            <h5 class="modal-title fw-bold text-danger">${titulo}</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                        <div class="modal-body">
                            <p class="mb-0">${message}</p>
                        </div>
                        <div class="modal-footer border-0">
                            <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancelar</button>
                            <button type="button" id="btn-confirmar-dinamico" class="btn btn-danger px-4">Confirmar</button>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        document.body.insertAdjacentHTML('beforeend', modalHTML);
        const modalElement = document.getElementById(idModal);
        const modalBS = new bootstrap.Modal(modalElement);

        document.getElementById('btn-confirmar-dinamico').onclick = () => {
            modalBS.hide();
            callbackConfirmado();
        };

        modalBS.show();
    }

    // --- 1. VERIFICAÇÃO DE SEGURANÇA ---
    const usuarioId = localStorage.getItem('usuarioLogadoId');
    const usuarioNome = localStorage.getItem('usuarioLogado');

    if (!usuarioId || !usuarioNome) {
        window.location.href = "login.html";
        return; 
    }

    const areaAutenticacao = document.getElementById('area-autenticacao');
    areaAutenticacao.innerHTML = `
        <span class="text-white me-3 fw-bold">Olá, ${usuarioNome}</span>
        <button id="btn-sair" class="btn btn-outline-light btn-sm btn-hover">Sair</button>
    `;

    document.getElementById('btn-sair').addEventListener('click', function() {
        localStorage.clear();
        window.location.href = "login.html";
    });

    // --- 2. INTEGRAÇÃO COM MAPAS E ROTAS (CÁLCULO TEXTUAL DA AGENDA) ---
    async function calcularTempoViagem(latDestino, lonDestino, dataHoraCompromisso, idElementoAviso) {
        try {
            if (!latDestino || !lonDestino) {
                const avisoElErro = document.getElementById(idElementoAviso);
                if(avisoElErro) {
                    avisoElErro.innerHTML = "Coordenadas não encontradas para o destino.";
                    avisoElErro.className = "text-muted small";
                }
                return;
            }

            const latOrigem = -23.3102;
            const lonOrigem = -51.1627;

            const rotaUrl = `https://router.project-osrm.org/route/v1/driving/${lonOrigem},${latOrigem};${lonDestino},${latDestino}?overview=false`;
            const rotaResposta = await fetch(rotaUrl);
            const rotaDados = await rotaResposta.json();

            const avisoEl = document.getElementById(idElementoAviso);
            if (!avisoEl) return;

            if (rotaDados.code !== 'Ok') {
                avisoEl.innerHTML = "Rota indisponível.";
                avisoEl.className = "text-muted small";
                return;
            }

            const duracaoMinutos = Math.ceil(rotaDados.routes[0].duration / 60);
            
            const dataObj = new Date(dataHoraCompromisso);
            dataObj.setMinutes(dataObj.getMinutes() - duracaoMinutos); 
            
            const horaSaida = dataObj.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

            avisoEl.innerHTML = `Saia às ${horaSaida} (Trajeto de ~${duracaoMinutos} min)`;
            avisoEl.className = "text-warning fw-bold small";
            
        } catch (erro) {
            console.error("Erro na API de mapas:", erro);
            const avisoElErro = document.getElementById(idElementoAviso);
            if(avisoElErro) {
                avisoElErro.innerHTML = "Erro ao calcular trajeto.";
                avisoElErro.className = "text-danger small";
            }
        }
    }

    // --- 3. EXCLUSÃO DE COMPROMISSO ---
    window.excluirCompromissoChamada = function(id) {
        mostrarConfirmacaoSite(
            "Remover Compromisso", 
            "Tem certeza que deseja remover este compromisso de sua agenda?", 
            () => {
                fetch(`http://localhost:8080/api/compromissos/excluir/${id}`, {
                    method: 'DELETE'
                })
                .then(resposta => {
                    if (resposta.ok) {
                        modalDetalhesBS.hide();
                        carregarCompromissos();
                    } else {
                        mostrarAlertaSite("Erro de Operação", "Não foi possível efetuar a exclusão no banco de dados.", "danger");
                    }
                })
                .catch(erro => console.error("Erro ao deletar:", erro));
            }
        );
    };

    // --- 4. ABRE MODAL DE DETALHES E GERA O MAPA COM A POSIÇÃO ATUAL ---
    window.visualizarDetalhesCompromisso = function(id) {
        const comp = listaCompromissosLocal.find(c => c.id === id);
        if (!comp) return;

        idCompromissoSelecionadoParaEdicao = id;

        const dataObj = new Date(comp.dataHora);
        const dataFormatada = dataObj.toLocaleDateString('pt-BR');
        const horaFormatada = dataObj.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

        document.getElementById('detalhe-titulo').innerText = comp.titulo;
        document.getElementById('detalhe-data-hora').innerHTML = `<i class="bi bi-clock me-2"></i>${dataFormatada} às ${horaFormatada}`;
        document.getElementById('detalhe-endereco').innerText = comp.endereco;

        document.getElementById('btn-detalhe-excluir').onclick = () => excluirCompromissoChamada(id);
        document.getElementById('btn-detalhe-editar').onclick = () => {
            modalDetalhesBS.hide();
            window.abrirModalEdicaoDireta(id);
        };

        // Captura o fechamento ou abertura do modal para reinicializar o container do mapa com segurança
        if (mapaInstance) {
            mapaInstance.remove();
            mapaInstance = null;
        }

        // Aguarda o modal do Bootstrap terminar a transição visual de abertura para renderizar o mapa sem falhas de tamanho
        const modalElement = document.getElementById('modalDetalhes');
        const dispararGeradorMapa = function () {
            // Tenta pegar a posição do GPS real do usuário pelo navegador
            if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition(
                    (position) => {
                        // Sucesso: Usuário permitiu acesso ao GPS do dispositivo
                        gerarMapaInterativoRota(position.coords.latitude, position.coords.longitude, comp);
                    },
                    (error) => {
                        // Erro/Negado: Carrega o Ponto A na posição central padrão de Londrina
                        gerarMapaInterativoRota(-23.3102, -51.1627, comp);
                    }
                );
            } else {
                gerarMapaInterativoRota(-23.3102, -51.1627, comp);
            }
            modalElement.removeEventListener('shown.bs.modal', dispararGeradorMapa);
        };

        modalElement.addEventListener('shown.bs.modal', dispararGeradorMapa);
        modalDetalhesBS.show();
    };

    // FUNÇÃO PRIVADA: Inicializa o Leaflet e monta o traçado ponto a ponto na tela
    async function gerarMapaInterativoRota(latOrigem, lonOrigem, comp) {
        try {
            // Cria a instância do mapa focada na origem inicial
            mapaInstance = L.map('mapa-rota').setView([latOrigem, lonOrigem], 14);

            // Identifica o tema ativo para escolher o design do mapa (Escuro ou Claro)
            const isDark = document.body.getAttribute('data-theme') === 'dark';
            const tileUrl = isDark 
                ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
                : 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png';

            L.tileLayer(tileUrl, {
                attribution: '&copy; OpenStreetMap contributors &copy; CARTO'
            }).addTo(mapaInstance);

            // Adiciona os marcadores visuais de partida e de destino no mapa
            L.marker([latOrigem, lonOrigem]).addTo(mapaInstance).bindPopup('Você está aqui').openPopup();
            L.marker([comp.latitude, comp.longitude]).addTo(mapaInstance).bindPopup(comp.titulo);

            // Solicita ao OSRM a rota completa contendo a geometria em formato GeoJSON
            const rotaUrl = `https://router.project-osrm.org/route/v1/driving/${lonOrigem},${latOrigem};${comp.longitude},${comp.latitude}?overview=full&geometries=geojson`;
            const resposta = await fetch(rotaUrl);
            const dadosRota = await resposta.json();

            if (dadosRota.code === 'Ok') {
                const coordenadasOSRM = dadosRota.routes[0].geometry.coordinates;
                
                // O OSRM retorna os pontos como [Longitude, Latitude]. Invertemos para [Latitude, Longitude] exigido pela Leaflet
                const pontosTrajeto = coordenadasOSRM.map(pt => [pt[1], pt[0]]);
                
                // Desenha a linha roxa unindo todas as coordenadas do percurso
                const polilinha = L.polyline(pontosTrajeto, { color: '#6f42c1', weight: 5, opacity: 0.8 }).addTo(mapaInstance);
                
                // Ajusta o zoom do mapa automaticamente para enquadrar a rota inteira na tela de forma perfeita
                mapaInstance.fitBounds(polilinha.getBounds(), { padding: [30, 30] });
            }
        } catch (e) {
            console.error("Erro ao gerar mapa visual:", e);
        }
    }

    // --- 5. PREPARA E ABRE FORMULÁRIO DE EDIÇÃO DIRETA ---
    window.abrirModalEdicaoDireta = function(id) {
        const comp = listaCompromissosLocal.find(c => c.id === id);
        if (!comp) return;

        idCompromissoSelecionadoParaEdicao = id;

        const partesDataHora = comp.dataHora.split('T');
        const dataIso = partesDataHora[0];
        const horaIso = partesDataHora[1].substring(0, 5);

        document.getElementById('editar-titulo').value = comp.titulo;
        document.getElementById('editar-data').value = dataIso;
        document.getElementById('editar-hora').value = horaIso;
        document.getElementById('editar-endereco').value = comp.endereco;

        latSelecionada = comp.latitude;
        lonSelecionada = comp.longitude;
        enderecoBaseSelecionado = comp.endereco.replace(/, Nº .*$/i, '') + ", Nº ";

        document.getElementById('btn-editar-salvar').disabled = false;
        modalEditarBS.show();
    };

    // --- 6. CARREGAR E EXIBIR A AGENDA NO DASHBOARD ---
    function carregarCompromissos() {
        fetch(`http://localhost:8080/api/compromissos/listar/${usuarioId}`)
        .then(resposta => resposta.json())
        .then(dados => {
            listaCompromissosLocal = dados; 
            const lista = document.getElementById('lista-compromissos');
            lista.innerHTML = ''; 

            if (dados.length === 0) {
                lista.innerHTML = '<div class="text-center p-4 text-muted">Você ainda não tem compromissos agendados.</div>';
                return;
            }

            dados.forEach(comp => {
                const dataObj = new Date(comp.dataHora);
                const horaFormatada = dataObj.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
                
                const idAviso = `aviso-${comp.id}`;

                const itemHTML = `
                    <div class="list-group-item list-group-item-action py-3 border-bottom d-flex justify-content-between align-items-center">
                        <div class="flex-grow-1 me-2" style="cursor: pointer;" onclick="visualizarDetalhesCompromisso(${comp.id})">
                            <div class="d-flex w-100 justify-content-between">
                                <h6 class="mb-1 fw-bold">${comp.titulo}</h6>
                                <small class="text-primary fw-bold">${horaFormatada}</small>
                            </div>
                            <p class="mb-1 text-muted small text-truncate">${comp.endereco}</p>
                            <small id="${idAviso}" class="text-secondary small">Calculando rota...</small>
                        </div>
                        <div class="d-flex gap-1">
                            <button class="btn btn-sm btn-outline-secondary" onclick="abrirModalEdicaoDireta(${comp.id})" title="Editar"><i class="bi bi-pencil"></i></button>
                            <button class="btn btn-sm btn-outline-danger" onclick="excluirCompromissoChamada(${comp.id})" title="Excluir"><i class="bi bi-trash"></i></button>
                        </div>
                    </div>
                `;
                
                lista.insertAdjacentHTML('beforeend', itemHTML);
                calcularTempoViagem(comp.latitude, comp.longitude, comp.dataHora, idAviso);
            });
        })
        .catch(erro => console.error("Erro ao carregar agenda:", erro));
    }

    carregarCompromissos();

    // --- 7. AUTOCOMPLETAR REUTILIZÁVEL ---
    let enderecoBaseSelecionado = ""; 
    let latSelecionada = null;
    let lonSelecionada = null;
    let timeoutBuscaEndereco;

    function inicializarAutocompletarCampos(inputElement, btnElement) {
        const containerSugestoes = document.createElement('div');
        containerSugestoes.className = 'list-group position-absolute w-100 shadow-sm';
        containerSugestoes.style.zIndex = '1060'; 
        containerSugestoes.style.display = 'none';
        containerSugestoes.style.maxHeight = '150px';
        containerSugestoes.style.overflowY = 'auto';
        
        inputElement.parentNode.style.position = 'relative';
        inputElement.parentNode.appendChild(containerSugestoes);

        inputElement.addEventListener('input', function() {
            const termoBusca = this.value;
            
            if (enderecoBaseSelecionado && termoBusca.startsWith(enderecoBaseSelecionado)) {
                btnElement.disabled = false;
                return; 
            }
            
            let ruaBusca = termoBusca.trim();
            let numeroExtraido = "";
            
            const ultimoEspaco = ruaBusca.lastIndexOf(' ');
            if (ultimoEspaco !== -1) {
                const possivelNumero = ruaBusca.substring(ultimoEspaco + 1);
                if (/^\d+$/.test(possivelNumero)) { 
                    numeroExtraido = possivelNumero;
                    ruaBusca = ruaBusca.substring(0, ultimoEspaco).trim();
                }
            }

            enderecoBaseSelecionado = "";
            latSelecionada = null;
            lonSelecionada = null;
            btnElement.disabled = true;
            clearTimeout(timeoutBuscaEndereco);
            
            if (ruaBusca.length < 4) {
                containerSugestoes.style.display = 'none';
                return;
            }

            containerSugestoes.innerHTML = '<div class="list-group-item py-2 text-muted small">Buscando endereços...</div>';
            containerSugestoes.style.display = 'block';

            timeoutBuscaEndereco = setTimeout(async () => {
                try {
                    const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(ruaBusca + ", Londrina, PR")}&limit=5&email=contato.agentia@app.com`;
                    const resposta = await fetch(url, { headers: { 'Accept': 'application/json', 'Accept-Language': 'pt-BR' } });
                    const locais = await resposta.json();

                    containerSugestoes.innerHTML = '';

                    if (Array.isArray(locais) && locais.length > 0) {
                        locais.forEach(local => {
                            const botaoSugestao = document.createElement('button');
                            botaoSugestao.type = 'button';
                            botaoSugestao.className = 'list-group-item list-group-item-action py-2 text-truncate small';
                            botaoSugestao.innerText = local.display_name;
                            
                            botaoSugestao.onclick = () => {
                                const partesEndereco = local.display_name.split(',');
                                const enderecoLimpo = partesEndereco.slice(0, 3).join(',').trim();
                                
                                enderecoBaseSelecionado = enderecoLimpo + ", Nº ";
                                inputElement.value = enderecoBaseSelecionado + numeroExtraido;
                                
                                latSelecionada = parseFloat(local.lat);
                                lonSelecionada = parseFloat(local.lon);
                                
                                inputElement.focus();
                                containerSugestoes.style.display = 'none';
                                btnElement.disabled = false; 
                            };
                            containerSugestoes.appendChild(botaoSugestao);
                        });
                        containerSugestoes.style.display = 'block';
                    } else {
                        containerSugestoes.innerHTML = '<div class="list-group-item py-2 text-danger small">Endereço não encontrado.</div>';
                    }
                } catch (erro) {
                    console.error(erro);
                }
            }, 1500);
        });

        document.addEventListener('click', (evento) => {
            if (evento.target !== inputElement) containerSugestoes.style.display = 'none';
        });
    }

    inicializarAutocompletarCampos(document.getElementById('endereco-compromisso'), document.querySelector('#form-manual button[type="submit"]'));
    inicializarAutocompletarCampos(document.getElementById('editar-endereco'), document.getElementById('btn-editar-salvar'));

    // --- 8. ENVIO DO FORMULÁRIO DE CADASTRO MANUAL ---
    document.getElementById('form-manual').addEventListener('submit', function(evento) {
        evento.preventDefault();

        const titulo = document.getElementById('titulo-compromisso').value;
        const data = document.getElementById('data-compromisso').value;
        const hora = document.getElementById('hora-compromisso').value;
        const enderecoCompleto = document.getElementById('endereco-compromisso').value;

        const novoCompromisso = {
            titulo: titulo,
            endereco: enderecoCompleto,
            dataHora: `${data}T${hora}:00`,
            latitude: latSelecionada,
            longitude: lonSelecionada
        };

        fetch(`http://localhost:8080/api/compromissos/cadastrar/${usuarioId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(novoCompromisso)
        })
        .then(resposta => {
            if (resposta.ok) {
                document.getElementById('form-manual').reset();
                document.querySelector('#form-manual button[type="submit"]').disabled = true;
                bootstrap.Modal.getInstance(document.getElementById('modalManual')).hide();
                carregarCompromissos();
            }
        })
        .catch(erro => console.error(erro));
    });

    // --- 9. ENVIO DO FORMULÁRIO DE EDIÇÃO ---
    document.getElementById('form-editar').addEventListener('submit', function(evento) {
        evento.preventDefault();

        const titulo = document.getElementById('editar-titulo').value;
        const data = document.getElementById('editar-data').value;
        const hora = document.getElementById('editar-hora').value;
        const enderecoCompleto = document.getElementById('editar-endereco').value;

        const compromissoAtualizado = {
            titulo: titulo,
            endereco: enderecoCompleto,
            dataHora: `${data}T${hora}:00`,
            latitude: latSelecionada,
            longitude: lonSelecionada
        };

        fetch(`http://localhost:8080/api/compromissos/editar/${idCompromissoSelecionadoParaEdicao}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(compromissoAtualizado)
        })
        .then(resposta => {
            if (resposta.ok) {
                modalEditarBS.hide();
                carregarCompromissos();
                mostrarAlertaSite("Sucesso", "Compromisso atualizado com êxito.", "primary");
            } else {
                mostrarAlertaSite("Erro de Edição", "Erro ao salvar alterações no back-end.", "danger");
            }
        })
        .catch(erro => console.error("Erro na edicao:", erro));
    });

    // --- 10. INTEGRAÇÃO COGNITIVA DO CHAT ---
    const formChat = document.getElementById('form-chat');
    const inputChat = document.getElementById('input-chat');
    const chatWindow = document.getElementById('chat-window');

    formChat.addEventListener('submit', function(evento) {
        evento.preventDefault();
        const mensagemUsuario = inputChat.value.trim();
        if (!mensagemUsuario) return;

        const divUser = document.createElement('div');
        divUser.className = 'chat-message user-message mb-3 text-end';
        divUser.innerHTML = `<div class="bg-white border p-3 rounded-3 shadow-sm d-inline-block text-start">${mensagemUsuario}</div>`;
        chatWindow.appendChild(divUser);
        inputChat.value = '';
        chatWindow.scrollTop = chatWindow.scrollHeight;

        fetch('http://localhost:8080/api/chat/processar', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ mensagem: mensagemUsuario })
        })
        .then(res => res.json())
        .then(async dados => {
            if (!dados.endereco) {
                const divIa = document.createElement('div');
                divIa.className = 'chat-message ia-message mb-3';
                divIa.innerHTML = `<div class="bg-primary text-white p-3 rounded-3 shadow-sm d-inline-block">Não identifiquei o endereço. Indique-o usando 'em', 'no' ou 'na'.</div>`;
                chatWindow.appendChild(divIa);
                return;
            }

            try {
                let ruaBusca = dados.endereco.trim();
                let numeroExtraido = "";
                const ultimoEspaco = ruaBusca.lastIndexOf(' ');
                if (ultimoEspaco !== -1) {
                    const possivelNumero = ruaBusca.substring(ultimoEspaco + 1);
                    if (/^\d+$/.test(possivelNumero)) {
                        numeroExtraido = possivelNumero;
                        ruaBusca = ruaBusca.substring(0, ultimoEspaco).trim();
                    }
                }

                const urlMapa = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(ruaBusca + ", Londrina, PR")}&limit=1&email=contato.agentia@app.com`;
                const resMapa = await fetch(urlMapa, { headers: { 'Accept': 'application/json', 'Accept-Language': 'pt-BR' } });
                const locais = await resMapa.json();

                if (!locais || locais.length === 0) return;

                const local = locais[0];
                let enderecoLimpo = local.display_name.split(',').slice(0, 3).join(',').trim();
                if (numeroExtraido) enderecoLimpo += ", Nº " + numeroExtraido;

                const novoCompromisso = {
                    titulo: dados.titulo,
                    endereco: enderecoLimpo,
                    dataHora: `${dados.data}T${dados.hora}:00`,
                    latitude: parseFloat(local.lat),
                    longitude: parseFloat(local.lon)
                };

                const resSalvar = await fetch(`http://localhost:8080/api/compromissos/cadastrar/${usuarioId}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(novoCompromisso)
                });

                if (resSalvar.ok) {
                    const divIa = document.createElement('div');
                    divIa.className = 'chat-message ia-message mb-3';
                    divIa.innerHTML = `<div class="bg-primary text-white p-3 rounded-3 shadow-sm d-inline-block">Agendado com sucesso via assistente!</div>`;
                    chatWindow.appendChild(divIa);
                    carregarCompromissos();
                }
            } catch (e) { console.error(e); }
        });
    });

});