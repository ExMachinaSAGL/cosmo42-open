export interface ChatConversationListItemDTO {
  uuid: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  // omitting other pageable properties for brevity
}
