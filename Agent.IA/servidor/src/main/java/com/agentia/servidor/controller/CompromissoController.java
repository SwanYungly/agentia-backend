package com.agentia.servidor.controller;

import com.agentia.servidor.model.Compromisso;
import com.agentia.servidor.model.Usuario;
import com.agentia.servidor.repository.CompromissoRepository;
import com.agentia.servidor.repository.UsuarioRepository;
import com.agentia.servidor.service.GeminiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

   @GetMapping({"/usuario/{usuarioId}"})
   public ResponseEntity<List<Compromisso>> listarPorUsuario(@PathVariable Long usuarioId) {
      List<Compromisso> lista = this.compromissoRepository.findByUsuarioId(usuarioId);
      return ResponseEntity.ok(lista);
   }

   @PostMapping({"/cadastrar-ia/{usuarioId}"})
   public ResponseEntity<String> cadastrarComIA(@PathVariable Long usuarioId, @RequestBody Map<String, String> payload) {
      String textoUsuario = (String)payload.get("mensagem");
      if (textoUsuario != null && !textoUsuario.trim().isEmpty()) {
         Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
         if (usuarioOpcional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado.");
         } else {
            try {
               String jsonResposta = this.geminiService.extrairDadosDeAgendamento(textoUsuario);
               if (jsonResposta == null) {
                  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao processar com a IA.");
               } else {
                  ObjectMapper objectMapper = new ObjectMapper();
                  Map<String, Object> mapaIA = (Map)objectMapper.readValue(jsonResposta, Map.class);
                  
                  Compromisso novoCompromisso = new Compromisso();
                  novoCompromisso.setUsuario((Usuario)usuarioOpcional.get());
                  novoCompromisso.setTitulo((String)mapaIA.get("titulo"));
                  
                  String localExtraido = (String)mapaIA.get("local");
                  novoCompromisso.setEndereco(localExtraido);
                  
                  String data = (String)mapaIA.get("data");
                  String horario = (String)mapaIA.get("horario");
                  novoCompromisso.setDataHora(java.time.LocalDateTime.parse(data + "T" + horario));

                  // --- CORREÇÃO AQUI: BUSCA AS COORDENADAS VIA API ---
                  double[] coordenadas = buscarCoordenadasNoNominatim(localExtraido);
                  novoCompromisso.setLatitude(coordenadas[0]);
                  novoCompromisso.setLongitude(coordenadas[1]);

                  this.compromissoRepository.save(novoCompromisso);
                  return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso interpretado e agendado com sucesso via IA!");
               }
            } catch (Exception var12) {
               return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao salvar: " + var12.getMessage());
            }
         }
      } else {
         return ResponseEntity.badRequest().body("Erro: O texto nao pode estar vazio.");
      }
   }

   // --- NOVO MÉTODO AUXILIAR (IGUAL AO SEU FRONT-END) ---
   private double[] buscarCoordenadasNoNominatim(String endereco) {
      // Coordenadas fallback padrão (Londrina) caso o endereço não seja encontrado
      double[] fallback = {-23.3102, -51.1627};
      
      if (endereco == null || endereco.trim().isEmpty()) {
         return fallback;
      }

      try {
         // Trata espaços em branco e caracteres especiais para a URL
         String enderecoFormatado = endereco.replace(" ", "+");
         String urlString = "https://nominatim.openstreetmap.org/search?format=json&q=" + enderecoFormatado + "&limit=1";
         
         RestTemplate restTemplate = new RestTemplate();
         // Definimos como URI para evitar problemas com formatação de caracteres especiais
         URI uri = URI.create(urlString);
         
         ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
         
         ObjectMapper mapper = new ObjectMapper();
         JsonNode root = mapper.readTree(response.getBody());

         // Se a API do OpenStreetMap retornou algum resultado válido
         if (root.isArray() && root.size() > 0) {
            double lat = root.get(0).path("lat").asDouble();
            double lon = root.get(0).path("lon").asDouble();
            return new double[]{lat, lon};
         }
      } catch (Exception e) {
         System.out.println("Falha ao geolocalizar endereço via Java: " + e.getMessage());
      }
      
      return fallback;
   }

   @DeleteMapping({"/excluir/{id}"})
   public ResponseEntity<String> excluirCompromisso(@PathVariable Long id) {
      try {
         if (!this.compromissoRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Compromisso nao encontrado.");
         } else {
            this.compromissoRepository.deleteById(id);
            return ResponseEntity.ok("Compromisso excluido com sucesso!");
         }
      } catch (Exception var3) {
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao excluir o compromisso.");
      }
   }

   @PutMapping({"/editar/{id}"})
   public ResponseEntity<String> editarCompromisso(@PathVariable Long id, @RequestBody Compromisso dadosAtualizados) {
      try {
         Optional<Compromisso> compromissoOpcional = this.compromissoRepository.findById(id);
         if (compromissoOpcional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Compromisso nao encontrado.");
         } else {
            Compromisso compromissoExistente = (Compromisso)compromissoOpcional.get();
            compromissoExistente.setTitulo(dadosAtualizados.getTitulo());
            compromissoExistente.setEndereco(dadosAtualizados.getEndereco());
            compromissoExistente.setDataHora(dadosAtualizados.getDataHora());
            compromissoExistente.setLatitude(dadosAtualizados.getLatitude());
            compromissoExistente.setLongitude(dadosAtualizados.getLongitude());
            this.compromissoRepository.save(compromissoExistente);
            return ResponseEntity.ok("Compromisso updated com sucesso!");
         }
      } catch (Exception var5) {
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao atualizar o compromisso.");
      }
   }
}