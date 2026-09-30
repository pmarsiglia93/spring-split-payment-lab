import React, { useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const scenarios = [
  { value: 'NONE', label: 'Fluxo saudável', expected: 'COMPLETED', detail: 'Todos os serviços respondem e a Saga é concluída.' },
  { value: 'SPLIT_TIMEOUT', label: 'Timeout + retry', expected: 'FAILED', detail: 'O split demora 2,5s; o cliente desiste em 1s e tenta novamente.' },
  { value: 'TRANSFER_FAILURE', label: 'Falha + compensação', expected: 'COMPENSATED', detail: 'A transferência é persistida, falha e depois é compensada.' }
]

const CIRCUIT_COOLDOWN_MS = 10_000
const CIRCUIT_STORAGE_KEY = 'splitCircuitCooldownUntil'

const serviceRoles = [
  { name: 'Frontend', icon: '01', role: 'Coleta os dados e mostra o resultado. Só conversa com o orquestrador.' },
  { name: 'Orchestrator', icon: '02', role: 'É o gerente: chama os serviços na ordem certa e decide como reagir às falhas.' },
  { name: 'Payment', icon: '03', role: 'Registra o pagamento e controla seu estado: criado, processando ou concluído.' },
  { name: 'Split', icon: '04', role: 'Funciona como calculadora: separa 90% para o vendedor e 10% para a plataforma.' },
  { name: 'Transfer', icon: '05', role: 'Registra o repasse e sabe executar uma compensação quando algo dá errado.' }
]

const glossary = [
  ['Saga', 'Registro que acompanha todas as etapas de uma operação distribuída.'],
  ['Timeout', 'Limite de tempo que um serviço aceita esperar pela resposta de outro.'],
  ['Retry', 'Nova tentativa automática depois de uma falha considerada temporária.'],
  ['Circuit breaker', 'Disjuntor que bloqueia temporariamente chamadas para um serviço que está falhando.'],
  ['Idempotência', 'Garantia de que repetir a mesma solicitação não repete o efeito financeiro.'],
  ['Compensação', 'Nova operação que neutraliza algo já realizado, como um estorno.'],
  ['Correlation ID', 'Número de protocolo usado para encontrar a mesma requisição nos logs de vários serviços.']
]

const faqEntries = [
  {
    question: 'Como você investigaria e corrigiria uma vulnerabilidade?',
    answer: 'Eu identificaria o componente e a versão afetada, confirmaria se a falha é alcançável, priorizaria por risco e impacto, aplicaria a correção, executaria os testes e monitoraria a publicação.',
    evidence: 'O repositório usa Dependabot, CodeQL, validação de entrada e respostas de erro sem expor stack trace.',
    limit: 'Em produção ainda seriam necessários autenticação, TLS, gestão de secrets, rate limiting e testes dinâmicos.'
  },
  {
    question: 'O que foi utilizado para testes automatizados?',
    answer: 'JUnit 5 e Mockito testam regras isoladas; MockMvc valida HTTP; Testcontainers usa MongoDB real descartável; k6 mede carga e concorrência; Vite valida o frontend.',
    evidence: 'A suíte Java e o build React rodam no GitHub Actions; os testes k6 são manuais porque desempenho depende do ambiente.',
    limit: 'Playwright ou Cypress poderiam cobrir a interface, Pact os contratos e OWASP ZAP os testes dinâmicos de segurança.'
  },
  {
    question: 'Como é feita uma consulta no banco de dados?',
    answer: 'O Controller recebe o HTTP, o Service aplica a regra e o Repository consulta o MongoDB. O Controller nunca acessa o banco diretamente.',
    evidence: 'O método findByTransactionId do PaymentRepository é interpretado pelo Spring Data e procura o documento pelo transactionId.',
    limit: 'Consultas reais também precisam considerar índices, paginação, limites e autorização por proprietário.'
  },
  {
    question: 'Este projeto usa arquitetura hexagonal?',
    answer: 'Não. Ele usa arquitetura em camadas: Controller → Service → Repository. Foi uma escolha consciente para deixar os fundamentos do Spring mais visíveis.',
    evidence: 'Os pacotes controller, service e repository tornam essa separação explícita em cada microsserviço.',
    limit: 'Na hexagonal, o domínio define portas e HTTP e MongoDB são adaptadores. Isso aumenta o isolamento, mas também a complexidade.'
  },
  {
    question: 'Como você criaria um novo endpoint?',
    answer: 'Eu definiria primeiro o caso de uso e o contrato HTTP; depois criaria DTOs e validações, Controller, regra no Service, acesso no Repository se necessário, tratamento de erros, testes e documentação.',
    evidence: 'GET /payments/{transactionId} demonstra esse caminho e devolve 200 quando encontra ou 404 por meio do GlobalExceptionHandler.',
    limit: 'Um endpoint de produção também exige autorização, observabilidade, versionamento e avaliação de compatibilidade.'
  },
  {
    question: 'Como o sistema lida com timeout entre microsserviços?',
    answer: 'O orquestrador limita quanto tempo espera pelo Split. Uma falha temporária pode receber novas tentativas; falhas repetidas abrem o circuit breaker e interrompem chamadas por alguns segundos.',
    evidence: 'O cenário “Timeout + retry” permite observar esse comportamento e o correlation ID ajuda a seguir a requisição nos logs.',
    limit: 'Timeout, retry e circuit breaker limitam o impacto; eles não corrigem a causa de um serviço lento.'
  },
  {
    question: 'Como tratar uma falha na orquestração?',
    answer: 'A Saga registra cada etapa. Se a falha ocorrer antes de um efeito financeiro, termina como FAILED. Se algo já foi registrado, o orquestrador solicita uma compensação e termina como COMPENSATED.',
    evidence: 'O cenário “Falha + compensação” mostra a transferência falhar e uma nova operação neutralizar o efeito.',
    limit: 'Compensação não é rollback mágico entre bancos; precisa ser auditável e idempotente.'
  },
  {
    question: 'Como evitar pagamentos duplicados?',
    answer: 'A mesma idempotency key representa a mesma intenção. Repeti-la devolve a Saga existente em vez de criar outro efeito financeiro.',
    evidence: 'O Service verifica a chave e o MongoDB possui índice único para proteger também contra requisições concorrentes.',
    limit: 'Em produção, a violação de unicidade precisa ser tratada para recuperar deterministicamente a operação vencedora.'
  },
  {
    question: 'Como este projeto poderia escalar?',
    answer: 'Os serviços podem ter várias instâncias atrás de um balanceador porque não dependem de sessão local. Primeiro, porém, é necessário medir o gargalo real.',
    evidence: 'As responsabilidades já estão separadas, permitindo escalar apenas o serviço que estiver sob maior carga.',
    limit: 'O laboratório já mede carga com k6 e protege a concorrência com índices únicos e optimistic locking. Dashboards históricos, tracing, alta disponibilidade e mensageria continuam condicionados a uma necessidade real.'
  },
  {
    question: 'O projeto está pronto para produção?',
    answer: 'Não. Ele é um laboratório didático criado para demonstrar conceitos e decisões arquiteturais de forma reproduzível.',
    evidence: 'Ele demonstra camadas, testes, MongoDB, comunicação, resiliência, idempotência, Saga e correlation ID.',
    limit: 'Faltam controles operacionais e de segurança como autenticação, autorização, secrets, TLS, auditoria e observabilidade completa.'
  }
]

const explanations = {
  NONE: { title: 'Todos os serviços responderam', text: 'O pagamento foi criado, o valor foi dividido em 90/10 e a transferência foi registrada. A Saga terminou sem precisar corrigir nada.', lesson: 'Caminho feliz: cada serviço executou apenas sua responsabilidade.' },
  SPLIT_TIMEOUT: { title: 'O serviço de split demorou além do limite', text: 'O orquestrador esperou 1 segundo por uma operação que demora 2,5 segundos. Depois das tentativas, o circuit breaker passou a bloquear novas chamadas temporariamente.', lesson: 'Timeout evita espera infinita; retry tenta recuperar; circuit breaker evita insistência excessiva.' },
  TRANSFER_FAILURE: { title: 'A transferência falhou depois de ser registrada', text: 'Como já existia um efeito financeiro, apenas marcar erro não seria suficiente. O orquestrador solicitou uma compensação e preservou todo o histórico.', lesson: 'Compensar é uma nova operação de negócio — não um rollback mágico entre bancos.' }
}

function newIdentifiers() {
  const suffix = Date.now().toString().slice(-7)
  return { transactionId: `tx-${suffix}`, idempotencyKey: `key-${crypto.randomUUID()}` }
}

function App() {
  const [ids, setIds] = useState(newIdentifiers)
  const [amount, setAmount] = useState('100.00')
  const [simulation, setSimulation] = useState('NONE')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [elapsedMs, setElapsedMs] = useState(null)
  const [cooldownUntil, setCooldownUntil] = useState(() => Number(window.localStorage.getItem(CIRCUIT_STORAGE_KEY)) || 0)
  const [clock, setClock] = useState(Date.now)
  const [resilienceStatus, setResilienceStatus] = useState(null)
  const cooldownMs = Math.max(0, cooldownUntil - clock)
  const cooldownSeconds = Math.ceil(cooldownMs / 1000)
  const circuitProtected = cooldownMs > 0

  useEffect(() => {
    if (!cooldownUntil) return undefined
    function updateClock() {
      const currentTime = Date.now()
      setClock(currentTime)
      if (currentTime >= cooldownUntil) {
        window.localStorage.removeItem(CIRCUIT_STORAGE_KEY)
      }
    }
    updateClock()
    const timer = window.setInterval(updateClock, 250)
    return () => window.clearInterval(timer)
  }, [cooldownUntil])

  useEffect(() => {
    loadResilienceStatus()
    if (!circuitProtected) return undefined
    const statusTimer = window.setInterval(loadResilienceStatus, 1000)
    return () => window.clearInterval(statusTimer)
  }, [circuitProtected])

  function activateCircuitCooldown() {
    const until = Date.now() + CIRCUIT_COOLDOWN_MS
    window.localStorage.setItem(CIRCUIT_STORAGE_KEY, String(until))
    setCooldownUntil(until)
    setClock(Date.now())
  }

  async function loadResilienceStatus() {
    try {
      const response = await fetch('/api/lab/resilience/split')
      if (response.ok) setResilienceStatus(await response.json())
    } catch {
      // O laboratório continua utilizável mesmo durante a inicialização do orquestrador.
    }
  }

  async function run(event) {
    event.preventDefault()
    setLoading(true)
    setError('')
    setResult(null)
    setElapsedMs(null)
    const startedAt = performance.now()
    try {
      const response = await fetch('/api/payment-flows', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...ids, amount: Number(amount), simulation })
      })
      const data = await response.json()
      if (!response.ok) throw new Error(data.error || 'Não foi possível executar o fluxo')
      setResult(data)
      if (data.failureReason?.includes("CircuitBreaker 'splitService' is OPEN")) {
        activateCircuitCooldown()
      }
      loadResilienceStatus()
    } catch (cause) {
      setError(cause.message)
    } finally {
      setElapsedMs(performance.now() - startedAt)
      setLoading(false)
    }
  }

  async function findExisting() {
    if (!ids.transactionId) return
    setLoading(true)
    setError('')
    setElapsedMs(null)
    try {
      const response = await fetch(`/api/payment-flows/${encodeURIComponent(ids.transactionId)}`)
      const data = await response.json()
      if (!response.ok) throw new Error(data.error || 'Transação não encontrada')
      setResult(data)
    } catch (cause) {
      setError(cause.message)
    } finally {
      setLoading(false)
    }
  }

  function reset() {
    setIds(newIdentifiers())
    setResult(null)
    setError('')
    setElapsedMs(null)
  }

  return <main>
    <header className="hero">
      <p className="eyebrow">JAVA 21 · SPRING BOOT · REACT</p>
      <h1>Split Payment <span>Resilience Lab</span></h1>
      <p className="subtitle">Execute falhas reais e acompanhe como timeout, retry, circuit breaker,
        idempotência e compensação afetam uma Saga distribuída.</p>
      <div className="architecture">
        {['Frontend', 'Orchestrator', 'Payment', 'Split', 'Transfer'].map((item, index) =>
          <React.Fragment key={item}><div>{item}</div>{index < 4 && <b>→</b>}</React.Fragment>)}
      </div>
      <a className="faq-shortcut" href="#faq">Ver FAQ da entrevista <span>↓</span></a>
    </header>

    <section className="purpose">
      <p className="eyebrow">POR QUE ESTE PROJETO EXISTE?</p>
      <h2>Um laboratório para transformar perguntas de entrevista em experimentos.</h2>
      <p>Uma compra parece simples, mas atravessa vários serviços. Aqui você provoca falhas de propósito,
        observa a reação do sistema e entende por que resiliência não é apenas “tentar de novo”.</p>
      <div className="purpose-goals">
        <span><b>1</b> Escolha um cenário</span>
        <span><b>2</b> Execute a Saga</span>
        <span><b>3</b> Compare causa e resultado</span>
      </div>
    </section>

    <section className="workspace">
      <form className="panel" onSubmit={run}>
        <div className="panel-title"><span>01</span><div><h2>Configure o experimento</h2><p>Cada cenário executa chamadas reais entre contêineres.</p></div></div>
        <label>Transaction ID<input value={ids.transactionId} onChange={e => setIds({ ...ids, transactionId: e.target.value })} />
          <small className="field-help">Identifica a operação de negócio do início ao fim.</small></label>
        <label>Valor do pagamento<input type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} /></label>
        <label>Idempotency key<input value={ids.idempotencyKey} onChange={e => setIds({ ...ids, idempotencyKey: e.target.value })} />
          <small className="field-help">Repita esta chave para provar que o efeito não será duplicado.</small></label>
        <div className="scenario-heading"><strong>Escolha o comportamento</strong><span>falhas intencionais</span></div>
        <div className="scenario-grid">
          {scenarios.map(item => <button type="button" key={item.value}
            className={simulation === item.value ? 'scenario active' : 'scenario'}
            onClick={() => setSimulation(item.value)}>
            <div><strong>{item.label}</strong><em>esperado: {item.expected}</em></div><small>{item.detail}</small>
          </button>)}
        </div>
        <p className="experiment-tip">Dica: execute o timeout por último. O circuit breaker fica aberto por alguns segundos para proteger o sistema.</p>
        {circuitProtected && <div className="circuit-cooldown" role="status" aria-live="polite">
          <div className="cooldown-message"><i></i><div><strong>Circuit breaker protegendo o Split · {cooldownSeconds}s</strong>
            <p>Aguarde a recuperação antes de executar outra Saga. Consultas continuam disponíveis.</p></div></div>
          <div className="cooldown-progress" aria-hidden="true"><span style={{ width: Math.min(100, (cooldownMs / CIRCUIT_COOLDOWN_MS) * 100) + '%' }}></span></div>
        </div>}
        <div className="actions">
          <button className="primary" disabled={loading || circuitProtected}>{loading ? 'Executando…' : circuitProtected ? 'Aguarde ' + cooldownSeconds + 's' : 'Executar Saga'}</button>
          <button type="button" className="secondary" onClick={findExisting} disabled={loading}>Consultar</button>
          <button type="button" className="ghost" onClick={reset}>Nova ID</button>
        </div>
        {loading && <div className="running"><i></i><div><strong>Microsserviços conversando…</strong><p>O orquestrador está coordenando as etapas e registrando o estado da Saga.</p></div></div>}
        {error && <p className="error">{error}</p>}
      </form>

      <section className="panel result-panel">
        <div className="panel-title"><span>02</span><div><h2>Resultado da orquestração</h2><p>Estado persistido para diagnóstico e recuperação.</p></div></div>
        {!result && <div className="empty"><div>◇</div><p>Execute um cenário para visualizar a Saga.</p></div>}
        {result && <Result saga={result} simulation={simulation} elapsedMs={elapsedMs} />}
      </section>
    </section>

    <section className="learning-section">
      <div className="section-heading"><p className="eyebrow">MAPA DE RESPONSABILIDADES</p><h2>Quem faz o quê?</h2>
        <p>Cada serviço tem uma responsabilidade pequena. Isso reduz acoplamento, mas cria o desafio da comunicação pela rede.</p></div>
      <div className="service-grid">
        {serviceRoles.map(service => <article className="service-card" key={service.name}>
          <span>{service.icon}</span><h3>{service.name}</h3><p>{service.role}</p>
        </article>)}
      </div>
    </section>

    <section className="learning-section glossary-section">
      <div className="section-heading"><p className="eyebrow">GLOSSÁRIO SEM “TECNIQUÊS”</p><h2>Conceitos usados no laboratório</h2></div>
      <div className="glossary-grid">
        {glossary.map(([term, meaning]) => <article key={term}><h3>{term}</h3><p>{meaning}</p></article>)}
      </div>
    </section>

    <section className="learning-section scalability-section" id="scalability">
      <div className="section-heading"><p className="eyebrow">ESCALABILIDADE COM EVIDÊNCIAS</p><h2>Crescer sem perder consistência</h2>
        <p>Antes de adicionar infraestrutura, o laboratório protege a concorrência, mede o comportamento atual e deixa explícito quando uma solução mais complexa seria justificável.</p></div>
      <div className="resilience-monitor">
        <div><span>RESILIENCE4J · SPLIT SERVICE</span><h3>Estado observado do circuit breaker</h3>
          <p>Contadores sanitizados do processo atual. Nenhum dado financeiro ou configuração sensível é exposto.</p></div>
        <strong className={'circuit-state ' + (resilienceStatus?.circuitState || 'unknown').toLowerCase()}>{resilienceStatus?.circuitState || 'CARREGANDO'}</strong>
        <dl>
          <div><dt>Chamadas avaliadas</dt><dd>{resilienceStatus?.bufferedCalls ?? '—'}</dd></div>
          <div><dt>Falhas</dt><dd>{resilienceStatus?.failedCalls ?? '—'}</dd></div>
          <div><dt>Bloqueadas</dt><dd>{resilienceStatus?.notPermittedCalls ?? '—'}</dd></div>
          <div><dt>Taxa de falha</dt><dd>{resilienceStatus && resilienceStatus.failureRate >= 0 ? resilienceStatus.failureRate.toFixed(0) + '%' : 'sem amostra'}</dd></div>
        </dl>
      </div>
      <div className="scaling-grid">
        <article><span>01 · CONSISTÊNCIA</span><h3>Concorrência protegida</h3><p>Índices únicos resolvem a corrida de criação; optimistic locking impede que uma atualização antiga sobrescreva a mais recente.</p></article>
        <article><span>02 · CAPACIDADE</span><h3>Medir antes de replicar</h3><p>O roteiro k6 mede taxa de erro e latência p95. Réplicas só fazem sentido depois que um gargalo real for identificado.</p></article>
        <article><span>03 · ESCALA HORIZONTAL</span><h3>Serviços sem sessão local</h3><p>As instâncias podem crescer atrás de um balanceador, mantendo a identidade financeira protegida no banco compartilhado.</p></article>
        <article className="boundary-card"><span>04 · LIMITE CONSCIENTE</span><h3>Complexidade sob demanda</h3><p>Kafka, Kubernetes e sharding permanecem fora do laboratório até volume, disponibilidade ou assincronia justificarem o custo.</p></article>
      </div>
    </section>

    <section className="learning-section faq-section" id="faq">
      <div className="section-heading"><p className="eyebrow">FAQ DA ENTREVISTA TÉCNICA</p><h2>Como eu responderia hoje?</h2>
        <p>Abra cada pergunta para ver uma resposta objetiva, a evidência prática no projeto e o que ainda faltaria em um ambiente de produção.</p></div>
      <div className="answer-framework">
        <span>ROTEIRO PARA RESPONDER</span>
        <p><b>1.</b> Explique o conceito · <b>2.</b> Mostre uma decisão concreta · <b>3.</b> Reconheça limites e próximos passos</p>
      </div>
      <div className="faq-list">
        {faqEntries.map((item, index) => <details key={item.question} open={index === 0}>
          <summary><span>{String(index + 1).padStart(2, '0')}</span>{item.question}<i>+</i></summary>
          <div className="faq-answer">
            <div><strong>Resposta curta</strong><p>{item.answer}</p></div>
            <div><strong>Como o projeto demonstra</strong><p>{item.evidence}</p></div>
            <div className="production-note"><strong>Limite / produção</strong><p>{item.limit}</p></div>
          </div>
        </details>)}
      </div>
    </section>

    <footer>Projeto de estudo · falhas são intencionais e reproduzíveis</footer>
  </main>
}

