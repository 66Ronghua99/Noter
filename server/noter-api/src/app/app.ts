import multipart from '@fastify/multipart';
import Fastify, {
  type FastifyInstance,
  type FastifyRequest,
  type FastifyServerOptions,
} from 'fastify';

import type { NoterApiConfig } from '../config/config.js';
import { ASR_AUDIO_LIMIT_BYTES, CHAT_BODY_LIMIT_BYTES } from '../config/config.js';
import { NoterError, isNoterError, toErrorEnvelope } from '../errors.js';
import { CompatibleAsrProvider } from '../providers/asr-provider.js';
import type { AgentCompletionRequest, AsrProvider, ChatProvider } from '../providers/contracts.js';
import { CompatibleChatProvider } from '../providers/chat-provider.js';
import {
  assertBearerToken,
  ConcurrencyGate,
  RequestRateLimiter,
  trustedSourceIp,
} from '../plugins/security.js';
import { AsrService } from '../services/asr-service.js';
import { ChatService } from '../services/chat-service.js';
import { agentCompletionSchema } from '../routes/schemas.js';

export interface AppDependencies {
  readonly config: NoterApiConfig;
  readonly logger?: FastifyServerOptions['logger'];
  readonly chatProvider?: ChatProvider;
  readonly asrProvider?: AsrProvider;
}

export function buildApp(dependencies: AppDependencies): FastifyInstance {
  const app = Fastify({
    logger: dependencies.logger ?? false,
    bodyLimit: ASR_AUDIO_LIMIT_BYTES + 64 * 1024,
    ajv: {
      customOptions: {
        coerceTypes: false,
        removeAdditional: false,
        useDefaults: false,
      },
    },
  });
  const chatService = new ChatService(
    dependencies.config,
    dependencies.chatProvider ?? new CompatibleChatProvider(),
  );
  const asrService = new AsrService(
    dependencies.config,
    dependencies.asrProvider ?? new CompatibleAsrProvider(),
  );
  const chatRateLimiter = new RequestRateLimiter(dependencies.config.chat.rateLimitPerMinute);
  const asrRateLimiter = new RequestRateLimiter(dependencies.config.asr.rateLimitPerMinute);
  const chatConcurrency = new ConcurrencyGate(dependencies.config.chat.maxConcurrency);
  const asrConcurrency = new ConcurrencyGate(dependencies.config.asr.maxConcurrency);

  void app.register(multipart, {
    limits: {
      fileSize: ASR_AUDIO_LIMIT_BYTES,
      files: 1,
      fields: 1,
      parts: 2,
    },
    throwFileSizeLimit: true,
  });

  app.addHook('onRequest', async (request) => {
    if (request.url.startsWith('/api/v1/')) {
      assertBearerToken(request, dependencies.config.clientToken);
    }
    if (request.url.startsWith('/api/v1/agent/completions')) {
      const contentLength = Number(request.headers['content-length']);
      if (Number.isFinite(contentLength) && contentLength > CHAT_BODY_LIMIT_BYTES) {
        throw new NoterError('payload_too_large');
      }
    }
    await Promise.resolve();
  });

  app.addHook('onSend', async (request, reply) => {
    reply.header('x-request-id', request.id);
    await Promise.resolve();
  });

  app.addHook('onResponse', async (request, reply) => {
    request.log.info(
      {
        requestId: request.id,
        route: request.routeOptions.url,
        status: reply.statusCode,
        latencyMs: Math.round(reply.elapsedTime),
        responseSize: reply.getHeader('content-length') ?? undefined,
      },
      'request complete',
    );
    await Promise.resolve();
  });

  app.setErrorHandler((error, request, reply) => {
    const noterError = normalizeHttpError(error);
    if (noterError.retryAfterSeconds !== undefined) {
      reply.header('retry-after', String(noterError.retryAfterSeconds));
    }
    void reply.code(noterError.statusCode).send(toErrorEnvelope(noterError, request.id));
  });

  app.get('/health/live', () => ({ status: 'ok' }));
  app.get('/health/ready', () => ({ status: 'ok' }));

  app.post<{ Body: AgentCompletionRequest }>(
    '/api/v1/agent/completions',
    {
      schema: agentCompletionSchema,
      preValidation: async (request) => {
        validateAgentCompletionRequest(request.body);
        if (Buffer.byteLength(JSON.stringify(request.body), 'utf8') > CHAT_BODY_LIMIT_BYTES) {
          throw new NoterError('payload_too_large');
        }
        await Promise.resolve();
      },
    },
    async (request, reply) => {
      chatRateLimiter.check(trustedSourceIp(request));
      const release = chatConcurrency.enter();
      try {
        const response = await chatService.complete(
          request.body,
          requestAbortSignal(request),
          request.id,
        );
        return reply.send(response);
      } finally {
        release();
      }
    },
  );

  app.post('/api/v1/asr/transcriptions', async (request, reply) => {
    const input = await parseAudioUpload(request);
    asrRateLimiter.check(trustedSourceIp(request));
    const release = asrConcurrency.enter();
    try {
      const response = await asrService.transcribe(
        input.audio,
        input.language,
        requestAbortSignal(request),
        request.id,
      );
      return reply.send(response);
    } finally {
      release();
    }
  });

  return app;
}

