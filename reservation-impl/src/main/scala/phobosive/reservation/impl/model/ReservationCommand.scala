package phobosive.reservation.impl.model

import akka.actor.typed.ActorRef
import phobosive.reservation.impl.model.ReservationResponse.ReservationReportResponse
object ReservationCommand {
  trait CommandSerializable
  sealed trait Command extends CommandSerializable

  final case class NewReservation(customerId: String, quantity: Int, replyTo: ActorRef[ReservationResponse])            extends Command
  final case class ExtendReservation(customerId: String, reservationId: String, replyTo: ActorRef[ReservationResponse]) extends Command
  final case class CancelReservation(customerId: String, reservationId: String, replyTo: ActorRef[ReservationResponse]) extends Command
  final case class GetCustomerReservations(customerId: String, replyTo: ActorRef[ReservationReportResponse])            extends Command
  final case class GetAllReservations(replyTo: ActorRef[ReservationReportResponse])                                     extends Command
}
