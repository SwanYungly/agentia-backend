package com.agentia.servidor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    public String extrairDadosDeAgendamento(String textoUsuario) {
        String urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

        String dataHoje = LocalDate.now().toString();

        // --- ENGENHARIA DE PROMPT: Regras estritas para a limpeza do endereço ---
        String instrucao = "Hoje e dia " + dataHoje + ". Voce e um extrator de dados de agendas. " +
                "Analise o texto do usuario e retorne ESTRITAMENTE um objeto JSON valido. " +
                "Nao inclua crases (```json), nem texto adicional. " +
                "O JSON deve ter exatamente estas chaves: " +
                "'titulo' (string), 'data' (formato YYYY-MM-DD), 'horario' (formato HH:MM), 'local' (string). " +
                "REGRAS PARA O LOCAL: Extraia apenas o nome oficial da rua/avenida e o numero. " +
                "NUNCA inclua preposicoes (na, no, em), nem o nome do lugar ou da pessoa. " +
                "Exemplo: se o texto for 'role na casa da ada na rua david santos filho 121', " +
                "o valor de 'local' deve ser ESTRITAMENTE 'Rua David Santos Filho 121'. " +
                "Se alguma informacao faltar, deixe o valor como null. " +
                "Texto do usuario: " + textoUsuario;

        try {
            ObjectMapper mapper = new ObjectMapper();
            
            Map<String, Object> part = new HashMap<>();
            part.put("text", instrucao);
            
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));
            
            Map<String, Object> requestBodyMap = new HashMap<>();
            requestBodyMap.put("contents", List.of(content));

            String requestBody = mapper.writeValueAsString(requestBodyMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            URI uri = URI.create(urlString);

            ResponseEntity<String> response = restTemplate.postForEntity(uri, request, String.class);
            
            JsonNode rootNode = mapper.readTree(response.getBody());
            String textoPuroIA = rootNode.path("candidates").get(0)
                                         .path("content")
                                         .path("parts").get(0)
                                         .path("text").asText();

            textoPuroIA = textoPuroIA.replace("```json", "").replace("```", "").trim();

            return textoPuroIA;

        } catch (Exception e) {
            System.out.println("Erro critico na integracao com Gemini: " + e.getMessage());
            return null;
        }
    }
}