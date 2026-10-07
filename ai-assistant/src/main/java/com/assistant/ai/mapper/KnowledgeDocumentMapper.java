package com.assistant.ai.mapper;

import com.assistant.ai.knowledge.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper {

    int insert(Document document);

    List<Document> findByOwner(@Param("ownerUserId") String ownerUserId);
}