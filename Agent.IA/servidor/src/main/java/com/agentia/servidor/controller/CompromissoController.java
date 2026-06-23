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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
@CrossOrigin(
   origins = {"*"}
)
public class CompromissoController {
   @Autowired
   private CompromissoRepository compromissoRepository;
   @Autowired
   private UsuarioRepository usuarioRepository;
   @Autowired
   private GeminiService geminiService;

   public CompromissoController() {
   }

   // --- MÉTODO ATUALIZADO: Tratamento de números para garantir o match no Nominatim ---
   private Map<String, Object> buscarDadosDoEndereco(String nomeLocal) {
      Map<String, Object> resultado = new HashMap<>();
      resultado.put("lat", -23.3102);
      resultado.put("lon", -51.1627);
      resultado.put("enderecoFormatado", nomeLocal); 

      // 1. Limpa o texto enviado pela IA (remove vírgulas caso o usuário tenha digitado)
      String ruaBusca = nomeLocal.replace(",", "").trim();
      String numeroExtraido = "";

      // 2. Tenta separar o número da rua (exatamente como fazemos no Front-End)
      int ultimoEspaco = ruaBusca.lastIndexOf(' ');
      if (ultimoEspaco != -1) {
         String possivelNumero = ruaBusca.substring(ultimoEspaco + 1);
         // Se a última palavra for composta apenas por números, nós a guardamos
         if (possivelNumero.matches("\\d+")) {
            numeroExtraido = possivelNumero;
            ruaBusca = ruaBusca.substring(0, ultimoEspaco).trim();
         }
      }

      try {
         // 3. Busca APENAS pelo nome da rua para o Nominatim encontrar com facilidade
         String url = "https://nominatim.openstreetmap.org/search?format=json&q=" + 
                      java.net.URLEncoder.encode(ruaBusca + ", Londrina, PR", "UTF-8") + "&limit=1";
         
         HttpHeaders headers = new HttpHeaders();
         headers.set("User-Agent", "AgentIA-App/1.0 (contato.agentia@app.com)");
         HttpEntity<String> entity = new HttpEntity<>(headers);

         RestTemplate restTemplate = new RestTemplate();
         ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class);
         
         if (response.getBody() != null && !response.getBody().isEmpty()) {
            Map<String, Object> localInfo = (Map<String, Object>) response.getBody().get(0);
            
            resultado.put("lat", Double.parseDouble(localInfo.get("lat").toString()));
            resultado.put("lon", Double.parseDouble(localInfo.get("lon").toString()));
            
            // 4. Pega o endereço oficial, limpa e adiciona o número de volta no formato bonito
            if (localInfo.get("display_name") != null) {
               String[] partes = localInfo.get("display_name").toString().split(",");
               String enderecoLimpo = partes[0];
               if (partes.length > 1) enderecoLimpo += "," + partes[1];
               if (partes.length > 2) enderecoLimpo += "," + partes[2];
               
               enderecoLimpo = enderecoLimpo.trim();
               if (!numeroExtraido.isEmpty()) {
                   enderecoLimpo += ", Nº " + numeroExtraido;
               }
               
               resultado.put("enderecoFormatado", enderecoLimpo);
            }
         }
      } catch (Exception e) {
         System.out.println("Erro na busca do Nominatim: " + e.getMessage());
      }
      return resultado;
   }

   @PostMapping({"/cadastrar/{usuarioId}"})
   public ResponseEntity<String> cadastrarCompromisso(@PathVariable Long usuarioId, @RequestBody Compromisso novoCompromisso) {
      Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
      if (usuarioOpcional.isEmpty()) {
         return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado.");
      } else {
         Usuario usuario = (Usuario)usuarioOpcional.get();
         novoCompromisso.setUsuario(usuario);
         try {
            this.compromissoRepository.save(novoCompromisso);
            return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso agendado com sucesso!");
         } catch (Exception var6) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao salvar.");
         }
      }
   }

   @PostMapping({"/cadastrar-ia/{usuarioId}"})
   public ResponseEntity<String> cadastrarComIA(@PathVariable Long usuarioId, @RequestBody Map<String, String> payload) {
      Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
      if (usuarioOpcional.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario nao encontrado.");

      String textoUsuario = payload.get("texto");
      if (textoUsuario == null || textoUsuario.trim().isEmpty()) {
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erro: O texto enviado nao pode estar vazio.");
      }

      try {
         String jsonResposta = this.geminiService.extrairDadosDeAgendamento(textoUsuario);
         if (jsonResposta == null) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro na IA.");

         ObjectMapper objectMapper = new ObjectMapper();
         Map<String, Object> mapaIA = objectMapper.readValue(jsonResposta, Map.class);

         Compromisso novoCompromisso = new Compromisso();
         novoCompromisso.setUsuario((Usuario)usuarioOpcional.get());
         novoCompromisso.setTitulo((String)mapaIA.get("titulo"));

         String localIA = (String)mapaIA.get("local");
         if (localIA == null) localIA = "Local não informado";
         
         Map<String, Object> dadosEndereco = buscarDadosDoEndereco(localIA);
         novoCompromisso.setEndereco((String)dadosEndereco.get("enderecoFormatado"));
         novoCompromisso.setLatitude((Double)dadosEndereco.get("lat"));
         novoCompromisso.setLongitude((Double)dadosEndereco.get("lon"));

         String data = (String)mapaIA.get("data");
         String horario = (String)mapaIA.get("horario");

         if (data == null || data.equals("null")) data = java.time.LocalDate.now().toString();
         if (horario == null || horario.equals("null")) horario = "12:00";

         novoCompromisso.setDataHora(java.time.LocalDateTime.parse(data + "T" + horario));
         this.compromissoRepository.save(novoCompromisso);
         return ResponseEntity.status(HttpStatus.CREATED).body("Agendado com endereço validado!");

      } catch (Exception e) {
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao processar e salvar o agendamento via IA: " + e.getMessage());
      }
   }

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
   public ResponseEntity<String> editarCompromisso(@PathVariable Long id, @RequestBody Compromisso dadosAtualizados) {
      Compromisso c = this.compromissoRepository.findById(id).get();
      c.setTitulo(dadosAtualizados.getTitulo());
      c.setEndereco(dadosAtualizados.getEndereco());
      c.setDataHora(dadosAtualizados.getDataHora());
      c.setLatitude(dadosAtualizados.getLatitude());
      c.setLongitude(dadosAtualizados.getLongitude());
      this.compromissoRepository.save(c);
      return ResponseEntity.ok("Atualizado!");
   }
}