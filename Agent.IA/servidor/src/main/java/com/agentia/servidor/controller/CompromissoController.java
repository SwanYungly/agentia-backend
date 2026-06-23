package com.agentia.servidor.controller;

import com.agentia.servidor.model.Compromisso;
import com.agentia.servidor.model.Usuario;
import com.agentia.servidor.repository.CompromissoRepository;
import com.agentia.servidor.repository.UsuarioRepository;
import com.agentia.servidor.service.GeminiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/compromissos")
@CrossOrigin(origins = "*")
public class CompromissoController {

    @Autowired
    private CompromissoRepository compromissoRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private GeminiService geminiService;

    // --- ROTA RESTAURADA: LISTAR ---
    @GetMapping("/listar/{usuarioId}")
    public ResponseEntity<List<Compromisso>> listarCompromissos(@PathVariable Long usuarioId) {
        List<Compromisso> compromissos = this.compromissoRepository.findByUsuarioId(usuarioId);
        return ResponseEntity.ok(compromissos);
    }

    // --- ROTA RESTAURADA: CADASTRO MANUAL ---
    @PostMapping("/cadastrar/{usuarioId}")
    public ResponseEntity<String> cadastrarCompromisso(@PathVariable Long usuarioId, @RequestBody Compromisso novoCompromisso) {
        Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
        if (usuarioOpcional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado.");
        }
        
        novoCompromisso.setUsuario(usuarioOpcional.get());
        try {
            this.compromissoRepository.save(novoCompromisso);
            return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso agendado com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao salvar o compromisso.");
        }
    }

    // --- ROTA CORRIGIDA: CADASTRO COM IA ---
    @PostMapping("/cadastrar-ia/{usuarioId}")
    public ResponseEntity<String> cadastrarComIA(@PathVariable Long usuarioId, @RequestBody Map<String, String> payload) {
        // CORREÇÃO: Buscando "texto", exatamente como o front-end envia
        String textoUsuario = payload.get("texto");
        
        if (textoUsuario == null || textoUsuario.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erro: O texto enviado nao pode estar vazio.");
        }

        Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
        if (usuarioOpcional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado.");
        }

        try {
            String jsonResposta = this.geminiService.extrairDadosDeAgendamento(textoUsuario);
            if (jsonResposta == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao processar na IA.");
            }

            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> mapaIA = objectMapper.readValue(jsonResposta, Map.class);
            
            Compromisso novoCompromisso = new Compromisso();
            novoCompromisso.setUsuario(usuarioOpcional.get());
            novoCompromisso.setTitulo((String) mapaIA.get("titulo"));
            
            String localExtraido = (String) mapaIA.get("local");
            novoCompromisso.setEndereco(localExtraido);
            
            String data = (String) mapaIA.get("data");
            String horario = (String) mapaIA.get("horario");
            novoCompromisso.setDataHora(java.time.LocalDateTime.parse(data + "T" + horario));

            // BUSCA COORDENADAS REAIS NA INTERNET
            double[] coordenadas = buscarCoordenadasNoNominatim(localExtraido);
            novoCompromisso.setLatitude(coordenadas[0]);
            novoCompromisso.setLongitude(coordenadas[1]);

            this.compromissoRepository.save(novoCompromisso);
            return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso interpretado e agendado com sucesso via IA!");

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao processar: " + e.getMessage());
        }
    }

    // --- MÉTODO AUXILIAR DE MAPAS CORRIGIDO ---
    private double[] buscarCoordenadasNoNominatim(String endereco) {
        double[] fallback = {-23.3102, -51.1627}; // Londrina como Plano B
        
        if (endereco == null || endereco.trim().isEmpty()) {
            return fallback;
        }

        try {
            // Prepara o texto para virar um link (ex: "São Paulo" vira "S%C3%A3o+Paulo")
            String enderecoFormatado = URLEncoder.encode(endereco, "UTF-8");
            
            // CORREÇÃO: Email adicionado para o Nominatim não bloquear a conexão
            String urlString = "https://nominatim.openstreetmap.org/search?format=json&q=" + enderecoFormatado + "&limit=1&email=contato.agentia@app.com";
            
            RestTemplate restTemplate = new RestTemplate();
            URI uri = URI.create(urlString);
            
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            if (root.isArray() && root.size() > 0) {
                double lat = root.get(0).path("lat").asDouble();
                double lon = root.get(0).path("lon").asDouble();
                return new double[]{lat, lon};
            }
        } catch (Exception e) {
            System.out.println("Falha ao geolocalizar endereco na API: " + e.getMessage());
        }
        
        return fallback;
    }

    @DeleteMapping("/excluir/{id}")
    public ResponseEntity<String> excluirCompromisso(@PathVariable Long id) {
        try {
            if (!this.compromissoRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Compromisso nao encontrado.");
            }
            this.compromissoRepository.deleteById(id);
            return ResponseEntity.ok("Compromisso excluido com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao excluir.");
        }
    }

    @PutMapping("/editar/{id}")
    public ResponseEntity<String> editarCompromisso(@PathVariable Long id, @RequestBody Compromisso dadosAtualizados) {
        try {
            Optional<Compromisso> compromissoOpcional = this.compromissoRepository.findById(id);
            if (compromissoOpcional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Compromisso nao encontrado.");
            }
            
            Compromisso compromissoExistente = compromissoOpcional.get();
            compromissoExistente.setTitulo(dadosAtualizados.getTitulo());
            compromissoExistente.setEndereco(dadosAtualizados.getEndereco());
            compromissoExistente.setDataHora(dadosAtualizados.getDataHora());
            compromissoExistente.setLatitude(dadosAtualizados.getLatitude());
            compromissoExistente.setLongitude(dadosAtualizados.getLongitude());
            
            this.compromissoRepository.save(compromissoExistente);
            return ResponseEntity.ok("Compromisso atualizado com sucesso!");
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao atualizar.");
        }
    }
}