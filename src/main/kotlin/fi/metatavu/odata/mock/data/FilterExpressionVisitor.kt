package fi.metatavu.odata.mock.data

import org.apache.olingo.commons.api.data.Entity
import org.apache.olingo.commons.api.edm.EdmEnumType
import org.apache.olingo.commons.api.edm.EdmType
import org.apache.olingo.commons.api.http.HttpStatusCode
import org.apache.olingo.commons.core.edm.primitivetype.*
import org.apache.olingo.server.api.ODataApplicationException
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty
import org.apache.olingo.server.api.uri.queryoption.expression.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mock implementation of filter expression visitor
 *
 * @param currentEntity entity
 */
class FilterExpressionVisitor(private val currentEntity: Entity) : ExpressionVisitor<Any> {

    override fun visitMember(member: Member): Any {
        val uriResourceParts = member.resourcePath.uriResourceParts

        return if (uriResourceParts.size == 1 && uriResourceParts[0] is UriResourcePrimitiveProperty) {
            val uriResourceProperty = uriResourceParts[0] as UriResourcePrimitiveProperty
            currentEntity.getProperty(uriResourceProperty.property.name).value
        } else {
            throw ODataApplicationException(
                "Only primitive properties are implemented in filter expressions",
                HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
            )
        }
    }

