package com.zaaaam.kalku.core

enum class AngleMode { DEG, RAD }

sealed class EvalResult {
    data class Value(val raw: Double, val formatted: String) : EvalResult()
    data class Error(val message: String) : EvalResult()
}

/**
 * Recursive-descent evaluator for calculator expressions.
 *
 * Grammar (highest binding last):
 *   expression := addSub
 *   addSub     := mulDiv (('+' | '-') mulDiv)*      // '%' on rhs = relative percent: a + b% == a + a*b/100
 *   mulDiv     := unary (('*' | '/') unary)*
 *   unary      := ('-' | '+') unary | pow
 *   pow        := postfix ('^' unary)?              // right associative, -2^2 == -(2^2)
 *   postfix    := primary (('!' | '%'))*
 *
 * '%' disambiguation: modulo ONLY when the next token starts an unsigned operand
 * (NUMBER, IDENTIFIER or '('). If it follows '+'/'-', '%' binds as postfix percent,
 * so "10%+5" == 0.1 + 5 and "50%-20" == 0.5 - 20 (calculator convention).
 * Negative-divisor modulo stays reachable via mod(a, b).
 */
object Evaluator {

    private const val MAX_FACTORIAL = 170.0

    fun evaluate(expression: String, angleMode: AngleMode = AngleMode.DEG, precision: Int = 10): EvalResult {
        return try {
            val tokens = Lexer(expression.trim()).tokenize()
            if (tokens.isEmpty()) return EvalResult.Error("Empty")
            val parser = Parser(tokens, angleMode)
            val value = parser.parseExpression()
            parser.expectEnd()
            if (value.isNaN()) EvalResult.Error("Undefined")
            else EvalResult.Value(value, Format.number(value, precision))
        } catch (e: SyntaxException) {
            EvalResult.Error(e.message ?: "Error")
        }
    }

    /** Quick preview evaluation used while typing; never throws. */
    fun preview(expression: String, angleMode: AngleMode, precision: Int): String =
        when (val r = evaluate(expression, angleMode, precision)) {
            is EvalResult.Value -> r.formatted
            is EvalResult.Error -> ""
        }

    private class SyntaxException(message: String) : Exception(message)

    private sealed class Tok {
        data class Num(val v: Double) : Tok()
        data class Id(val name: String) : Tok()
        data class Op(val ch: Char) : Tok()
    }

    private class Lexer(private val src: String) {
        private var i = 0
        fun tokenize(): List<Tok> {
            val out = mutableListOf<Tok>()
            while (i < src.length) {
                val c = src[i]
                when {
                    c.isWhitespace() -> i++
                    c.isDigit() || c == '.' -> out.add(Tok.Num(readNumber()))
                    c.isLetter() || c == 'π' || c == '_' -> out.add(readIdent())
                    c in "+-*/%^!()" -> { out.add(Tok.Op(c)); i++ }
                    c == ',' || c == ';' -> { out.add(Tok.Op(',')); i++ } // argument separator
                    c == '×' -> { out.add(Tok.Op('*')); i++ }
                    c == '÷' -> { out.add(Tok.Op('/')); i++ }
                    c == '−' -> { out.add(Tok.Op('-')); i++ }
                    c == '√' -> { out.add(Tok.Id("sqrt")); i++ }
                    else -> throw SyntaxException("Bad character")
                }
            }
            return out
        }

        private fun readNumber(): Double {
            val start = i
            while (i < src.length && (src[i].isDigit() || src[i] == '.')) i++
            if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                val save = i
                i++
                if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                if (i < src.length && src[i].isDigit()) {
                    while (i < src.length && src[i].isDigit()) i++
                } else {
                    i = save // 'e' belongs to an identifier like `exp`
                }
            }
            return src.substring(start, i).toDoubleOrNull() ?: throw SyntaxException("Bad number")
        }

