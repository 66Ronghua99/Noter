import { NoterError } from '../errors.js';

export interface TimedSignal {
  readonly signal: AbortSignal;
  readonly didTimeout: () => boolean;
  readonly dispose: () => void;
}

export function createTimedSignal(parent: AbortSignal, timeoutMs: number): TimedSignal {
  const controller = new AbortController();
  let timedOut = false;
  const onParentAbort = () => controller.abort(parent.reason);
  parent.addEventListener('abort', onParentAbort, { once: true });
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort(new Error('upstream timeout'));
  }, timeoutMs);

  return {
    signal: controller.signal,
    didTimeout: () => timedOut,
    dispose: () => {
      clearTimeout(timer);
      parent.removeEventListener('abort', onParentAbort);
    },
  };
}

export async function fetchUpstream(
  url: string,
  init: RequestInit,
  timedSignal: TimedSignal,
): Promise<Response> {
  try {
    return await fetch(url, { ...init, signal: timedSignal.signal });
  } catch (error) {
    if (timedSignal.didTimeout()) {
      throw new NoterError('upstream_timeout', { cause: error });
    }
    throw new NoterError('upstream_unavailable', { cause: error });
  }
}

export async function readJson(response: Response): Promise<unknown> {
  try {
    return JSON.parse(await response.text()) as unknown;
  } catch (error) {
    throw new NoterError('invalid_upstream_response', { cause: error });
  }
}

export function mapUpstreamStatus(status: number): NoterError {
  if (status === 408 || status === 504) {
    return new NoterError('upstream_timeout');
  }
  if (status === 429 || status >= 500) {
    return new NoterError('upstream_unavailable');
  }
  return new NoterError('upstream_rejected');
}

export function requireRecord(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new NoterError('invalid_upstream_response');
  }
  return value as Record<string, unknown>;
}
