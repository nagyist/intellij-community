// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import com.intellij.psi.JavaTokenType
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.nj2k.types.JKType

interface JKOperator {
    val token: JKOperatorToken
    val returnType: JKType
}

interface JKOperatorToken {
    val text: String

    @Suppress("MemberVisibilityCanBePrivate", "SpellCheckingInspection")
    companion object {
        fun fromElementType(elementType: IElementType) = javaElementTypeToToken.getValue(elementType)

        fun toKtElementType(token: JKOperatorToken): KtToken? = tokenToKotlinElementType[token]

        val RANGE = JKKtSingleValueOperatorToken(KtTokens.RANGE)
        val RANGE_UNTIL = JKKtSingleValueOperatorToken(KtTokens.RANGE_UNTIL)
        val UNTIL = JKKtWordOperatorToken("until")
        val DOWN_TO = JKKtWordOperatorToken("downTo")

        val DIV = JKKtSingleValueOperatorToken(KtTokens.DIV)
        val MINUS = JKKtSingleValueOperatorToken(KtTokens.MINUS)
        val ANDAND = JKKtSingleValueOperatorToken(KtTokens.ANDAND)
        val OROR = JKKtSingleValueOperatorToken(KtTokens.OROR)
        val PLUS = JKKtSingleValueOperatorToken(KtTokens.PLUS)
        val MUL = JKKtSingleValueOperatorToken(KtTokens.MUL)
        val GT = JKKtSingleValueOperatorToken(KtTokens.GT)
        val GTEQ = JKKtSingleValueOperatorToken(KtTokens.GTEQ)
        val LT = JKKtSingleValueOperatorToken(KtTokens.LT)
        val LTEQ = JKKtSingleValueOperatorToken(KtTokens.LTEQ)
        val PERC = JKKtSingleValueOperatorToken(KtTokens.PERC)
        val EQ = JKKtSingleValueOperatorToken(KtTokens.EQ)
        val EQEQ = JKKtSingleValueOperatorToken(KtTokens.EQEQ)
        val EXCLEQ = JKKtSingleValueOperatorToken(KtTokens.EXCLEQ)

        val PLUSEQ = JKKtSingleValueOperatorToken(KtTokens.PLUSEQ)
        val MINUSEQ = JKKtSingleValueOperatorToken(KtTokens.MINUSEQ)
        val DIVEQ = JKKtSingleValueOperatorToken(KtTokens.DIVEQ)
        val MULTEQ = JKKtSingleValueOperatorToken(KtTokens.MULTEQ)
        val PERCEQ = JKKtSingleValueOperatorToken(KtTokens.PERCEQ)

        val PLUSPLUS = JKKtSingleValueOperatorToken(KtTokens.PLUSPLUS)
        val MINUSMINUS = JKKtSingleValueOperatorToken(KtTokens.MINUSMINUS)
        val EXCL = JKKtSingleValueOperatorToken(KtTokens.EXCL)
        val EQEQEQ = JKKtSingleValueOperatorToken(KtTokens.EQEQEQ)
        val EXCLEQEQEQ = JKKtSingleValueOperatorToken(KtTokens.EXCLEQEQEQ)
        val ELVIS = JKKtSingleValueOperatorToken(KtTokens.ELVIS)

        val AND = JKKtWordOperatorToken("and")
        val OR = JKKtWordOperatorToken("or")
        val XOR = JKKtWordOperatorToken("xor")
        val USHR = JKKtWordOperatorToken("ushr")
        val SHR = JKKtWordOperatorToken("shr")
        val SHL = JKKtWordOperatorToken("shl")

        val ANDEQ = JKJavaOperatorToken(JavaTokenType.ANDEQ)
        val OREQ = JKJavaOperatorToken(JavaTokenType.OREQ)
        val XOREQ = JKJavaOperatorToken(JavaTokenType.XOREQ)
        val LTLTEQ = JKJavaOperatorToken(JavaTokenType.LTLTEQ)
        val GTGTEQ = JKJavaOperatorToken(JavaTokenType.GTGTEQ)
        val GTGTGTEQ = JKJavaOperatorToken(JavaTokenType.GTGTGTEQ)

        val ARITHMETIC_OPERATORS = listOf(PLUS, MINUS, DIV, MUL, PERC)

        val BITWISE_LOGICAL_OPERATORS = listOf(AND, OR, XOR)

        val SHIFT_OPERATORS = listOf(SHL, SHR, USHR)

        val RANGE_OPERATORS = listOf(RANGE, RANGE_UNTIL, UNTIL, DOWN_TO)

        private val javaElementTypeToToken: Map<IElementType, JKOperatorToken> = mapOf(
            JavaTokenType.DIV to DIV,
            JavaTokenType.MINUS to MINUS,
            JavaTokenType.ANDAND to ANDAND,
            JavaTokenType.OROR to OROR,
            JavaTokenType.PLUS to PLUS,
            JavaTokenType.ASTERISK to MUL,
            JavaTokenType.GT to GT,
            JavaTokenType.GE to GTEQ,
            JavaTokenType.LT to LT,
            JavaTokenType.LE to LTEQ,
            JavaTokenType.PERC to PERC,

            JavaTokenType.EQ to EQ,
            JavaTokenType.EQEQ to EQEQ,
            JavaTokenType.NE to EXCLEQ,

            JavaTokenType.PLUSEQ to PLUSEQ,
            JavaTokenType.MINUSEQ to MINUSEQ,
            JavaTokenType.DIVEQ to DIVEQ,
            JavaTokenType.ASTERISKEQ to MULTEQ,

            JavaTokenType.PLUSPLUS to PLUSPLUS,
            JavaTokenType.MINUSMINUS to MINUSMINUS,
            JavaTokenType.EXCL to EXCL,

            KtTokens.EQEQEQ to EQEQEQ,
            KtTokens.EXCLEQEQEQ to EXCLEQEQEQ,

            JavaTokenType.AND to AND,
            JavaTokenType.OR to OR,
            JavaTokenType.XOR to XOR,
            JavaTokenType.GTGTGT to USHR,
            JavaTokenType.GTGT to SHR,
            JavaTokenType.LTLT to SHL,

            JavaTokenType.ANDEQ to ANDEQ,
            JavaTokenType.OREQ to OREQ,
            JavaTokenType.XOREQ to XOREQ,
            JavaTokenType.PERCEQ to PERCEQ,
            JavaTokenType.LTLTEQ to LTLTEQ,
            JavaTokenType.GTGTEQ to GTGTEQ,
            JavaTokenType.GTGTGTEQ to GTGTGTEQ
        )

        private val tokenToKotlinElementType: Map<JKOperatorToken, KtToken> = mapOf(
            DIV to KtTokens.DIV,
            MINUS to KtTokens.MINUS,
            ANDAND to KtTokens.ANDAND,
            OROR to KtTokens.OROR,
            PLUS to KtTokens.PLUS,
            MUL to KtTokens.MUL,
            GT to KtTokens.GT,
            GTEQ to KtTokens.GTEQ,
            LT to KtTokens.LT,
            LTEQ to KtTokens.LTEQ,
            PERC to KtTokens.PERC,

            EQ to KtTokens.EQ,
            EQEQ to KtTokens.EQEQ,
            EXCLEQ to KtTokens.EXCLEQ,

            PLUSEQ to KtTokens.PLUSEQ,
            MINUSEQ to KtTokens.MINUSEQ,
            DIVEQ to KtTokens.DIVEQ,
            MULTEQ to KtTokens.MULTEQ,

            PLUSPLUS to KtTokens.PLUSPLUS,
            MINUSMINUS to KtTokens.MINUSMINUS,
            EXCL to KtTokens.EXCL,

            EQEQEQ to KtTokens.EQEQEQ,
            EXCLEQEQEQ to KtTokens.EXCLEQEQEQ,

            PERCEQ to KtTokens.PERCEQ,

            RANGE to KtTokens.RANGE,
            RANGE_UNTIL to KtTokens.RANGE_UNTIL,
        )
    }
}

class JKKtWordOperatorToken(override val text: String) : JKKtOperatorToken

class JKKtOperatorImpl(override val token: JKOperatorToken, override val returnType: JKType) : JKOperator

interface JKKtOperatorToken : JKOperatorToken

class JKJavaOperatorToken(private val psiToken: IElementType) : JKOperatorToken {
    override val text: String
        get() = error("Java token '$psiToken' should not be printed, it should be replaced with the corresponding Kotlin one")
}

class JKKtSingleValueOperatorToken(val psiToken: KtSingleValueToken) : JKKtOperatorToken {
    override val text: String = psiToken.value
}

object JKKtSpreadOperatorToken : JKKtOperatorToken {
    override val text: String = "*"
}

class JKKtSpreadOperator(override val returnType: JKType) : JKOperator {
    override val token: JKOperatorToken = JKKtSpreadOperatorToken
}