        private fun readIdent(): Tok {
            val sb = StringBuilder()
            while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '_' || src[i] == 'π')) {
                sb.append(src[i]); i++
            }
            return Tok.Id(sb.toString())
        }
    }

    private class Parser(private val t: List<Tok>, private val angle: AngleMode) {
        private var p = 0

        /** Original value of the last postfix-percent operand (before /100). */
        private var lastPercentOperand: Double? = null

        fun parseExpression(): Double = addSub()

        fun expectEnd() {
            if (p < t.size) throw SyntaxException("Syntax error")
        }

        private fun peek(): Tok? = t.getOrNull(p)

        private fun pop(): Tok = t.getOrNull(p++) ?: throw SyntaxException("Unexpected end")

        private fun eatOp(ch: Char): Boolean {
            val cur = peek()
            if (cur is Tok.Op && cur.ch == ch) { p++; return true }
            return false
        }

        private fun addSub(): Double {
            var acc = mulDiv()
            while (true) {
                val op = when {
                    eatOp('+') -> '+'
                    eatOp('-') -> '-'
                    else -> return acc
                }
                val rhs = mulDiv()
                // Calculator convention: a + b% == a + a*b/100 (percent relative to lhs)
                val pct = lastPercentOperand.also { lastPercentOperand = null }
                acc += when {
                    pct != null && op == '+' -> acc * pct / 100.0
                    pct != null && op == '-' -> -(acc * pct / 100.0)
                    op == '+' -> rhs
                    else -> -rhs
                }
            }
        }

        private fun mulDiv(): Double {
            var acc = unary()
            while (true) {
                when {
                    eatOp('*') -> acc *= unary()
                    eatOp('/') -> {
                        val d = unary()
                        acc = if (d == 0.0 && acc == 0.0) Double.NaN else acc / d
                    }
                    else -> return acc
                }
            }
        }

        private fun startsValue(tok: Tok?): Boolean = tok is Tok.Num || tok is Tok.Id ||
            (tok is Tok.Op && tok.ch == '(')

        private fun unary(): Double {
            // Each operand consumption resets the percent marker; postfix may set it again.
            lastPercentOperand = null
            val cur = peek()
            if (cur is Tok.Op && (cur.ch == '-' || cur.ch == '+')) {
                p++
                val v = unary()
                return if (cur.ch == '-') -v else v
            }
            return pow()
        }

        private fun pow(): Double {
            val base = postfix()
            if (eatOp('^')) {
                val exp = unary()
                val r = Math.pow(base, exp)
                if (r.isNaN() && base >= 0 && exp >= 0) throw SyntaxException("Domain error")
                return r
            }
            return base
        }

        private fun postfix(): Double {
            var v = primary()
            while (true) {
                val cur = peek()
                if (cur !is Tok.Op) return v
                when (cur.ch) {
                    '!' -> { p++; v = factorial(v) }
                    '%' -> {
                        val nextTok = t.getOrNull(p + 1)
                        if (startsValue(nextTok)) {
                            // Binary modulo: "5 % 3"
                            p++
                            val rhs = unary()
                            if (rhs == 0.0) throw SyntaxException("Modulo by zero")
                            v %= rhs
                        } else {
                            // Postfix percent: "50%" == 0.5
                            p++
                            lastPercentOperand = v
                            v /= 100.0
                        }
                    }
                    else -> return v
                }
            }
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != Math.floor(v) || v > MAX_FACTORIAL) throw SyntaxException("Invalid factorial")
            var r = 1.0
            for (k in 2..v.toInt()) r *= k
            return r
        }

        private fun primary(): Double = when (val cur = pop()) {
            is Tok.Num -> cur.v
            is Tok.Id -> identifier(cur.name)
            is Tok.Op -> when (cur.ch) {
                '(' -> {
                    val v = parseExpression()
                    if (!eatOp(')')) throw SyntaxException("Missing ')'")
                    v
                }
                '-' -> -unary()
                '+' -> unary()
                else -> throw SyntaxException("Syntax error")
            }
        }

        private fun identifier(name: String): Double {
            val lower = name.lowercase().replace("π", "pi")
            constants[lower]?.let { return it }
            val fn = functions[lower] ?: throw SyntaxException("Unknown: $name")
            val args = mutableListOf<Double>()
            if (!eatOp('(')) throw SyntaxException("Expected (")
            if (!eatOp(')')) {
                do { args.add(parseExpression()) } while (eatOp(','))
                if (!eatOp(')')) throw SyntaxException("Missing ')'")
            }
            if (args.size !in fn.arity..fn.arity) throw SyntaxException("Wrong args")
            return fn.run(args, angle)
        }

        companion object {
            private val constants = mapOf("pi" to Math.PI, "e" to Math.E, "tau" to 2 * Math.PI)

            private class Fn(val arity: Int, val run: (List<Double>, AngleMode) -> Double)

            private val trigIn: (Double, AngleMode) -> Double = { v, m ->
                if (m == AngleMode.DEG) Math.toRadians(v) else v
            }
            private val trigOut: (Double, AngleMode) -> Double = { v, m ->
                if (m == AngleMode.DEG) Math.toDegrees(v) else v
            }

            private val functions = mapOf(
                "sin" to Fn(1) { a, m -> Math.sin(trigIn(a[0], m)) },
                "cos" to Fn(1) { a, m -> Math.cos(trigIn(a[0], m)) },
                "tan" to Fn(1) { a, m -> Math.tan(trigIn(a[0], m)) },
                "asin" to Fn(1) { a, m -> trigOut(Math.asin(a[0]), m) },
                "acos" to Fn(1) { a, m -> trigOut(Math.acos(a[0]), m) },
                "atan" to Fn(1) { a, m -> trigOut(Math.atan(a[0]), m) },
                "sinh" to Fn(1) { a, _ -> Math.sinh(a[0]) },
                "cosh" to Fn(1) { a, _ -> Math.cosh(a[0]) },
                "tanh" to Fn(1) { a, _ -> Math.tanh(a[0]) },
                "log" to Fn(1) { a, _ -> Math.log10(a[0]) },
                "ln" to Fn(1) { a, _ -> Math.log(a[0]) },
                "sqrt" to Fn(1) { a, _ -> Math.sqrt(a[0]) },
                "cbrt" to Fn(1) { a, _ -> Math.cbrt(a[0]) },
                "exp" to Fn(1) { a, _ -> Math.exp(a[0]) },
                "abs" to Fn(1) { a, _ -> Math.abs(a[0]) },
                "round" to Fn(1) { a, _ -> Math.round(a[0]).toDouble() },
                "floor" to Fn(1) { a, _ -> Math.floor(a[0]) },
                "ceil" to Fn(1) { a, _ -> Math.ceil(a[0]) },
                "root" to Fn(2) { a, _ -> Math.pow(a[0], 1.0 / a[1]) },
                "logb" to Fn(2) { a, _ -> Math.log(a[0]) / Math.log(a[1]) },
                "pow" to Fn(2) { a, _ -> Math.pow(a[0], a[1]) },
                "mod" to Fn(2) { a, _ -> a[0] % a[1] },
            )
        }
    }
}
