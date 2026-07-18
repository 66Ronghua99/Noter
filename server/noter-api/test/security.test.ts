import { describe, expect, it } from 'vitest';

import type { FastifyRequest } from 'fastify';

import { NoterError } from '../src/errors.js';
import {
  assertBearerToken,
  ConcurrencyGate,
  RequestRateLimiter,
  trustedSourceIp,
} from '../src/plugins/security.js';

describe('protected-route security controls', () => {
  it('compares bearer tokens through fixed-size digests', () => {
    const request = (authorization: string) =>
      ({ headers: { authorization } }) as unknown as FastifyRequest;

    expect(() => assertBearerToken(request('Bearer app-token'), 'app-token')).not.toThrow();
    assertThrowsCode(() => assertBearerToken(request('Bearer wrong'), 'app-token'), 'unauthorized');
    assertThrowsCode(
      () => assertBearerToken(request('Bearer a-much-longer-wrong-token'), 'app-token'),
      'unauthorized',
    );
  });

  it('uses forwarding headers only behind a private proxy hop', () => {
    const request = (remoteAddress: string, forwarded?: string) =>
      ({
        headers: forwarded === undefined ? {} : { 'x-forwarded-for': forwarded },
        raw: { socket: { remoteAddress } },
      }) as unknown as FastifyRequest;

    expect(trustedSourceIp(request('10.0.0.4', '198.51.100.7, 10.0.0.4'))).toBe('198.51.100.7');
    expect(trustedSourceIp(request('198.51.100.7', '10.0.0.4'))).toBe('198.51.100.7');
  });

  it('enforces a bounded source window and reports Retry-After', () => {
    let now = 1_000;
    const limiter = new RequestRateLimiter(2, 60_000, () => now);

    limiter.check('source');
    limiter.check('source');
    let error: unknown;
    try {
      limiter.check('source');
    } catch (caught) {
      error = caught;
    }
    expect(error).toBeInstanceOf(NoterError);
    expect((error as NoterError).code).toBe('rate_limited');
    expect((error as NoterError).retryAfterSeconds).toBe(60);
    now += 60_000;
    expect(() => limiter.check('source')).not.toThrow();
  });

  it('rejects new work when a process-wide concurrency gate is full', () => {
    const gate = new ConcurrencyGate(1);
    const release = gate.enter();

    assertThrowsCode(() => gate.enter(), 'service_unavailable');
    release();
    expect(() => gate.enter()).not.toThrow();
  });

  function assertThrowsCode(action: () => unknown, code: string): void {
    let error: unknown;
    try {
      action();
    } catch (caught) {
      error = caught;
    }
    expect(error).toBeInstanceOf(NoterError);
    expect((error as NoterError).code).toBe(code);
  }
});
