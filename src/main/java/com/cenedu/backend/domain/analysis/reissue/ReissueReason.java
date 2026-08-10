package com.cenedu.backend.domain.analysis.reissue;

import java.util.List;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.entity.LearningStatus;
import com.cenedu.backend.global.common.enums.DisplayLabels;

/**
 * 왜 이렇게 골랐는지를 세 칸을 아울러 한 번에 설명한다.
 *
 * <p>화면이 상태 코드로 문장을 지으면 같은 규칙이 두 곳에 생기고, 규칙을 바꿀 때 한쪽만
 * 고쳐진다. 서버가 만들어 보낸다.
 *
 * <p>칸마다 따로 적지 않고 한 덩어리로 쓰는 이유는, 교사가 알아야 하는 것이 "복습이 왜 1개인가"
 * 가 아니라 <b>"이 학생에게 왜 이 구성인가"</b> 이기 때문이다. 0인 칸도 왜 0인지 적는다.
 */
final class ReissueReason {

    private ReissueReason() {
    }

    static String of(List<ReissueProposalService.Config> configs) {
        if (configs.isEmpty()) {
            return "이 회차에서 취약 개념을 찾지 못했습니다. 뱅크 소단원 대응이 없는 개념만 "
                    + "있었을 수도 있습니다.";
        }

        StringBuilder text = new StringBuilder();
        text.append("이 회차에서 개념 ").append(configs.size()).append("개를 살펴봤습니다. ")
                .append(overview(configs)).append("\n\n");

        text.append(stageParagraph(configs, ReissueStage.RETRACE)).append("\n\n")
                .append(stageParagraph(configs, ReissueStage.BASIC)).append("\n\n")
                .append(stageParagraph(configs, ReissueStage.INDEPENDENT));

        text.append("\n\n선정에는 소단원과 난이도만 씁니다. 평가 영역·소주제·풀이 구간은 "
                + "교사 화면에 표시하지만 문항을 고르는 데는 쓰지 않습니다. 분류로 좁히면 "
                + "재고가 마르고, 실제로 유사도도 올라가지 않았습니다.");
        return text.toString();
    }

    private static String overview(List<ReissueProposalService.Config> configs) {
        long support = configs.stream().filter(c -> statusIs(c, "NEEDS_SUPPORT")).count();
        long watch = configs.stream().filter(c -> statusIs(c, "WATCH")).count();
        long clear = configs.stream().filter(c -> c.focus().noErrorLeft()).count();
        StringBuilder parts = new StringBuilder();
        if (support > 0) {
            parts.append("집중 지도가 필요한 개념 ").append(support).append("개, ");
        }
        if (watch > 0) {
            parts.append("다시 확인할 개념 ").append(watch).append("개, ");
        }
        if (clear > 0) {
            parts.append("오류가 없던 개념 ").append(clear).append("개, ");
        }
        return parts.isEmpty() ? "" : parts.substring(0, parts.length() - 2) + "입니다.";
    }

    private static String stageParagraph(
            List<ReissueProposalService.Config> configs, ReissueStage stage) {
        int total = configs.stream().mapToInt(c -> c.counts().get(stage)).sum();
        String head = stage.label() + " " + total + "문항 — ";
        List<ReissueProposalService.Config> used = configs.stream()
                .filter(c -> c.counts().get(stage) > 0).toList();

        if (total == 0) {
            return head + emptyReason(stage);
        }
        String detail = used.stream().map(c -> detail(c, stage))
                .collect(Collectors.joining(" "));
        return head + reasonHead(stage) + " " + detail;
    }

    private static String reasonHead(ReissueStage stage) {
        return switch (stage) {
            case RETRACE -> "시스템 오류로 답이 기록되지 않은 문항입니다. 학생이 틀린 것이 "
                    + "아니라 재지 못한 것이라 판정에서 뺐고, 원본 문항을 그대로 다시 냅니다.";
            case BASIC -> "오류가 관찰된 개념이라 같은 소단원에서 다른 문항을 고릅니다. "
                    + "이미 낸 문항과 이미지 문항은 빼고, 빈칸이 적은 것부터 냅니다.";
            case INDEPENDENT -> "상 난이도까지 올라와 확인할 오류가 없는 개념입니다. "
                    + "다른 맥락으로 옮길 수 있는지 봅니다.";
        };
    }

    private static String emptyReason(ReissueStage stage) {
        return switch (stage) {
            case RETRACE -> "시스템 오류로 기록되지 않은 문항이 없습니다. 답을 쓴 문항을 그대로 "
                    + "다시 내면 실력이 아니라 기억을 재게 되므로 복습은 이 경우에만 냅니다.";
            case BASIC -> "확인할 오류가 관찰된 개념이 없습니다. 오류가 없는 개념에 유사 문항을 "
                    + "내도 볼 것이 없습니다.";
            case INDEPENDENT -> "상 난이도까지 올라와 오류가 없는 개념이 아직 없습니다. "
                    + "사다리를 끝까지 올라간 학생만 응용 대상입니다.";
        };
    }

    private static String detail(ReissueProposalService.Config config, ReissueStage stage) {
        ConceptFocus focus = config.focus();
        String name = DisplayLabels.difficulty(focus.nextDifficulty().band());
        int count = config.counts().get(stage);

        return switch (stage) {
            case RETRACE -> focus.bankUnit() + "에서 " + count + "문항("
                    + String.join(", ", focus.lostProblemIds()) + ")입니다.";
            case BASIC -> {
                String moved = focus.nextDifficulty() == focus.dwell()
                        ? "이전 문제지와 같은 " + name + " 난이도로"
                        : "이전 문제지의 "
                                + DisplayLabels.difficulty(focus.dwell().band())
                                + " 난이도에서 " + name + "으로 옮겨";
                String why = focus.state() != null
                        && focus.state().status() == LearningStatus.NEEDS_SUPPORT
                        ? "서로 다른 문항 " + focus.sourceQuestionNos().size()
                                + "개를 틀려 지원이 필요한 상태라"
                        : "오류가 보여";
                yield focus.bankUnit() + topicParticle(focus.bankUnit()) + " "
                        + why + " " + moved + " " + count + "문항.";
            }
            case INDEPENDENT -> focus.bankUnit() + "에서 " + count
                    + "문항입니다. 다만 응용 문항은 생성이 필요해 아직 나가지 않습니다.";
        };
    }

    /**
     * 앞 글자의 받침에 따라 "은/는"을 고른다.
     *
     * <p>소단원명이 데이터에서 오므로 문장에 조사를 박아 둘 수 없다. "최대공약수와 최소공배수은"
     * 처럼 어색한 문장이 교사 화면에 그대로 나간다.
     */
    private static String topicParticle(String word) {
        if (word == null || word.isBlank()) {
            return "는";
        }
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) {
            return "는";
        }
        return (last - 0xAC00) % 28 == 0 ? "는" : "은";
    }

    private static boolean statusIs(ReissueProposalService.Config config, String status) {
        return config.focus().state() != null
                && config.focus().state().status().name().equals(status);
    }
}
