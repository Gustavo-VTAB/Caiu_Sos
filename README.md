# Caiu SOS

App Android de detecção automática de quedas e alerta de emergência, voltado a ciclistas, motociclistas, entregadores e idosos.

## Como funciona
1. Um serviço em segundo plano lê acelerômetro, giroscópio e proximidade.
2. O algoritmo identifica a sequência queda livre → impacto → rotação → imobilidade.
3. Abre uma tela de alerta com contagem regressiva (padrão 30 s) e botão "Estou bem".
4. Se não for cancelada, envia SMS/WhatsApp com a localização para os contatos de emergência e registra o evento na API Socorro.

## Sensores utilizados
Acelerômetro, giroscópio, proximidade, barômetro, temperatura ambiente, sensor de luz (modo noturno automático) e GPS.

## Funcionalidades
- Botão de pânico (segurar 3 s)
- Contatos de emergência e histórico de eventos
- Telemetria periódica para a API (bateria, localização, velocidade, pressão, temperatura)
- Clima do dia com aviso de risco (OpenWeatherMap)
- Configuração de sensibilidade, tempo de cancelamento e canais de alerta

## Tecnologias
Java, XML, Material Design 3, HttpURLConnection, SharedPreferences, Foreground Service, FusedLocationProvider.
