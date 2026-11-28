package com.accbot.dca.domain.util

/** Limits shared by every feature that puts a payload into a QR code. */
object QrLimits {
    /** Largest UTF-8 payload that still scans reliably as a single QR code. */
    const val MAX_PAYLOAD_BYTES = 1850
}
