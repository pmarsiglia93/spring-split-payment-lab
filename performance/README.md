# Testes de capacidade e concorrência

Os scripts k6 respondem perguntas diferentes:

| Script | Pergunta |
|---|---|
| payment-flow-load.js | O fluxo saudável mantém baixa taxa de erro e latência p95 aceitável sob carga moderada? |
| idempotency-race.js | Dez requisições simultâneas com a mesma identidade produzem somente uma Saga? |

Eles não rodam automaticamente no CI porque desempenho depende da máquina e do ambiente. O CI continua responsável por testes determinísticos; carga deve ser executada em um ambiente controlado.

## Pré-requisitos

Suba a aplicação e aguarde o health check:

    docker compose up -d --build
    curl --retry 20 --retry-delay 2 --retry-all-errors http://localhost:8080/actuator/health

## Executar com Docker

Linux:

    docker run --rm --network host \
      -e BASE_URL=http://localhost:3000 \
      -v "$PWD/performance:/scripts:ro" \
      grafana/k6 run /scripts/idempotency-race.js

    docker run --rm --network host \
      -e BASE_URL=http://localhost:3000 \
      -e VUS=5 \
      -e DURATION=15s \
      -v "$PWD/performance:/scripts:ro" \
      grafana/k6 run /scripts/payment-flow-load.js

Docker Desktop:

    docker run --rm \
      -v "$PWD/performance:/scripts:ro" \
      grafana/k6 run /scripts/payment-flow-load.js

Os limites padrão são didáticos:

- menos de 1% de requisições HTTP com erro;
- mais de 99% dos checks aprovados;
- latência p95 abaixo de 2 segundos no fluxo saudável.

Esses números não são um SLA universal. Primeiro se mede o comportamento atual; depois se define uma meta ligada ao requisito de negócio e à infraestrutura disponível.

## Como interpretar

- **CPU alta no serviço:** avaliar otimização e réplicas horizontais.
- **Pool do MongoDB saturado:** revisar consultas, índices e conexões antes de adicionar instâncias.
- **p95 alto com CPU baixa:** investigar chamadas de rede e dependências.
- **erros de chave duplicada convertidos em uma única Saga:** proteção concorrente funcionando.
- **muitos retries:** risco de amplificação de carga; investigar a causa antes de aumentar tentativas.

Os testes criam dados descartáveis com prefixos load- e race-.
