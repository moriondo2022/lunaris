package lunaris.app

import better.files.File
import lunaris.data.BlockGzippedWithIndex

import scala.language.reflectiveCalls
import lunaris.io.InputId

object Lunaris {
  def main(args: Array[String]): Unit = {
    println(s"This is ${LunarisInfo.versionLong}")
    val conf = new LunarisConf(args)
    conf.subcommands match {
      case List(conf.batch) =>
        val input = InputId(conf.batch.requestFile())
        BatchRunner.run(input)
      case List(conf.server) =>
        ServerRunner.run(conf.server.host.toOption, conf.server.port.toOption)
      case List(conf.variantEffectPredictor) =>
        val dataFileWithIndex =
          BlockGzippedWithIndex(
            conf.variantEffectPredictor.dataFile(),
            conf.variantEffectPredictor.indexFile.toOption
          )
        VariantEffectPredictorServerRunner.run(
          conf.variantEffectPredictor.host.toOption,
          conf.variantEffectPredictor.port.toOption,
          conf.variantEffectPredictor.inputsFolder.map(File(_))(),
          conf.variantEffectPredictor.resultsFolder.map(File(_))(),
          dataFileWithIndex,
          conf.variantEffectPredictor.varId()
        )
    }
  }
}
