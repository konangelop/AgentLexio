import type { ChatRequest, ChatResponse } from './types';

const API_BASE_URL = 'http://localhost:8080/api';

export async function sendMessage(message: string): Promise<ChatResponse> {
  const request: ChatRequest = { message };

  const response = await fetch(`${API_BASE_URL}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return response.json();
}

export async function checkHealth(): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/chat/health`);

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return response.text();
}
