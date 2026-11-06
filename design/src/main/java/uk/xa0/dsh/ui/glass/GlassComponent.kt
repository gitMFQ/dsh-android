// One switch per liquid-glass pane. See NOTICE.md: this package is the only place
// allowed to name a miuix-blur symbol, so the component catalog lives here next to
// `liquidPane`, and callers outside `:design` name only the opaque [GlassComponent].
package uk.xa0.dsh.ui.glass

/** Which settings section a [GlassComponent] is listed under. */
enum class GlassPaneSection { CONVERSATION, SCREENS, WIDGETS }

/**
 * A single pane that can wear the liquid material, addressable by its own switch.
 *
 * The reader asked to split the old one "cards and sheets" switch into one switch per
 * component, so a RenderThread crash can be bisected down to the exact pane that
 * triggers it. Each [key] is stored in `DshConfig.glassComponents` and carried by
 * [LocalGlassComponentsEnabled]; `liquidPane` lights the material for a call site only
 * when that site's [key] is in the enabled set.
 *
 * The set defaults empty: every pane stays byte-identical to the flat tier. Turning one
 * key on re-enters the path #70 removed, with `LaunchGuard` (#66) as the net. The
 * `liquidGlassLevel` dropdown is still the master ceiling — a component switch can only
 * take the material away, never grant what the level withheld.
 *
 * [key] is a stable, snake_case string: it is persisted in preferences, so renaming one
 * would silently turn that component back off on upgrade. Keep it in sync with the
 * `settings_glass_pane_<key>_title` string resources.
 */
enum class GlassComponent(val key: String, val section: GlassPaneSection) {
    VIEW_TABS("view_tabs", GlassPaneSection.CONVERSATION),
    SMALL_GLASS_PILL("small_glass_pill", GlassPaneSection.CONVERSATION),
    CONNECTION_PILL("connection_pill", GlassPaneSection.CONVERSATION),
    AGENT_PRESET_CHIP("agent_preset_chip", GlassPaneSection.CONVERSATION),
    SCROLL_TO_BOTTOM("scroll_to_bottom", GlassPaneSection.CONVERSATION),
    COMPOSER("composer", GlassPaneSection.CONVERSATION),
    READOUT_PILLS("readout_pills", GlassPaneSection.CONVERSATION),
    TODO_CARD("todo_card", GlassPaneSection.CONVERSATION),
    GOAL_BAR("goal_bar", GlassPaneSection.CONVERSATION),
    QUEUE_DOCK("queue_dock", GlassPaneSection.CONVERSATION),
    APPROVAL_CARD("approval_card", GlassPaneSection.CONVERSATION),
    QUESTION_CARD("question_card", GlassPaneSection.CONVERSATION),
    TOOL_COLLAPSE_BAR("tool_collapse_bar", GlassPaneSection.CONVERSATION),
    FILES_PANES("files_panes", GlassPaneSection.SCREENS),
    SETTINGS_BACK("settings_back", GlassPaneSection.SCREENS),
    SESSIONS_DRAWER("sessions_drawer", GlassPaneSection.SCREENS),
    CHOICE_CHIP("choice_chip", GlassPaneSection.SCREENS),
    COMMAND_MENU("command_menu", GlassPaneSection.SCREENS),
    SHEET("sheet", GlassPaneSection.WIDGETS),
    DIALOG("dialog", GlassPaneSection.WIDGETS),
    SIDE_RAIL("side_rail", GlassPaneSection.WIDGETS),
    SUPER_SEARCH_BAR("super_search_bar", GlassPaneSection.WIDGETS),
    SEGMENTED_LIST("segmented_list", GlassPaneSection.WIDGETS),
    SEARCH_STATUS("search_status", GlassPaneSection.WIDGETS),
    WARNING_CARD("warning_card", GlassPaneSection.WIDGETS),
    SCROLL_TO_TOP("scroll_to_top", GlassPaneSection.WIDGETS),
}
