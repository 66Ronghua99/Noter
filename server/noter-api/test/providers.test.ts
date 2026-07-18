import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'node:http';

import { afterEach, describe, expect, it } from 'vitest';

import { loadConfig } from '../src/config/config.js';
import { CompatibleAsrProvider } from '../src/providers/asr-provider.js';
import type { ProviderAsrRequest, ProviderChatRequest } from '../src/providers/contracts.js';
import { CompatibleChatProvider } from '../src/providers/chat-provider.js';

const servers: Server[] = [];

afterEach(async () => {
  await Promise.all(
    servers
      .splice(0)
      .map(
        (server) =>
          new Promise<void>((resolve, reject) =>
            server.close((error) => (error ? reject(error) : resolve())),
          ),
      ),
  );
});

async function startServer(
  handler: (request: IncomingMessage, response: ServerResponse) => void | Promise<void>,
): Promise<string> {
  const server = createServer((request, response) => {
    void handler(request, response);
  });
  servers.push(server);
  await new Promise<void>((resolve, reject) => {
    server.listen(0, '127.0.0.1', () => resolve());
    server.once('error', reject);
  });
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('Test server did not expose a TCP address.');
  }
  return `http://127.0.0.1:${address.port}/upstream`;
}

async function readBody(request: IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const rawChunk of request) {
    const chunk: unknown = rawChunk;
    if (!Buffer.isBuffer(chunk) && !(chunk instanceof Uint8Array)) {
      throw new Error('Test server received a non-binary request chunk.');
    }
    chunks.push(Buffer.from(chunk));
  }
  return Buffer.concat(chunks).toString('utf8');
}

function config(chatUrl: string, asrUrl = chatUrl, overrides: Record<string, string> = {}) {
  return loadConfig({
    NOTER_CLIENT_TOKEN: 'app-token',
    NOTER_API_DOMAIN: 'api.example.test',
    CHAT_UPSTREAM_URL: chatUrl,
    CHAT_UPSTREAM_API_KEY: 'chat-secret',
    CHAT_UPSTREAM_MODEL: 'chat-model',
    ASR_UPSTREAM_URL: asrUrl,
    ASR_UPSTREAM_API_KEY: 'asr-secret',
    ASR_UPSTREAM_MODEL: 'asr-model',
    ...overrides,
  });
}

function chatRequest(
  url: string,
  toolChoice: ProviderChatRequest['toolChoice'] = { mode: 'required' },
): ProviderChatRequest {
  return {
    messages: [
      { role: 'system', content: 'You create alarms.' },
      { role: 'user', content: 'wake me at eight' },
    ],
    tools: [
      { name: 'create_alarm', description: 'Create an alarm.', parameters: { type: 'object' } },
    ],
    toolChoice,
    config: config(url).chat,
    signal: new AbortController().signal,
  };
}

function asrRequest(url: string): ProviderAsrRequest {
  return {
    audio: Buffer.from('audio-sentinel'),
    language: 'en-US',
    config: config('http://127.0.0.1:9/chat', url).asr,
    signal: new AbortController().signal,
  };
}

