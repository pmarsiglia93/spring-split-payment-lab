import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const scenarios = [
  { value: 'NONE', label: 'Fluxo saudável', detail: 'Todos os serviços respondem e a Saga é concluída.' },
  { value: 'SPLIT_TIMEOUT', label: 'Timeout + retry', detail: 'O split demora 2,5s; o cliente desiste em 1s e tenta novamente.' },
  { value: 'TRANSFER_FAILURE', label: 'Falha + compensação', detail: 'A transferência é persistida, falha e depois é compensada.' }
]

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

  async function run(event) {
    event.preventDefault()
    setLoading(true)
    setError('')
    setResult(null)
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
      setLoading(false)
    }
  }

  async function findExisting() {
    if (!ids.transactionId) return
    setLoading(true)
    setError('')
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

    <section className="workspace">
      <form className="panel" onSubmit={run}>
        <div className="panel-title"><span>01</span><div><h2>Configure o experimento</h2><p>Cada cenário executa chamadas reais entre contêineres.</p></div></div>
        <label>Transaction ID<input value={ids.transactionId} onChange={e => setIds({ ...ids, transactionId: e.target.value })} /></label>
        <label>Valor do pagamento<input type="number" min="0.01" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} /></label>
        <label>Idempotency key<input value={ids.idempotencyKey} onChange={e => setIds({ ...ids, idempotencyKey: e.target.value })} /></label>
        <div className="scenario-grid">
          {scenarios.map(item => <button type="button" key={item.value}
            className={simulation === item.value ? 'scenario active' : 'scenario'}
            onClick={() => setSimulation(item.value)}>
            <strong>{item.label}</strong><small>{item.detail}</small>
          </button>)}
        </div>
        <div className="actions">
          <button className="primary" disabled={loading}>{loading ? 'Executando…' : 'Executar Saga'}</button>
          <button type="button" className="secondary" onClick={findExisting} disabled={loading}>Consultar</button>
          <button type="button" className="ghost" onClick={reset}>Nova ID</button>
        </div>
        {error && <p className="error">{error}</p>}
      </form>

      <section className="panel result-panel">
        <div className="panel-title"><span>02</span><div><h2>Resultado da orquestração</h2><p>Estado persistido para diagnóstico e recuperação.</p></div></div>
        {!result && <div className="empty"><div>◇</div><p>Execute um cenário para visualizar a Saga.</p></div>}
        {result && <Result saga={result} />}
      </section>
    </section>

    <footer>Projeto de estudo · falhas são intencionais e reproduzíveis</footer>
  </main>
}

function Result({ saga }) {
  const steps = [
    ['Pagamento criado', ['PAYMENT_CREATED', 'SPLIT_COMPLETED', 'TRANSFER_COMPLETED', 'COMPENSATION_COMPLETED'].includes(saga.currentStep)],
    ['Split calculado', ['SPLIT_COMPLETED', 'TRANSFER_COMPLETED', 'COMPENSATION_COMPLETED'].includes(saga.currentStep)],
    ['Transferência', ['TRANSFER_COMPLETED'].includes(saga.currentStep) || saga.status === 'COMPENSATED'],
    ['Compensação', saga.status === 'COMPENSATED']
  ]
  return <div className="result">
    <div className={`status ${saga.status.toLowerCase()}`}>{saga.status}</div>
    <div className="timeline">
      {steps.map(([label, complete], index) => <div className={complete ? 'step complete' : 'step'} key={label}>
        <i>{complete ? '✓' : index + 1}</i><span>{label}</span>
      </div>)}
    </div>
    <dl>
      <div><dt>Transação</dt><dd>{saga.transactionId}</dd></div>
      <div><dt>Valor</dt><dd>R$ {Number(saga.amount).toFixed(2)}</dd></div>
      <div><dt>Etapa atual</dt><dd>{saga.currentStep}</dd></div>
      <div><dt>Correlation ID</dt><dd>{saga.correlationId}</dd></div>
    </dl>
    {saga.failureReason && <div className="failure"><strong>Falha observada</strong><p>{saga.failureReason}</p></div>}
  </div>
}

createRoot(document.getElementById('root')).render(<App />)
