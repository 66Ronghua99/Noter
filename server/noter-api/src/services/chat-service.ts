import type {
  AgentCompletionRequest,
  AgentCompletionResponse,
  ChatProvider,
} from '../providers/contracts.js';
import type { NoterApiConfig } from '../config/config.js';

export class ChatService {
  public constructor(
    private readonly config: NoterApiConfig,
    private readonly provider: ChatProvider,
  ) {}

  public async complete(
    request: AgentCompletionRequest,
    signal: AbortSignal,
    requestId: string,
  ): Promise<AgentCompletionResponse> {
    const result = await this.provider.complete({
      ...request,
      config: this.config.chat,
      signal,
    });
    return { requestId, message: result.message };
  }
}
