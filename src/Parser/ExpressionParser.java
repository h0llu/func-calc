package Parser;

import Exceptions.ParsingException;
import Operations.*;

import java.util.ArrayList;
import java.util.List;

public class ExpressionParser implements Parser {

	/*------------------------------------------------------------------
	 * Грамматические правила парсера LL(1)
	 *------------------------------------------------------------------*/
//
//    expression : addSubstract* END ;
//
//    addSubstract: multiplyDivide ( ( '+' | '-' ) multiplyDivide )* ;
//
//    multiplyDivide : factorPower ( ( '*' | '/' ) factorPower )* ;
//
//	  factorPower: factor ( '^' factor )* ;
//
//	  factor : CONST | VARIABLE |'(' expression ')' | '|' expression '|' | '-' expression;

	public Expression parse(String expression) throws ParsingException {
		if (expression.equals("")) {
			throw new ParsingException("Empty expression");
		}
		List<Lexeme> lexemes = Lexeme.lexAnalyze(expression);
		LexemeBuffer lexemeBuffer = new LexemeBuffer(lexemes);
		return Syntax.parseAnalyze(lexemeBuffer);
	}

	private enum LexemeType {
		PLUS, MINUS, MULTIPLY, DIVIDE, POWER, LEFT_BRACKET,
		RIGHT_BRACKET, VARIABLE, CONST, ABS, UNARY_MINUS, END
	}
	private static class Lexeme {
		LexemeType type;
		String value;

		public Lexeme(LexemeType type, String value) {
			this.type = type;
			this.value = value;
		}
		public Lexeme(LexemeType type, Character value) {
			this.type = type;
			this.value = value.toString();
		}
		@Override
		public String toString() {
			return "Lexeme{" +
					"type=" + type +
					", value='" + value + '\'' +
					'}';
		}

		public static List<Lexeme> lexAnalyze(String expText) throws ParsingException {
			ArrayList<Lexeme> lexemes = new ArrayList<>();
			int pos = 0;
			while (pos < expText.length()) {
				char c = expText.charAt(pos);
				switch (Character.toLowerCase(c)) {
					case '|':
						lexemes.add(new Lexeme(LexemeType.ABS, c));
						pos++;
						continue;
					case '(':
						lexemes.add(new Lexeme(LexemeType.LEFT_BRACKET, c));
						pos++;
						continue;
					case ')':
						lexemes.add(new Lexeme(LexemeType.RIGHT_BRACKET, c));
						pos++;
						continue;
					case '+':
						lexemes.add(new Lexeme(LexemeType.PLUS, c));
						pos++;
						continue;
					case '-':
						// UNARY_MINUS
						if (lexemes.isEmpty() ||
							lexemes.get(lexemes.size() - 1).value.equals("+") ||
							lexemes.get(lexemes.size() - 1).value.equals("-") ||
							lexemes.get(lexemes.size() - 1).value.equals("*") ||
							lexemes.get(lexemes.size() - 1).value.equals("/") ||
							lexemes.get(lexemes.size() - 1).value.equals("(") ||
							lexemes.get(lexemes.size() - 1).value.equals("^"))
						{
							lexemes.add(new Lexeme(LexemeType.UNARY_MINUS, c));
						}
						// BINARY_MINUS
						else {
							lexemes.add(new Lexeme(LexemeType.MINUS, c));
						}
						pos++;
						continue;
					case '*':
						lexemes.add(new Lexeme(LexemeType.MULTIPLY, c));
						pos++;
						continue;
					case '/':
						lexemes.add(new Lexeme(LexemeType.DIVIDE, c));
						pos++;
						continue;
					case '^':
						lexemes.add(new Lexeme(LexemeType.POWER, c));
						pos++;
						continue;
					case 'x':
						lexemes.add(new Lexeme(LexemeType.VARIABLE, c));
						pos++;
						continue;
					default:
						if (Character.isDigit(c)) {
							StringBuilder sb = new StringBuilder();
							do {
								sb.append(c);
								pos++;
								if (pos >= expText.length()) {
									break;
								}
								c = expText.charAt(pos);
							} while(Character.isDigit(c) || c == '.');
							lexemes.add(new Lexeme(LexemeType.CONST, sb.toString()));
						} else {
							if (c != ' ') {
								throw new ParsingException("Unexpected character: " + c);
							}
							pos++;
						}
				}
			}
			lexemes.add(new Lexeme(LexemeType.END, ""));
			return lexemes;
		}
	}
	private static class LexemeBuffer {
		private int pos;
		public List<Lexeme> lexemes;

