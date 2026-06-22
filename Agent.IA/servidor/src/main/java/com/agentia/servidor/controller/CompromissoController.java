package com.agentia.servidor.controller;

import com.agentia.servidor.model.Compromisso;
import com.agentia.servidor.model.Usuario;
import com.agentia.servidor.repository.CompromissoRepository;
import com.agentia.servidor.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/compromissos")
@CrossOrigin(origins = "*")
public class CompromissoController {

    @Autowired
    private CompromissoRepository compromissoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping("/cadastrar/{usuarioId}")
    public ResponseEntity<String> cadastrarCompromisso(@PathVariable Long usuarioId, @RequestBody Compromisso novoCompromisso) {
        Optional<Usuario> usuarioOpcional = usuarioRepository.findById(usuarioId);
        if (usuarioOpcional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Usuario nao encontrado para vincular o compromisso.");
        }

        Usuario usuario = usuarioOpcional.get();
        novoCompromisso.setUsuario(usuario);
        
        try {
            compromissoRepository.save(novoCompromisso);
            return ResponseEntity.status(HttpStatus.CREATED).body("Compromisso agendado com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao salvar o compromisso.");
        }
    }

    @GetMapping("/listar/{usuarioId}")
    public ResponseEntity<List<Compromisso>> listarCompromissos(@PathVariable Long usuarioId) {
        List<Compromisso> compromissos = compromissoRepository.findByUsuarioId(usuarioId);
        return ResponseEntity.ok(compromissos);
    }

    // ROTA PARA EXCLUIR COMPROMISSO
    @DeleteMapping("/excluir/{id}")
    public ResponseEntity<String> excluirCompromisso(@PathVariable Long id) {
        try {
            if (!compromissoRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Compromisso nao encontrado.");
            }
            compromissoRepository.deleteById(id);
            return ResponseEntity.ok("Compromisso excluido com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao excluir o compromisso.");
        }
    }

    // ROTA PARA EDITAR COMPROMISSO
    @PutMapping("/editar/{id}")
    public ResponseEntity<String> editarCompromisso(@PathVariable Long id, @RequestBody Compromisso dadosAtualizados) {
        try {
            Optional<Compromisso> compromissoOpcional = compromissoRepository.findById(id);
            if (compromissoOpcional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: Compromisso nao encontrado.");
            }

            Compromisso compromissoExistente = compromissoOpcional.get();
            compromissoExistente.setTitulo(dadosAtualizados.getTitulo());
            compromissoExistente.setEndereco(dadosAtualizados.getEndereco());
            compromissoExistente.setDataHora(dadosAtualizados.getDataHora());
            compromissoExistente.setLatitude(dadosAtualizados.getLatitude());
            compromissoExistente.setLongitude(dadosAtualizados.getLongitude());

            compromissoRepository.save(compromissoExistente);
            return ResponseEntity.ok("Compromisso atualizado com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao atualizar o compromisso.");
        }
    }
}