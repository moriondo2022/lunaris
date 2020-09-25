package lunaris.utils

import better.files.File
import com.typesafe.config.{Config, ConfigValue, ConfigValueFactory, ConfigValueType}
import lunaris.app.{Lunaris, LunarisMode}
import lunaris.io.InputId
import org.broadinstitute.yootilz.core.snag.Snag

import scala.util.{Failure, Success, Try}

trait ConfigBox[B <: ConfigBox[B]] {
  def map(mapper: Config => Config): B

  def withFallback(oConfigBox: B): B = map(_.withFallback(oConfigBox.config))

  def config: Config
}

object ConfigBox {

  trait Field[B <: ConfigBox[B], V] {
    def configBox: B

    def path: String

    def getValue: Either[Snag, ConfigValue] = {
      val config = configBox.config
      if (config.hasPath(path)) {
        Right(config.getValue(path))
      } else {
        if (config.hasPathOrNull(path)) {
          Left(Snag(s"Config value at $path is null."))
        } else {
          Left(Snag(s"No config value at $path."))
        }
      }
    }

    def getValueOfType(valueType: ConfigValueType): Either[Snag, ConfigValue] = {
      for {
        value <- getValue
        _ <- {
          if (value.valueType() == valueType) {
            Right(())
          } else {
            Left(Snag(s"Wrong type of config value: expected $valueType, but got ${value.valueType()}"))
          }
        }
      } yield value
    }

    def get: Either[Snag, V]

    def set(value: V): B
  }

  trait PrimitiveValueField[B <: ConfigBox[B], T] extends Field[B, T] {
    def configValueType: ConfigValueType

    def unwrapValue(configValue: ConfigValue): Either[Snag, T]

    def wrapValue(value: T): ConfigValue

    override def get: Either[Snag, T] = {
      getValueOfType(configValueType).flatMap(unwrapValue)
    }

    override def set(value: T): B = {
      configBox.map { config =>
        config.withValue(path, wrapValue(value))
      }
    }
  }

  case class StringField[B <: ConfigBox[B]](configBox: B, path: String) extends PrimitiveValueField[B, String] {
    override def configValueType: ConfigValueType = ConfigValueType.STRING

    override def unwrapValue(configValue: ConfigValue): Either[Snag, String] =
      Right(configValue.unwrapped().asInstanceOf[String])

    override def wrapValue(string: String): ConfigValue = ConfigValueFactory.fromAnyRef(string)
  }

  case class IntField[B <: ConfigBox[B]](configBox: B, path: String) extends PrimitiveValueField[B, Int] {
    override def configValueType: ConfigValueType = ConfigValueType.NUMBER

    override def unwrapValue(configValue: ConfigValue): Either[Snag, Int] =
      Right(configValue.unwrapped().asInstanceOf[Int])

    override def wrapValue(integer: Int): ConfigValue = ConfigValueFactory.fromAnyRef(integer)
  }

  case class FileField[B <: ConfigBox[B]](configBox: B, path: String) extends PrimitiveValueField[B, File] {
    override def configValueType: ConfigValueType = ConfigValueType.STRING

    override def unwrapValue(configValue: ConfigValue): Either[Snag, File] = {
      Try(File(configValue.unwrapped().asInstanceOf[String])) match {
        case Failure(exception) => Left(Snag(exception))
        case Success(file) => Right(file)
      }
    }

    override def wrapValue(file: File): ConfigValue = ConfigValueFactory.fromAnyRef(file.toString())
  }

  case class InputIdField[B <: ConfigBox[B]](configBox: B, path: String) extends PrimitiveValueField[B, InputId] {
    override def configValueType: ConfigValueType = ConfigValueType.STRING

    override def unwrapValue(configValue: ConfigValue): Either[Snag, InputId] = {
      Right(InputId(configValue.unwrapped().asInstanceOf[String]))
    }

    override def wrapValue(inputId: InputId): ConfigValue = ConfigValueFactory.fromAnyRef(inputId.toString())
  }

  case class LunarisModeField[B <: ConfigBox[B]](configBox: B, path: String)
    extends PrimitiveValueField[B, LunarisMode] {
    override def configValueType: ConfigValueType = ConfigValueType.STRING

    override def unwrapValue(configValue: ConfigValue): Either[Snag, LunarisMode] =
      LunarisMode.parse(configValue.unwrapped().asInstanceOf[String])

    override def wrapValue(mode: LunarisMode): ConfigValue = ConfigValueFactory.fromAnyRef(mode.toString)
  }

}
