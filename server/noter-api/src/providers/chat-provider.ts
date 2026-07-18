import type {
  AgentMessage,
  AgentTool,
  ChatProvider,
  ProviderChatRequest,
  ProviderChatResponse,
  ToolChoice,
} from './contracts.js';
import { NoterError } from '../errors.js';
import {
  createTimedSignal,
  fetchUpstream,
  mapUpstreamStatus,
  readJson,
  requireRecord,
} from './http.js';

export class CompatibleChatProvider implements ChatProvider {
  public async complete(request: ProviderChatRequest): Promise<ProviderChatResponse> {
    const timedSignal = createTimedSignal(request.signal, request.config.timeoutMs);
    try {
      const upstreamResponse = await fetchUpstream(
        request.config.url,
        {
          method: 'POST',
          headers: {
            authorization: `Bearer ${request.config.apiKey}`,
            'content-type': 'application/json',
          },
          body: JSON.stringify({
            model: request.config.model,
            messages: request.messages.map(toProviderMessage),
            tools: request.tools.map(toProviderTool),
            tool_choice: toProviderToolChoice(request.toolChoice),
            parallel_tool_calls: false,
            max_tokens: request.config.maxOutputTokens,
          }),
        },
        timedSignal,
      );

      if (!upstreamResponse.ok) {
        await upstreamResponse.arrayBuffer();
        throw mapUpstreamStatus(upstreamResponse.status);
      }
      const payload = await readJson(upstreamResponse);
      return {
        message: parseAssistantMessage(payload),
        upstreamStatus: upstreamResponse.status,
      };
    } finally {
      timedSignal.dispose();
    }
  }
}

function toProviderMessage(message: AgentMessage): Record<string, unknown> {
  return {
    role: message.role,
    content: message.content,
    ...(message.toolCallId ? { tool_call_id: message.toolCallId } : {}),
    ...(message.toolName ? { name: message.toolName } : {}),
    ...(message.toolCalls && message.toolCalls.length > 0
      ? {
          tool_calls: message.toolCalls.map((call) => ({
            id: call.id,
            type: 'function',
            function: { name: call.name, arguments: call.arguments },
          })),
        }
      : {}),
  };
}

function toProviderTool(tool: AgentTool): Record<string, unknown> {
  return {
    type: 'function',
    function: {
      name: tool.name,
      description: tool.description,
      parameters: tool.parameters,
    },
  };
}

function toProviderToolChoice(choice: ToolChoice): unknown {
  switch (choice.mode) {
    case 'auto':
      return 'auto';
    case 'required':
      return 'required';
    case 'named':
      return { type: 'function', function: { name: choice.toolName } };
  }
}

function parseAssistantMessage(payload: unknown): AgentMessage {
  const root = requireRecord(payload);
  const choices = root.choices;
  if (!Array.isArray(choices) || choices.length === 0) {
    throw new NoterError('invalid_upstream_response');
  }
  const firstChoice = requireRecord(choices[0]);
  const rawMessage = requireRecord(firstChoice.message);
  if (rawMessage.role !== undefined && rawMessage.role !== 'assistant') {
    throw new NoterError('invalid_upstream_response');
  }
  const content =
    rawMessage.content === null || rawMessage.content === undefined
      ? ''
      : typeof rawMessage.content === 'string'
        ? rawMessage.content
        : (() => {
            throw new NoterError('invalid_upstream_response');
          })();
  const rawToolCalls = rawMessage.tool_calls;
  const toolCalls =
    rawToolCalls === undefined
      ? []
      : !Array.isArray(rawToolCalls)
        ? (() => {
            throw new NoterError('invalid_upstream_response');
          })()
        : rawToolCalls.map(parseToolCall);
  if (content.trim().length === 0 && toolCalls.length === 0) {
    throw new NoterError('invalid_upstream_response');
  }
  return {
    role: 'assistant',
    content,
    ...(toolCalls.length > 0 ? { toolCalls } : {}),
  };
}

function parseToolCall(value: unknown): { id: string; name: string; arguments: string } {
  const toolCall = requireRecord(value);
  const id = toolCall.id;
  const functionValue = requireRecord(toolCall.function);
  const name = functionValue.name;
  const args = functionValue.arguments;
  if (
    typeof id !== 'string' ||
    id.length === 0 ||
    typeof name !== 'string' ||
    name.length === 0 ||
    typeof args !== 'string'
  ) {
    throw new NoterError('invalid_upstream_response');
  }
  return { id, name, arguments: args };
}
