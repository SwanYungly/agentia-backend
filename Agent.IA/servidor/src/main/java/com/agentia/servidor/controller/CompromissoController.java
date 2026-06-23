package com.agentia.servidor.controller;

import com.agentia.servidor.model.Compromisso;
import com.agentia.servidor.model.Usuario;
import com.agentia.servidor.repository.CompromissoRepository;
import com.agentia.servidor.repository.UsuarioRepository;
import com.agentia.servidor.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

   @PostMapping({"/cadastrar/{usuarioId}"})
   public ResponseEntity<String> cadastrarCompromisso(@PathVariable Long usuarioId, @RequestBody Compromisso novoCompromisso) {
      Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
      if (usuarioOpcional.isEmpty()) {
         return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado para vincular o compromisso.");
      } else {
         Usuario usuario = (Usuario)usuarioOpcional.get();
         novoCompromisso.setUsuario(usuario);

         try {
            this.compromissoRepository.save(novoCompromisso);
            return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso agendado com sucesso!");
         } catch (Exception var6) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao salvar o compromisso.");
         }
      }
   }

   @PostMapping({"/cadastrar-ia/{usuarioId}"})
   public ResponseEntity<String> cadastrarComIA(@PathVariable Long usuarioId, @RequestBody Map<String, String> payload) {
      Optional<Usuario> usuarioOpcional = this.usuarioRepository.findById(usuarioId);
      if (usuarioOpcional.isEmpty()) {
         return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado para vincular o compromisso.");
      }

      String textoUsuario = payload.get("texto");
      if (textoUsuario == null || textoUsuario.trim().isEmpty()) {
         return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erro: O texto enviado nao pode estar vazio.");
      }

      try {
         // 1. Envia a frase livre para o Gemini extrair os dados em formato JSON
         String jsonResposta = this.geminiService.extrairDadosDeAgendamento(textoUsuario);
         if (jsonResposta == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao processar o comando com a inteligencia artificial.");
         }

         // 2. Transforma o JSON texto retornado pela IA em um mapa de chaves e valores
         ObjectMapper objectMapper = new ObjectMapper();
         Map<String, Object> mapaIA = objectMapper.readValue(jsonResposta, Map.class);

         // 3. Constroi o objeto Compromisso mapeando os campos extraidos
         Compromisso novoCompromisso = new Compromisso();
         novoCompromisso.setUsuario((Usuario)usuarioOpcional.get());
         novoCompromisso.setTitulo((String)mapaIA.get("titulo"));
         novoCompromisso.setEndereco((String)mapaIA.get("local"));

         String data = (String)mapaIA.get("data");
         String horario = (String)mapaIA.get("horario");

         // 4. Junta e converte os valores de data e hora para salvar no formato LocalDateTime esperado pelo banco
         novoCompromisso.setDataHora(java.time.LocalDateTime.parse(data + "T" + horario));

         // 5. Adiciona coordenadas padrao para o banco aceitar o salvamento
         novoCompromisso.setLatitude(-23.3102);
         novoCompromisso.setLongitude(-51.1627);

         // 6. Salva definitivamente no banco de dados relacional
         this.compromissoRepository.save(novoCompromisso);
         return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso interpretado e agendado com sucesso via IA!");

      } catch (Exception e) {
         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao processar e salvar o agendamento via IA: " + e.getMessage());
      }
   }

   @GetMapping({"/listar/{usuarioId}"})
   public ResponseEntity<List<Compromisso>> listarCompromissos(@PathVariable Long usuarioId) {
      List<Compromisso> compromissos = this.compromissoRepository.findByUsuarioId(usuarioId);
      return ResponseEntity.ok(compromissos);
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