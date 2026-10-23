# Verificação local — 23/10/2026

Equipamentos: Windows deste usuário e Samsung SM-A356E conectado por USB. O Quest e os ambientes VR foram preservados.

| Fluxo | Evidência real |
|---|---|
| Chat online Android | Resposta correta sobre a conta GitHub usando sessão privada e memória; `.work/phone-online-result.json` |
| Chat offline Android | Qwen 0.8B respondeu usando memória; `.work/offline-device-result.json` |
| Chat offline PC | Qwen 4B respondeu usando memória; `.work/pc-offline-result.json` |
| Memória | 110 notas importadas de preferências e metadados de projetos; histórico SQLite persistente; sincronização autenticada com PC |
| Voz Android | 67 vozes do sistema, nove portuguesas offline; WAV real de 4,71 s / 24 kHz; reconhecedor local disponível |
| Controles Android | Serviço de acessibilidade habilitado; leitura real da árvore da tela |
| Imagem Android | PNG real 256×256, 124.581 bytes, SD Turbo local |
| Imagem PC | PNG real 256×256, 125.022 bytes, SD Turbo local |
| Vídeo PC | WebM VP8 real 128×128, 8 fps, 0,5 s, Wan local; teste mínimo de execução |
| Vídeo leve Android | MP4 H.264 real 256×256, 17 quadros, 8 fps, 2,125 s, 91.569 bytes; imagem SD Turbo com movimento de câmera, codificada no aparelho |
| Terminal Android | Agente executou shell no UID do app e criou/leu `forge-terminal-proof.txt`; resultado `FORGE_ANDROID_OK` |
| Pacote Windows | EXE empacotado abriu interface nativa; chat online respondeu corretamente usando memória; `.work/packaged-pc-online.json` |
| Continuidade | Testes de diário, hash de arquivos, resultado incerto e três agentes; ações confirmadas não são repetidas |

Os resultados de imagem/vídeo acima são testes pequenos de funcionamento, sem comprovar qualidade de um projeto final. Os testes Wan no Android foram encerrados pelo sistema por pressão de memória. O modo leve Android passou e fica selecionado inicialmente: anima uma imagem gerada, sem sintetizar movimento independente dos personagens. Wan continua selecionável. Há opção de descarregar pesos para armazenamento e processamento segmentado; a escolha de modelos maiores continua disponível.

Não foram comprovados: comandos físicos de voz/hotword ditos pelo usuário, escuta permanente em segundo plano, inferência completa de 4B no A35, edição final de avatar/Unity por este app, deploy real Vercel/Supabase ou compilação arbitrária de APK/EXE/Linux pelo agente. Ferramentas e permissões disponíveis determinam o que cada plataforma executa. O terminal Android opera sob o UID do aplicativo; não concede root nem inclui um SDK de compilação completo.

Credenciais ficam no cofre Android e no perfil privado do PC. A versão atual não publica segredos no GitHub. O túnel HTTPS de teste é temporário; a conexão entre plataformas é opcional.
