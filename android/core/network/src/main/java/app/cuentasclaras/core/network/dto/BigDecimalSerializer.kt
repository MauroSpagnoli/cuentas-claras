package app.cuentasclaras.core.network.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * Serializa `BigDecimal` como número JSON sin comillas y sin notación
 * científica, que es la forma en la que el backend (Jackson) los emite y
 * espera.
 *
 * Importante para porcentajes y pesos: si pasaran por `Double`, una suma como
 * `33.33 + 33.33 + 33.34` dejaría de ser exactamente `100` y el servidor
 * rechazaría el reparto.
 */
@OptIn(ExperimentalSerializationApi::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return BigDecimal(decoder.decodeString())
        val element = jsonDecoder.decodeJsonElement() as JsonPrimitive
        return BigDecimal(element.content)
    }

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: return encoder.encodeString(value.toPlainString())
        jsonEncoder.encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
    }
}