    override fun visitLiteral(literal: Literal): Any {
        return when (literal.type) {
            is EdmString -> {
                var stringLiteral = ""
                if (literal.text.length > 2) {
                    stringLiteral = literal.text.substring(1, literal.text.length - 1)
                }

                stringLiteral
            }

            is EdmDate -> {
                parseDate(literal = literal.text, tryFormats = arrayOf("yyyy-MM-dd"))
            }

            is EdmDateTimeOffset -> {
                parseDate(literal = literal.text, tryFormats = arrayOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mmXXX")).time
            }

            is EdmTimeOfDay -> {
                parseDate(literal = literal.text, tryFormats = arrayOf("HH:mm"))
            }

            is EdmInt64 -> {
                literal.text.toInt()
            }

            is EdmInt32 -> {
                literal.text.toInt()
            }

            is EdmInt16 -> {
                literal.text.toInt()
            }

            else -> {
                throw ODataApplicationException("Visit literal ${literal.type} not implemented.",
                    HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
                )
            }
        }
    }

    override fun visitUnaryOperator(operator: UnaryOperatorKind, operand: Any): Any {
        if (operator == UnaryOperatorKind.NOT && operand is Boolean) {
            return !operand
        } else if (operator == UnaryOperatorKind.MINUS && operand is Int) {
            return -operand
        }

        throw ODataApplicationException(
            "Invalid type for unary operator",
            HttpStatusCode.BAD_REQUEST.statusCode, Locale.ENGLISH
        )
    }

    override fun visitBinaryOperator(operator: BinaryOperatorKind?, left: Any?, right: MutableList<Any>?): Any {
        throw ODataApplicationException(
            "Binary lists are not implemented",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun visitBinaryOperator(operator: BinaryOperatorKind, left: Any, right: Any): Any {
        return if (operator == BinaryOperatorKind.ADD || operator == BinaryOperatorKind.MOD || operator == BinaryOperatorKind.MUL || operator == BinaryOperatorKind.DIV || operator == BinaryOperatorKind.SUB) {
            evaluateArithmeticOperation(operator, left, right)
        } else if (operator == BinaryOperatorKind.EQ || operator == BinaryOperatorKind.NE || operator == BinaryOperatorKind.GE || operator == BinaryOperatorKind.GT || operator == BinaryOperatorKind.LE || operator == BinaryOperatorKind.LT) {
            evaluateComparisonOperation(operator, left, right)
        } else if (operator == BinaryOperatorKind.AND
            || operator == BinaryOperatorKind.OR
        ) {
            evaluateBooleanOperation(operator, left, right)
        } else {
            throw ODataApplicationException(
                "Binary operation " + operator.name + " is not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
            )
        }
    }

    /**
     * Evaluate boolean operator
     *
     * @param operator operator
     * @param left left side
     * @param right right side
     * @return evaluation result
     */
    private fun evaluateBooleanOperation(operator: BinaryOperatorKind, left: Any, right: Any): Any {
        return if (left is Boolean && right is Boolean) {
            if (operator == BinaryOperatorKind.AND) {
                left && right
            } else {
                left || right
            }
        } else {
            throw ODataApplicationException(
                "Boolean operations needs two numeric operands",
                HttpStatusCode.BAD_REQUEST.statusCode, Locale.ENGLISH
            )
        }
    }

    /**
     * Evaluates compare operation
     *
     * @param operator operator
     * @param left left side
     * @param right right side
     * @return result
     */
    private fun evaluateComparisonOperation(operator: BinaryOperatorKind, left: Any, right: Any): Any {
        return if (isComparable(left = left, right = right)) {
            val result = when (left) {
                is Int -> left.compareTo((right as Int))
                is String -> left.compareTo((right as String))
                is Boolean -> left.compareTo((right as Boolean))
                is GregorianCalendar -> left.compareTo(right as GregorianCalendar)
                is Date -> left.compareTo(right as Date)
                else -> {
                    throw ODataApplicationException(
                        "Class " + left.javaClass.canonicalName + " not expected",
                        HttpStatusCode.INTERNAL_SERVER_ERROR.statusCode, Locale.ENGLISH
                    )
                }
            }

            if (operator == BinaryOperatorKind.EQ) {
                result == 0
            } else if (operator == BinaryOperatorKind.NE) {
                result != 0
            } else if (operator == BinaryOperatorKind.GE) {
                result >= 0
            } else if (operator == BinaryOperatorKind.GT) {
                result > 0
            } else if (operator == BinaryOperatorKind.LE) {
                result <= 0
            } else {
                result < 0
            }
        } else {
            throw ODataApplicationException(
                "Comparison needs two equal types ${left.javaClass} !== ${right.javaClass}",
                HttpStatusCode.BAD_REQUEST.statusCode, Locale.ENGLISH
            )
        }
    }

    /**
     * Returns whether comparison of left and right is supported by this service
     *
     * @param left left side
     * @param right right side
     * @return true if comparable
     */
    private fun isComparable(left: Any, right: Any): Boolean {
        if (left !is Comparable<*> || right !is Comparable<*>) {
            return false
        }

        if (left is Int && right is Int) {
            return true
        } else if (left is String && right is String) {
            return true
        } else if (left is Boolean && right is Boolean) {
            return true
        } else if (left is GregorianCalendar && right is GregorianCalendar) {
            return true
        } else if (left is Date && right is Date) {
            return true
        }

        return left.javaClass == right.javaClass
    }

    /**
     * Evaluates arithmetic operation
     *
     * @param operator operator
     * @param left left side
     * @param right right side
     * @return result
     */
    private fun evaluateArithmeticOperation(
        operator: BinaryOperatorKind,
        left: Any,
        right: Any
    ): Any {
        return if (left is Int && right is Int) {
            if (operator == BinaryOperatorKind.ADD) {
                left + right
            } else if (operator == BinaryOperatorKind.SUB) {
                left - right
            } else if (operator == BinaryOperatorKind.MUL) {
                left * right
            } else if (operator == BinaryOperatorKind.DIV) {
                left / right
            } else {
                left % right
            }
        } else {
            throw ODataApplicationException(
                "Arithmetic operations needs two numeric operands",
                HttpStatusCode.BAD_REQUEST.statusCode, Locale.ENGLISH
            )
        }
    }

    override fun visitMethodCall(methodCall: MethodKind, parameters: List<Any>): Any {
        return if (methodCall == MethodKind.CONTAINS) {
            if (parameters[0] is String && parameters[1] is String) {
                val valueParam1 = parameters[0] as String
                val valueParam2 = parameters[1] as String
                valueParam1.contains(valueParam2)
            } else {
                throw ODataApplicationException(
                    "Contains needs two parameters of type Edm.String",
                    HttpStatusCode.BAD_REQUEST.statusCode, Locale.ENGLISH
                )
            }
        } else {
            throw ODataApplicationException(
                "Method call $methodCall not implemented",
                HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
            )
        }
    }

    override fun visitTypeLiteral(type: EdmType): Any {
        throw ODataApplicationException(
            "Type literals are not implemented",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun visitAlias(aliasName: String): Any {
        throw ODataApplicationException(
            "Aliases are not implemented",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun visitEnum(type: EdmEnumType, enumValues: List<String>): Any? {
        if (enumValues.isEmpty()) {
            return null
        }

        val enumValue = enumValues.first()
        val member = type.getMember(enumValue) ?: return null
        val value = member.value ?: return null

        return when (type.underlyingType) {
            is EdmInt16 -> value.toInt()
            is EdmInt32 -> value.toInt()
            is EdmInt64 -> value.toInt()
            is EdmString -> value
            else -> {
                throw ODataApplicationException(
                    "Enums are not implemented for type ${type.underlyingType}",
                    HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
                )
            }
        }
    }

    override fun visitLambdaExpression(lambdaFunction: String, lambdaVariable: String, expression: Expression): Any {
        throw ODataApplicationException(
            "Lambda expressions are not implemented",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    override fun visitLambdaReference(variableName: String): Any {
        throw ODataApplicationException(
            "Lambda references are not implemented",
            HttpStatusCode.NOT_IMPLEMENTED.statusCode, Locale.ENGLISH
        )
    }

    /**
     * Parses string literal to Calendar object using given date format
     *
     * If parsing fails, IllegalArgumentException is thrown
     *
     * @param literal string literal
     * @param dateFormat date format
     * @return Calendar object
     */
    private fun parseDate(literal: String, dateFormat: SimpleDateFormat): Calendar {
        val date = dateFormat.parse(literal)
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar
    }

    /**
     * Tries to parse string literal to Calendar object using given date formats
     *
     * If parsing fails, IllegalArgumentException is thrown
     *
     * @param literal string literal
     * @param tryFormats date formats to try
     * @return Calendar object
     */
    private fun parseDate(literal: String, tryFormats: Array<String>): Calendar {
        for (tryFormat in tryFormats) {
            try {
                return parseDate(literal, SimpleDateFormat(tryFormat))
            } catch (e: Exception) {
                // Ignore
            }
        }

        throw IllegalArgumentException("Failed to parse date $literal")
    }

}