package sangria.execution

import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{Matchers, WordSpec}
import sangria.schema._
import sangria.util.{GraphQlSupport, AwaitSupport}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class MutationSpec extends WordSpec with Matchers with GraphQlSupport {
  class NumberHolder(initial: Int) {
    val theNumber = new AtomicInteger(initial)

    def getAndSet(newNumber: Int) =
      theNumber getAndSet newNumber
  }

  class Root(initial: Int) {
    val numberHolder = new NumberHolder(initial)
    val rnd = new Random

    def immediatelyChangeTheNumber(newNumber: Int) = {
      numberHolder getAndSet newNumber
      numberHolder
    }

    def promiseToChangeTheNumber(newNumber: Int) =
      Future {
        Thread.sleep(rnd nextInt 50)
        numberHolder getAndSet newNumber
        Thread.sleep(rnd nextInt 50)
        numberHolder
      }

    def failToChangeTheNumber(newNumber: Int): NumberHolder =
      throw new IllegalStateException("Cannot change the number")

    def promiseAndFailToChangeTheNumber(newNumber: Int): Future[NumberHolder] =
      Future {
        Thread.sleep(rnd nextInt 50)
        throw new IllegalStateException("Cannot change the number")
      }
  }

  case class UserContext(num: Int)

  val NumberHolderType = ObjectType("NumberHolder", List[Field[UserContext, NumberHolder]](
    Field("theNumber", OptionType(IntType), resolve = _.value.theNumber.get()),
    Field("userCtx", OptionType(IntType), resolve = _.ctx.num)
  ))

  val NewNumberArg = Argument("newNumber", OptionInputType(IntType))

  val schema = Schema(
    ObjectType("Query", List[Field[UserContext, Root]](
      Field("numberHolder", OptionType(NumberHolderType), resolve = _.value.numberHolder)
    )),
    Some(ObjectType("Mutation", List[Field[UserContext, Root]](
      Field("immediatelyChangeTheNumber", OptionType(NumberHolderType),
        arguments = NewNumberArg :: Nil,
        resolve = ctx => UpdateCtx(ctx.value.immediatelyChangeTheNumber(ctx.arg(NewNumberArg)))(v => ctx.ctx.copy(num = 10 + v.theNumber.get()))),
      Field("promiseToChangeTheNumber", OptionType(NumberHolderType),
        arguments = NewNumberArg :: Nil,
        resolve = ctx => UpdateCtx(ctx.value.promiseToChangeTheNumber(ctx.arg(NewNumberArg)))(v => ctx.ctx.copy(num = 10 + v.theNumber.get()))),
      Field("failToChangeTheNumber", OptionType(NumberHolderType),
        arguments = NewNumberArg :: Nil,
        resolve = ctx => UpdateCtx(ctx.value.failToChangeTheNumber(ctx.arg(NewNumberArg)))(v => ctx.ctx.copy(num = 10 + v.theNumber.get()))),
      Field("promiseAndFailToChangeTheNumber", OptionType(NumberHolderType),
        arguments = NewNumberArg :: Nil,
        resolve = ctx => UpdateCtx(ctx.value.promiseAndFailToChangeTheNumber(ctx.arg(NewNumberArg)))(v => ctx.ctx.copy(num = 10 + v.theNumber.get())))
    )))
  )

  "Execute: Handles mutation execution ordering" should {
    "evaluates mutations serially" in check(
      new Root(6),
      """
        mutation M {
          first: immediatelyChangeTheNumber(newNumber: 1) {
            theNumber
            userCtx
          },
          second: promiseToChangeTheNumber(newNumber: 2) {
            theNumber
            userCtx
          },
          third: immediatelyChangeTheNumber(newNumber: 3) {
            theNumber
            userCtx
          }
          fourth: promiseToChangeTheNumber(newNumber: 4) {
            theNumber
            userCtx
          },
          fifth: immediatelyChangeTheNumber(newNumber: 5) {
            theNumber
            userCtx
          }
        }
      """,
      Map(
        "data" -> Map(
          "first"  -> Map(
            "theNumber" -> 1,
            "userCtx" -> 10),
          "second" -> Map(
            "theNumber" -> 2,
            "userCtx" -> 11),
          "third"  -> Map(
            "theNumber" -> 3,
            "userCtx" -> 12),
          "fourth" -> Map(
            "theNumber" -> 4,
            "userCtx" -> 13),
          "fifth"  -> Map(
            "theNumber" -> 5,
            "userCtx" -> 14)
        )
      ),
      userContext = UserContext(10)
    )

    "evaluates mutations correctly in the presense of a failed mutation" in checkErrors(
      new Root(6),
      """
        mutation M {
          first: immediatelyChangeTheNumber(newNumber: 1) {
            theNumber userCtx
          },
          second: promiseToChangeTheNumber(newNumber: 2) {
            theNumber userCtx
          },
          third: failToChangeTheNumber(newNumber: 3) {
            theNumber userCtx
          }
          fourth: promiseToChangeTheNumber(newNumber: 4) {
            theNumber userCtx
          },
          fifth: immediatelyChangeTheNumber(newNumber: 5) {
            theNumber userCtx
          }
          sixth: promiseAndFailToChangeTheNumber(newNumber: 6) {
            theNumber userCtx
          }
        }
      """,
      Map(
        "first"  -> Map(
          "theNumber" -> 1,
          "userCtx" -> 10),
        "second" -> Map(
          "theNumber" -> 2,
          "userCtx" -> 11),
        "third"  -> null,
        "fourth" -> Map(
          "theNumber" -> 4,
          "userCtx" -> 12),
        "fifth"  -> Map(
          "theNumber" -> 5,
          "userCtx" -> 14),
        "sixth"  -> null
      ),
      List(
        Map(
          "message" -> "Cannot change the number",
          "field" -> "third",
          "locations" -> List(Map("line" -> 9, "column" -> 11))), // todo fix duplicate errors
        Map(
          "message" -> "Cannot change the number",
          "field" -> "third",
          "locations" -> List(Map("line" -> 9, "column" -> 11))),
        Map("message" -> "Cannot change the number",
          "field" -> "sixth",
          "locations" -> List(Map("line" -> 18, "column" -> 11)))),
      userContext = UserContext(10)
    )
  }
}
