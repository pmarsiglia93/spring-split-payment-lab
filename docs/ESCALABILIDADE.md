# Escalabilidade: decisões e limites

Escalabilidade neste laboratório significa preservar correção enquanto a concorrência aumenta. A ordem adotada é:

    medir → localizar o gargalo → proteger a consistência → aplicar a menor mudança → medir novamente

## O que está implementado

### Criação concorrente

transactionId e idempotencyKey possuem índices únicos. A aplicação também trata DuplicateKeyException: quando duas requisições tentam inserir a mesma intenção, a perdedora recupera e devolve o registro vencedor.

A validação compara transação, chave e valor. Reutilizar um identificador com dados diferentes retorna conflito em vez de esconder uma inconsistência.

### Optimistic locking

Payment e Saga possuem um campo @Version. Cada atualização inclui a versão lida. Se outra requisição gravou uma versão mais nova, o MongoDB rejeita a atualização obsoleta.

Isso evita o problema de “última escrita vence” apagando silenciosamente um estado mais recente.

### Observabilidade de resiliência

GET /api/lab/resilience/split devolve somente estado e contadores do circuit breaker e retry. O Actuator permanece restrito a health; dados financeiros, stack traces e configurações não são expostos.

### Testes

- Testcontainers valida índices e locking contra MongoDB real.
- Mockito reproduz a corrida em que outra requisição vence o insert.
- k6 mede o fluxo saudável e dispara dez requisições simultâneas com a mesma identidade.

Consulte [performance/README.md](../performance/README.md).

### Baseline local de referência

Em 30 de setembro de 2026, com cinco usuários virtuais durante 15 segundos nesta máquina de desenvolvimento:

- 264 Sagas concluídas;
- 0% de erros HTTP;
- latência p95 de 176 ms;
- dez requisições simultâneas com a mesma identidade convergiram para uma única Saga.

Esse resultado prova que o roteiro funciona e cria um ponto de comparação. Não representa SLA ou capacidade de produção.

## Como escalar horizontalmente

Os serviços não guardam sessão de usuário em memória, portanto podem receber réplicas atrás de um balanceador. Antes de fazer isso:

1. executar o teste de carga e registrar o baseline;
2. identificar qual serviço ou dependência satura primeiro;
3. confirmar que a identidade e o locking funcionam entre instâncias;
4. adicionar réplicas somente ao componente necessário;
5. repetir o mesmo teste e comparar throughput, p95 e erros.

Cada réplica do orquestrador mantém seu próprio circuit breaker. Isso é intencional: a proteção é local ao processo. Métricas agregadas são necessárias para enxergar o conjunto.

## Limites atuais

- um único contêiner MongoDB é ponto único de falha;
- comunicação síncrona soma a latência dos serviços;
- não existe autenticação ou autorização;
- não há tracing distribuído nem dashboard histórico;
- compensações que falham exigiriam recuperação operacional;
- o teste local não representa capacidade de produção.

## Quando aumentar a complexidade

| Tecnologia | Sinal que justificaria |
|---|---|
| Réplicas + balanceador | CPU/saturação comprovada e serviço stateless |
| MongoDB Replica Set | requisito de alta disponibilidade |
| Mensageria + Outbox | processamento assíncrono ou garantia de publicação |
| Cache | leitura repetida e cara comprovada por métricas |
| Kubernetes | várias cargas, necessidade operacional de rollout e autoscaling |
| Sharding | volume que não cabe ou não performa em um único cluster |

Sem esses sinais, adicionar a tecnologia aumenta custo, superfície de falha e dificuldade de diagnóstico sem provar benefício.
