import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.K6_BASE_URL;
const LOGIN_EMAIL = 'admin@dev.com';
const LOGIN_PASSWORD = 'admin';

export const options = {
  stages: [
    { duration: '30s', target: 5 },    // Warm-up
    { duration: '1m',  target: 15 },   // Ramp-up
    { duration: '2m',  target: 25 },   // Stress
    { duration: '3m',  target: 25 },   // Sustain
    { duration: '1m',  target: 0 },    // Cool-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<5000'],
    'http_req_failed': ['rate<0.2'],
  },
};

export default function () {
  // Health check (~20% of requests)
  if (Math.random() < 0.2) {
    const healthRes = http.get(`${BASE_URL}/health`);
    check(healthRes, {
      'health: status 200': (r) => r.status === 200,
    });
  }

  // Login - BCrypt CPU stress (~80% of requests)
  const loginRes = http.post(
    `${BASE_URL}/v1/login`,
    JSON.stringify({
      email: LOGIN_EMAIL,
      password: LOGIN_PASSWORD,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(loginRes, {
    'login: status 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  sleep(0.1);
}

export function handleSummary(data) {
  return {
    'results.json': JSON.stringify(data, null, 2),
  };
}
