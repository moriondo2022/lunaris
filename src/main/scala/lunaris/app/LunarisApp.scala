package lunaris.app

import lunaris.data.DataSources
import lunaris.genomics.Region
import lunaris.io.query.RecordExtractor

object LunarisApp {
  def main(args: Array[String]): Unit = {
    val usingLocalSimulatedDataOnOliversLaptop: Boolean = true
    val dataSourceWithIndex = if (usingLocalSimulatedDataOnOliversLaptop)
      DataSources.simDataOnOliversOldLaptop
    else
      DataSources.simDataOnTerra
    val regionsBySequence = Map("1" -> Seq(Region(1, 100000)))
    val recordEitherator = RecordExtractor.extract(dataSourceWithIndex, regionsBySequence)
    recordEitherator.foreach { record =>
      println(record)
    }
  }
}
