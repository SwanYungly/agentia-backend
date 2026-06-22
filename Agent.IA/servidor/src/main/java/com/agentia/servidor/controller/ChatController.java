package com.agentia.servidor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @PostMapping("/processar")
    public ResponseEntity<Map<String, String>> processarMensagem(@RequestBody Map<String, String> payload) {
        String mensagem = payload.get("mensagem");
        Map<String, String> resposta = new HashMap<>();

        if (mensagem == null || mensagem.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Variáveis padrão
        String titulo = mensagem;
        String dataStr = LocalDate.now().toString(); // Assume "hoje" por padrão se não achar data
        String horaStr = "12:00"; // Horário padrão de segurança
        String endereco = "";

        // 1. Identificar Data (Amanhã ou Hoje)
        if (mensagem.toLowerCase().contains("amanhã") || mensagem.toLowerCase().contains("amanha")) {
            dataStr = LocalDate.now().plusDays(1).toString(); // Soma 1 dia na data do servidor
            titulo = titulo.replaceAll("(?i)amanhã|amanha", "");
        } else if (mensagem.toLowerCase().contains("hoje")) {
            dataStr = LocalDate.now().toString();
            titulo = titulo.replaceAll("(?i)hoje", "");
        }

        // 2. Identificar Hora (Ex: 14h, às 14h, 14:30) usando Regex
        Pattern patternHora = Pattern.compile("(?:às|as)?\\s*(\\d{1,2})(?:h|:(\\d{2}))?", Pattern.CASE_INSENSITIVE);
        Matcher matcherHora = patternHora.matcher(mensagem);
        if (matcherHora.find()) {
            String hora = matcherHora.group(1);
            String minuto = matcherHora.group(2) != null ? matcherHora.group(2) : "00";
            
            // Coloca o zero na frente se for hora única (ex: "9" vira "09")
            if (hora.length() == 1) {
                hora = "0" + hora;
            }
            horaStr = hora + ":" + minuto;
            
            // Retira a parte da hora do texto que vai virar o título
            titulo = titulo.replace(matcherHora.group(0), ""); 
        }

        // 3. Identificar Endereço (Tudo após " na ", " no " ou " em ")
        Pattern patternEndereco = Pattern.compile("\\s(?:na|no|em)\\s+(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcherEndereco = patternEndereco.matcher(mensagem);
        if (matcherEndereco.find()) {
            endereco = matcherEndereco.group(1).trim();
            // Retira o endereço do título
            titulo = titulo.replace(matcherEndereco.group(0), ""); 
        }

        // 4. Limpeza final do Título
        titulo = titulo.trim();
        // Remove conectivos comuns que o usuário digita antes da tarefa
        if (titulo.toLowerCase().startsWith("preciso ir ao ")) titulo = titulo.substring(14);
        if (titulo.toLowerCase().startsWith("preciso ir na ")) titulo = titulo.substring(14);
        if (titulo.toLowerCase().startsWith("tenho ")) titulo = titulo.substring(6);
        if (titulo.toLowerCase().startsWith("marcar ")) titulo = titulo.substring(7);

        // Deixa a primeira letra maiúscula para ficar elegante
        if (!titulo.isEmpty()) {
            titulo = titulo.substring(0, 1).toUpperCase() + titulo.substring(1);
        } else {
            titulo = "Novo Compromisso";
        }

        // Monta o pacote de resposta para o JavaScript
        resposta.put("titulo", titulo);
        resposta.put("data", dataStr);
        resposta.put("hora", horaStr);
        resposta.put("endereco", endereco);

        return ResponseEntity.ok(resposta);
    }
}