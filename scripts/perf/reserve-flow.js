import http from "k6/http";
import {check, sleep} from "k6";
import {Counter} from "k6/metrics";
import {SharedArray} from "k6/data";
import exec from "k6/execution";

const reserveSubmitOk = new Counter("reserve_submit_ok");
const reserveSubmitFail = new Counter("reserve_submit_fail");
const reserveResultPending = new Counter("reserve_result_pending");
const reserveResultSuccess = new Counter("reserve_result_success");
const reserveResultFail = new Counter("reserve_result_fail");
const payOk = new Counter("pay_ok");
const payFail = new Counter("pay_fail");
const cancelOk = new Counter("cancel_ok");
const cancelFail = new Counter("cancel_fail");
const retryCount = new Counter("reserve_retry_count");

const users = new SharedArray("users", function () {
    const file = __ENV.TOKENS_FILE || "./tokens.json";
    const raw = open(file);
    const normalized = raw.replace(/^\uFEFF/, "");
    return JSON.parse(normalized);
});

const BASE_URL = (__ENV.BASE_URL || "http://localhost:25001").replace(/\/+$/, "");
const SLOT_ID = Number(__ENV.SLOT_ID || 1);
const SLOT_ID_START = Number(__ENV.SLOT_ID_START || SLOT_ID);
const SLOT_ID_END = Number(__ENV.SLOT_ID_END || SLOT_ID);
const SESSION_ID = Number(__ENV.SESSION_ID || 1);

const RESULT_POLL_MAX = Number(__ENV.RESULT_POLL_MAX || 20);
const RESULT_POLL_INTERVAL_MS = Number(__ENV.RESULT_POLL_INTERVAL_MS || 200);
const RESERVE_MAX_RETRIES = Number(__ENV.RESERVE_MAX_RETRIES || 3);

const SUCCESS_PAY_RATIO = Number(__ENV.SUCCESS_PAY_RATIO || 0.4);
const SUCCESS_CANCEL_RATIO = Number(__ENV.SUCCESS_CANCEL_RATIO || 0.3);
const FAIL_RETRY_RATIO = Number(__ENV.FAIL_RETRY_RATIO || 0.8);
const FAIL_RETRY_SLEEP_MS = Number(__ENV.FAIL_RETRY_SLEEP_MS || 150);

export const options = {
    scenarios: {
        rush_flow: {
            executor: "constant-arrival-rate",
            rate: Number(__ENV.RATE || 2000),
            timeUnit: __ENV.TIME_UNIT || "1m",
            duration: __ENV.DURATION || "1m",
            preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS || 200),
            maxVUs: Number(__ENV.MAX_VUS || users.length),
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.1"],
        http_req_duration: ["p(95)<1200"],
    },
};

function parseBizResponse(res) {
    let body;
    try {
        body = JSON.parse(res.body || "{}");
    } catch (_) {
        body = null;
    }
    return {
        httpStatus: res.status,
        code: body && typeof body.code !== "undefined" ? Number(body.code) : null,
        message: body && typeof body.message !== "undefined" ? String(body.message) : "",
        data: body && typeof body.data !== "undefined" ? body.data : null,
    };
}

function pickSlotId(userIndex, attempt) {
    const start = Math.min(SLOT_ID_START, SLOT_ID_END);
    const end = Math.max(SLOT_ID_START, SLOT_ID_END);
    const count = end - start + 1;
    // Evenly distribute users across slot range; retries rotate to next slot.
    return start + ((userIndex + attempt) % count);
}

function reserveOnce(token, slotId) {
    const payload = JSON.stringify({slotId: slotId, sessionId: SESSION_ID});
    const res = http.post(`${BASE_URL}/reserve/`, payload, {
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
        },
        timeout: "8s",
        tags: {api: "reserve_submit"},
    });
    const biz = parseBizResponse(res);
    if (biz.code === 200 && biz.data && biz.data.traceId) {
        reserveSubmitOk.add(1);
        return {ok: true, traceId: String(biz.data.traceId)};
    }
    reserveSubmitFail.add(1);
    return {ok: false, code: biz.code, message: biz.message};
}

function pollReserveResult(token, traceId) {
    for (let i = 0; i < RESULT_POLL_MAX; i++) {
        const res = http.get(`${BASE_URL}/reserve/result/${encodeURIComponent(traceId)}`, {
            headers: {Authorization: `Bearer ${token}`},
            timeout: "8s",
            tags: {api: "reserve_result"},
        });
        const biz = parseBizResponse(res);
        if (biz.code !== 200 || !biz.data) {
            sleep(RESULT_POLL_INTERVAL_MS / 1000);
            continue;
        }
        const status = String(biz.data.status || "");
        if (status === "PENDING") {
            reserveResultPending.add(1);
            sleep(RESULT_POLL_INTERVAL_MS / 1000);
            continue;
        }
        if (status === "SUCCESS") {
            reserveResultSuccess.add(1);
            return {
                ok: true,
                reservationId: Number(biz.data.reservationId),
                reservationStatus: String(biz.data.reservationStatus || ""),
            };
        }
        reserveResultFail.add(1);
        return {ok: false};
    }
    reserveResultFail.add(1);
    return {ok: false};
}

function doPay(token, reservationId) {
    const res = http.post(`${BASE_URL}/pay/wechat/${reservationId}`, null, {
        headers: {Authorization: `Bearer ${token}`},
        timeout: "8s",
        tags: {api: "pay_create"},
    });
    const biz = parseBizResponse(res);
    if (biz.code === 200) {
        payOk.add(1);
        return;
    }
    payFail.add(1);
}

function doCancel(token, reservationId) {
    const res = http.del(`${BASE_URL}/reserve/${reservationId}`, null, {
        headers: {Authorization: `Bearer ${token}`},
        timeout: "8s",
        tags: {api: "reserve_cancel"},
    });
    const biz = parseBizResponse(res);
    if (biz.code === 200) {
        cancelOk.add(1);
        return;
    }
    cancelFail.add(1);
}

function runFlowForUser(token) {
    const userIndex = __VU - 1;
    for (let attempt = 0; attempt <= RESERVE_MAX_RETRIES; attempt++) {
        if (attempt > 0) {
            retryCount.add(1);
            sleep(FAIL_RETRY_SLEEP_MS / 1000);
        }

        const slotId = pickSlotId(userIndex, attempt);
        const submit = reserveOnce(token, slotId);
        if (!submit.ok) {
            const retry = Math.random() < FAIL_RETRY_RATIO;
            if (retry && attempt < RESERVE_MAX_RETRIES) {
                continue;
            }
            return;
        }

        const result = pollReserveResult(token, submit.traceId);
        if (!result.ok || !result.reservationId) {
            const retry = Math.random() < FAIL_RETRY_RATIO;
            if (retry && attempt < RESERVE_MAX_RETRIES) {
                continue;
            }
            return;
        }

        const r = Math.random();
        if (r < SUCCESS_PAY_RATIO) {
            doPay(token, result.reservationId);
            return;
        }
        if (r < SUCCESS_PAY_RATIO + SUCCESS_CANCEL_RATIO) {
            doCancel(token, result.reservationId);
            return;
        }
        return;
    }
}

export default function () {
    const iteration = exec.scenario.iterationInTest;
    const idx = iteration % users.length;
    const user = users[idx];
    const token = user && user.token ? String(user.token) : "";

    check(user, {
        "user token exists": () => token.length > 0,
    });
    if (!token) {
        return;
    }

    runFlowForUser(token);
}
