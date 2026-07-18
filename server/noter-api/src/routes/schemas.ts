export const agentCompletionSchema = {
  body: {
    type: 'object',
    additionalProperties: false,
    required: ['messages', 'tools', 'toolChoice'],
    properties: {
      messages: {
        type: 'array',
        minItems: 1,
        items: {
          type: 'object',
          additionalProperties: false,
          required: ['role', 'content'],
          properties: {
            role: { type: 'string', enum: ['system', 'user', 'assistant', 'tool'] },
            content: { type: 'string' },
            toolCallId: { type: 'string', minLength: 1 },
            toolName: { type: 'string', minLength: 1 },
            toolCalls: {
              type: 'array',
              items: {
                type: 'object',
                additionalProperties: false,
                required: ['id', 'name', 'arguments'],
                properties: {
                  id: { type: 'string', minLength: 1 },
                  name: { type: 'string', minLength: 1 },
                  arguments: { type: 'string' },
                },
              },
            },
          },
        },
      },
      tools: {
        type: 'array',
        items: {
          type: 'object',
          additionalProperties: false,
          required: ['name', 'description', 'parameters'],
          properties: {
            name: { type: 'string', minLength: 1 },
            description: { type: 'string' },
            parameters: { type: 'object' },
          },
        },
      },
      toolChoice: {
        oneOf: [
          {
            type: 'object',
            additionalProperties: false,
            required: ['mode'],
            properties: { mode: { const: 'auto' } },
          },
          {
            type: 'object',
            additionalProperties: false,
            required: ['mode'],
            properties: { mode: { const: 'required' } },
          },
          {
            type: 'object',
            additionalProperties: false,
            required: ['mode', 'toolName'],
            properties: {
              mode: { const: 'named' },
              toolName: { type: 'string', minLength: 1 },
            },
          },
        ],
      },
    },
  },
} as const;
