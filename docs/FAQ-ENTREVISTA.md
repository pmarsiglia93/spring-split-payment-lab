# FAQ da entrevista técnica

Este FAQ registra as perguntas que motivaram o laboratório. Cada resposta separa três coisas: a explicação curta para uma entrevista, a evidência existente no projeto e o que ainda seria necessário em produção.

## Como você investigaria e corrigiria uma vulnerabilidade?

### Resposta curta

Eu começaria identificando o componente e a versão afetada, confirmaria se a vulnerabilidade é realmente alcançável no contexto da aplicação e priorizaria por severidade, exposição e impacto. Em seguida atualizaria ou mitigaria o componente, executaria testes automatizados, publicaria a correção e monitoraria. A decisão e o risco residual também devem ser registrados.

```text
detectar → confirmar → priorizar → corrigir → testar → publicar → monitorar
```

Não basta dizer “eu atualizaria a biblioteca”. Uma CVE crítica em um código não utilizado pode ter prioridade diferente de uma falha explorável em um endpoint público.

### Como este repositório demonstra isso

- **Dependabot:** procura versões novas para Maven, npm, GitHub Actions e Docker.
- **CodeQL:** faz análise estática de Java e JavaScript em pushes, pull requests e semanalmente.
- **CI:** recompila o frontend e executa os testes a cada alteração.
- **Validação:** DTOs rejeitam entradas inválidas antes da regra de negócio.
- **Erros controlados:** exceptions são convertidas em respostas HTTP sem stack trace para o cliente.

### O que faltaria em produção

- autenticação e autorização;
- TLS e gestão de secrets;
- scan das imagens com Trivy;
- teste dinâmico com OWASP ZAP;
- proteção contra abuso e rate limiting;
- política de atualização, auditoria e resposta a incidentes.

## O que foi utilizado para testes automatizados?

### Resposta curta

Usei ferramentas diferentes porque cada nível responde a uma pergunta diferente:

| Nível | Ferramenta | O que comprova |
|---|---|---|
| Unitário | JUnit 5 + Mockito | Regra do Service isolada e interações com dependências |
| Web | MockMvc + Mockito | Contrato HTTP, validação, JSON, headers e status codes |
| Integração | Testcontainers + MongoDB | Repository e índices contra o mesmo banco usado pela aplicação |
| Build frontend | Vite | Código React pode ser empacotado para produção |
| Pipeline | GitHub Actions | Verificações são repetidas automaticamente a cada mudança |

Os testes unitários são rápidos e não acessam banco. O teste de integração inicia um MongoDB descartável em uma porta aleatória, executa as consultas e remove o contêiner ao terminar.

### Outras ferramentas possíveis

- WireMock para simular APIs externas;
- Pact para contratos entre microsserviços;
- Playwright ou Cypress para testes de interface;
- k6 ou Gatling para carga e concorrência;
- OWASP ZAP para testes dinâmicos de segurança.

Ter muitos testes não garante qualidade. É necessário testar comportamentos relevantes, evitar dependência entre testes e saber qual risco cada teste cobre.

## Como é feita uma consulta no banco de dados?

### Resposta curta

O Controller não acessa o banco diretamente. Ele chama o Service, que aplica as regras e usa o Repository:

```text
GET /payments/{transactionId}
        ↓
PaymentController
        ↓
PaymentService
        ↓
PaymentRepository
        ↓
MongoDB
```

No Spring Data MongoDB, o Repository declara:

```java
Optional<Payment> findByTransactionId(String transactionId);
```

O Spring interpreta o nome do método e cria a consulta equivalente a:

```javascript
db.payments.findOne({ transactionId: "tx-123" })
```

`transactionId` possui índice único. Isso melhora a busca e impede que duas operações com o mesmo identificador sejam persistidas. O teste `PaymentRepositoryIntegrationTest` comprova a consulta e a restrição no MongoDB real.

Consultas que retornam coleções também deveriam considerar paginação, índices, projeções e limites. Em uma aplicação com usuários, o filtro de proprietário deve fazer parte da consulta para evitar vazamento de dados.

## Este projeto usa arquitetura hexagonal?

### Resposta curta

Não. Atualmente ele utiliza arquitetura em camadas, com `Controller → Service → Repository`. Essa foi uma decisão consciente para deixar fundamentos do Spring e as responsabilidades visíveis.

