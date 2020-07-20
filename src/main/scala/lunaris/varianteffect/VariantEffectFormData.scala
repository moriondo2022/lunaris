package lunaris.varianteffect

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Multipart
import lunaris.genomics.Variant

import scala.concurrent.{ExecutionContextExecutor, Future}

case class VariantEffectFormData(fileName: String, variantsByChrom: Map[String, Seq[Variant]])

object VariantEffectFormData {
  def fromFields(fields: Map[String, FormField]): VariantEffectFormData = {
    val inputFileField = fields(FormField.Keys.inputFile).asInstanceOf[InputFileField]
    val fileName = inputFileField.fileName
    val variantsByChrom = inputFileField.variantsByChrom
    VariantEffectFormData(fileName, variantsByChrom)
  }

  sealed trait FormField {
    def name: String
  }

  case class InputFileField(fileName: String, variantsByChrom: Map[String, Seq[Variant]]) extends FormField {
    override def name: String = FormField.Keys.inputFile
  }

  case class IgnoredField(name: String) extends FormField

  object FormField {

    object Keys {
      val inputFile: String = "inputFile"
    }

    def bodyPartToFieldFut(bodyPart: Multipart.FormData.BodyPart)(
      implicit actorSystem: ActorSystem): Future[FormField] = {
      implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
      bodyPart.name match {
        case Keys.inputFile =>
          VcfStreamVariantsReader.newVariantsByChromFuture(bodyPart.entity.dataBytes).map { variantsByChrom =>
            InputFileField(bodyPart.filename.get, variantsByChrom)
          }
        case unknownName: String =>
          bodyPart.entity.dataBytes.runFold(())((_, _) => ()).map(_ => IgnoredField(unknownName))
      }

    }
  }

}