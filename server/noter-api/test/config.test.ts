import { describe, expect, it } from 'vitest';

import {
  ASR_AUDIO_LIMIT_BYTES,
  CHAT_BODY_LIMIT_BYTES,
  ConfigurationError,
  loadConfig,
} from '../src/config/config.js';

const validEnvironment = (): Record<string, string> => ({
  NOTER_CLIENT_TOKEN: 'app-token',
  NOTER_API_DOMAIN: 'api.example.test',
  CHAT_UPSTREAM_URL: 'https://chat.example.test/v1',
  CHAT_UPSTREAM_API_KEY: 'chat-secret',
  CHAT_UPSTREAM_MODEL: 'chat-model',
  ASR_UPSTREAM_URL: 'https://asr.example.test/v1',
  ASR_UPSTREAM_API_KEY: 'asr-secret',
  ASR_UPSTREAM_MODEL: 'asr-model',
});

describe('loadConfig', () => {
  it('loads independent upstream settings and documented defaults', () => {
    const config = loadConfig(validEnvironment());

    expect(config.clientToken).toBe('app-token');
    expect(config.apiDomain).toBe('api.example.test');
    expect(config.chat).toMatchObject({
      url: 'https://chat.example.test/v1',
      apiKey: 'chat-secret',
      model: 'chat-model',
      timeoutMs: 60_000,
      maxOutputTokens: 512,
      rateLimitPerMinute: 30,
      maxConcurrency: 8,
    });
    expect(config.asr).toMatchObject({
      url: 'https://asr.example.test/v1',
      apiKey: 'asr-secret',
      model: 'asr-model',
      timeoutMs: 60_000,
      rateLimitPerMinute: 10,
      maxConcurrency: 4,
    });
    expect(config.chatBodyLimitBytes).toBe(CHAT_BODY_LIMIT_BYTES);
    expect(config.asrAudioLimitBytes).toBe(ASR_AUDIO_LIMIT_BYTES);
  });

  it('accepts deployment tuning without changing source code', () => {
    const environment = validEnvironment();
    Object.assign(environment, {
      CHAT_MAX_OUTPUT_TOKENS: '1024',
      CHAT_UPSTREAM_TIMEOUT_MS: '2500',
      ASR_UPSTREAM_TIMEOUT_MS: '3000',
      CHAT_RATE_LIMIT_PER_MINUTE: '40',
      ASR_RATE_LIMIT_PER_MINUTE: '12',
      CHAT_MAX_CONCURRENCY: '3',
      ASR_MAX_CONCURRENCY: '2',
      NOTER_LOG_LEVEL: 'debug',
    });

    const config = loadConfig(environment);

    expect(config.chat).toMatchObject({
      maxOutputTokens: 1024,
      timeoutMs: 2500,
      rateLimitPerMinute: 40,
      maxConcurrency: 3,
    });
    expect(config.asr).toMatchObject({
      timeoutMs: 3000,
      rateLimitPerMinute: 12,
      maxConcurrency: 2,
    });
    expect(config.logLevel).toBe('debug');
  });

  it.each([
    'NOTER_CLIENT_TOKEN',
    'CHAT_UPSTREAM_URL',
    'CHAT_UPSTREAM_API_KEY',
    'CHAT_UPSTREAM_MODEL',
    'ASR_UPSTREAM_URL',
    'ASR_UPSTREAM_API_KEY',
    'ASR_UPSTREAM_MODEL',
    'NOTER_API_DOMAIN',
  ])('fails with a sanitized diagnostic when %s is missing', (variable) => {
    const environment = validEnvironment();
    delete environment[variable];

    expect(() => loadConfig(environment)).toThrow(ConfigurationError);
    expect(() => loadConfig(environment)).toThrow(`Invalid server configuration for ${variable}`);
    expect(() => loadConfig(environment)).not.toThrow(/chat-secret|asr-secret|app-token/);
  });

  it.each(['CHAT_MAX_OUTPUT_TOKENS', 'CHAT_UPSTREAM_TIMEOUT_MS', 'ASR_UPSTREAM_TIMEOUT_MS'])(
    'rejects non-positive %s',
    (variable) => {
      const environment = validEnvironment();
      environment[variable] = '0';

      expect(() => loadConfig(environment)).toThrow(`Invalid server configuration for ${variable}`);
    },
  );

  it('rejects malformed or credential-bearing upstream URLs', () => {
    const malformed = validEnvironment();
    malformed.CHAT_UPSTREAM_URL = 'not-a-url';
    expect(() => loadConfig(malformed)).toThrow('CHAT_UPSTREAM_URL');

    const credentialBearing = validEnvironment();
    credentialBearing.ASR_UPSTREAM_URL = 'https://user:password@asr.example.test/v1';
    expect(() => loadConfig(credentialBearing)).toThrow('ASR_UPSTREAM_URL');
  });

  it('rejects an API domain that includes a scheme, path, or port', () => {
    for (const value of [
      'https://api.example.test',
      'api.example.test/path',
      'api.example.test:8443',
    ]) {
      const environment = validEnvironment();
      environment.NOTER_API_DOMAIN = value;
      expect(() => loadConfig(environment)).toThrow('NOTER_API_DOMAIN');
    }
  });
});
