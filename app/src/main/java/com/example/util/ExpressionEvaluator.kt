package com.example.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object ExpressionEvaluator {

    fun evaluate(expression: String): Result<Double> {
        val cleaned = expression.trim()
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter an expression"))
        }
        return try {
            val parser = Parser(cleaned)
            val result = parser.parse()
            if (result.isNaN() || result.isInfinite()) {
                Result.failure(ArithmeticException("Math domain error"))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatResult(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "Error"
        val roundedLong = value.toLong()
        return if (value == roundedLong.toDouble() && abs(value) < 1e12) {
            roundedLong.toString()
        } else {
            String.format(java.util.Locale.US, "%.8f", value)
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    private class Parser(private val input: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < input.length) input[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < input.length) {
                throw IllegalArgumentException("Unexpected character: '${ch.toChar()}'")
            }
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                when {
                    eat('*'.code) -> x *= parsePower()
                    eat('/'.code) -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    eat('%'.code) -> {
                        val mod = parsePower()
                        if (mod == 0.0) throw ArithmeticException("Division by zero")
                        x %= mod
                    }
                    else -> return x
                }
            }
        }

        private fun parsePower(): Double {
            var x = parseFactor()
            if (eat('^'.code)) {
                x = x.pow(parsePower())
            }
            return x
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            val x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing closing ')'")
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = input.substring(startPos, pos).toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid number")
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) nextChar()
                val name = input.substring(startPos, pos).lowercase()
                x = when (name) {
                    "pi" -> PI
                    "e" -> kotlin.math.E
                    else -> {
                        val arg = parseFactor()
                        when (name) {
                            "sqrt" -> {
                                if (arg < 0) throw ArithmeticException("Negative square root")
                                sqrt(arg)
                            }
                            "sin" -> sin(Math.toRadians(arg))
                            "cos" -> cos(Math.toRadians(arg))
                            "tan" -> tan(Math.toRadians(arg))
                            "log" -> {
                                if (arg <= 0) throw ArithmeticException("Log domain error")
                                log10(arg)
                            }
                            "ln" -> {
                                if (arg <= 0) throw ArithmeticException("Ln domain error")
                                ln(arg)
                            }
                            "abs" -> abs(arg)
                            else -> throw IllegalArgumentException("Unknown function: $name")
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Invalid syntax")
            }
            return x
        }
    }
}
