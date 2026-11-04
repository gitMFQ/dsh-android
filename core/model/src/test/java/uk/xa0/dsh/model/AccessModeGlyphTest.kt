package uk.xa0.dsh.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which glyph the access-mode pill wears.
 *
 * The pill drew one shield for all three modes, so the row's most consequential state —
 * may the agent write, may it run anything — was only visible after opening a sheet.
 * The classification is pinned here because a picture cannot show that it is the *wrong*
 * picture, and because the host has spelled these presets several ways.
 */
class AccessModeGlyphTest {

    @Test
    fun `the three known modes get three distinct glyphs`() {
        assertEquals(AccessModeGlyph.READ_ONLY, accessModeGlyphOf("read-only"))
        assertEquals(AccessModeGlyph.WORKSPACE_WRITE, accessModeGlyphOf("workspace-write"))
        assertEquals(AccessModeGlyph.FULL_ACCESS, accessModeGlyphOf("full-access"))
    }

    /** The host has used camel and snake spellings of the tree preset. */
    @Test
    fun `the spellings the host has used all classify the same way`() {
        assertEquals(AccessModeGlyph.WORKSPACE_WRITE, accessModeGlyphOf("workspaceWrite"))
        assertEquals(AccessModeGlyph.WORKSPACE_WRITE, accessModeGlyphOf("workspace_write"))
        assertEquals(AccessModeGlyph.READ_ONLY, accessModeGlyphOf("readonly"))
        assertEquals(AccessModeGlyph.READ_ONLY, accessModeGlyphOf("READ-ONLY"))
    }

    /**
     * An unknown mode is *its own* glyph, not one of the three: drawing a new host mode
     * as "full access" would be the most dangerous possible guess.
     */
    @Test
    fun `an unknown preset is neither the most nor the least permissive`() {
        assertEquals(AccessModeGlyph.OTHER, accessModeGlyphOf("sandbox-only"))
        assertEquals(AccessModeGlyph.OTHER, accessModeGlyphOf(""))
        assertEquals(AccessModeGlyph.OTHER, accessModeGlyphOf(null))
        assertEquals(AccessModeGlyph.OTHER, accessModeGlyphOf("   "))
    }

    /** Unconfined access is spelled two ways in the descriptors. */
    @Test
    fun `unconfined spellings are full access`() {
        assertEquals(AccessModeGlyph.FULL_ACCESS, accessModeGlyphOf("unrestricted"))
        assertEquals(AccessModeGlyph.FULL_ACCESS, accessModeGlyphOf("full"))
    }
}