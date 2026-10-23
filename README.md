# Forge - assistente pessoal

Android e PC independentes: chat com memoria persistente, modelos locais selecionaveis, conta online privada, voz e ferramentas reais. Projeto pessoal independente da OpenAI.

Baixe os pacotes em [Releases](https://github.com/GABRIELMSORENSEN/Forge-Mobile/releases/tag/v1.1.0-preview.1). O repositorio permanece privado.

## Usar

- Android: instale `artifacts/Forge-Android.apk`. A instalacao deste usuario foi provisionada por USB; o APK distribuivel nao leva sua credencial nem suas notas.
- Windows: extraia `artifacts/Forge-PC-Windows.zip` e abra `Forge-PC/Forge-PC.exe`. Preserve a pasta completa. O atalho Forge PC abre a copia local.
- Chat: selecione modelo, acesso e modo offline. Em Configuracoes ficam Memoria, Voz, Contas, Arquivos e Imagens e videos.
- Modelos: 0.8B, 2B, 4B, 9B e 27B, ou importacao GGUF. Nenhuma selecao garante que o hardware consiga carregar. Ajuste contexto, threads e saida.
- Contas: tokens GitHub, Vercel e Supabase ficam apenas no dispositivo onde foram configurados. O PC tambem pode usar o login local do GitHub CLI. APIs sao chamadas diretamente; nao ha sincronizacao de credenciais para GitHub Actions.
- Voz: vozes gratuitas instaladas no sistema. Escolha uma voz offline. Ativacao por Forge enquanto o app esta ativo; no PC o ditado requer Python, sounddevice, faster-whisper e modelo Whisper ja instalado.
- Imagens/videos: modelos baixados separadamente com SHA-256. SD Turbo e Wan executam localmente, sem creditos. Android tambem oferece video leve de imagem IA com movimento de camera; Wan no A35 nao passou por falta de RAM. A opcao de armazenamento reduz RAM e aumenta o tempo. Resolucoes/quadros pequenos servem para validar o motor.

## Memoria e continuidade

Conversas, preferencias e resultados ficam em SQLite. O prompt recebe somente notas relevantes e historico recente; o restante continua pesquisavel. Planejador e revisor usam inferencia local. Cota/rede indisponivel transfere o mesmo estado para o modelo local. Retomar usa o diario de acoes e hashes, sem repetir efeitos confirmados. Exporte copias de seguranca: armazenamento local nao equivale a garantia de nunca perder dados.

PC e Android podem sincronizar notas e conversas por HTTPS autenticado. O pareamento e opcional. O tunel de teste e temporario; reconecte se a URL mudar. O PC precisa estar ligado somente para executar tarefas no proprio PC.

## Arquivos privados

Android: cofre AES-GCM com Android Keystore, banco e modelos no armazenamento privado do aplicativo. PC: `%USERPROFILE%/.forge-assistant`, com ACL do usuario no Windows. Credencial online PC vem do login local ou chave informada. O login ChatGPT no Android usa OAuth; a sessao importada nao renova o token compartilhado do PC.

Provisionamento USB privado: `python scripts/provision_private.py`. Requer variante debug para importar; atualize depois para release com a mesma assinatura. `scripts/pair.py` agora usa esse fluxo privado e nao grava segredos no GitHub.

## Desenvolvimento

`python scripts/build.py` compila debug/release e executa lint. `python scripts/build_desktop.py` gera a pasta Windows com runtimes. `python -m unittest discover -s tests` verifica persistencia, acesso, hashes e continuidade. Os executaveis nativos Android usam llama.cpp e stable-diffusion.cpp; licencas acompanham os pacotes.

Consulte [verificacao real](docs/VALIDATION.md) e [politica de tokens](docs/TOKEN-POLICY.md). Compilar projetos APK/EXE/Linux exige SDKs e ferramentas instalados no ambiente executor; o app nao promete root Android, recursos oficiais identicos ao Codex nem credito online ilimitado.
