package sangria.validation.rules

import sangria.ast
import sangria.ast.AstVisitorCommand._
import sangria.renderer.SchemaRenderer
import sangria.schema.LeafType
import sangria.validation._

import scala.language.postfixOps

/**
 * Scalar leafs
 *
 * A GraphQL document is valid only if all leaf fields (fields without
 * sub selections) are of scalar or enum types.
 */
class ScalarLeafs extends ValidationRule {
  override def visitor(ctx: ValidationContext) = new AstValidatingVisitor {
    override val onEnter: ValidationVisit = {
      case ast.Field(_, name, _, _, sels, pos) =>
        ctx.typeInfo.tpe match {
          case Some(fieldType) =>
            val named = ctx.typeInfo.getNamedType(fieldType)

            named match {
              case tpe if tpe.isInstanceOf[LeafType] && sels.nonEmpty =>
                Left(Vector(NoSubselectionAllowedViolation(name, SchemaRenderer.renderTypeName(tpe, true), ctx.sourceMapper, pos.toList)))
              case tpe if !tpe.isInstanceOf[LeafType] && sels.isEmpty =>
                Left(Vector(RequiredSubselectionViolation(name, SchemaRenderer.renderTypeName(tpe, true), ctx.sourceMapper, pos.toList)))
              case _ => Right(Continue)
            }
          case None => Right(Continue)
        }
    }
  }
}