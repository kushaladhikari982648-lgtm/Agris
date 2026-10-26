package com.agris.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Recursive-descent compiler and fast AST evaluator for OptiFine / Iris
 * {@code shaders.properties} custom uniform &amp; variable expressions
 * ({@code variable.float.foo = smooth(wetness, 0.5, 1.0)},
 * {@code uniform.float.bar = if(rainStrength > 0.1, clamp(sin(frameTimeCounter * 2.0), 0.0, 1.0), 0.0)}).
 */
public final class UniformExpression {
    @FunctionalInterface
    public interface Node {
        double eval(ToDoubleFunction<String> symbols);
    }

    private final String source;
    private final Node root;

    private UniformExpression(final String source, final Node root) {
        this.source = source;
        this.root = root;
    }

    public static UniformExpression compile(final String expression) {
        Objects.requireNonNull(expression, "expression");
        final Parser parser = new Parser(expression);
        final Node ast = parser.parseTernary();
        parser.skipSpaces();
        if (!parser.eof()) {
            throw new IllegalArgumentException("Unexpected token in uniform expression '"
                    + expression + "' at index " + parser.pos);
        }
        return new UniformExpression(expression, ast);
    }

    public double evaluate(final ToDoubleFunction<String> symbolResolver) {
        return this.root.eval(symbolResolver == null ? k -> 0.0 : symbolResolver);
    }

    public float evaluateFloat(final ToDoubleFunction<String> symbolResolver) {
        final double v = evaluate(symbolResolver);
        return Double.isNaN(v) || Double.isInfinite(v) ? 0.0f : (float) v;
    }

