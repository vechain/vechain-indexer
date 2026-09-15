package org.vechain.indexer.stargate.token

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@Suppress("PropertyName")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TokenLevelDecimalValues(
    @get:JsonProperty("Strength") @param:JsonProperty("Strength") val Strength: BigDecimal? = null,
    @get:JsonProperty("Thunder") @param:JsonProperty("Thunder") val Thunder: BigDecimal? = null,
    @get:JsonProperty("Mjolnir") @param:JsonProperty("Mjolnir") val Mjolnir: BigDecimal? = null,
    @get:JsonProperty("VeThorX") @param:JsonProperty("VeThorX") val VeThorX: BigDecimal? = null,
    @get:JsonProperty("StrengthX")
    @param:JsonProperty("StrengthX")
    val StrengthX: BigDecimal? = null,
    @get:JsonProperty("ThunderX") @param:JsonProperty("ThunderX") val ThunderX: BigDecimal? = null,
    @get:JsonProperty("MjolnirX") @param:JsonProperty("MjolnirX") val MjolnirX: BigDecimal? = null,
    @get:JsonProperty("Dawn") @param:JsonProperty("Dawn") val Dawn: BigDecimal? = null,
    @get:JsonProperty("Lightning")
    @param:JsonProperty("Lightning")
    val Lightning: BigDecimal? = null,
    @get:JsonProperty("Flash") @param:JsonProperty("Flash") val Flash: BigDecimal? = null,
) {
    operator fun get(level: TokenLevel): BigDecimal? =
        when (level) {
            TokenLevel.All -> null
            TokenLevel.Strength -> Strength
            TokenLevel.Thunder -> Thunder
            TokenLevel.Mjolnir -> Mjolnir
            TokenLevel.VeThorX -> VeThorX
            TokenLevel.StrengthX -> StrengthX
            TokenLevel.ThunderX -> ThunderX
            TokenLevel.MjolnirX -> MjolnirX
            TokenLevel.Dawn -> Dawn
            TokenLevel.Lightning -> Lightning
            TokenLevel.Flash -> Flash
        }

    fun toMap(): Map<TokenLevel, BigDecimal> = buildMap {
        Strength?.let { put(TokenLevel.Strength, it) }
        Thunder?.let { put(TokenLevel.Thunder, it) }
        Mjolnir?.let { put(TokenLevel.Mjolnir, it) }
        VeThorX?.let { put(TokenLevel.VeThorX, it) }
        StrengthX?.let { put(TokenLevel.StrengthX, it) }
        ThunderX?.let { put(TokenLevel.ThunderX, it) }
        MjolnirX?.let { put(TokenLevel.MjolnirX, it) }
        Dawn?.let { put(TokenLevel.Dawn, it) }
        Lightning?.let { put(TokenLevel.Lightning, it) }
        Flash?.let { put(TokenLevel.Flash, it) }
    }

    companion object {
        fun empty(): TokenLevelDecimalValues = TokenLevelDecimalValues()

        fun fromMap(values: Map<TokenLevel, BigDecimal>): TokenLevelDecimalValues =
            TokenLevelDecimalValues(
                Strength = values[TokenLevel.Strength],
                Thunder = values[TokenLevel.Thunder],
                Mjolnir = values[TokenLevel.Mjolnir],
                VeThorX = values[TokenLevel.VeThorX],
                StrengthX = values[TokenLevel.StrengthX],
                ThunderX = values[TokenLevel.ThunderX],
                MjolnirX = values[TokenLevel.MjolnirX],
                Dawn = values[TokenLevel.Dawn],
                Lightning = values[TokenLevel.Lightning],
                Flash = values[TokenLevel.Flash],
            )
    }
}
