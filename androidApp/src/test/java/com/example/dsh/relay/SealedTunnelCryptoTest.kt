package com.example.dsh.relay

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedTunnelCryptoTest {
    @Test
    fun matchesSharedSealedTunnelV1Vectors() {
        val vectors = JSONObject(
            javaClass.classLoader!!.getResource("sealed-tunnel-v1.json")!!.readText(),
        )
        val master = vectors.getString("masterKeyB64")
        val session = vectors.getString("accessSessionId")
        val clientRandom = vectors.getString("clientRandomB64")
        val serverRandom = vectors.getString("serverRandomB64")
        val claim = SealedTunnelCrypto.deriveClaimToken(master)
        assertEquals(vectors.getString("claimTokenB64"), claim)
        assertEquals(vectors.getString("claimTokenHashHex"), SealedTunnelCrypto.hashClaimToken(claim))
        assertEquals(
            vectors.getString("clientProofB64"),
            SealedTunnelCrypto.clientProof(master, session, clientRandom),
        )
        assertEquals(
            vectors.getString("serverProofB64"),
            SealedTunnelCrypto.serverProof(master, session, clientRandom, serverRandom),
        )
        val host = SealedTunnelCrypto.createHostCipher(master, session, clientRandom, serverRandom)
        val opened = host.open(
            SealedPayload("0", vectors.getString("httpReqCiphertextB64")),
        )
        assertEquals("http_req", opened.getString("type"))
        assertEquals("ch_test", opened.getString("channel"))
        assertEquals("request_1", opened.getString("id"))
        assertEquals("/canary", opened.getJSONObject("payload").getString("path"))
        assertEquals("POST", opened.getJSONObject("payload").getString("method"))

        val client = SealedTunnelCrypto.createClientCipher(
            master,
            session,
            clientRandom,
            serverRandom,
            vectors.getString("serverProofB64"),
        )
        val hostRoundtrip = SealedTunnelCrypto.createHostCipher(master, session, clientRandom, serverRandom)
        val echoed = hostRoundtrip.open(client.seal(JSONObject().put("ping", true)))
        assertTrue(echoed.getBoolean("ping"))
    }
}
