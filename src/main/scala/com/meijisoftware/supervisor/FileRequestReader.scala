package com.meijisoftware.supervisor

import java.time.ZonedDateTime

import com.meijisoftware.models.OrderRequest
import com.meijisoftware.stocker.ShelfTemperature
import play.api.libs.json.{JsArray, JsValue, Json}

import scala.io.Source

trait RequestReader {
  def nextRequest(): Option[OrderRequest]
}

/**
 * Reads order request from a json file
 *
 * Create using the companion object.
 *
 * @param fileName the filename containing the requests
 */
class FileRequestReader(private val fileName: String) extends RequestReader {


  private var rawRequests: Seq[JsValue] = fetchRawRequests()

  /**
   * Get the next request, or None if there are
   * no more requests.
   */
  def nextRequest(): Option[OrderRequest] = {
    val order: Option[OrderRequest] = rawRequests.headOption
      .map(head => asRequest(head))
    order.foreach(_ => {rawRequests = rawRequests.tail})
    order
  }

  /**
   * Get the raw json requests from the file
   */
  private def fetchRawRequests(): Seq[JsValue] = {
    val bufferedSource = Source.fromResource(fileName)
    val fileContents: String = try {
      bufferedSource.mkString
    } finally {
      bufferedSource.close
    }
    val jsonContents = Json.parse(fileContents)
    jsonContents.as[JsArray].value.toSeq
  }

  /**
   * Convert a json value into an OrderRequest
   * @param jsValue the json value object
   */
  private def asRequest(jsValue: JsValue): OrderRequest = {
    val id = jsValue("id").as[String]
    val name = jsValue("name").as[String]
    val temp = ShelfTemperature.withName(jsValue("temp").as[String])
    val shelfLife = jsValue("shelfLife").as[Int]
    val decayRate = jsValue("decayRate").as[Float]
    OrderRequest(id, name, temp, shelfLife, decayRate, ZonedDateTime.now)
  }
}

/**
 * Factor for creating FileRequestReader instances
 */
object FileRequestReader {

    def apply(fileName: String) = new FileRequestReader(fileName)
}
