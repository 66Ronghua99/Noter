import { afterEach, describe, expect, it } from 'vitest';
import { Writable } from 'node:stream';

import { buildApp } from '../src/app/app.js';
import { loadConfig } from '../src/config/config.js';
import type {
  AsrProvider,
  ChatProvider,
  ProviderAsrRequest,
  ProviderAsrResponse,
  ProviderChatRequest,
  ProviderChatResponse,
} from '../src/providers/contracts.js';

const token = 'app-token';

function environment(overrides: Record<string, string> = {}): Record<string, string> {
  return {
    NOTER_CLIENT_TOKEN: token,
    NOTER_API_DOMAIN: 'api.example.test',
    CHAT_UPSTREAM_URL: 'https://chat.example.test/v1/chat/completions',
    CHAT_UPSTREAM_API_KEY: 'chat-secret',
    CHAT_UPSTREAM_MODEL: 'chat-model',
    ASR_UPSTREAM_URL: 'https://asr.example.test/v1/audio/transcriptions',
    ASR_UPSTREAM_API_KEY: 'asr-secret',
    ASR_UPSTREAM_MODEL: 'asr-model',
    ...overrides,
  };
}

function chatPayload(overrides: Record<string, unknown> = {}) {
  return {
    messages: [{ role: 'user', content: 'wake me at eight' }],
    tools: [
      {
        name: 'create_alarm',
        description: 'Create an alarm.',
        parameters: { type: 'object', properties: {} },
      },
    ],
    toolChoice: { mode: 'auto' },
    ...overrides,
  };
}

function jsonBody(response: { readonly body: string }): unknown {
  return JSON.parse(response.body) as unknown;
}

function multipartForm(
  audio: Buffer,
  fields: Record<string, string> = {},
): { readonly headers: Record<string, string>; readonly payload: Buffer } {
  const boundary = 'noter-test-boundary';
  const chunks: Buffer[] = [];
  const append = (value: string | Buffer) =>
    chunks.push(Buffer.isBuffer(value) ? value : Buffer.from(value));
  append(`--${boundary}\r\n`);
  append('Content-Disposition: form-data; name="audio"; filename="recording.m4a"\r\n');
  append('Content-Type: audio/mp4\r\n\r\n');
  append(audio);
  append('\r\n');
  for (const [name, value] of Object.entries(fields)) {
    append(`--${boundary}\r\n`);
    append(`Content-Disposition: form-data; name="${name}"\r\n\r\n`);
    append(value);
    append('\r\n');
  }
  append(`--${boundary}--\r\n`);
  return {
    headers: { 'content-type': `multipart/form-data; boundary=${boundary}` },
    payload: Buffer.concat(chunks),
  };
}

class FakeChatProvider implements ChatProvider {
  public calls: ProviderChatRequest[] = [];
  public response: ProviderChatResponse = {
    upstreamStatus: 200,
    message: {
      role: 'assistant',
      content: '',
      toolCalls: [{ id: 'call_1', name: 'create_alarm', arguments: '{}' }],
    },
  };

  public complete(request: ProviderChatRequest): Promise<ProviderChatResponse> {
    this.calls.push(request);
    return Promise.resolve(this.response);
  }
}

class FakeAsrProvider implements AsrProvider {
  public calls: ProviderAsrRequest[] = [];
  public response: ProviderAsrResponse = { text: 'wake me at eight', upstreamStatus: 200 };

  public transcribe(request: ProviderAsrRequest): Promise<ProviderAsrResponse> {
    this.calls.push(request);
    return Promise.resolve(this.response);
  }
}

const apps: ReturnType<typeof buildApp>[] = [];

afterEach(async () => {
  await Promise.all(apps.splice(0).map((app) => app.close()));
});

