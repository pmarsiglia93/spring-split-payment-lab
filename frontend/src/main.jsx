import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const scenarios = [
  { value: 'NONE', label: 'Fluxo saudável', expected: 'COMPLETED', detail: 'Todos os serviços respondem e a Saga é concluída.' },
  { value: 'SPLIT_TIMEOUT', label: 'Timeout + retry', expected: 'FAILED', detail: 'O split demora 2,5s; o cliente desiste em 1s e tenta novamente.' },
  { value: 'TRANSFER_FAILURE', label: 'Falha + compensação', expected: 'COMPENSATED', detail: 'A transferência é persistida, falha e depois é compensada.' }
]

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
        <div className="actions">
          <button className="primary" disabled={loading}>{loading ? 'Executando…' : 'Executar Saga'}</button>
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
    </dl>
    {saga.failureReason && <div className="failure"><strong>Falha técnica observada</strong><p>{saga.failureReason}</p></div>}
    <div className="lesson"><span>O que este cenário ensina</span><p>{explanation.lesson}</p></div>
  </div>
}

createRoot(document.getElementById('root')).render(<App />)
