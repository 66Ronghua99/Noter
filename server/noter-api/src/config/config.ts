export const CHAT_BODY_LIMIT_BYTES = 256 * 1024;
export const ASR_AUDIO_LIMIT_BYTES = 10 * 1024 * 1024;

const DEFAULTS = {
  chatMaxOutputTokens: 512,
  chatUpstreamTimeoutMs: 60_000,
  asrUpstreamTimeoutMs: 60_000,
  chatRateLimitPerMinute: 30,
  asrRateLimitPerMinute: 10,
  chatMaxConcurrency: 8,
  asrMaxConcurrency: 4,
  logLevel: 'info',
} as const;

export type NoterLogLevel = 'fatal' | 'error' | 'warn' | 'info' | 'debug';

export interface UpstreamConfig {
  readonly url: string;
  readonly apiKey: string;
  readonly model: string;
  readonly timeoutMs: number;
}

export interface NoterApiConfig {
  readonly clientToken: string;
  readonly apiDomain: string;
  readonly chat: UpstreamConfig & {
    readonly maxOutputTokens: number;
    readonly rateLimitPerMinute: number;
    readonly maxConcurrency: number;
  };
  readonly asr: UpstreamConfig & {
    readonly rateLimitPerMinute: number;
    readonly maxConcurrency: number;
  };
  readonly logLevel: NoterLogLevel;
  readonly chatBodyLimitBytes: typeof CHAT_BODY_LIMIT_BYTES;
  readonly asrAudioLimitBytes: typeof ASR_AUDIO_LIMIT_BYTES;
}

export class ConfigurationError extends Error {
  public readonly code = 'invalid_configuration';

  public constructor(
    public readonly variable: string,
    message: string,
  ) {
    super(`Invalid server configuration for ${variable}: ${message}`);
    this.name = 'ConfigurationError';
  }
}

type Environment = Record<string, string | undefined>;

export function loadConfig(environment: Environment): NoterApiConfig {
  const clientToken = required(environment, 'NOTER_CLIENT_TOKEN');
  const apiDomain = requiredDomain(environment, 'NOTER_API_DOMAIN');
  const chatUrl = requiredUrl(environment, 'CHAT_UPSTREAM_URL');
  const chatApiKey = required(environment, 'CHAT_UPSTREAM_API_KEY');
  const chatModel = required(environment, 'CHAT_UPSTREAM_MODEL');
  const asrUrl = requiredUrl(environment, 'ASR_UPSTREAM_URL');
  const asrApiKey = required(environment, 'ASR_UPSTREAM_API_KEY');
  const asrModel = required(environment, 'ASR_UPSTREAM_MODEL');

  return Object.freeze({
    clientToken,
    apiDomain,
    chat: Object.freeze({
      url: chatUrl,
      apiKey: chatApiKey,
      model: chatModel,
      timeoutMs: boundedInteger(
        environment,
        'CHAT_UPSTREAM_TIMEOUT_MS',
        DEFAULTS.chatUpstreamTimeoutMs,
        1,
        600_000,
      ),
      maxOutputTokens: boundedInteger(
        environment,
        'CHAT_MAX_OUTPUT_TOKENS',
        DEFAULTS.chatMaxOutputTokens,
        1,
        32_768,
      ),
      rateLimitPerMinute: boundedInteger(
        environment,
        'CHAT_RATE_LIMIT_PER_MINUTE',
        DEFAULTS.chatRateLimitPerMinute,
        1,
        100_000,
      ),
      maxConcurrency: boundedInteger(
        environment,
        'CHAT_MAX_CONCURRENCY',
        DEFAULTS.chatMaxConcurrency,
        1,
        1_024,
      ),
    }),
    asr: Object.freeze({
      url: asrUrl,
      apiKey: asrApiKey,
      model: asrModel,
      timeoutMs: boundedInteger(
        environment,
        'ASR_UPSTREAM_TIMEOUT_MS',
        DEFAULTS.asrUpstreamTimeoutMs,
        1,
        600_000,
      ),
      rateLimitPerMinute: boundedInteger(
        environment,
        'ASR_RATE_LIMIT_PER_MINUTE',
        DEFAULTS.asrRateLimitPerMinute,
        1,
        100_000,
      ),
      maxConcurrency: boundedInteger(
        environment,
        'ASR_MAX_CONCURRENCY',
        DEFAULTS.asrMaxConcurrency,
        1,
        1_024,
      ),
    }),
    logLevel: logLevel(environment),
    chatBodyLimitBytes: CHAT_BODY_LIMIT_BYTES,
    asrAudioLimitBytes: ASR_AUDIO_LIMIT_BYTES,
  });
}

export function loadProcessConfig(): NoterApiConfig {
  return loadConfig(process.env);
}

function required(environment: Environment, variable: string): string {
  const value = environment[variable]?.trim();
  if (!value) {
    throw new ConfigurationError(variable, 'value is required');
  }
  return value;
}

function requiredUrl(environment: Environment, variable: string): string {
  const value = required(environment, variable);
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new ConfigurationError(variable, 'must be an absolute URL');
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new ConfigurationError(variable, 'must use http or https');
  }
  if (parsed.username || parsed.password) {
    throw new ConfigurationError(variable, 'must not contain credentials');
  }
  return parsed.toString().replace(/\/$/, '');
}

function requiredDomain(environment: Environment, variable: string): string {
  const value = required(environment, variable);
  let parsed: URL;
  try {
    parsed = new URL(`https://${value}`);
  } catch {
    throw new ConfigurationError(variable, 'must be a hostname without a scheme or path');
  }
  if (
    parsed.username ||
    parsed.password ||
    parsed.port ||
    parsed.pathname !== '/' ||
    parsed.search ||
    parsed.hash ||
    parsed.hostname !== value.toLowerCase()
  ) {
    throw new ConfigurationError(variable, 'must be a hostname without a scheme or path');
  }
  return value;
}

function boundedInteger(
  environment: Environment,
  variable: string,
  fallback: number,
  minimum: number,
  maximum: number,
): number {
  const raw = environment[variable]?.trim();
  if (!raw) {
    return fallback;
  }
  if (!/^\d+$/.test(raw)) {
    throw new ConfigurationError(variable, `must be an integer between ${minimum} and ${maximum}`);
  }
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
    throw new ConfigurationError(variable, `must be an integer between ${minimum} and ${maximum}`);
  }
  return value;
}

function logLevel(environment: Environment): NoterLogLevel {
  const value = environment.NOTER_LOG_LEVEL?.trim() || DEFAULTS.logLevel;
  if (!['fatal', 'error', 'warn', 'info', 'debug'].includes(value)) {
    throw new ConfigurationError('NOTER_LOG_LEVEL', 'must be fatal, error, warn, info, or debug');
  }
  return value as NoterLogLevel;
}
