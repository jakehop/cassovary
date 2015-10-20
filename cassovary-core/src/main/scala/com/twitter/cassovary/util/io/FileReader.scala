package com.twitter.cassovary.util.io

import java.io.IOException

import com.twitter.logging.Logger
import com.twitter.util.NonFatal

import scala.io.Source

abstract class FileReader[T](fileName: String) extends Iterator[T] {
  private val log = Logger.get()
  private var _lastLineParsed = 0
  log.info("Starting reading from file %s...\n", fileName)
  private val lines = Source.fromFile(fileName).getLines().map { x =>
    _lastLineParsed += 1
    x
  }
  private var _next: Option[T] = checkNext()

  def hasNext: Boolean = _next.isDefined
  def next(): T = {
    val curr = saveCurr(_next)
    _next = checkNext()
    if (!hasNext) close()
    curr.get
  }

  protected def processOneLine(line: String): T

  // by default, to save state, we simply return x
  // but this is available to be overridden should x
  // be a reference that might be lost.
  protected def saveCurr(x: Option[T]) = x

  private def checkNext(): Option[T] = {
    var found: Option[T] = None
    while (lines.hasNext && found.isEmpty) {
      val line = lines.next().trim
      if (line.charAt(0) != '#') {
        try {
          found = Some(processOneLine(line))
        } catch {
          case NonFatal(exc) =>
            throw new IOException("Parsing failed near line: %d in %s"
                .format(_lastLineParsed, fileName), exc)
        }
      }
    }
    found
  }

  private def close(): Unit = {
    log.info("Finished reading from file %s...\n", fileName)
    Source.fromFile(fileName).close()
  }

}

// T is the id type, typically Int or Long or String
class TwoTsFileReader[T](fileName: String,
    separator: Char,
    idReader: (String, Int, Int) => T)
    extends FileReader[(T, T)](fileName) {

  def processOneLine(line: String): (T, T) = {
    val i = line.indexOf(separator)
    val source = idReader(line, 0, i - 1)
    val dest = idReader(line, i + 1, line.length - 1)
    (source, dest)
  }

}