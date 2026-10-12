# Vale Corrida Pro Android

Protótipo Android complementar ao painel Base44. O app captura a tela com autorização explícita, reconhece textos de ofertas exibidas pela Uber ou 99, calcula os indicadores localmente e exibe uma sobreposição colorida. Ele não toca, aceita ou recusa corridas.

## Recursos incluídos

- Captura autorizada com MediaProjection
- OCR local com Google ML Kit
- Leitura heurística de valor, quilômetros e minutos
- Cálculo de R$/km e R$/hora
- Critérios editáveis no celular
- Sobreposição verde, amarela ou vermelha
- Atalho para o painel Base44
- Nenhuma imagem é armazenada ou enviada

## Abrir no Android Studio

1. Extraia o arquivo ZIP.
2. Abra o Android Studio e escolha **Open**.
3. Selecione a pasta `ValeCorridaProAndroid`.
4. Aguarde a sincronização do Gradle.
5. Conecte um aparelho Android com depuração USB ou utilize um emulador.
6. Execute o módulo `app`.

## Primeiro teste no celular

1. Abra o Vale Corrida Pro.
2. Ajuste os três critérios e toque em **Salvar critérios**.
3. Toque em **Iniciar leitura da tela**.
4. Autorize **aparecer sobre outros apps**.
5. Autorize a captura da tela.
6. Abra Uber Driver ou 99 Motorista e aguarde uma oferta.
7. Confira se a bolha mostra valor por km, valor por hora e a classificação.

## Valores iniciais

- R$ 1,80 por quilômetro
- R$ 35,00 por hora
- 5 km até o passageiro

Todos podem ser alterados pela tela inicial.

## Pontos que exigem teste real

Uber e 99 podem mudar a posição e a redação dos dados. O parser inicial considera o primeiro valor em reais, até duas distâncias em quilômetros e até dois tempos em minutos. Os testes reais devem registrar somente o texto reconhecido, sem dados pessoais ou imagens, para ajustar as regras de cada tela.

## Integração Base44

O MVP mantém os critérios localmente para garantir resposta rápida e funcionamento sem internet. A próxima etapa é criar no Base44 um endpoint autenticado que retorne somente as configurações do usuário. O Android baixa as configurações ao iniciar, mas continua calculando localmente.

## Privacidade e distribuição

A captura é iniciada somente após a autorização do Android e apresenta uma notificação permanente enquanto estiver ativa. Para publicação na Google Play, revise as declarações de coleta de dados e permissões. Para testes internos, gere um APK de depuração pelo Android Studio.
