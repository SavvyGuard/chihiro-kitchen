package com.meijisoftware.supervisor

import org.scalatest.funspec.AnyFunSpec

class FileRequestReaderTest extends AnyFunSpec {

  describe("A File Request Reader") {

    it("should be able to read an orders file") {
      val reader = FileRequestReader("orders.json")
    }

    it("should be able to read an empty list") {
      val reader = FileRequestReader("empty_orders.json")
    }

    it("should provide the next order if available") {
      val reader = FileRequestReader("one_order.json")
      assert(reader.nextRequest.nonEmpty)
    }

    it("should return Empty if no more orders") {
      val reader = FileRequestReader("one_order.json")
      reader.nextRequest
      assert(reader.nextRequest.isEmpty)
    }

  }

}
