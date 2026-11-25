package com.accbot.dca.exchange

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class PreSendFailureTest {
    @Test
    fun `failures before the request is sent are confirmed`() {
        assertTrue(isPreSendFailure(UnknownHostException("api.binance.com")))
        assertTrue(isPreSendFailure(ConnectException("Failed to connect")))
        assertTrue(isPreSendFailure(SSLHandshakeException("handshake")))
        assertTrue(isPreSendFailure(SocketTimeoutException("connect timed out")))
        assertTrue(isPreSendFailure(IOException("wrapped", ConnectException("refused"))))
    }

    @Test
    fun `failures after sending stay uncertain`() {
        assertFalse(isPreSendFailure(SocketTimeoutException("timeout")))
        assertFalse(isPreSendFailure(SocketTimeoutException("Read timed out")))
        assertFalse(isPreSendFailure(IOException("unexpected end of stream")))
        assertFalse(isPreSendFailure(java.net.SocketException("Connection reset")))
    }
}
