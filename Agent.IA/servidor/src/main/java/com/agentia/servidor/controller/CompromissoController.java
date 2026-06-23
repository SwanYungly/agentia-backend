package com.agentia.servidor.controller;

import com.agentia.servidor.model.Compromisso;
import com.agentia.servidor.model.Usuario;
import com.agentia.servidor.repository.CompromissoRepository;
import com.agentia.servidor.repository.UsuarioRepository;
import com.agentia.servidor.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping({"/api/compromissos"})
@CrossOrigin(origins = {"*"})
public class CompromissoController {
    @Autowired
    private CompromissoRepository compromissoRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private GeminiService geminiService;

    // Método para validar o endereço e obter coordenadas reais
    private Map<String, Double> buscarCoordenadasDoEndereco(String endereco) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=json&q=" + 
                         java.net.URLEncoder.encode(endereco + ", Londrina, PR", "UTF-8") + "&limit=1";
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            
            if (response.getBody() != null && !response.getBody().isEmpty()) {
                Map<String, Object> local = (Map<String, Object>) response.getBody().get(0);
                Map<String, Double> coords = new HashMap<>();
                coords.put("lat", Double.parseDouble(local.get("lat").toString()));
                coords.put("lon", Double.parseDouble(local.get("lon").toString()));
                return coords;
            }
        } catch (Exception e) {
            System.out.println("Erro na busca de coordenadas: " + e.getMessage());
        }
        return Map.of("lat", -23.3102, "lon", -51.1627); // Coordenada padrão de Londrina
    }

    @PostMapping({"/cadastrar/{usuarioId}"})
    public ResponseEntity<String> cadastrarCompromisso(@PathVariable Long usuarioId, @RequestBody Compromisso novoCompromisso) {
        Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
        if (usuarioOpcional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado.");
        }
        novoCompromisso.setUsuario(usuarioOpcional.get());
        this.compromissoRepository.save(novoCompromisso);
        return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso agendado!");
    }

    @PostMapping({"/cadastrar-ia/{usuarioId}"})
    public ResponseEntity<String> cadastrarComIA(@PathVariable Long usuarioId, @RequestBody Map<String, String> payload) {
        Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
        if (usuarioOpcional.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario nao encontrado.");

        String textoUsuario = payload.get("texto");
        try {
            String jsonResposta = this.geminiService.extrairDadosDeAgendamento(textoUsuario);
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> mapaIA = objectMapper.readValue(jsonResposta, Map.class);

            Compromisso novoCompromisso = new Compromisso();
            novoCompromisso.setUsuario(usuarioOpcional.get());
            novoCompromisso.setTitulo((String) mapaIA.get("titulo"));
            
            String endereco = (String) mapaIA.get("local");
            novoCompromisso.setEndereco(endereco);

            // A MÁGICA: Buscar coordenadas reais com base no endereço da IA
            Map<String, Double> coords = buscarCoordenadasDoEndereco(endereco);
            novoCompromisso.setLatitude(coords.get("lat"));
            novoCompromisso.setLongitude(coords.get("lon"));

            String data = (String) mapaIA.get("data");
            String horario = (String) mapaIA.get("horario");
            novoCompromisso.setDataHora(java.time.LocalDateTime.parse(data + "T" + horario));

            this.compromissoRepository.save(novoCompromisso);
            return ResponseEntity.status(HttpStatus.CREATED).body("Agendado com endereço validado!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro: " + e.getMessage());
        }
    }

    // ... (listar, excluir e editar permanecem iguais)
    @GetMapping({"/listar/{usuarioId}"})
    public ResponseEntity<List<Compromisso>> listarCompromissos(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(this.compromissoRepository.findByUsuarioId(usuarioId));
    }

    @DeleteMapping({"/excluir/{id}"})
    public ResponseEntity<String> excluirCompromisso(@PathVariable Long id) {
        this.compromissoRepository.deleteById(id);
        return ResponseEntity.ok("Excluido!");
    }

    @PutMapping({"/editar/{id}"})
    public ResponseEntity<String> editarCompromisso(@PathVariable Long id, @RequestBody Compromisso d) {
        Compromisso c = this.compromissoRepository.findById(id).get();
        c.setTitulo(d.getTitulo());
        c.setEndereco(d.getEndereco());
        c.setDataHora(d.getDataHora());
        c.setLatitude(d.getLatitude());
        c.setLongitude(d.getLongitude());
        this.compromissoRepository.save(c);
        return ResponseEntity.ok("Atualizado!");
    }
}