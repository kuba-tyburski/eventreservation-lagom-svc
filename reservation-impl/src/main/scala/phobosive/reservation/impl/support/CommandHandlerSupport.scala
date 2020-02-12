package phobosive.reservation.impl.support

import java.time.Instant
import java.util.UUID

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.{Effect, ReplyEffect}
import phobosive.reservation.api.ReservationStatus
import phobosive.reservation.impl.EventReservation
import phobosive.reservation.impl.EventReservation.ReservationItem
import phobosive.reservation.impl.model.ReservationEvent.{Event, ReservationAdded, ReservationCancelled, ReservationExtended}
import phobosive.reservation.impl.model.ReservationResponse
import phobosive.reservation.impl.model.ReservationResponse._

trait CommandHandlerSupport { self: EventReservationProperties =>
  private[impl] def onNewReservation(
    customerId: String,
    quantity: Int,
    replyTo: ActorRef[ReservationResponse]
  ): ReplyEffect[Event, EventReservation] =
    ticketMap.get(customerId) match {
      case None =>
        if (quantity <= 0)
          Effect.reply(replyTo)(IllegalQuantity)
        else if (ticketsAvailable == ticketsReserved)
          Effect.reply(replyTo)(NoTicketsAvailable)
        else {
          val reservationId = UUID.randomUUID().toString
          val reservedAt    = Instant.now()

          var reservedQuantity                     = quantity
          var reservationStatus: ReservationStatus = ReservationStatus.Full

          if (customerTicketLimit < reservedQuantity) {
            reservedQuantity  = customerTicketLimit
            reservationStatus = ReservationStatus.PartialCustomerLimit
          }
          if (ticketsAvailable - ticketsReserved < reservedQuantity) {
            reservedQuantity  = ticketsAvailable - ticketsReserved
            reservationStatus = ReservationStatus.PartialEventFull
          }

          Effect
            .persist(ReservationAdded(customerId, reservationId, quantity, reservedQuantity, reservedAt, reservationStatus))
            .thenReply(replyTo)(_ => Success(reservationId, reservedQuantity, reservedAt, reservationStatus))
        }

      case Some(_) => reply(replyTo)(ReservationAlreadyExists)
    }

  private[impl] def onExtendReservation(
    customerId: String,
    reservationId: String,
    replyTo: ActorRef[ReservationResponse]
  ): ReplyEffect[Event, EventReservation] =
    ticketMap.get(customerId) match {
      case Some(reservationItem) if reservationItem.reservationId == reservationId =>
        import reservationItem.{quantity, status}

        val newReservedAt = Instant.now()
        Effect
          .persist(ReservationExtended(customerId, reservationId, newReservedAt))
          .thenReply(replyTo)(_ => Success(reservationId, quantity, newReservedAt, status))

      case _ => reply(replyTo)(ReservationNotFound)
    }

  private[impl] def onCancelReservation(
    customerId: String,
    reservationId: String,
    replyTo: ActorRef[ReservationResponse]
  ): ReplyEffect[Event, EventReservation] =
    ticketMap.get(customerId) match {
      case Some(reservationItem) if reservationItem.reservationId == reservationId =>
        val cancelledAt = Instant.now()
        Effect
          .persist(ReservationCancelled(customerId, reservationId, cancelledAt))
          .thenReply(replyTo)(_ => SuccessCancel(reservationId, ReservationStatus.Cancelled, cancelledAt))

      case _ => reply(replyTo)(ReservationNotFound)
    }

  private[impl] def onGetCustomerReservations(customerId: String, replyTo: ActorRef[ReservationReportResponse]): ReplyEffect[Event, EventReservation] =
    reply(replyTo)(Ok(createReport(customerId)))

  private[impl] def onGetAllReservations(replyTo: ActorRef[ReservationReportResponse]): ReplyEffect[Event, EventReservation] =
    reply(replyTo)(Ok(createReport()))

  private def createReport(customerId: String): Map[String, ReservationItem] =
    this.ticketMap.get(customerId) match {
      case Some(tickets) => Map(customerId -> tickets)
      case None          => Map.empty
    }

  private def createReport(): Map[String, ReservationItem] =
    this.ticketMap
}
