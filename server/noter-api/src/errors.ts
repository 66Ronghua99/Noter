export type NoterErrorCode =
  | 'unauthorized'
  | 'invalid_request'
  | 'payload_too_large'
  | 'rate_limited'
  | 'upstream_rejected'
  | 'upstream_unavailable'
  | 'upstream_timeout'
  | 'invalid_upstream_response'
  | 'service_unavailable'
  | 'internal_error';

const DEFAULT_ERROR_MESSAGES: Record<NoterErrorCode, string> = {
  unauthorized: 'The service is unavailable.',
  invalid_request: 'The request is invalid.',
  payload_too_large: 'The request is too large.',
  rate_limited: 'The service is busy. Please try again later.',
  upstream_rejected: 'The model service rejected the request.',
  upstream_unavailable: 'The model service is temporarily unavailable.',
  upstream_timeout: 'The model service timed out.',
  invalid_upstream_response: 'The model service returned an invalid response.',
  service_unavailable: 'The service is temporarily unavailable.',
  internal_error: 'The service could not complete the request.',
};

const RETRYABLE_CODES = new Set<NoterErrorCode>([
  'rate_limited',
  'upstream_unavailable',
  'upstream_timeout',
  'service_unavailable',
]);

const STATUS_CODES: Record<NoterErrorCode, number> = {
  unauthorized: 401,
  invalid_request: 422,
  payload_too_large: 413,
  rate_limited: 429,
  upstream_rejected: 502,
  upstream_unavailable: 503,
  upstream_timeout: 504,
  invalid_upstream_response: 502,
  service_unavailable: 503,
  internal_error: 500,
};

export class NoterError extends Error {
  public readonly statusCode: number;
  public readonly retryable: boolean;
  public readonly retryAfterSeconds?: number;

  public constructor(
    public readonly code: NoterErrorCode,
    options: {
      readonly statusCode?: number;
      readonly retryable?: boolean;
      readonly retryAfterSeconds?: number;
      readonly cause?: unknown;
    } = {},
  ) {
    super(DEFAULT_ERROR_MESSAGES[code], { cause: options.cause });
    this.name = 'NoterError';
    this.statusCode = options.statusCode ?? STATUS_CODES[code];
    this.retryable = options.retryable ?? RETRYABLE_CODES.has(code);
    if (options.retryAfterSeconds !== undefined) {
      this.retryAfterSeconds = options.retryAfterSeconds;
    }
  }
}

export interface ErrorEnvelope {
  readonly error: {
    readonly code: NoterErrorCode;
    readonly message: string;
    readonly requestId: string;
    readonly retryable: boolean;
  };
}

export function toErrorEnvelope(error: NoterError, requestId: string): ErrorEnvelope {
  return {
    error: {
      code: error.code,
      message: error.message,
      requestId,
      retryable: error.retryable,
    },
  };
}

export function isNoterError(error: unknown): error is NoterError {
  return error instanceof NoterError;
}
