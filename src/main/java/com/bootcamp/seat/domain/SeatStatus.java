package com.bootcamp.seat.domain;

/**
 * 좌석(수강생) 상태. 라벨·색·아이콘·타이머 기준의 유일한 정의 위치.
 */
public enum SeatStatus {

    IN("입실", "#10b981", "fa-sign-in-alt", null, null),
    OUT("퇴실", "#64748b", "fa-sign-out-alt", null, null),
    WC("화장실", "#0ea5e9", "fa-restroom", null, 10),
    SMOKE("흡연", "#f59e0b", "fa-smoking", null, 10),
    STORE("편의점", "#C2410C", "fa-shopping-cart", null, 10),
    ABSENT("결석", "#ef4444", "fa-user-times", null, null),
    LATE("지각", "#06b6d4", "fa-clock", null, null),
    OUT_OFFICE("외출", "#6366f1", "fa-building", 90, 120),
    LUNCH("점심시간", "#f97316", "fa-utensils", null, null),
    GIVEUP("중도포기", "#94a3b8", "fa-user-slash", null, null);

    private final String label;
    private final String color;
    private final String icon;
    /** 경고 시작(분). null 이면 경고 단계 없음 */
    private final Integer warningMinutes;
    /** 위험(깜박임) 기준(분). null 이면 타이머 대상 아님 */
    private final Integer dangerMinutes;

    SeatStatus(String label, String color, String icon, Integer warningMinutes, Integer dangerMinutes) {
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.warningMinutes = warningMinutes;
        this.dangerMinutes = dangerMinutes;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    public Integer getWarningMinutes() {
        return warningMinutes;
    }

    public Integer getDangerMinutes() {
        return dangerMinutes;
    }

    public boolean isTimed() {
        return dangerMinutes != null;
    }
}
