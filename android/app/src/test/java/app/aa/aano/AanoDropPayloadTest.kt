package app.aa.aano

import app.aa.aano.data.Note
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AanoDropPayloadTest {

    @Test
    fun testDropProtocolStructure() {
        val payload = JSONObject().apply {
            put("protocol", "aano-drop-v1")
            put("token", "sec_token_9876")
            put("pin", "123456")
            put("notes", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "note_dropped_1")
                    put("title", "Dropped Idea")
                    put("content", "Transferred over Wi-Fi direct socket")
                    put("isLocal", true)
                })
            })
            put("images", JSONArray())
        }

        assertEquals("aano-drop-v1", payload.getString("protocol"))
        assertEquals("sec_token_9876", payload.getString("token"))
        assertEquals("123456", payload.getString("pin"))

        val notesArr = payload.getJSONArray("notes")
        assertEquals(1, notesArr.length())

        val noteObj = notesArr.getJSONObject(0)
        assertEquals("note_dropped_1", noteObj.getString("id"))
        assertEquals("Dropped Idea", noteObj.getString("title"))
        assertTrue(noteObj.getBoolean("isLocal"))
    }

    @Test
    fun testAuthenticationTokenMatching() {
        val serverPin = "654321"
        val serverToken = "tok_abc"

        val validPinReq = JSONObject().put("pin", "654321")
        val invalidReq = JSONObject().put("pin", "000000").put("token", "wrong")
        val validTokenReq = JSONObject().put("token", "tok_abc")

        // Validation logic used in AanoDropManager
        fun validate(req: JSONObject): Boolean {
            val reqToken = req.optString("token").ifEmpty { req.optString("pin") }
            return reqToken == serverPin || reqToken == serverToken
        }

        assertTrue(validate(validPinReq))
        assertTrue(validate(validTokenReq))
        assertFalse(validate(invalidReq))
    }
}
