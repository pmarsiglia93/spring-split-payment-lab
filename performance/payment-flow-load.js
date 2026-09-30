import http from 'k6/http'
import { check, sleep } from 'k6'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:3000'

export const options = {
  scenarios: {
    healthy_payment_flows: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '15s'
    }
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000']
  }
}

export default function () {
  const suffix = String(Date.now()) + '-' + __VU + '-' + __ITER
  const response = http.post(
    baseUrl + '/api/payment-flows',
    JSON.stringify({
      transactionId: 'load-' + suffix,
      amount: 100,
      idempotencyKey: 'load-key-' + suffix,
      simulation: 'NONE'
    }),
    { headers: { 'Content-Type': 'application/json', 'X-Correlation-ID': 'load-' + suffix } }
  )

  check(response, {
    'flow returns 201': current => current.status === 201,
    'Saga completes': current => current.json('status') === 'COMPLETED'
  })
  sleep(0.2)
}
