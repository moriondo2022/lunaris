package lunaris.streams

import lunaris.genomics.Locus
import lunaris.streams.utils.RecordStreamTypes.{Meta, Record, RecordSource}
import lunaris.streams.utils.StreamTagger.TaggedItem
import lunaris.streams.utils.{StreamTagger, TaggedRecordOrdering}
import org.broadinstitute.yootilz.core.snag.Snag

object RecordStreamJoinerWithFallback {
  type Joiner = (Record, Record) => Either[Snag, Record]
  type Fallback = Record => Either[Snag, Record]
  type SnagLogger = Snag => Unit

  sealed trait SourceId

  object DriverSourceId extends SourceId

  case class DataSourceId(i: Int) extends SourceId

  class MergedTaggedRecordProcessor(nDataSources: Int)(joiner: Joiner)(fallback: Fallback)(snagLogger: SnagLogger) {

    case class GotLastOf(gotLastOfDriver: Boolean, gotLastsOfData: Seq[Boolean]) {
      def add(sourceId: SourceId, gotLast: Boolean): GotLastOf = {
        sourceId match {
          case DriverSourceId => copy(gotLastOfDriver = gotLast)
          case DataSourceId(i) => copy(gotLastsOfData = gotLastsOfData.updated(i, gotLast))
        }
      }
    }

    object GotLastOf {
      def createNew(): GotLastOf = GotLastOf(gotLastOfDriver = false, Seq.fill(nDataSources)(false))
    }

    case class BufferForLocus(locus: Locus, drivers: Seq[Record], dataRecords: Seq[Map[String, Record]]) {
      def add(sourceId: SourceId, record: Record): BufferForLocus = {
        sourceId match {
          case DriverSourceId =>
            copy(drivers = drivers :+ record)
          case DataSourceId(i) =>
            val recordsForSourceOld = dataRecords(i)
            val recordsForSourceNew = recordsForSourceOld + (record.id -> record)
            copy(dataRecords = dataRecords.updated(i, recordsForSourceNew))
        }
      }
    }

    object BufferForLocus {
      def fromRecord(sourceId: SourceId, record: Record): BufferForLocus = {
        val locus = record.locus
        sourceId match {
          case DriverSourceId =>
            BufferForLocus(locus, Seq(record), Seq.fill(nDataSources)(Map.empty))
          case DataSourceId(i) =>
            val dataRecords = Seq.tabulate[Map[String, Record]](nDataSources) { j =>
              if(j == i) {
                Map(record.id -> record)
              } else {
                Map.empty
              }
            }
            BufferForLocus(locus, Seq.empty, dataRecords)
        }
      }
    }

    case class Buffer(gotLastOf: GotLastOf, buffersByLocus: Seq[BufferForLocus]) {
      def add(taggedRecord: TaggedItem[Record, SourceId]): Buffer = {
        val sourceId = taggedRecord.sourceId
        val gotLastOfNew = gotLastOf.add(sourceId, taggedRecord.isLast)
        val recordAdded = taggedRecord.item
        val locusAdded = recordAdded.locus
        val buffersByLocusNew = buffersByLocus.lastOption match {
          case Some(bufferForLocusLast) if bufferForLocusLast.locus == locusAdded =>
            val bufferForLocusLastNew = bufferForLocusLast.add(sourceId, recordAdded)
            buffersByLocus.updated(buffersByLocus.size - 1, bufferForLocusLastNew)
          case _ =>
            buffersByLocus :+ BufferForLocus.fromRecord(sourceId, recordAdded)
        }
        Buffer(gotLastOfNew, buffersByLocusNew)
      }

      def join(): (Buffer, Seq[Record]) = {
        ???
      }

      def process(taggedRecord: TaggedItem[Record, SourceId]): (Buffer, Seq[Record]) = add(taggedRecord).join()
    }

    object Buffer {
      def create(): Buffer = Buffer(GotLastOf.createNew(), Seq.empty)
    }

    var buffer: Buffer = Buffer.create()
    def processNext(taggedRecord: TaggedItem[Record, SourceId]): Seq[Record] = {
      val (bufferNew, recordsJoined) = buffer.process(taggedRecord)
      buffer = bufferNew
      recordsJoined
    }
  }


  def joinWithFallback(meta: Meta,
                       driverSource: RecordSource,
                       dataSources: Seq[RecordSource]
                      )(
                        joiner: (Record, Record) => Either[Snag, Record]
                      )(
                        fallBack: Record => Either[Snag, Record]
                      )(
                        snagLogger: SnagLogger
                      ): RecordSource = {
    val driverSourceTagged = StreamTagger.tagSource[Record, Meta, SourceId](driverSource, DriverSourceId)
    val dataSourcesTagged = dataSources.zipWithIndex.map {
      case (dataSource, i) =>
        StreamTagger.tagSource[Record, Meta, SourceId](dataSource, DataSourceId(i))
    }
    implicit val taggedRecordOrdering: TaggedRecordOrdering[SourceId] = TaggedRecordOrdering(meta.chroms)
    val mergedTaggedSource = dataSourcesTagged.foldLeft(driverSourceTagged)(_.mergeSorted(_))
    val mergedTaggedRecordProcessor = new MergedTaggedRecordProcessor(dataSources.size)(joiner)(fallBack)(snagLogger)
    mergedTaggedSource.statefulMapConcat(() => mergedTaggedRecordProcessor.processNext).mapMaterializedValue(_ => meta)
  }
}
