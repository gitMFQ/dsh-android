package uk.xa0.dsh.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plugin inventory read.
 *
 * The fixture shapes here are copied from the host's own type declarations
 * (`dsh-host-plugin-inventory/lib/types/types.d.ts`), not invented: `fiberPhase` is
 * one of six literals or null, `enabled` is the Loader's *effective* answer, and
 * `meta` is optional. A parser tested against a shape this app made up would prove
 * nothing about the host it talks to.
 */
class PluginInventoryTest {

    private fun snapshot(entries: String, management: Boolean? = null): JSONObject {
        val json = JSONObject()
        json.put("entries", org.json.JSONArray(entries))
        if (management != null) json.put("managementAvailable", management)
        return json
    }

    @Test
    fun `an entry keeps its specifier, its own name and its description`() {
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """
                [{
                  "entryId": "e1",
                  "moduleName": "@deepseek-ai/dsh-client-ui-jobs",
                  "meta": { "name": "Jobs", "description": "Background work." },
                  "enabled": true,
                  "fiberPhase": "active"
                }]
                """,
            ),
        )

        val entry = inventory.entries.single()
        assertEquals("@deepseek-ai/dsh-client-ui-jobs", entry.moduleName)
        assertEquals("Jobs", entry.displayName)
        assertEquals("Background work.", entry.description)
        assertEquals("Jobs", entry.label)
    }

    @Test
    fun `an entry with no display name falls back to its specifier`() {
        // The specifier is usually scoped and long, so it is the *fallback* — but it is
        // never dropped: the row shows it as its second line.
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """[{"entryId":"e1","moduleName":"some-plugin","enabled":true,"fiberPhase":"active"}]""",
            ),
        )

        assertEquals("some-plugin", inventory.entries.single().label)
        assertNull(inventory.entries.single().displayName)
    }

    @Test
    fun `enabled and live are different facts and are kept apart`() {
        // An entry can be enabled with no live root Fiber (this profile does not mount
        // it), which is the case a single "on" light would have to lie about.
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """[{"entryId":"e1","moduleName":"a","enabled":true,"fiberPhase":null}]""",
            ),
        )

        val entry = inventory.entries.single()
        assertTrue(entry.enabled)
        assertFalse(entry.live)
        assertEquals(PluginState.IDLE, entry.state)
    }

    @Test
    fun `an active entry is live`() {
        val inventory = PluginInventoryWire.parse(
            snapshot("""[{"entryId":"e1","moduleName":"a","enabled":true,"fiberPhase":"active"}]"""),
        )

        assertTrue(inventory.entries.single().live)
        assertEquals(PluginState.ACTIVE, inventory.entries.single().state)
    }

    @Test
    fun `a failed entry carries the host's own message`() {
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """
                [{"entryId":"e1","moduleName":"a","enabled":true,"fiberPhase":"failed",
                  "diagnostic":"Cannot find module 'nope'"}]
                """,
            ),
        )

        val entry = inventory.entries.single()
        assertTrue(entry.failed)
        assertEquals(PluginState.FAILED, entry.state)
        // Verbatim: a paraphrased failure is one the reader cannot search for.
        assertEquals("Cannot find module 'nope'", entry.diagnostic)
    }

    @Test
    fun `a disabled entry is disabled, not merely idle`() {
        val inventory = PluginInventoryWire.parse(
            snapshot("""[{"entryId":"e1","moduleName":"a","enabled":false,"fiberPhase":null}]"""),
        )

        assertEquals(PluginState.DISABLED, inventory.entries.single().state)
    }

    @Test
    fun `the three transition phases are all not-yet-running`() {
        // `pending`, `loading` and `unloading` are not "running right now", which is the
        // claim the dot makes. The precise phase stays on the row.
        for (phase in listOf("pending", "loading", "unloading")) {
            val inventory = PluginInventoryWire.parse(
                snapshot(
                    """[{"entryId":"e1","moduleName":"a","enabled":true,"fiberPhase":"$phase"}]""",
                ),
            )

            val entry = inventory.entries.single()
            assertFalse("$phase must not read as live", entry.live)
            assertEquals(PluginState.IDLE, entry.state)
            assertEquals(phase, entry.fiberPhase)
        }
    }

    @Test
    fun `an entry with no module name is dropped, not drawn blank`() {
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """[{"entryId":"e1","enabled":true},{"entryId":"e2","moduleName":"ok"}]""",
            ),
        )

        assertEquals(listOf("ok"), inventory.entries.map { it.moduleName })
    }

    @Test
    fun `an entry that omits enabled is treated as present`() {
        // A row the host listed without saying whether it is enabled is one the host
        // considers present; drawing it as disabled would be this client inventing a
        // fact.
        val inventory = PluginInventoryWire.parse(
            snapshot("""[{"entryId":"e1","moduleName":"a"}]"""),
        )

        assertTrue(inventory.entries.single().enabled)
    }

    @Test
    fun `managementAvailable absent means no`() {
        assertFalse(PluginInventoryWire.parse(snapshot("[]")).managementAvailable)
        assertTrue(
            PluginInventoryWire.parse(snapshot("[]", management = true)).managementAvailable,
        )
    }

    @Test
    fun `an empty inventory parses to an empty list rather than failing`() {
        val inventory = PluginInventoryWire.parse(snapshot("[]"))

        assertTrue(inventory.entries.isEmpty())
    }

    @Test
    fun `a reply with no entries key parses to an empty list`() {
        // Several hosts in this app's range answer a namespace they know with a
        // narrower document; a missing key must not throw in the middle of a page.
        val inventory = PluginInventoryWire.parse(JSONObject())

        assertTrue(inventory.entries.isEmpty())
    }

    @Test
    fun `trouble sorts first, then running, then the rest`() {
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """
                [
                  {"entryId":"e1","moduleName":"z-idle","enabled":true,"fiberPhase":null},
                  {"entryId":"e2","moduleName":"b-active","enabled":true,"fiberPhase":"active"},
                  {"entryId":"e3","moduleName":"a-failed","enabled":true,"fiberPhase":"failed",
                   "diagnostic":"boom"},
                  {"entryId":"e4","moduleName":"c-off","enabled":false,"fiberPhase":null}
                ]
                """,
            ),
        )

        assertEquals(
            listOf("a-failed", "b-active", "z-idle", "c-off"),
            PluginInventoryWire.sorted(inventory.entries).map { it.moduleName },
        )
    }

    @Test
    fun `the order is stable within a state`() {
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """
                [
                  {"entryId":"e1","moduleName":"Zeta","enabled":true,"fiberPhase":"active"},
                  {"entryId":"e2","moduleName":"alpha","enabled":true,"fiberPhase":"active"}
                ]
                """,
            ),
        )

        // Case-insensitive, so the order does not depend on the specifier's casing.
        assertEquals(
            listOf("alpha", "Zeta"),
            PluginInventoryWire.sorted(inventory.entries).map { it.moduleName },
        )
    }

    @Test
    fun `a label prefers the display name so sorting uses what the reader sees`() {
        val inventory = PluginInventoryWire.parse(
            snapshot(
                """
                [
                  {"entryId":"e1","moduleName":"zzz-module","meta":{"name":"Aardvark"},
                   "enabled":true,"fiberPhase":"active"},
                  {"entryId":"e2","moduleName":"aaa-module","enabled":true,"fiberPhase":"active"}
                ]
                """,
            ),
        )

        assertEquals(
            listOf("Aardvark", "aaa-module"),
            PluginInventoryWire.sorted(inventory.entries).map { it.label },
        )
    }
}
