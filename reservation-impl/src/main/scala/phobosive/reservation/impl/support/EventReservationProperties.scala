package phobosive.reservation.impl.support

import phobosive.reservation.impl.EventReservation.ReservationItem

trait EventReservationProperties {
  val ticketsAvailable: Int
  val customerTicketLimit: Int
  val ticketsReserved: Int
  val ticketMap: Map[String, ReservationItem]
}