		public LexemeBuffer(List<Lexeme> lexemes) {
			this.lexemes = lexemes;
		}

		public Lexeme next() {
			return lexemes.get(pos++);
		}

		public void back() {
			pos--;
		}

		public int getPos() {
			return pos;
		}
	}
	private static class Syntax {

		public static Expression parseAnalyze(LexemeBuffer lexemes) throws ParsingException {
			return expression(lexemes);
		}

		// expression : addSubstract* END ;
		private static Expression expression(LexemeBuffer lexemes) throws ParsingException {
			Lexeme lexeme = lexemes.next();
			if (lexeme.type == LexemeType.END) {
				return null;
			}
			else {
				lexemes.back();
				return addSubstract(lexemes);
			}
		}

		// addSubstract: multiplyDivide ( ( '+' | '-' ) multiplyDivide )* ;
		private static Expression addSubstract(LexemeBuffer lexemes) throws ParsingException {
			// правило практически аналогично multiplyDivide
			// меняются только знаки и вместо factor - multiplyDivide
			Expression value = multiplyDivide(lexemes);
			while (true) {
				Lexeme lexeme = lexemes.next();
				switch (lexeme.type) {
					case PLUS -> value = new Add(value, multiplyDivide(lexemes));
					case MINUS -> value = new Subtract(value, multiplyDivide(lexemes));
					default -> {
						lexemes.back();
						return value;
					}
				}
			}
		}

		// multiplyDivide : factorPower ( ( '*' | '/' ) factorPower )* ;
		private static Expression multiplyDivide(LexemeBuffer lexemes) throws ParsingException {
			Expression value = factorPower(lexemes);
			// т.к. грамматикой задано, что ( '*' | '/' ) factorPower
			// может быть любое конечно количество, создаем бесконечный цикл
			// в котором продолжаем парсить выражение, пока не встретиться что-то
			// кроме умножения, деления и factorPower
			while (true) {
				Lexeme lexeme = lexemes.next();
				switch (lexeme.type) {
					case MULTIPLY -> value = new Multiply(value, factorPower(lexemes));
					case DIVIDE -> value = new Divide(value, factorPower(lexemes));
					default -> {
						lexemes.back();
						return value;
					}
				}
			}
		}

		// factorPower: factor ( '^' factor )* ;
		private static Expression factorPower(LexemeBuffer lexemes) throws ParsingException {
			Expression value = factor(lexemes);
			while (true) {
				Lexeme lexeme = lexemes.next();
				if (lexeme.type == LexemeType.POWER) {
					value = new RaiseToAPower(value, factor(lexemes));
				}
				else {
					lexemes.back();
					return value;
				}
			}
		}

//	  		   factor : CONST | VARIABLE |'(' expression ')' | '|' expression '|' | '-' expression;
		private static Expression factor(LexemeBuffer lexemes) throws ParsingException {
			Lexeme lexeme = lexemes.next();
			switch (lexeme.type) {
				case CONST:
					return new Const(Double.parseDouble(lexeme.value));
				case VARIABLE:
					return new Variable(lexeme.value.toLowerCase());
				case UNARY_MINUS:
					return new Negate(expression(lexemes));
				case LEFT_BRACKET:
					Expression bracketsValue = expression(lexemes);
					// после выполнения expression() "указатель"
					// должен быть на правой скобке
					lexeme = lexemes.next();
					// если его там нет, тогда забыли скобку
					if (lexeme.type != LexemeType.RIGHT_BRACKET) {
						throw new ParsingException("Unexpected " + lexeme.value
								+ " at position " + lexemes.getPos());
					}
					return bracketsValue;
				case ABS:
					Expression absValue = new AbsoluteValue(expression(lexemes));
					lexeme = lexemes.next();
					if (lexeme.type != LexemeType.ABS) {
						throw new ParsingException("Unexpected " + lexeme.value
								+ " at position " + lexemes.getPos());
					}
					return absValue;
				// если на данном этапе встречена другая лексема,
				// выражение написано неверно
				default:
					throw new ParsingException("Unexpected " + lexeme.value
							+ " at position " + lexemes.getPos());
			}
		}
	}
}