function requestAbortSignal(request: FastifyRequest): AbortSignal {
  const controller = new AbortController();
  request.raw.once('close', () => controller.abort());
  return controller.signal;
}

function normalizeHttpError(error: unknown): NoterError {
  if (isNoterError(error)) {
    return error;
  }
  const candidate = asHttpError(error);
  if (
    candidate.code === 'FST_ERR_CTP_BODY_TOO_LARGE' ||
    candidate.code === 'FST_REQ_FILE_TOO_LARGE'
  ) {
    return new NoterError('payload_too_large');
  }
  if (
    candidate.code === 'FST_ERR_VALIDATION' ||
    candidate.statusCode === 400 ||
    candidate.statusCode === 415
  ) {
    return new NoterError('invalid_request');
  }
  return new NoterError('internal_error', { cause: candidate.error });
}

function asHttpError(error: unknown): {
  readonly error: unknown;
  readonly code?: string;
  readonly statusCode?: number;
} {
  if (typeof error !== 'object' || error === null) {
    return { error };
  }
  const candidate = error as { readonly code?: unknown; readonly statusCode?: unknown };
  return {
    error,
    ...(typeof candidate.code === 'string' ? { code: candidate.code } : {}),
    ...(typeof candidate.statusCode === 'number' ? { statusCode: candidate.statusCode } : {}),
  };
}

function validateAgentCompletionRequest(request: AgentCompletionRequest): void {
  const toolNames = new Set<string>();
  for (const tool of request.tools) {
    if (toolNames.has(tool.name)) {
      throw new NoterError('invalid_request');
    }
    toolNames.add(tool.name);
  }
  if (request.toolChoice.mode === 'named' && !toolNames.has(request.toolChoice.toolName)) {
    throw new NoterError('invalid_request');
  }

  const assistantCalls = new Map<string, string>();
  for (const message of request.messages) {
    if (message.role === 'assistant') {
      if (message.toolCallId || message.toolName) {
        throw new NoterError('invalid_request');
      }
      for (const call of message.toolCalls ?? []) {
        if (assistantCalls.has(call.id) || !toolNames.has(call.name)) {
          throw new NoterError('invalid_request');
        }
        assistantCalls.set(call.id, call.name);
      }
      continue;
    }
    if (message.role === 'tool') {
      if (!message.toolCallId || !message.toolName || message.toolCalls?.length) {
        throw new NoterError('invalid_request');
      }
      if (assistantCalls.get(message.toolCallId) !== message.toolName) {
        throw new NoterError('invalid_request');
      }
      continue;
    }
    if (message.toolCallId || message.toolName || message.toolCalls?.length) {
      throw new NoterError('invalid_request');
    }
  }
}

interface ParsedAudioUpload {
  readonly audio: Buffer;
  readonly language?: string;
}

async function parseAudioUpload(request: FastifyRequest): Promise<ParsedAudioUpload> {
  let audio: Buffer | undefined;
  let language: string | undefined;
  try {
    for await (const part of request.parts()) {
      if (part.type === 'file') {
        if (part.fieldname !== 'audio' || audio !== undefined) {
          throw new NoterError('invalid_request');
        }
        const chunks: Buffer[] = [];
        for await (const rawChunk of part.file) {
          const chunk: unknown = rawChunk;
          if (!Buffer.isBuffer(chunk) && !(chunk instanceof Uint8Array)) {
            throw new NoterError('invalid_request');
          }
          chunks.push(Buffer.from(chunk));
        }
        if (part.file.truncated) {
          throw new NoterError('payload_too_large');
        }
        audio = Buffer.concat(chunks);
        if (audio.length === 0) {
          throw new NoterError('invalid_request');
        }
        continue;
      }
      if (part.fieldname !== 'language' || language !== undefined) {
        throw new NoterError('invalid_request');
      }
      if (typeof part.value !== 'string') {
        throw new NoterError('invalid_request');
      }
      const value = part.value.trim();
      if (!isBcp47(value)) {
        throw new NoterError('invalid_request');
      }
      language = value;
    }
  } catch (error) {
    if (isNoterError(error)) {
      throw error;
    }
    if (error instanceof Error && /file.?size|too large/i.test(error.message)) {
      throw new NoterError('payload_too_large');
    }
    if (error instanceof Error && /limit|parts|files|fields/i.test(error.message)) {
      throw new NoterError('invalid_request');
    }
    throw new NoterError('invalid_request', { cause: error });
  }
  if (!audio) {
    throw new NoterError('invalid_request');
  }
  return language ? { audio, language } : { audio };
}

function isBcp47(value: string): boolean {
  return /^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$/.test(value);
}
