package io.repolens.web;

import io.repolens.analyzers.context.AiPromptGenerator;
import io.repolens.analyzers.context.AiPromptResult;
import io.repolens.analyzers.context.AiTask;
import io.repolens.analyzers.context.AiTaskPreset;
import io.repolens.analyzers.context.ContextBudget;
import io.repolens.analyzers.context.ContextFormat;
import io.repolens.analyzers.context.ContextPurpose;
import io.repolens.analyzers.context.ContextRequest;
import io.repolens.analyzers.context.ContextResult;
import io.repolens.analyzers.context.ContextScope;
import io.repolens.analyzers.context.ContextStrategy;
import io.repolens.api.ContextRequestDto;
import io.repolens.api.ContextResponseDto;

final class ContextDtoMapper {

    private ContextDtoMapper() {
    }

    static ContextRequest toDomain(ContextRequestDto dto) {
        ContextPurpose purpose = ContextPurpose.valueOf(normalizeEnum(dto.purpose()));
        ContextFormat format = parseFormat(dto.format());
        int budget = dto.tokenBudget() == null ? 8_000 : dto.tokenBudget();
        ContextScope.ScopeMode mode = ContextScope.ScopeMode.valueOf(
                normalizeEnum(dto.scope() == null ? "ENTIRE_REPOSITORY" : dto.scope().mode()));
        ContextScope scope = new ContextScope(
                mode,
                dto.scope() == null ? null : dto.scope().filePaths(),
                dto.scope() == null ? null : dto.scope().symbolIds(),
                dto.scope() == null ? null : dto.scope().graphNodeIds()
        );
        ContextStrategy strategy = ContextStrategy.ARCHITECTURE_OVERVIEW;
        if (dto.strategy() != null && !dto.strategy().isBlank()) {
            strategy = ContextStrategy.valueOf(normalizeEnum(dto.strategy()));
        }
        return new ContextRequest(purpose, scope, ContextBudget.of(budget), format, dto.title(), strategy);
    }

    static AiTask toAiTask(ContextRequestDto dto) {
        if (dto.aiTask() == null || dto.aiTask().isBlank()) {
            return null;
        }
        AiTaskPreset preset = AiTaskPreset.valueOf(normalizeEnum(dto.aiTask()));
        String custom = dto.customTask() == null ? "" : dto.customTask().trim();
        return new AiTask(preset, custom);
    }

    static ContextResponseDto toDto(ContextResult result) {
        return toDto(result, null);
    }

    static ContextResponseDto toDto(ContextResult result, AiPromptResult prompt) {
        return new ContextResponseDto(
                result.id(),
                result.title(),
                result.purpose().name(),
                result.scope().mode().name(),
                result.format().name().toLowerCase(),
                result.tokenBudget(),
                result.estimatedTokens(),
                result.tokenEstimateApproximate(),
                result.content(),
                result.included(),
                result.excluded(),
                new ContextResponseDto.ScopeEchoDto(
                        result.scope().filePaths(),
                        result.scope().symbolIds(),
                        result.scope().graphNodeIds()
                ),
                prompt == null ? null : prompt.preset().name(),
                prompt == null ? null : prompt.taskText(),
                prompt == null ? null : prompt.prompt(),
                result.strategy().name(),
                result.strategy().displayName()
        );
    }

    static AiPromptResult buildPrompt(AiTask task, ContextResult result) {
        return new AiPromptGenerator().generate(task, result);
    }

    private static ContextFormat parseFormat(String format) {
        String normalized = normalizeEnum(format);
        if ("MD".equals(normalized)) {
            return ContextFormat.MARKDOWN;
        }
        return ContextFormat.valueOf(normalized);
    }

    private static String normalizeEnum(String value) {
        return value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }
}
