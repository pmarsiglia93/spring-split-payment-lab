# Guia de estudo e entrevista

## 1. Camadas e injeção de dependência

O Controller é a fronteira HTTP. O Service contém regras de negócio e o Repository abstrai persistência. O Spring cria os beans e os conecta pelos construtores, reduzindo acoplamento e permitindo fornecer mocks nos testes.

No teste de `PaymentService`, o Repository é um mock. Assim, sucesso, valor inválido e transação existente são validados sem iniciar MongoDB.

## 2. Como diagnosticar um timeout

Timeout é um sintoma: o chamador não recebeu resposta no limite definido. A investigação deve separar timeout de conexão de timeout de leitura.

Roteiro:

1. Identificar a requisição pelo correlation ID.
2. Conferir logs no chamador e no serviço chamado.
3. Medir latência e taxa de erro.
4. Verificar banco, pools, threads, CPU, memória e rede.
5. Reproduzir com uma chamada controlada.
6. Confirmar se o limite é coerente com o SLA da operação.

Neste laboratório, o split demora 2,5s e o read timeout é 1s. O retry faz até três tentativas. Como repetir chamadas durante uma indisponibilidade aumenta a carga, o circuit breaker interrompe temporariamente novas chamadas.

Retry não deve ser automático para qualquer erro. Erros de validação não melhoram com uma nova tentativa. Operações com efeito financeiro precisam ser idempotentes antes de serem repetidas.

## 3. Falha na orquestração

Quando serviços anteriores concluíram e um serviço posterior falhou, retornar apenas `500` não desfaz os efeitos já realizados.

O orquestrador deste projeto:

1. Persiste a Saga como `PROCESSING`.
2. Cria o pagamento.
3. Calcula o split.
4. Solicita a transferência.
5. Se tudo funcionar, conclui a Saga.
6. Se a transferência falhar depois de persistida, executa compensação.
7. Persiste `COMPENSATED` ou `FAILED` para recuperação operacional.

Compensação é uma transação nova, como um estorno. Ela também pode falhar; nesse caso, a Saga permanece `FAILED` com o motivo registrado para intervenção.

## 4. Resposta curta para entrevista

> Eu começaria correlacionando os logs dos serviços e separando falha de conexão, lentidão de processamento e dependência de banco. Configuraria timeouts finitos e usaria retry somente em erros transitórios e operações seguras, com backoff. Um circuit breaker evitaria sobrecarregar um serviço indisponível. Em uma orquestração, persistiria o estado de cada etapa e, se uma etapa posterior falhasse, executaria compensações idempotentes nas etapas concluídas. Também manteria o estado observável para recuperação manual quando necessário.

## 5. Perguntas para praticar

- Por que timeout não é a causa raiz?
- Por que três retries podem produzir quatro vezes mais carga?
- Por que o transfer não possui retry automático neste projeto?
- Qual a diferença entre rollback e compensação?
- Por que a Saga precisa ter estado persistido?
- O que acontece se a própria compensação falhar?
- Como o correlation ID ajuda sem resolver o problema sozinho?
- Por que um índice único ainda exige tratamento de concorrência na aplicação?
