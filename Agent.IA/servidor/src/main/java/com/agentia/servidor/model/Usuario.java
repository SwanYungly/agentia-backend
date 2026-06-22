package com.agentia.servidor.model;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(name = "aceitou_termos_lgpd", nullable = false)
    private boolean aceitouTermosLgpd;

    // Construtor vazio (Exigência do Spring/JPA para funcionar)
    public Usuario() {
    }

    // Getters e Setters (Para permitir leitura e gravação dos dados)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public boolean isAceitouTermosLgpd() { return aceitouTermosLgpd; }
    public void setAceitouTermosLgpd(boolean aceitouTermosLgpd) { this.aceitouTermosLgpd = aceitouTermosLgpd; }
}