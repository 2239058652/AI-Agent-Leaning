package com.assistant.ai.knowledge;

import lombok.Data;

@Data
public class Document {

    private Long id;

    private String sourceName;

    private String sourceType;

    private String version;

    private String content;

    private String title;

    private String ownerUserId;
}