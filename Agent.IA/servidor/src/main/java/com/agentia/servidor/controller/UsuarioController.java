package com.agentia.servidor.controller;

import com.agentia.servidor.model.Usuario;
import com.agentia.servidor.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    // ROTA DE CADASTRO
    @PostMapping("/cadastrar")
    public ResponseEntity<String> cadastrarUsuario(@RequestBody Usuario novoUsuario) {
        
        Usuario usuarioExistente = usuarioRepository.findByEmail(novoUsuario.getEmail());
        if (usuarioExistente != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erro: E-mail já cadastrado.");
        }

        try {
            usuarioRepository.save(novoUsuario);
            return ResponseEntity.status(HttpStatus.CREATED).body("Usuário cadastrado com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro ao salvar o usuário.");
        }
    }

    // ROTA DE LOGIN (Agora devolvendo ID e Nome em formato JSON)
    @PostMapping("/login")
    public ResponseEntity<Object> fazerLogin(@RequestBody Usuario credenciais) {
        
        Usuario usuarioExistente = usuarioRepository.findByEmail(credenciais.getEmail());
        
        if (usuarioExistente == null || !usuarioExistente.getSenha().equals(credenciais.getSenha())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Erro: E-mail ou senha incorretos.");
        }

        // Monta um pacote JSON seguro com os dados da sessão
        Map<String, Object> dadosUsuario = new HashMap<>();
        dadosUsuario.put("id", usuarioExistente.getId());
        dadosUsuario.put("nome", usuarioExistente.getNome());

        return ResponseEntity.ok(dadosUsuario);
    }
}