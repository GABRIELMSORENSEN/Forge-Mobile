# Forge Mobile

Cliente Android pessoal para executar tarefas com Codex em ambientes remotos do GitHub Actions. Projeto independente, sem afiliação com a OpenAI. Não replica o aplicativo oficial nem todas as ferramentas desta sessão.

## Instalar

Baixe **Forge-Mobile.apk** em [Releases](https://github.com/GABRIELMSORENSEN/Forge-Mobile/releases). O repositório é privado: entre com a conta proprietária para baixar.

O APK não contém tokens, chaves nem a sessão Codex. O pareamento desta instalação guarda o token GitHub no Android Keystore. Nenhum serviço no PC é necessário depois que o ambiente remoto estiver habilitado.

## Estado desta entrega

Consulte [VALIDATION.md](docs/VALIDATION.md) para resultados reais e impedimentos. **A publicação do workflow foi bloqueada porque o token atual do GitHub não tem o escopo `workflow`.** Código e APK podem ser publicados sem esse escopo, mas as tarefas remotas não rodam antes da ativação.

## Ativar pelo celular

1. Abra **Conexões → GitHub → Criar token**. Use um token clássico pessoal com `repo` e `workflow`, ou um token fino equivalente com Contents, Actions, Workflows e Secrets com escrita nos repositórios necessários.
2. Cole o token em **Token de acesso** e toque **Salvar conexão**. O token é validado e sincronizado como segredo do ambiente remoto.
3. Toque **Habilitar execução remota**. O app publica o workflow incluído no APK no centro de execução privado.
4. Em **Projetos**, escolha ou crie um repositório. Em **Estúdio**, descreva o pedido e envie. Use **Testar ambiente** primeiro.
5. Consulte **Atividade**; ao terminar, abra a resposta, baixe os arquivos ou continue a partir da branch gerada.

Alternativa pelo computador: `gh auth refresh -h github.com -s workflow`, depois `python scripts/enable_remote.py`. A autorização exige login no GitHub; não pode ser concedida pelo aplicativo em seu lugar.

## O que está implementado

- Cliente Android com projetos privados/públicos, criação de projetos privados, pedidos ao agente, terminal Bash/PowerShell, histórico de execuções, cancelamento e download de arquivos.
- Execução do Codex CLI com acesso completo **ao runner isolado**, internet, instalação de ferramentas e alterações de código.
- Ambientes Linux e Windows. Node 22, Python 3.12 e Java 17 preparados pelo workflow; demais ferramentas dependem do projeto e da imagem do runner.
- Código salvo em `forge/task-ID`; continuar uma tarefa usa essa branch como ponto de partida. Cada execução inicia uma nova sessão do agente: não há memória integral de conversas anteriores.
- Coleta de APK, AAB, EXE, MSI, AppImage e DEB; arquivos ZIP/TAR dentro de `artifacts/`. Gerar cada formato depende de ferramentas, dependências, código e assinatura apropriados. A presença da opção não comprova que um projeto arbitrário compilará.
- GitHub conectado diretamente. Vercel e Supabase têm consulta real de projetos e envio de tokens criptografados ao executor, que pode usar suas APIs/CLIs conforme o pedido.
- Credenciais protegidas por AES-GCM/Android Keystore no telefone. Segredos remotos enviados ao GitHub com sealed boxes/libsodium; nenhum token acompanha o APK.

## Conta Codex

Este centro privado pode utilizar `CODEX_AUTH_JSON` obtido pelo pareamento ou `OPENAI_API_KEY`. A API tem cobrança independente da assinatura. Os limites e modelos da conta continuam valendo.

O script de pareamento importa a sessão de `~/.codex/auth.json` para um segredo **somente de repositório privado**. O executor persiste renovações antes de apagar o arquivo temporário. Uma única execução remota roda por vez. Se a sessão expirar ou for revogada, será preciso reconectar. Para uso duradouro, prefira uma autenticação dedicada ao ambiente remoto; sessões compartilhadas com outro dispositivo podem sofrer conflitos de renovação.

```powershell
python scripts/pair.py --codex-account --device SERIAL_ADB
```

O provisionamento USB só funciona na variante debug. A versão release usa a mesma assinatura e preserva o cofre ao atualizar. Não deixe a variante debug instalada para uso diário.

Referências oficiais: [autenticação do Codex](https://learn.chatgpt.com/docs/auth), [execução não interativa](https://learn.chatgpt.com/docs/non-interactive-mode), [conta Codex em CI privado](https://learn.chatgpt.com/docs/auth/ci-cd-auth).

## Limites

- Requer internet, conta Codex/API com acesso e GitHub Actions habilitado com minutos disponíveis. Não promete gratuidade ilimitada.
- Até 55 minutos por job, com até 40 minutos no comando principal. O runner é temporário; não hospeda aplicações permanentes. Artefatos expiram em 14 dias e a coleta tem limite de 500 MB.
- O centro precisa ser privado, o disparo é restrito ao proprietário e os projetos alvo devem pertencer a ele. Repositórios de organizações/outros proprietários ainda não são suportados.
- Projetos e scripts executados recebem as credenciais das integrações habilitadas. Use apenas código e pedidos confiáveis. Não torne o centro público.
- Não controla outros apps Android, não remove permissões do sistema, não acessa arquivos do PC desligado e não leva automaticamente os plugins, memórias ou sessões do Codex desktop.
- O histórico mostra etapas do GitHub; a resposta e a saída do terminal ficam disponíveis após o upload do resultado. Não é streaming de conversa em tempo real.
- Instalação/assinatura de aplicações geradas e publicação nas lojas exigem os requisitos das respectivas plataformas.

## Compilar e verificar

JDK 17, Android SDK 35 e Python 3.11+. Ajuste `local.properties` com `sdk.dir=...`.

```powershell
npm ci --ignore-scripts
npm run vendor
python -m unittest discover -s tests -v
python scripts/build.py
```

O script cria uma assinatura pessoal em `~/.forge-mobile/`, fora do Git. Faça backup dessa pasta para manter a capacidade de atualizar o mesmo app. Saídas: `app/build/outputs/apk/{debug,release}/`.

Os assets libsodium 0.7.15 incluem suas licenças. A depuração WebView é habilitada somente em debug. Backups Android e tráfego HTTP sem TLS estão desabilitados.
