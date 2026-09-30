import http from 'k6/http'
import { check, sleep } from 'k6'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:3000'

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0']
  }
}

export default function () {
  const suffix = String(Date.now())
  const transactionId = 'race-' + suffix
  const payload = JSON.stringify({
    transactionId,
    amount: 100,
    idempotencyKey: 'race-key-' + suffix,
    simulation: 'NONE'
  })
  const request = {
    method: 'POST',
    url: baseUrl + '/api/payment-flows',
    body: payload,
    params: { headers: { 'Content-Type': 'application/json' } }
  }

  const responses = http.batch(Array.from({ length: 10 }, () => request))
  const sagaIds = new Set(responses.map(response => response.json('id')))

  check(responses, {
    'all concurrent requests are accepted': items => items.every(item => item.status === 201),
    'all requests resolve to one Saga': () => sagaIds.size === 1 && !sagaIds.has(undefined)
  })

  sleep(2)
  const finalResponse = http.get(baseUrl + '/api/payment-flows/' + transactionId)
  check(finalResponse, {
    'winner finishes successfully': response =>
      response.status === 200 && response.json('status') === 'COMPLETED'
  })
}
