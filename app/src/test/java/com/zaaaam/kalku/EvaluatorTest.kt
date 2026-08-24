package com.zaaaam.kalku

import com.zaaaam.kalku.core.AngleMode
import com.zaaaam.kalku.core.EvalResult
import com.zaaaam.kalku.core.Evaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluatorTest {

    private fun value(expr: String, mode: AngleMode = AngleMode.DEG): Double {
        val r = Evaluator.evaluate(expr, mode)
        assertTrue("expected value for '$expr' but got $r", r is EvalResult.Value)
        return (r as EvalResult.Value).raw
    }

    private fun err(expr: String): String {
        val r = Evaluator.evaluate(expr)
        assertTrue("expected error for '$expr'", r is EvalResult.Error)
        return (r as EvalResult.Error).message
    }

    @Test fun basicArithmetic() {
        assertEquals(7.0, value("2+3*2-1"), 1e-9)
        assertEquals(3.0, value("(2+4)/2"), 1e-9)
        assertEquals(0.5, value("1/2"), 1e-9)
        assertEquals(8.0, value("2^3"), 1e-9)
    }

    @Test fun precedenceAndAssociativity() {
        assertEquals(-4.0, value("-2^2"), 1e-9)      // unary minus below power
        assertEquals(0.25, value("2^-2"), 1e-9)      // exponent may be negative
    }

    @Test fun rightAssocPower() {
        // 2^3^2 must be 2^(3^2) = 512 with right associativity
        assertEquals(512.0, value("2^3^2"), 1e-9)
    }

    @Test fun percentPostfix() {
        assertEquals(0.5, value("50%"), 1e-9)
        assertEquals(150.0, value("100+50%"), 1e-9) // calculator-style: % relative to lhs
    }

    @Test fun moduloBinary() {
        assertEquals(2.0, value("5%3"), 1e-9)
    }

    @Test fun percentVersusModuloDisambiguation() {
        // '%' followed by +/- binds as postfix percent (calculator convention),
        // NOT as binary modulo — "10%+5" used to evaluate to 10 mod 5 == 0.
        assertEquals(5.1, value("10%+5"), 1e-9)
        assertEquals(-19.5, value("50%-20"), 1e-9)
        // Parenthesised operand still means modulo.
        assertEquals(0.0, value("5%(2+3)"), 1e-9)
        assertEquals(4.0, value("10%(3+3)"), 1e-9)
        // Negative-divisor modulo remains reachable via mod(a,b).
        // Java/Kotlin remainder: sign follows the dividend → 10 % -3 == 1.
        assertEquals(1.0, value("mod(10,-3)"), 1e-9)
    }

    @Test fun factorial() {
        assertEquals(120.0, value("5!"), 1e-9)
        assertEquals(1.0, value("0!"), 1e-9)
        err("3.5!")
        err("(-2)!")
    }

    @Test fun constants() {
        assertEquals(Math.PI, value("π"), 1e-12)
        assertEquals(Math.PI, value("pi"), 1e-12)
        assertEquals(Math.E, value("e"), 1e-12)
    }

    @Test fun functions() {
        assertEquals(2.0, value("sqrt(4)"), 1e-12)
        assertEquals(3.0, value("log(10^3)"), 1e-9)
        assertEquals(1.0, value("ln(e)"), 1e-12)
        assertEquals(1.0, value("sin(90)"), 1e-12)
        assertEquals(0.0, value("cos(90)"), 1e-12)
        assertEquals(45.0, value("asin(sqrt(2)/2)"), 1e-9)
        assertEquals(1.0, value("sin(pi/2)", AngleMode.RAD), 1e-12)
        assertEquals(12.0, value("3!*2"), 1e-9)
        assertEquals(10.0, value("abs(-10)"), 1e-12)
        assertEquals(3.0, value("round(2.6)"), 1e-12)
        assertEquals(2.0, value("floor(2.99)"), 1e-12)
        assertEquals(3.0, value("ceil(2.01)"), 1e-12)
        assertEquals(2.0, value("root(8,3)"), 1e-12)
    }

    @Test fun scientificNotation() {
        assertEquals(150000.0, value("1.5e5"), 1e-6)
        assertEquals(300.0, value("3E2"), 1e-9)
        assertEquals(0.001, value("1e-3"), 1e-15)
    }

    @Test fun divisionByZeroYieldsInfinity() {
        val r = Evaluator.evaluate("1/0")
        assertTrue(r is EvalResult.Value)
        assertEquals(Double.POSITIVE_INFINITY, (r as EvalResult.Value).raw, 0.0)
    }

    @Test fun domainErrors() {
        err("sqrt(-1)")
        err("ln(-5)")
    }

    @Test fun syntaxErrors() {
        err("2++")
        err("(2+3")
        err("foo(2)")
        err("")
    }

    @Test fun complexExpression() {
        assertEquals(8.0, value("2*(3+4)-3!"), 1e-9)    // 14 - 6
        assertEquals(-1.0, value("5-2*3"), 1e-9)
    }
}