describe('Noter API HTTP contract', () => {
  it('exposes unauthenticated health with only a status value', async () => {
    const app = buildApp({ config: loadConfig(environment()) });
    apps.push(app);

    const live = await app.inject({ method: 'GET', url: '/health/live' });
    const ready = await app.inject({ method: 'GET', url: '/health/ready' });

    expect(live.statusCode).toBe(200);
    expect(jsonBody(live)).toEqual({ status: 'ok' });
    expect(ready.statusCode).toBe(200);
    expect(jsonBody(ready)).toEqual({ status: 'ok' });
  });

  it('passes a valid Chat request to the neutral service seam and returns a request ID', async () => {
    const provider = new FakeChatProvider();
    const app = buildApp({ config: loadConfig(environment()), chatProvider: provider });
    apps.push(app);

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/agent/completions',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      payload: JSON.stringify(chatPayload()),
    });

    expect(response.statusCode).toBe(200);
    const responseBody = jsonBody(response) as {
      readonly requestId: string;
      readonly message: unknown;
    };
    expect(responseBody.requestId).toMatch(/^req-/);
    expect(responseBody.message).toEqual({
      role: 'assistant',
      content: '',
      toolCalls: [{ id: 'call_1', name: 'create_alarm', arguments: '{}' }],
    });
    expect(response.headers['x-request-id']).toBe(responseBody.requestId);
    expect(provider.calls).toHaveLength(1);
    expect(provider.calls[0]?.config).toMatchObject({
      url: 'https://chat.example.test/v1/chat/completions',
      apiKey: 'chat-secret',
      model: 'chat-model',
      maxOutputTokens: 512,
    });
  });

  it('logs request metadata without request content or sensitive values', async () => {
    const logChunks: string[] = [];
    const logStream = new Writable({
      write(chunk, _encoding, callback) {
        logChunks.push(String(chunk));
        callback();
      },
    });
    const app = buildApp({
      config: loadConfig(environment({ NOTER_CLIENT_TOKEN: 'client-token-sentinel' })),
      chatProvider: new FakeChatProvider(),
      asrProvider: new FakeAsrProvider(),
      logger: { level: 'info', stream: logStream },
    });

    const chatSentinel = 'chat-prompt-sentinel';
    const audioSentinel = 'audio-bytes-sentinel';
    const chatResponse = await app.inject({
      method: 'POST',
      url: '/api/v1/agent/completions',
      headers: {
        authorization: 'Bearer client-token-sentinel',
        'content-type': 'application/json',
      },
      payload: JSON.stringify(chatPayload({ messages: [{ role: 'user', content: chatSentinel }] })),
    });
    const form = multipartForm(Buffer.from(audioSentinel), { language: 'en-US' });
    const asrResponse = await app.inject({
      method: 'POST',
      url: '/api/v1/asr/transcriptions',
      headers: { authorization: 'Bearer client-token-sentinel', ...form.headers },
      payload: form.payload,
    });
    await app.close();

    expect(chatResponse.statusCode).toBe(200);
    expect(asrResponse.statusCode).toBe(200);
    const logs = logChunks.join('');
    expect(logs).toContain('request complete');
    expect(logs).toContain('/api/v1/agent/completions');
    expect(logs).toContain('/api/v1/asr/transcriptions');
    expect(logs).not.toContain(chatSentinel);
    expect(logs).not.toContain(audioSentinel);
    expect(logs).not.toContain('client-token-sentinel');
    expect(logs).not.toContain('chat-secret');
    expect(logs).not.toContain('asr-secret');
  });

  it('rejects authentication, unknown fields, linkage, and named-choice errors before Chat', async () => {
    const provider = new FakeChatProvider();
    const app = buildApp({ config: loadConfig(environment()), chatProvider: provider });
    apps.push(app);
    const request = (payload: unknown, authorization = `Bearer ${token}`) =>
      app.inject({
        method: 'POST',
        url: '/api/v1/agent/completions',
        headers: { authorization, 'content-type': 'application/json' },
        payload: JSON.stringify(payload),
      });

    const missingAuth = await request(chatPayload(), '');
    const unknownField = await request({ ...chatPayload(), model: 'client-model' });
    const invalidLinkage = await request(
      chatPayload({
        messages: [
          { role: 'tool', content: '{}', toolCallId: 'call_missing', toolName: 'create_alarm' },
        ],
      }),
    );
    const invalidNamedChoice = await request(
      chatPayload({
        toolChoice: { mode: 'named', toolName: 'not_registered' },
      }),
    );

    for (const response of [missingAuth, unknownField, invalidLinkage, invalidNamedChoice]) {
      expect([401, 422]).toContain(response.statusCode);
      const body = jsonBody(response) as {
        readonly error: {
          readonly code: string;
          readonly requestId: string;
          readonly retryable: boolean;
        };
      };
      expect(body.error.code).toMatch(/unauthorized|invalid_request/);
      expect(body.error.requestId).toMatch(/^req-/);
      expect(body.error.retryable).toBe(false);
    }
    expect(provider.calls).toHaveLength(0);
  });

  it('rejects a Chat payload over 256 KiB before the provider', async () => {
    const provider = new FakeChatProvider();
    const app = buildApp({ config: loadConfig(environment()), chatProvider: provider });
    apps.push(app);
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/agent/completions',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
      payload: JSON.stringify(
        chatPayload({ messages: [{ role: 'user', content: 'x'.repeat(270_000) }] }),
      ),
    });

    expect(response.statusCode).toBe(413);
    expect(jsonBody(response)).toMatchObject({
      error: { code: 'payload_too_large', retryable: false },
    });
    expect(provider.calls).toHaveLength(0);
  });

  it('accepts one m4a upload and forwards bytes plus language to ASR', async () => {
    const provider = new FakeAsrProvider();
    const app = buildApp({ config: loadConfig(environment()), asrProvider: provider });
    apps.push(app);
    const audio = Buffer.from('m4a-sentinel-audio');
    const form = multipartForm(audio, { language: 'zh-HK' });

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/asr/transcriptions',
      headers: { authorization: `Bearer ${token}`, ...form.headers },
      payload: form.payload,
    });

    expect(response.statusCode).toBe(200);
    const responseBody = jsonBody(response) as {
      readonly requestId: string;
      readonly text: string;
    };
    expect(responseBody.requestId).toMatch(/^req-/);
    expect(responseBody.text).toBe('wake me at eight');
    expect(provider.calls).toHaveLength(1);
    expect(provider.calls[0]?.audio.equals(audio)).toBe(true);
    expect(provider.calls[0]?.language).toBe('zh-HK');
    expect(provider.calls[0]?.config).toMatchObject({
      apiKey: 'asr-secret',
      model: 'asr-model',
    });
  });

  it('rejects malformed multipart fields and oversized audio before ASR', async () => {
    const provider = new FakeAsrProvider();
    const app = buildApp({
      config: loadConfig(environment({ ASR_RATE_LIMIT_PER_MINUTE: '100' })),
      asrProvider: provider,
    });
    apps.push(app);
    const unknownFieldForm = multipartForm(Buffer.from('audio'), { model: 'client-model' });
    const oversizedForm = multipartForm(Buffer.alloc(10 * 1024 * 1024 + 1));

    const unknownField = await app.inject({
      method: 'POST',
      url: '/api/v1/asr/transcriptions',
      headers: { authorization: `Bearer ${token}`, ...unknownFieldForm.headers },
      payload: unknownFieldForm.payload,
    });
    const oversized = await app.inject({
      method: 'POST',
      url: '/api/v1/asr/transcriptions',
      headers: { authorization: `Bearer ${token}`, ...oversizedForm.headers },
      payload: oversizedForm.payload,
    });

    expect(unknownField.statusCode).toBe(422);
    expect(oversized.statusCode).toBe(413);
    expect(provider.calls).toHaveLength(0);
  });

  it('returns Retry-After and stops calling the provider when a source is rate limited', async () => {
    const provider = new FakeChatProvider();
    const app = buildApp({
      config: loadConfig(environment({ CHAT_RATE_LIMIT_PER_MINUTE: '1' })),
      chatProvider: provider,
    });
    apps.push(app);

    const request = () =>
      app.inject({
        method: 'POST',
        url: '/api/v1/agent/completions',
        headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
        payload: chatPayload(),
      });
    expect((await request()).statusCode).toBe(200);
    const limited = await request();

    expect(limited.statusCode).toBe(429);
    expect(Number(limited.headers['retry-after'])).toBeGreaterThanOrEqual(1);
    expect(limited.json()).toMatchObject({ error: { code: 'rate_limited', retryable: true } });
    expect(provider.calls).toHaveLength(1);
  });
});
