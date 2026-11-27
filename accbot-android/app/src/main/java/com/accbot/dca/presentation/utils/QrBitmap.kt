package com.accbot.dca.presentation.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Renders [content] as a black-on-white QR code, or null if it cannot be encoded. */
fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val pixels = IntArray(sizePx * sizePx) { i ->
            if (bitMatrix[i % sizePx, i / sizePx]) Color.BLACK else Color.WHITE
        }
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).also {
            it.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    } catch (_: Exception) {
        null
    }
}
