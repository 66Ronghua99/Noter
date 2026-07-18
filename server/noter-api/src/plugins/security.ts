import { createHash, timingSafeEqual } from 'node:crypto';

import type { FastifyRequest } from 'fastify';

import { NoterError } from '../errors.js';

interface WindowEntry {
  startedAt: number;
  count: number;
}

export class RequestRateLimiter {
  private readonly entries = new Map<string, WindowEntry>();

  public constructor(
    private readonly limit: number,
    private readonly windowMs = 60_000,
    private readonly clock: () => number = Date.now,
  ) {}

  public check(key: string): void {
    const now = this.clock();
    const current = this.entries.get(key);
    if (!current || now - current.startedAt >= this.windowMs) {
      this.entries.set(key, { startedAt: now, count: 1 });
      return;
    }
    if (current.count >= this.limit) {
      const retryAfterSeconds = Math.max(
        1,
        Math.ceil((this.windowMs - (now - current.startedAt)) / 1000),
      );
      throw new NoterError('rate_limited', { retryAfterSeconds });
    }
    current.count += 1;
  }
}

export class ConcurrencyGate {
  private active = 0;

  public constructor(private readonly maximum: number) {}

  public enter(): () => void {
    if (this.active >= this.maximum) {
      throw new NoterError('service_unavailable');
    }
    this.active += 1;
    let released = false;
    return () => {
      if (!released) {
        released = true;
        this.active -= 1;
      }
    };
  }

  public get activeCount(): number {
    return this.active;
  }
}

export function assertBearerToken(request: FastifyRequest, expectedToken: string): void {
  const authorization = request.headers.authorization;
  const presented =
    typeof authorization === 'string' && authorization.startsWith('Bearer ')
      ? authorization.slice('Bearer '.length)
      : '';
  const expectedDigest = createHash('sha256').update(expectedToken).digest();
  const presentedDigest = createHash('sha256').update(presented).digest();
  if (!timingSafeEqual(expectedDigest, presentedDigest)) {
    throw new NoterError('unauthorized');
  }
}

export function trustedSourceIp(request: FastifyRequest): string {
  const remoteAddress = normalizeAddress(request.raw.socket.remoteAddress) ?? 'unknown';
  if (!isTrustedPrivateHop(remoteAddress)) {
    return remoteAddress;
  }

  const forwarded = request.headers['x-forwarded-for'];
  if (typeof forwarded === 'string') {
    const firstAddress = normalizeAddress(forwarded.split(',')[0]?.trim());
    if (firstAddress) {
      return firstAddress;
    }
  }
  return remoteAddress;
}

function normalizeAddress(address: string | undefined): string | undefined {
  if (!address) {
    return undefined;
  }
  if (address.startsWith('::ffff:')) {
    return address.slice('::ffff:'.length);
  }
  return address;
}

function isTrustedPrivateHop(address: string): boolean {
  if (address === '::1' || address === 'localhost') {
    return true;
  }
  const octets = address.split('.').map(Number);
  if (
    octets.length !== 4 ||
    octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)
  ) {
    return address.startsWith('fc') || address.startsWith('fd') || address.startsWith('fe80:');
  }
  const [first, second] = octets;
  return (
    first === 10 ||
    first === 127 ||
    (first === 172 && second !== undefined && second >= 16 && second <= 31) ||
    (first === 192 && second === 168)
  );
}
