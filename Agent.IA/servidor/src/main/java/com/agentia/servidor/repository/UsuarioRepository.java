package com.agentia.servidor.repository;

import com.agentia.servidor.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    
    // Método que o Spring vai traduzir para "SELECT * FROM usuarios WHERE email = ?"
    Usuario findByEmail(String email);
    
}