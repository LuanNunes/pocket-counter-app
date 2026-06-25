package com.resolveprogramming.pocketcounter.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal

/**
 * The backend (Jackson) emits BigDecimal as a JSON *number*; the app's other
 * [com.resolveprogramming.pocketcounter.domain.model.BigDecimalSerializer] expects a
 * string. This one reads the raw JSON primitive content so it tolerates either form.
 * On write it emits a quoted plain string, which Jackson coerces back to BigDecimal.
 */
object RemoteBigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RemoteBigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        val content = run {
            if (decoder is JsonDecoder) return@run decoder.decodeJsonElement().jsonPrimitive.content
            decoder.decodeString()
        }
        return BigDecimal(content)
    }
}
