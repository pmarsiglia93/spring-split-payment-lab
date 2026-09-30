# Guia de estudo e apresentação

Este documento ajuda a estudar o projeto sem precisar decorar termos. A pergunta principal em cada etapa é: **qual problema existe, como o código reage e qual evidência prova isso?**

## 1. O problema de negócio

Um marketplace recebe R$ 100 de um cliente. O valor precisa ser registrado, dividido em R$ 90 para o vendedor e R$ 10 para a plataforma e, por fim, transferido.

No laboratório, essas responsabilidades não estão em uma aplicação única. Elas foram separadas para demonstrar os desafios que aparecem quando processos conversam pela rede.

```text
Frontend → Orchestrator → Payment → Split → Transfer
```

## 2. O caminho de uma requisição

1. O **Controller** recebe o HTTP, valida o formato e delega.
2. O **Service** executa as regras de negócio.
3. O **Repository** salva ou consulta o estado no MongoDB.
4. O **Orchestrator** chama os serviços na ordem correta.
5. A **Saga** registra até onde a operação chegou.
6. O frontend traduz o estado final em uma linha do tempo.

O Controller não deve conter a regra do pagamento. A mesma regra pode ser chamada futuramente por outro Controller, um consumidor de fila ou um processo agendado. Por isso ela pertence ao Service.

## 3. Injeção de dependência

Os Services recebem seus Repositories pelo construtor. O Spring cria as implementações das interfaces que estendem `MongoRepository` e injeta os objetos necessários.

Isso evita acoplamento com uma implementação concreta e permite substituir o Repository por um mock nos testes. Não é necessário — nem correto neste caso — usar `new PaymentRepository()`.

## 4. Idempotência

Imagine que o cliente clicou em “pagar”, a resposta demorou e o navegador enviou novamente. Sem proteção, dois pagamentos poderiam ser criados.

A `idempotencyKey` funciona como um número único daquela intenção. Se os mesmos dados forem enviados novamente, o sistema devolve a Saga existente. Se a chave for reutilizada com dados diferentes, responde `409 Conflict`.

O índice único no MongoDB é importante porque duas requisições simultâneas podem consultar antes de qualquer uma salvar. A garantia precisa existir também no banco, não apenas em um `if` no código.

## 5. Timeout, retry e circuit breaker

Esses mecanismos resolvem problemas diferentes:

- **Timeout:** limita quanto tempo o orquestrador espera. Sem ele, uma chamada lenta pode prender recursos indefinidamente.
- **Retry:** repete uma operação quando a falha pode ser temporária. Repetir demais aumenta a sobrecarga, portanto as tentativas são limitadas.
- **Circuit breaker:** depois de falhas sucessivas, interrompe chamadas por um período. É como um disjuntor elétrico protegendo o restante do sistema.

No cenário de timeout, o Split leva 2,5 segundos, mas o limite de leitura é 1 segundo. O retry tenta novamente e o circuit breaker pode abrir por 10 segundos.

Uma consideração importante: retries em operações que alteram dinheiro exigem idempotência. Caso contrário, “tentar novamente” pode duplicar efeitos.

## 6. Saga e compensação

Não existe uma transação única que faça rollback automático em bancos de serviços diferentes. A Saga mantém o estado da operação distribuída.

Se a transferência falhar depois de ser persistida, o orquestrador solicita uma **compensação**. Compensar não apaga a história: é uma nova ação de negócio que neutraliza o efeito anterior, semelhante a um estorno.

Por isso o resultado é `COMPENSATED`, e não `COMPLETED` nem simplesmente `FAILED`.

## 7. Correlation ID

Uma requisição passa por processos e arquivos de log diferentes. O `Correlation ID` é o número de protocolo comum a todos eles.

Para acompanhar uma execução:

```bash
docker compose logs orchestrator-service payment-service split-service transfer-service | grep SEU_CORRELATION_ID
```

Em produção, esse identificador normalmente seria combinado com logs estruturados, métricas e tracing distribuído.

## 8. O que os testes provam

Os testes unitários usam JUnit 5 e Mockito. O MongoDB não é acessado: os Repositories e clientes HTTP são substituídos por mocks, permitindo validar regra e interação isoladamente.

Exemplos importantes no `PaymentServiceTest`:

- valor positivo cria e salva um pagamento;
- valor zero ou negativo é rejeitado e `save()` não é chamado;
- transação existente é devolvida e nenhum novo pagamento é persistido.

Isso prova o comportamento do Service, mas não substitui testes de integração. Cada tipo de teste responde a uma pergunta diferente.

## 9. Respostas curtas para uma entrevista

**Como diagnosticar um timeout?**

Eu começaria pelo correlation ID e verificaria métricas e logs nas duas pontas para separar lentidão, indisponibilidade e problema de rede. Confirmaria os timeouts configurados, a duração real da dependência e o efeito dos retries. Depois corrigiria a causa e ajustaria timeout, retry e circuit breaker com base em dados, preservando idempotência.

**Como tratar falha na orquestração?**

Eu persistiria o estado de cada etapa da Saga. Se a falha ocorrer antes de um efeito financeiro, marco a operação como falha e permito recuperação segura. Se algo já foi efetivado, executo uma ação compensatória idempotente. Também mantenho observabilidade e mecanismos para retomar operações interrompidas.

**Por que usar injeção de dependência?**

Porque a classe declara do que depende sem criar a implementação. Isso reduz acoplamento, centraliza a construção dos objetos no Spring e facilita testes com mocks. A injeção por construtor ainda torna as dependências obrigatórias e explícitas.

## 10. Limites e próximos passos

Este é um laboratório síncrono e local. Uma evolução realista incluiria mensageria, outbox transacional, tracing com OpenTelemetry, métricas, autenticação e testes de contrato. Esses itens ficaram fora desta versão para que os conceitos centrais continuem visíveis e explicáveis.
