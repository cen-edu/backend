package com.cenedu.backend.domain.problem.authoring.semantic.persistence;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;

/** Converts normalized semantic contracts to reproducible JSON documents. */
public final class ProblemSemanticDocumentCodec {
    private final ObjectMapper mapper;

    public ProblemSemanticDocumentCodec(ObjectMapper mapper) {
        this.mapper = mapper.rebuild()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .build();
    }

    public SemanticModelDocument semanticModel(ProblemSemanticModelV1 model) {
        String json = write(model);
        return new SemanticModelDocument(model.schemaVersion(), json, hash(json));
    }

    public RenderSpecDocument renderSpec(DiagramSpecV1 spec, String rendererVersion) {
        String json = write(spec);
        return new RenderSpecDocument(1, json, hash(json), rendererVersion);
    }

    public ProblemSemanticModelV1 readSemanticModel(String json) { return read(json, ProblemSemanticModelV1.class); }
    public DiagramSpecV1 readRenderSpec(String json) { return read(json, DiagramSpecV1.class); }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalArgumentException("semantic document를 JSON으로 변환할 수 없습니다.", e); }
    }
    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JacksonException e) { throw new IllegalArgumentException("semantic document를 읽을 수 없습니다.", e); }
    }
    private String hash(String json) {
        try { return HexFormatLower.format(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static final class HexFormatLower { static String format(byte[] bytes) { StringBuilder b=new StringBuilder(64); for(byte x:bytes)b.append(String.format("%02x",x)); return b.toString(); } }
}
