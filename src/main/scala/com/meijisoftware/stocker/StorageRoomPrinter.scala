package com.meijisoftware.stocker

/**
 * Prints the storage room prettily
 */
object StorageRoomPrinter {

  val header = "Logging current status of storage room\n"

  /**
   * Generates a multiline string representation of the storage
   * room's contents
   * @param storageRoom the storage room to print
   */
  def apply(storageRoom: StorageRoom): String = {
    val allShelvesOrderIds = storageRoom.shelves
      .map(shelf => {
        val orderIds = shelf.orders
          .map(preparedOrder => preparedOrder.orderInfo.orderId)
        (shelf.name, orderIds)
      })
    val shelfDescriptions = allShelvesOrderIds
      .map(shelfOrderIds => "Shelf: " + shelfOrderIds._1  + " Capacity: " + shelfOrderIds._2.size + " Contents: | " + shelfOrderIds._2.mkString(" | ") + " |")
      .mkString("\n")
    header + shelfDescriptions
  }

}
