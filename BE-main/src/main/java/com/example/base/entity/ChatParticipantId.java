package com.example.base.entity;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ChatParticipantId implements Serializable {
    private Long conversationId;
    private Long accountId;
}
