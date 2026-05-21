export interface ChatConversationListItemDTO {
  uuid: string;
  title: string;
}

export interface Page<T> {
  content: T[];
  // omitting other pageable properties for brevity
}
