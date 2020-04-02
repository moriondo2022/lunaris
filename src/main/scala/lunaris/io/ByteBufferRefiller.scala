package lunaris.io

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

import org.broadinstitute.yootilz.core.snag.Snag

import scala.util.control.NonFatal

trait ByteBufferRefiller {
  def buffer: ByteBuffer

  def refill(nBytesNeeded: Int): Either[Snag, Int]

  def makeAvailable(nBytesNeeded: Int): Either[Snag, Int] = {
    if (buffer.remaining() < nBytesNeeded) {
      refill(nBytesNeeded)
    } else {
      Right(buffer.remaining())
    }
  }

  def read[T](reader: ByteBuffer => T): Either[Snag, T] = {
    try {
      Right(reader(buffer))
    } catch {
      case NonFatal(ex) => Left(Snag("Could not read from buffer", Snag(ex)))
    }
  }

  def read[T](nBytesNeeded: Int)(reader: ByteBuffer => T): Either[Snag, T] = {
    val snagOrBytesAvailable = makeAvailable(nBytesNeeded)
    for {
      _ <- snagOrBytesAvailable
      value <- read(reader)
    } yield value
  }
}

object ByteBufferRefiller {

  def apply(channel: ReadableByteChannel, bufferSize: Int): FromChannel = new FromChannel(channel, bufferSize)

  class FromChannel(val channel: ReadableByteChannel, val bufferSize: Int) extends ByteBufferRefiller {
    val bytes: Array[Byte] = new Array[Byte](bufferSize)
    override val buffer: ByteBuffer = ByteBuffer.wrap(bytes)
    channel.read(buffer)
    buffer.flip()

    override def refill(nBytesNeeded: Int): Either[Snag, Int] = {
      try {
        buffer.compact()
        val nBytesRead = channel.read(buffer)
        buffer.flip()
        val nBytesRemaining = buffer.remaining()
        if(buffer.remaining() < nBytesNeeded) {
          Left(Snag(
            s"Even after trying to refill buffer, only have $nBytesRemaining bytes remaining, but need $nBytesNeeded"
          ))
        } else {
          Right(nBytesRead)
        }
      } catch {
        case NonFatal(ex) => Left(Snag("Exception while trying to refill buffer", Snag(ex)))
      }
    }
  }

}