describe('compatible upstream providers', () => {
  it('injects server Chat model/key and maps tool calls without retrying', async () => {
    const url = await startServer(async (request, response) => {
      expect(request.headers.authorization).toBe('Bearer chat-secret');
      expect(request.headers['content-type']).toBe('application/json');
      const body = JSON.parse(await readBody(request)) as {
        model: string;
        max_tokens: number;
        parallel_tool_calls: boolean;
        tool_choice: unknown;
        messages: unknown;
        tools: unknown;
      };
      expect(body).toMatchObject({
        model: 'chat-model',
        max_tokens: 512,
        parallel_tool_calls: false,
        tool_choice: 'required',
      });
      expect(body.messages).toEqual([
        { role: 'system', content: 'You create alarms.' },
        { role: 'user', content: 'wake me at eight' },
      ]);
      expect(body.tools).toEqual([
        {
          type: 'function',
          function: {
            name: 'create_alarm',
            description: 'Create an alarm.',
            parameters: { type: 'object' },
          },
        },
      ]);
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(
        JSON.stringify({
          choices: [
            {
              message: {
                role: 'assistant',
                content: null,
                tool_calls: [
                  {
                    id: 'call_42',
                    type: 'function',
                    function: { name: 'create_alarm', arguments: '{}' },
                  },
                ],
              },
            },
          ],
        }),
      );
    });

    const result = await new CompatibleChatProvider().complete(chatRequest(url));

    expect(result).toEqual({
      upstreamStatus: 200,
      message: {
        role: 'assistant',
        content: '',
        toolCalls: [{ id: 'call_42', name: 'create_alarm', arguments: '{}' }],
      },
    });
  });

  it('maps named tool choice and rejects malformed Chat responses', async () => {
    let requestBody = '';
    const url = await startServer(async (request, response) => {
      requestBody = await readBody(request);
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(
        JSON.stringify({ choices: [{ message: { role: 'assistant', content: 'done' } }] }),
      );
    });
    const result = await new CompatibleChatProvider().complete(
      chatRequest(url, { mode: 'named', toolName: 'create_alarm' }),
    );
    const parsed = JSON.parse(requestBody) as { tool_choice: unknown };

    expect(parsed.tool_choice).toEqual({ type: 'function', function: { name: 'create_alarm' } });
    expect(result.message).toEqual({ role: 'assistant', content: 'done' });

    const invalidUrl = await startServer((_request, response) => {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end(JSON.stringify({ choices: [] }));
    });
    await expect(
      new CompatibleChatProvider().complete(chatRequest(invalidUrl)),
    ).rejects.toMatchObject({
      code: 'invalid_upstream_response',
      retryable: false,
    });
  });

  it('maps upstream failures and timeouts without issuing a second paid request', async () => {
    let calls = 0;
    const unavailableUrl = await startServer((_request, response) => {
      calls += 1;
      response.writeHead(503, { 'content-type': 'application/json' });
      response.end('{"error":{"message":"provider sentinel"}}');
    });
    await expect(
      new CompatibleChatProvider().complete(chatRequest(unavailableUrl)),
    ).rejects.toMatchObject({
      code: 'upstream_unavailable',
      retryable: true,
    });
    expect(calls).toBe(1);

    const timeoutUrl = await startServer((_request, response) => {
      setTimeout(() => response.end('{"choices":[]}'), 100);
    });
    await expect(
      new CompatibleChatProvider().complete({
        ...chatRequest(timeoutUrl),
        config: config(timeoutUrl, timeoutUrl, { CHAT_UPSTREAM_TIMEOUT_MS: '10' }).chat,
      }),
    ).rejects.toMatchObject({ code: 'upstream_timeout' });
  });

  it('converts m4a bytes to the configured ASR JSON shape and normalizes text', async () => {
    const url = await startServer(async (request, response) => {
      expect(request.headers.authorization).toBe('Bearer asr-secret');
      const body = JSON.parse(await readBody(request)) as {
        model: string;
        language: string;
        input_audio: { data: string; format: string };
      };
      expect(body.model).toBe('asr-model');
      expect(body.language).toBe('en-US');
      expect(body.input_audio).toEqual({
        data: Buffer.from('audio-sentinel').toString('base64'),
        format: 'm4a',
      });
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end('{"text":"  wake me at eight  "}');
    });

    await expect(new CompatibleAsrProvider().transcribe(asrRequest(url))).resolves.toEqual({
      text: 'wake me at eight',
      upstreamStatus: 200,
    });

    const invalidUrl = await startServer((_request, response) => {
      response.writeHead(200, { 'content-type': 'application/json' });
      response.end('{"text":" "}');
    });
    await expect(
      new CompatibleAsrProvider().transcribe(asrRequest(invalidUrl)),
    ).rejects.toMatchObject({
      code: 'invalid_upstream_response',
    });
  });
});
