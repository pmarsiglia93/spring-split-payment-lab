# spring-split-payment-lab

Laboratório incremental para estudar microsserviços com Java e Spring Boot.

Nesta primeira etapa, o repositório contém somente o `payment-service`. Orquestração,
split, transferências, retry, circuit breaker e Saga ainda não fazem parte do projeto.

## Pré-requisitos

- Java 21
- Maven 3.6.3 ou superior
- MongoDB acessível localmente ou por uma URI configurada

## Executar o MongoDB

Uma opção para desenvolvimento local é iniciar um contêiner:

```bash
docker run --name payment-mongodb -p 27017:27017 -d mongo:8
```

Por padrão, a aplicação usa `mongodb://localhost:27017/paymentdb`. Para usar outro
servidor, defina a variável `MONGODB_URI`.

## Executar o payment-service

```bash
cd payment-service
mvn spring-boot:run
```

O serviço ficará disponível em `http://localhost:8080`.

Criar um pagamento:

```bash
curl -i -X POST http://localhost:8080/payments \
  -H 'Content-Type: application/json' \
  -d '{
    "transactionId": "transaction-123",
    "amount": 150.00,
    "idempotencyKey": "payment-key-123"
  }'
```

Consultar pelo `transactionId`:

```bash
curl -i http://localhost:8080/payments/transaction-123
```

## Executar os testes

```bash
cd payment-service
mvn test
```

Os testes de `PaymentService` são unitários: o repositório é um mock do Mockito e
nenhuma instância real do MongoDB é acessada.

## Estrutura desta etapa

```text
payment-service/src/main/java/com/example/paymentservice/
├── controller/
├── dto/
├── exception/
├── model/
├── repository/
└── service/
```

Ao receber novamente um `transactionId` já salvo, o serviço retorna o pagamento
existente sem fazer um novo `save`. Esse comportamento é apenas a regra solicitada
nesta etapa; uma estratégia completa de idempotência será estudada posteriormente.
