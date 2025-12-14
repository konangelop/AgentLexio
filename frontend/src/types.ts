export interface ChatRequest {
  message: string;
}

export interface ChatResponse {
  message: string;
  success: boolean;
  error: string | null;
}

export interface Message {
  id: string;
  content: string;
  sender: 'user' | 'assistant';
  timestamp: Date;
}