Na arquitetura hexagonal, o núcleo não depende diretamente de HTTP ou MongoDB. Ele define portas, e a infraestrutura fornece adaptadores:

```text
Adaptador REST
      ↓
Porta de entrada / caso de uso
      ↓
Domínio
      ↓
Porta de saída
      ↓
Adaptador MongoDB
```

Uma possível estrutura seria:

```text
domain/
application/
  port/in/
  port/out/
  service/
adapter/
  in/web/
  out/mongodb/
```

O benefício é proteger regras de negócio de detalhes externos e facilitar a troca de adaptadores. O custo é mais interfaces, mapeamentos e conceitos. Para este laboratório pequeno, migrar todos os serviços agora aumentaria a complexidade sem benefício proporcional. Uma evolução didática possível é refatorar somente o `payment-service` e comparar os dois modelos.

## Como é criado um novo endpoint?

### Resposta curta

Eu começo pelo contrato, não pelo Controller:

1. definir o caso de uso e quem pode executá-lo;
2. escolher método, caminho, entrada, saída e status HTTP;
3. criar DTOs e validações;
4. expor o método no Controller;
5. implementar a regra no Service;
6. adicionar consulta no Repository somente se necessária;
7. mapear erros esperados;
8. criar testes unitários e HTTP;
9. documentar e observar o endpoint.

O endpoint `GET /payments/{transactionId}` é um exemplo. Ele recebe o identificador no Controller, delega a busca ao Service, consulta o Repository e devolve `200 OK`. Quando não existe pagamento, `PaymentNotFoundException` é convertida em `404 Not Found` pelo `GlobalExceptionHandler`.

O `PaymentControllerTest` valida esse contrato sem iniciar servidor ou banco. Esse isolamento permite saber se uma falha está no protocolo HTTP ou na persistência.

## Como o sistema lida com timeout entre microsserviços?

O orquestrador limita o tempo de espera pelo Split Service. Uma falha temporária pode ser repetida com backoff; falhas consecutivas abrem o circuit breaker e impedem novas chamadas por um período.

Antes de mudar configurações, eu procuraria o correlation ID nos logs e compararia duração, taxa de erro e saturação nas duas pontas. Timeout, retry e circuit breaker não corrigem um serviço lento: eles limitam o impacto da lentidão no restante do sistema.

Retry só deve ser usado quando repetir for seguro. Por isso o cálculo do split pode ter retry, enquanto uma transferência financeira exige idempotência e uma estratégia mais cuidadosa.

## Como tratar uma falha na orquestração?

A Saga persiste a etapa atual. Se a falha acontecer antes de um efeito financeiro, a operação pode terminar como `FAILED`. Se uma transferência já foi registrada, o orquestrador executa uma ação compensatória e termina como `COMPENSATED`.

Compensação não é apagar registro nem executar rollback entre bancos. É uma nova operação de negócio, auditável e idealmente idempotente, que neutraliza o efeito anterior.

## Como evitar pagamentos duplicados?

A aplicação recebe uma `idempotencyKey`. Repetir a mesma intenção devolve a Saga já criada, enquanto reutilizar a chave com dados diferentes gera `409 Conflict`.

Além da verificação no Service, o banco precisa de índice único, pois duas requisições simultâneas podem consultar antes de qualquer uma salvar. Em produção, a aplicação também deveria tratar `DuplicateKeyException` e recuperar a operação vencedora de forma determinística.

## Como este projeto poderia escalar?

Os serviços devem permanecer sem sessão local para permitir múltiplas instâncias atrás de um balanceador. Antes de simplesmente adicionar réplicas, seria necessário proteger a Saga contra processamento concorrente, medir gargalos e testar carga.

Evoluções planejadas:

- teste concorrente com a mesma chave de idempotência;
- optimistic locking ou claim atômico da Saga;
- rate limiting e bulkhead;
- métricas e tracing distribuído;
- mensageria e Outbox Pattern quando houver necessidade real de processamento assíncrono.

## O projeto está pronto para produção?

Não. Ele é um laboratório intencionalmente pequeno para estudar decisões. Ainda faltam autenticação, autorização por proprietário, secrets, TLS, auditoria de segurança, observabilidade completa, recuperação operacional e testes de carga.

Reconhecer esses limites faz parte da solução. A qualidade de uma arquitetura também depende de saber quais riscos foram aceitos e quais serão tratados na próxima etapa.
