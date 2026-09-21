# Validação — 21/09/2026

## Confirmado

- APK debug e release compilados e assinados com chave particular externa ao repositório.
- `assembleDebug`, `assembleRelease`, `lintDebug`: sucesso; lint sem erros. Avisos de atualização de dependências/SDK e uso intencional de JavaScript no WebView restrito.
- Quatro testes do executor: bloqueio de repositório de outro proprietário/injeção em nome; remoção de credenciais dos logs; coleta restrita de artefatos; recusa de arquivos de credenciais no commit.
- Sintaxe JavaScript/Python e estrutura YAML do workflow verificadas.
- Instalação no Samsung A35 `SM-A356E`, Android 16, serial `RQCXC015NYX`.
- Chamadas reais pelo WebView e pela ponte Java no A35: autenticação como `GABRIELMSORENSEN`, listagem/renderização de 16 repositórios e presença do GATARME, consulta dos nomes de segredos remotos, criptografia sealed box/libsodium e rejeição de operação/link não permitidos. Evidência: [device-check.json](device-check.json).
- Interface renderizada e inspecionada no WebView do A35 e no emulador Android. O telefone estava bloqueado durante parte da validação; isso limita a validação por toques físicos na tela, mas não substitui nem invalida os resultados das chamadas reais do app.
- Chaves de acesso ausentes dos arquivos fonte e do APK distribuído. Cofre Android conserva as credenciais ao substituir debug por release da mesma assinatura.

## Bloqueios e funções não validadas

- **Execução remota não ativada.** GitHub rejeitou a criação de `.github/workflows/remote.yml`: o OAuth token atual tem `repo`, `read:org` e `gist`, mas não `workflow`. Tentativa de reautorização expirou sem concessão da permissão. O template está no APK e em `app/src/main/assets/remote.yml`; o botão **Habilitar execução remota** permite publicá-lo depois de conectar um token com a permissão necessária.
- Não houve execução real do Codex em runner GitHub, compilação remota de APK/EXE/Linux, download de resultado remoto nem teste de cancelamento de job. Esses fluxos estão implementados, mas não são apresentados como comprovados.
- Sessão Codex armazenada como segredo do centro privado. Existência do segredo foi verificada; validade de inferência no runner ainda não foi testada.
- **Vercel:** a credencial encontrada localmente retornou HTTP 403, `invalidToken: true`. A cópia inválida foi retirada do app e do centro remoto. É necessário conectar um novo token.
- **Supabase:** nenhuma credencial utilizável encontrada. Integração implementada, não conectada nem validada com conta real.
- O aplicativo não oferece equivalência integral ao Codex desktop: sem controle geral do Android, arquivos do PC desligado, plugins e memória desta sessão, nem conversa com streaming contínuo.

## Reprodução

`python -m unittest discover -s tests -v` e `python scripts/build.py`.

Para a ponte Android: instale debug, faça o pareamento com `scripts/pair.py`, encaminhe a porta 9227 para `localabstract:webview_devtools_remote_PID` do app e execute `python scripts/device_check.py`. O script precisa de Playwright Python e usa as conexões reais da instalação; não inclui credenciais no relatório.

Após autorizar `workflow`, publique pelo app ou com `python scripts/enable_remote.py`. Execute um diagnóstico em Linux e Windows, depois uma tarefa Codex com um projeto de teste e confira a branch e os artefatos antes de considerar o fluxo remoto validado.
