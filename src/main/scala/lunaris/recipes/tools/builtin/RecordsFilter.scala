package lunaris.recipes.tools.builtin

import lunaris.expressions.BooleanRecordExpression
import lunaris.recipes.eval.LunWorker.RecordStreamWorker
import lunaris.recipes.eval.WorkerMaker.WorkerBox
import lunaris.recipes.eval.{LunCompileContext, LunRunContext, LunRunnable, LunWorker, WorkerMaker}
import lunaris.recipes.tools.{Tool, ToolArgUtils, ToolCall, ToolInstanceUtils}
import lunaris.recipes.values.{LunType, LunValue, RecordStream}
import lunaris.recipes.{eval, tools}
import org.broadinstitute.yootilz.core.snag.Snag

object RecordsFilter extends tools.Tool {
  override def resultType: LunType = LunType.RecordStreamType

  object Params {

    object Keys {
      val from = "from"
      val filter = "filter"
    }

    val from: Tool.RefParam = Tool.RefParam(Keys.from, LunType.RecordStreamType, isRequired = true)
    val filter: Tool.ValueParam =
      Tool.ValueParam(Keys.filter, LunType.ExpressionType(LunType.BoolType), isRequired = true)
  }

  override def params: Seq[Tool.Param] = Seq(Params.from, Params.filter)

  override def isFinal: Boolean = false

  override def newToolInstance(args: Map[String, ToolCall.Arg]): Either[Snag, ToolInstance] = {
    for {
      from <- ToolArgUtils.asRef(Params.Keys.from, args)
      expression <- ToolArgUtils.asExpression(Params.Keys.filter, args)
      booleanExpression <- {
        expression match {
          case booleanExpression: BooleanRecordExpression => Right(booleanExpression)
          case otherExpression =>
            Left(Snag(s"Expected boolean expression, but expression is of type ${otherExpression.returnType.asString}"))
        }
      }
    } yield ToolInstance(from, booleanExpression)
  }

  case class ToolInstance(from: String, filter: BooleanRecordExpression) extends tools.ToolInstance {
    override def refs: Map[String, String] = Map(Params.Keys.from -> from)

    override def newWorkerMaker(context: LunCompileContext,
                                workers: Map[String, LunWorker]): Either[Snag, eval.WorkerMaker] = {
      ToolInstanceUtils.newWorkerMakerSingleRef(Params.Keys.from, workers) { fromWorker =>
        new WorkerMaker(fromWorker, filter)
      }
    }
  }

  class WorkerMaker(fromWorker: RecordStreamWorker, filter: BooleanRecordExpression)
    extends eval.WorkerMaker with eval.WorkerMaker.WithOutput {
    override def finalizeAndShip(): WorkerMaker.WorkerBox = new WorkerBox {
      override def pickupWorkerOpt(receipt: WorkerMaker.Receipt): Option[LunWorker] = Some[RecordStreamWorker] {
        (context: LunRunContext) =>
          fromWorker.getSnagOrStreamDisposable(context).map(_.map { fromStream =>
            val filteredSource = fromStream.source.filter { record =>
              println("Filter gives " + filter.evaluate(record))
              filter.evaluate(record) match {
                case Right(LunValue.PrimitiveValue.BoolValue(value)) => value
                case Left(snag) =>
                  println(snag.message)
                  false
              }
            }
            val meta = fromStream.meta
            RecordStream(meta, filteredSource)
          })
      }

      override def pickupRunnableOpt(): Option[LunRunnable] = None
    }
  }

}