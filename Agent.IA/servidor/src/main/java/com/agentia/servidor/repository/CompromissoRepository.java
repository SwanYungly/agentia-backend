package com.agentia.servidor.repository;

import com.agentia.servidor.model.Compromisso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CompromissoRepository extends JpaRepository<Compromisso, Long> {
    
    // Método personalizado para buscar apenas os compromissos de um usuário específico
    List<Compromisso> findByUsuarioId(Long usuarioId);
    
}