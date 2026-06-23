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

      // Remove prefixos para a primeira tentativa de busca (aumenta a chance de acerto)
      String ruaLimpa = ruaBusca.replaceAll("(?i)^(rua|avenida|av\\.?|travessa|alameda|rodovia)\\s+", "").trim();

      String enderecoFallback = formatarNomeBonito(ruaBusca);
      if (!numeroExtraido.isEmpty()) enderecoFallback += " " + numeroExtraido;
      resultado.put("enderecoFormatado", enderecoFallback); 

      try {
         RestTemplate restTemplate = new RestTemplate();
         HttpHeaders headers = new HttpHeaders();
         headers.set("User-Agent", "AgentIA-App/Final (estudante@agentia.com)");
         headers.set("Accept-Language", "pt-BR");
         HttpEntity<String> entity = new HttpEntity<>(headers);

         // A MÁGICA REAL: Deixar o Spring lidar com o texto puro usando o placeholder {q}
         String url = "https://nominatim.openstreetmap.org/search?format=json&q={q}&limit=1";

         // TENTATIVA 1: Nome da rua limpo + Cidade
         String queryBusca = ruaLimpa + ", Londrina, PR";
         ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class, queryBusca);
         
         // TENTATIVA 2: Se não achar, tenta com o nome completo que a IA deu
         if (response.getBody() == null || response.getBody().isEmpty()) {
             queryBusca = ruaBusca + ", Londrina, PR";
             response = restTemplate.exchange(url, HttpMethod.GET, entity, List.class, queryBusca);
         }

         if (response.getBody() != null && !response.getBody().isEmpty()) {
            Map<String, Object> localInfo = (Map<String, Object>) response.getBody().get(0);
            
            resultado.put("lat", Double.parseDouble(localInfo.get("lat").toString()));
            resultado.put("lon", Double.parseDouble(localInfo.get("lon").toString()));
            
            if (localInfo.get("display_name") != null) {
               String[] partes = localInfo.get("display_name").toString().split(",");
               String enderecoLimpo = partes[0].trim();
               if (partes.length > 1) enderecoLimpo += ", " + partes[1].trim();
               if (partes.length > 2) enderecoLimpo += ", " + partes[2].trim();
               
               if (!numeroExtraido.isEmpty()) {
                   enderecoLimpo += ", Nº " + numeroExtraido;
               }
               resultado.put("enderecoFormatado", enderecoLimpo);
            }
         } else {
             System.out.println("Nominatim não encontrou nada para: " + queryBusca);
         }
      } catch (Exception e) {
         System.out.println("Erro na busca do Nominatim: " + e.getMessage());
      }
      return resultado;
   }

   @PostMapping({"/cadastrar/{usuarioId}"})
   public ResponseEntity<String> cadastrarCompromisso(@PathVariable Long usuarioId, @RequestBody Compromisso novoCompromisso) {
      Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
      if (usuarioOpcional.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado.");
      Usuario usuario = (Usuario)usuarioOpcional.get();
      novoCompromisso.setUsuario(usuario);
      this.compromissoRepository.save(novoCompromisso);
      return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso agendado com sucesso!");
   }

   @PostMapping({"/cadastrar-ia/{usuarioId}"})
   public ResponseEntity<String> cadastrarComIA(@PathVariable Long usuarioId, @RequestBody Map<String, String> payload) {
      Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
      if (usuarioOpcional.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario nao encontrado.");

      String textoUsuario = payload.get("texto");
      if (textoUsuario == null || textoUsuario.trim().isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Texto vazio.");

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
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro via IA: " + e.getMessage());
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