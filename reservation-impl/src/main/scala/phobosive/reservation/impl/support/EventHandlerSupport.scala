package phobosive.reservation.impl.support

import java.time.Instant

import phobosive.reservation.api.ReservationStatus
import phobosive.reservation.impl.EventReservation
import phobosive.reservation.impl.EventReservation.ReservationItem

trait EventHandlerSupport { self: EventReservation with EventReservationProperties =>
  private[impl] def onTicketReservationNew(customerId: String,
                                           reservationId: String,
                                           quantity: Int,
                                           reservedAt: Instant,
                                           reservationStatus: ReservationStatus) =
    copy(
      ticketsReserved = ticketsReserved + quantity,
      ticketMap       = ticketMap + (customerId -> ReservationItem(reservationId, quantity, reservedAt, reservationStatus))
    )

  private[impl] def onTicketReservationExtended(customerId: String, reservationId: String, reservedAt: Instant) =
    ticketMap.get(customerId) match {
      case Some(reservationItem) =>
        copy(
          ticketMap = ticketMap + (customerId -> reservationItem.copy(reservedAt = Instant.now()))
        )
      case None => this // no hit, we don't change anything
    }

  private[impl] def onTicketReservationCancelled(customerId: String, reservationId: String) =
    ticketMap.get(customerId) match {
      case Some(reservationItem) =>
        copy(
          ticketsReserved = ticketsReserved - reservationItem.quantity,
          ticketMap       = ticketMap - customerId
        )
      case None => this // no hit, we don't change anything
    }
}