    public String source() {
        return this.source;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(final String s) {
            this.s = s;
        }

        boolean eof() {
            return this.pos >= this.s.length();
        }

        void skipSpaces() {
            while (!eof() && Character.isWhitespace(this.s.charAt(this.pos))) {
                this.pos++;
            }
        }

        boolean match(final String token) {
            skipSpaces();
            if (this.s.startsWith(token, this.pos)) {
                this.pos += token.length();
                return true;
            }
            return false;
        }

        Node parseTernary() {
            final Node cond = parseOr();
            skipSpaces();
            if (match("?")) {
                final Node whenTrue = parseTernary();
                if (!match(":")) {
                    throw new IllegalArgumentException("Expected ':' in ternary expression: " + this.s);
                }
                final Node whenFalse = parseTernary();
                return sym -> cond.eval(sym) != 0.0 ? whenTrue.eval(sym) : whenFalse.eval(sym);
            }
            return cond;
        }

        Node parseOr() {
            Node left = parseAnd();
            while (match("||")) {
                final Node l = left;
                final Node r = parseAnd();
                left = sym -> (l.eval(sym) != 0.0 || r.eval(sym) != 0.0) ? 1.0 : 0.0;
            }
            return left;
        }

        Node parseAnd() {
            Node left = parseEquality();
            while (match("&&")) {
                final Node l = left;
                final Node r = parseEquality();
                left = sym -> (l.eval(sym) != 0.0 && r.eval(sym) != 0.0) ? 1.0 : 0.0;
            }
            return left;
        }

        Node parseEquality() {
            Node left = parseComparison();
            while (true) {
                if (match("==")) {
                    final Node l = left;
                    final Node r = parseComparison();
                    left = sym -> Math.abs(l.eval(sym) - r.eval(sym)) < 1.0e-6 ? 1.0 : 0.0;
                } else if (match("!=")) {
                    final Node l = left;
                    final Node r = parseComparison();
                    left = sym -> Math.abs(l.eval(sym) - r.eval(sym)) >= 1.0e-6 ? 1.0 : 0.0;
                } else {
                    return left;
                }
            }
        }

        Node parseComparison() {
            Node left = parseAdditive();
            while (true) {
                if (match("<=")) {
                    final Node l = left;
                    final Node r = parseAdditive();
                    left = sym -> l.eval(sym) <= r.eval(sym) ? 1.0 : 0.0;
                } else if (match(">=")) {
                    final Node l = left;
                    final Node r = parseAdditive();
                    left = sym -> l.eval(sym) >= r.eval(sym) ? 1.0 : 0.0;
                } else if (match("<")) {
                    final Node l = left;
                    final Node r = parseAdditive();
                    left = sym -> l.eval(sym) < r.eval(sym) ? 1.0 : 0.0;
                } else if (match(">")) {
                    final Node l = left;
                    final Node r = parseAdditive();
                    left = sym -> l.eval(sym) > r.eval(sym) ? 1.0 : 0.0;
                } else {
                    return left;
                }
            }
        }

        Node parseAdditive() {
            Node left = parseMultiplicative();
            while (true) {
                skipSpaces();
                if (!eof() && this.s.charAt(this.pos) == '+' && !this.s.startsWith("++", this.pos)) {
                    this.pos++;
                    final Node l = left;
                    final Node r = parseMultiplicative();
                    left = sym -> l.eval(sym) + r.eval(sym);
                } else if (!eof() && this.s.charAt(this.pos) == '-' && !this.s.startsWith("--", this.pos)) {
                    this.pos++;
                    final Node l = left;
                    final Node r = parseMultiplicative();
                    left = sym -> l.eval(sym) - r.eval(sym);
                } else {
                    return left;
                }
            }
        }

        Node parseMultiplicative() {
            Node left = parseUnary();
            while (true) {
                if (match("*")) {
                    final Node l = left;
                    final Node r = parseUnary();
                    left = sym -> l.eval(sym) * r.eval(sym);
                } else if (match("/")) {
                    final Node l = left;
                    final Node r = parseUnary();
                    left = sym -> {
                        final double denom = r.eval(sym);
                        return Math.abs(denom) < 1.0e-12 ? 0.0 : l.eval(sym) / denom;
                    };
                } else if (match("%")) {
                    final Node l = left;
                    final Node r = parseUnary();
                    left = sym -> {
                        final double denom = r.eval(sym);
                        return Math.abs(denom) < 1.0e-12 ? 0.0 : l.eval(sym) % denom;
                    };
                } else {
                    return left;
                }
            }
        }

        Node parseUnary() {
            skipSpaces();
            if (match("!")) {
                final Node operand = parseUnary();
                return sym -> operand.eval(sym) == 0.0 ? 1.0 : 0.0;
            }
            if (match("-")) {
                final Node operand = parseUnary();
                return sym -> -operand.eval(sym);
            }
            if (match("+")) {
                return parseUnary();
            }
            return parsePrimary();
        }

        Node parsePrimary() {
            skipSpaces();
            if (eof()) {
                throw new IllegalArgumentException("Unexpected end of uniform expression: " + this.s);
            }
            if (match("(")) {
                final Node inner = parseTernary();
                if (!match(")")) {
                    throw new IllegalArgumentException("Missing ')' in uniform expression: " + this.s);
                }
                return inner;
            }
            final char c = this.s.charAt(this.pos);
            if ((c >= '0' && c <= '9') || c == '.') {
                return parseNumber();
            }
            if (Character.isJavaIdentifierStart(c)) {
                return parseIdentifierOrCall();
            }
            throw new IllegalArgumentException("Unexpected character '" + c + "' in expression: " + this.s);
        }

        Node parseNumber() {
            final int start = this.pos;
            while (!eof()) {
                final char ch = this.s.charAt(this.pos);
                if ((ch >= '0' && ch <= '9') || ch == '.' || ch == 'e' || ch == 'E'
                        || ((ch == '+' || ch == '-') && (this.s.charAt(this.pos - 1) == 'e' || this.s.charAt(this.pos - 1) == 'E'))) {
                    this.pos++;
                } else {
                    break;
                }
            }
            if (!eof() && (this.s.charAt(this.pos) == 'f' || this.s.charAt(this.pos) == 'F')) {
                this.pos++;
            }
            final double literal = Double.parseDouble(this.s.substring(start, this.pos).replace("f", "").replace("F", ""));
            return sym -> literal;
        }

        Node parseIdentifierOrCall() {
            final int start = this.pos;
            while (!eof()) {
                final char ch = this.s.charAt(this.pos);
                if (Character.isJavaIdentifierPart(ch) || ch == '.') {
                    this.pos++;
                } else {
                    break;
                }
            }
            final String name = this.s.substring(start, this.pos);
            skipSpaces();
            if (match("(")) {
                final List<Node> args = new ArrayList<>(4);
                skipSpaces();
                if (!match(")")) {
                    do {
                        args.add(parseTernary());
                    } while (match(","));
                    if (!match(")")) {
                        throw new IllegalArgumentException("Missing ')' in call to " + name + " in " + this.s);
                    }
                }
                return buildFunction(name.toLowerCase(Locale.ROOT), args);
            }
            if ("true".equalsIgnoreCase(name)) {
                return sym -> 1.0;
            }
            if ("false".equalsIgnoreCase(name)) {
                return sym -> 0.0;
            }
            if ("pi".equalsIgnoreCase(name)) {
                return sym -> Math.PI;
            }
            return sym -> sym.applyAsDouble(name);
        }

        private static Node buildFunction(final String fn, final List<Node> args) {
            return switch (fn) {
                case "sin" -> unary(args, Math::sin);
                case "cos" -> unary(args, Math::cos);
                case "tan" -> unary(args, Math::tan);
                case "asin" -> unary(args, Math::asin);
                case "acos" -> unary(args, Math::acos);
                case "atan" -> unary(args, Math::atan);
                case "torad" -> unary(args, Math::toRadians);
                case "todeg" -> unary(args, Math::toDegrees);
                case "abs" -> unary(args, Math::abs);
                case "floor" -> unary(args, Math::floor);
                case "ceil" -> unary(args, Math::ceil);
                case "round" -> unary(args, v -> (double) Math.round(v));
                case "sqrt" -> unary(args, v -> v <= 0.0 ? 0.0 : Math.sqrt(v));
                case "exp" -> unary(args, Math::exp);
                case "log" -> unary(args, v -> v <= 0.0 ? 0.0 : Math.log(v));
                case "sign" -> unary(args, Math::signum);
                case "frac", "fract" -> unary(args, v -> v - Math.floor(v));
                case "atan2" -> binary(args, Math::atan2);
                case "min" -> binary(args, Math::min);
                case "max" -> binary(args, Math::max);
                case "pow" -> binary(args, Math::pow);
                case "fmod" -> binary(args, (a, b) -> Math.abs(b) < 1.0e-12 ? 0.0 : a % b);
                case "step" -> binary(args, (edge, x) -> x < edge ? 0.0 : 1.0);
                case "clamp" -> {
                    requireArity(fn, args, 3);
                    final Node x = args.get(0), lo = args.get(1), hi = args.get(2);
                    yield sym -> Math.max(lo.eval(sym), Math.min(hi.eval(sym), x.eval(sym)));
                }
                case "mix", "lerp" -> {
                    requireArity(fn, args, 3);
                    final Node a = args.get(0), b = args.get(1), t = args.get(2);
                    yield sym -> {
                        final double av = a.eval(sym);
                        return av + (b.eval(sym) - av) * t.eval(sym);
                    };
                }
                case "smoothstep" -> {
                    requireArity(fn, args, 3);
                    final Node e0 = args.get(0), e1 = args.get(1), x = args.get(2);
                    yield sym -> {
                        final double lo = e0.eval(sym);
                        final double hi = e1.eval(sym);
                        final double denom = hi - lo;
                        if (Math.abs(denom) < 1.0e-12) {
                            return 0.0;
                        }
                        final double t = Math.max(0.0, Math.min(1.0, (x.eval(sym) - lo) / denom));
                        return t * t * (3.0 - 2.0 * t);
                    };
                }
                case "if" -> {
                    if (args.size() < 3 || args.size() % 2 == 0) {
                        throw new IllegalArgumentException("if(...) expects odd argument count >= 3");
                    }
                    yield sym -> {
                        for (int i = 0; i + 1 < args.size(); i += 2) {
                            if (args.get(i).eval(sym) != 0.0) {
                                return args.get(i + 1).eval(sym);
                            }
                        }
                        return args.get(args.size() - 1).eval(sym);
                    };
                }
                case "smooth" -> {
                    if (args.isEmpty()) {
                        throw new IllegalArgumentException("smooth(...) expects at least 1 argument");
                    }
                    final Node first = args.get(0);
                    yield first::eval;
                }
                default -> throw new IllegalArgumentException("Unsupported uniform function: " + fn);
            };
        }

        private static Node unary(final List<Node> args, final java.util.function.DoubleUnaryOperator op) {
            requireArity("unary", args, 1);
            final Node a = args.get(0);
            return sym -> op.applyAsDouble(a.eval(sym));
        }

        private static Node binary(final List<Node> args, final java.util.function.DoubleBinaryOperator op) {
            requireArity("binary", args, 2);
            final Node a = args.get(0);
            final Node b = args.get(1);
            return sym -> op.applyAsDouble(a.eval(sym), b.eval(sym));
        }

        private static void requireArity(final String fn, final List<Node> args, final int expected) {
            if (args.size() != expected) {
                throw new IllegalArgumentException("Function " + fn + " expects " + expected + " args, got " + args.size());
            }
        }
    }
}
