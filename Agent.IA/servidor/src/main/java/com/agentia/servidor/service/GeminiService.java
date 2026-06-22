package com.agentia.servidor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Service
public class GeminiService {

    // O Spring Boot injeta a chave automaticamente aqui
    @Value("${gemini.api.key}")
    private String apiKey;

    public String extrairDadosDeAgendamento(String textoUsuario) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        // O prompt rigoroso que obriga a IA a devolver apenas um JSON estruturado
        String instrucao = "Você é um extrator de dados de um sistema de agendas. " +
                           "Analise o texto do usuário e retorne ESTRITAMENTE um objeto JSON válido. " +
                           "Não inclua crases (```json), nem texto adicional. " +
                           "O JSON deve ter exatamente estas chaves: " +
                           "'titulo' (string), 'data' (formato YYYY-MM-DD), 'horario' (formato HH:MM), 'local' (string). " +
                           "Se alguma informação faltar, deixe o valor como null. " +
                           "Texto do usuário: " + textoUsuario;

        // Montando o corpo da requisição exigido pela API do Gemini
        String requestBody = "{\n" +
                "  \"contents\": [{\n" +
                "    \"parts\":[{\"text\": \"" + instrucao + "\"}]\n" +
                "  }]\n" +
                "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            // Faz a chamada externa para o Google
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getBody(); // Retorna o JSON cru para nós
        } catch (Exception e) {
            System.out.println("Erro ao comunicar com a IA: " + e.getMessage());
            return null;
        }
    }
}