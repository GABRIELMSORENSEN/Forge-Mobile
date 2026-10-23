# Uso eficiente de tokens

- Respostas diretas em português; sem introdução, recapitulação ou promessas repetidas.
- Planejador e revisor usam o modelo local, mesmo quando o executor usa Codex online. Perguntas simples não acionam a equipe.
- Perfis e notas relevantes têm prioridade; contexto recente é limitado. Todo o histórico permanece no banco e pode ser pesquisado.
- Ações confirmadas ficam no diário persistente. Retomadas recebem estado e resultados, sem repetir inferência ou efeitos confirmados.
- Etapas locais de planejamento/revisão têm orçamento menor no modo econômico. Limite de saída, janela de contexto e threads são configuráveis.
- Falha de rede/cota transfere o mesmo estado para o modelo local. Não se pede um relatório extra pago depois da falha: o checkpoint acompanha cada ação.
- Não há promessa de saldo financeiro em tempo real. Cabeçalhos de limite só são usados quando o provedor os fornece.

As regras de concisão foram adaptadas da skill Caveman instalada. Economia não remove negações, resultados, permissões nem informação necessária.
