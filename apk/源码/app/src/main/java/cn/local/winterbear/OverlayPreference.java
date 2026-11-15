package cn.local.winterbear;

/** A manual expansion stays selected until the user folds the panel again. */
final class OverlayPreference {
    private boolean expandedByUser;
    void choose(boolean collapsed){expandedByUser=!collapsed;}
    boolean keepExpanded(){return expandedByUser;}
}
