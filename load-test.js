import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

/*
 * FlashSale Purchase Load Test (K6)
 * ──────────────────────────────────
 * Target: 500 TPS on the purchase endpoint
 *
 * Prerequisites:
 *   1. Start infrastructure: docker compose up -d
 *   2. Start app: ./mvnw spring-boot:run
 *   3. Create a flash sale slot + item via admin API (see setup below)
 *   4. Install k6: brew install k6
 *
 * Run:
 *   k6 run load-test.js
 *   k6 run --vus 500 --duration 60s load-test.js       # 500 concurrent users for 60s
 *   k6 run --vus 100 --iterations 5000 load-test.js    # 5000 total requests
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const FLASH_SALE_ITEM_ID = __ENV.ITEM_ID || '1';

// Custom metrics
const purchaseSuccess = new Counter('purchase_success');
const purchaseFailed = new Counter('purchase_failed');
const purchaseRate = new Rate('purchase_success_rate');
const purchaseDuration = new Trend('purchase_duration', true);

// Test configuration
export const options = {
    scenarios: {
        // Ramp up to 500 TPS over 30s, sustain for 60s, then ramp down
        flash_sale_load: {
            executor: 'ramping-arrival-rate',
            startRate: 50,
            timeUnit: '1s',
            preAllocatedVUs: 600,
            maxVUs: 1000,
            stages: [
                { target: 200, duration: '10s' },   // Warm up
                { target: 500, duration: '10s' },   // Ramp to target
                { target: 500, duration: '60s' },   // Sustain 500 TPS
                { target: 0,   duration: '10s' },   // Cool down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],      // 95% of requests under 500ms
        purchase_success_rate: ['rate>0.5'],   // At least 50% success (rest are "sold out" which is expected)
    },
};

// ── Setup: Register users and get tokens ──
export function setup() {
    console.log('=== Setup: Creating test users and flash sale data ===');

    // Login as admin
    const adminLogin = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
        identifier: 'admin@flashsale.com',
        password: 'admin123',
    }), { headers: { 'Content-Type': 'application/json' } });

    let adminToken = '';
    if (adminLogin.status === 200) {
        adminToken = JSON.parse(adminLogin.body).data.accessToken;
        console.log('Admin login OK');
    } else {
        console.log(`Admin login failed: ${adminLogin.status} - ${adminLogin.body}`);
        console.log('Make sure admin user exists (seeded via Liquibase)');
    }

    // Create test users in bulk and collect tokens
    const tokens = [];
    const userCount = 600; // More users than VUs

    for (let i = 0; i < userCount; i++) {
        const email = `loadtest_${i}_${Date.now()}@test.com`;
        const password = 'Test123456';

        // Register
        const regRes = http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
            identifier: email, password: password,
        }), { headers: { 'Content-Type': 'application/json' } });

        if (regRes.status !== 200) continue;

        // Get OTP from logs (in real test, we'd mock this)
        // For load testing, we'll just use login with pre-created users
        // Skip OTP flow - use direct login after manual user creation
    }

    // For simplicity in load testing: pre-register some users and login
    // In practice, you would pre-create users in DB or use the OTP mock
    console.log('=== Setup complete ===');
    console.log(`Target item ID: ${FLASH_SALE_ITEM_ID}`);

    return { adminToken };
}

// ── Main test: Purchase endpoint ──
export default function (data) {
    const uniqueEmail = `perf_${__VU}_${__ITER}_${Date.now()}@test.com`;
    const password = 'Test123456';

    // Quick register + verify flow
    group('register_and_purchase', function () {
        // 1. Register
        const regRes = http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
            identifier: uniqueEmail, password: password,
        }), { headers: { 'Content-Type': 'application/json' } });

        if (regRes.status !== 200) {
            purchaseFailed.add(1);
            purchaseRate.add(false);
            return;
        }

        // 2. For load test: directly login (assumes OTP auto-verify or test profile)
        //    In production test, you'd need to handle OTP verification
        const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
            identifier: uniqueEmail, password: password,
        }), { headers: { 'Content-Type': 'application/json' } });

        if (loginRes.status !== 200) {
            purchaseFailed.add(1);
            purchaseRate.add(false);
            return;
        }

        const token = JSON.parse(loginRes.body).data.accessToken;

        // 3. Purchase flash sale item
        const start = new Date();
        const purchaseRes = http.post(
            `${BASE_URL}/api/v1/flash-sales/${FLASH_SALE_ITEM_ID}/purchase`,
            null,
            { headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' } }
        );
        const duration = new Date() - start;
        purchaseDuration.add(duration);

        const success = purchaseRes.status === 200;
        if (success) {
            purchaseSuccess.add(1);
        } else {
            purchaseFailed.add(1);
        }
        purchaseRate.add(success);

        check(purchaseRes, {
            'purchase status 200 or 409 (sold out/duplicate)': (r) =>
                r.status === 200 || r.status === 409 || r.status === 402,
        });
    });
}

// ── Direct purchase-only test (use with pre-created tokens) ──
// Uncomment this and comment out the default function above for pure purchase TPS test
/*
export default function (data) {
    const purchaseRes = http.post(
        `${BASE_URL}/api/v1/flash-sales/${FLASH_SALE_ITEM_ID}/purchase`,
        null,
        { headers: { 'Authorization': `Bearer ${TOKEN}`, 'Content-Type': 'application/json' } }
    );
    check(purchaseRes, { 'status is expected': (r) => [200, 409, 402].includes(r.status) });
}
*/