function Result({ saga, simulation, elapsedMs }) {
  const effectiveScenario = saga.status === 'COMPLETED'
    ? 'NONE'
    : saga.status === 'COMPENSATED' ? 'TRANSFER_FAILURE' : saga.status === 'FAILED' ? 'SPLIT_TIMEOUT' : simulation
  const explanation = explanations[effectiveScenario]
  const failedAtSplit = saga.status === 'FAILED'
  const compensated = saga.status === 'COMPENSATED'
  const steps = [
    { label: 'Pagamento registrado', detail: 'Payment Service', state: 'done' },
    { label: failedAtSplit ? 'Split excedeu o tempo' : 'Split calculado em 90/10', detail: 'Split Service', state: failedAtSplit ? 'error' : 'done' },
    { label: failedAtSplit ? 'Transferência não iniciada' : compensated ? 'Transferência falhou' : 'Transferência concluída', detail: 'Transfer Service', state: failedAtSplit ? 'skipped' : compensated ? 'error' : 'done' },
    { label: compensated ? 'Efeitos compensados' : 'Compensação desnecessária', detail: 'Orchestrator', state: compensated ? 'done' : 'skipped' }
  ]
  return <div className="result">
    <div className="result-header"><div className={`status ${saga.status.toLowerCase()}`}>{saga.status}</div>
      {elapsedMs !== null && <span className="elapsed">observado em {(elapsedMs / 1000).toFixed(2)}s</span>}</div>
    <div className="plain-summary"><strong>{explanation.title}</strong><p>{explanation.text}</p></div>
    <div className="timeline">
      {steps.map(step => <div className={`step ${step.state}`} key={step.label}>
        <i>{step.state === 'done' ? '✓' : step.state === 'error' ? '!' : '–'}</i><span><strong>{step.label}</strong><small>{step.detail}</small></span>
      </div>)}
    </div>
    <dl>
      <div><dt>Transação</dt><dd>{saga.transactionId}</dd></div>
      <div><dt>Valor</dt><dd>R$ {Number(saga.amount).toFixed(2)}</dd></div>
      <div><dt>Etapa atual</dt><dd>{saga.currentStep}</dd></div>
      <div><dt>Correlation ID</dt><dd>{saga.correlationId}</dd></div>
      <div><dt>Versão da Saga</dt><dd>{saga.version ?? 'legada'}</dd></div>
    </dl>
    {saga.failureReason && <div className="failure"><strong>Falha técnica observada</strong><p>{saga.failureReason}</p></div>}
    <div className="lesson"><span>O que este cenário ensina</span><p>{explanation.lesson}</p></div>
  </div>
}

createRoot(document.getElementById('root')).render(<App />)
