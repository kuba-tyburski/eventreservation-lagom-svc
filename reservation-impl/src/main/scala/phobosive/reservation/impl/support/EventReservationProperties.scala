package phobosive.reservation.impl.support

import phobosive.reservation.impl.EventReservation.ReservationItem

trait EventReservationProperties {
  val ticketsAvailable: Int
  val clientTicketLimit: Int
  val ticketsReserved: Int
  val ticketMap: Map[String, ReservationItem]
}
