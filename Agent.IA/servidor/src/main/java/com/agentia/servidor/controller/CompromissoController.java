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

   public CompromissoController() {
   }

   private String formatarNomeBonito(String texto) {
       if (texto == null || texto.isEmpty()) return texto;
       String[] palavras = texto.split("\\s+");
       StringBuilder sb = new StringBuilder();
       for (String palavra : palavras) {
           if (palavra.length() > 2) {
               sb.append(Character.toUpperCase(palavra.charAt(0))).append(palavra.substring(1).toLowerCase());
           } else {
               sb.append(palavra.toLowerCase());
           }
           sb.append(" ");
       }
       return sb.toString().trim();
   }

   // --- A SOLUÇÃO: API Photon (Komoot) que não bloqueia servidores em nuvem ---
   private Map<String, Object> buscarDadosDoEndereco(String nomeLocal) {
      Map<String, Object> resultado = new HashMap<>();
      resultado.put("lat", -23.3102);
      resultado.put("lon", -51.1627);

      String ruaBusca = nomeLocal.replace(",", "").trim();
      String numeroExtraido = "";

      int ultimoEspaco = ruaBusca.lastIndexOf(' ');
      if (ultimoEspaco != -1) {
         String possivelNumero = ruaBusca.substring(ultimoEspaco + 1);
         if (possivelNumero.matches("\\d+")) {
            numeroExtraido = possivelNumero;
            ruaBusca = ruaBusca.substring(0, ultimoEspaco).trim();
         }
      }

      String enderecoFallback = formatarNomeBonito(ruaBusca);
      if (!numeroExtraido.isEmpty()) enderecoFallback += " " + numeroExtraido;
      resultado.put("enderecoFormatado", enderecoFallback); 

      try {
         // Consulta amigável para nuvem usando Photon API (baseada no mesmo mapa do Nominatim)
         String query = java.net.URLEncoder.encode(ruaBusca + " Londrina", "UTF-8").replace("+", "%20");
         String url = "https://photon.komoot.io/api/?q=" + query + "&limit=1";
         
         RestTemplate restTemplate = new RestTemplate();
         ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
         
         if (response.getBody() != null && response.getBody().containsKey("features")) {
             List<Map<String, Object>> features = (List<Map<String, Object>>) response.getBody().get("features");
             
             if (!features.isEmpty()) {
                 Map<String, Object> feature = features.get(0);
                 Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
                 Map<String, Object> properties = (Map<String, Object>) feature.get("properties");
                 
                 List<Number> coordinates = (List<Number>) geometry.get("coordinates");
                 
                 // O Photon retorna a ordem inversa do Nominatim: [Longitude, Latitude]
                 resultado.put("lon", coordinates.get(0).doubleValue());
                 resultado.put("lat", coordinates.get(1).doubleValue());
                 
                 // Montando o endereço estruturado
                 String nomeRua = properties.containsKey("name") ? properties.get("name").toString() : formatarNomeBonito(ruaBusca);
                 String bairro = properties.containsKey("district") ? properties.get("district").toString() : "";
                 String cidade = properties.containsKey("city") ? properties.get("city").toString() : "Londrina";
                 
                 String enderecoLimpo = nomeRua;
                 if (!bairro.isEmpty()) enderecoLimpo += ", " + bairro;
                 enderecoLimpo += ", " + cidade;
                 
                 if (!numeroExtraido.isEmpty()) {
                     enderecoLimpo += ", Nº " + numeroExtraido;
                 }
                 resultado.put("enderecoFormatado", enderecoLimpo);
             } else {
                 System.out.println("Aviso: O Photon nao encontrou a rua: " + ruaBusca);
             }
         }
      } catch (Exception e) {
         System.out.println("Erro na integracao com Photon: " + e.getMessage());
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