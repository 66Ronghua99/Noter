import type { NoterApiConfig } from '../config/config.js';

export type AgentRole = 'system' | 'user' | 'assistant' | 'tool';

export interface AgentToolCall {
  readonly id: string;
  readonly name: string;
  readonly arguments: string;
}

export interface AgentMessage {
  readonly role: AgentRole;
  readonly content: string;
  readonly toolCallId?: string;
  readonly toolName?: string;
  readonly toolCalls?: readonly AgentToolCall[];
}

export interface AgentTool {
  readonly name: string;
  readonly description: string;
  readonly parameters: Record<string, unknown>;
}

export type ToolChoice =
  | { readonly mode: 'auto' }
  | { readonly mode: 'required' }
  | { readonly mode: 'named'; readonly toolName: string };

export interface AgentCompletionRequest {
  readonly messages: readonly AgentMessage[];
  readonly tools: readonly AgentTool[];
  readonly toolChoice: ToolChoice;
}

export interface AgentCompletionResponse {
  readonly requestId: string;
  readonly message: AgentMessage;
}

export interface ChatProvider {
  complete(request: ProviderChatRequest): Promise<ProviderChatResponse>;
}

export interface ProviderChatRequest {
  readonly messages: readonly AgentMessage[];
  readonly tools: readonly AgentTool[];
  readonly toolChoice: ToolChoice;
  readonly config: NoterApiConfig['chat'];
  readonly signal: AbortSignal;
}

export interface ProviderChatResponse {
  readonly message: AgentMessage;
  readonly upstreamStatus: number;
}

export interface AsrProvider {
  transcribe(request: ProviderAsrRequest): Promise<ProviderAsrResponse>;
}

export interface ProviderAsrRequest {
  readonly audio: Buffer;
  readonly language?: string;
  readonly config: NoterApiConfig['asr'];
  readonly signal: AbortSignal;
}

export interface ProviderAsrResponse {
  readonly text: string;
  readonly upstreamStatus: number;
}
