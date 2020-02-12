package phobosive.reservation.impl.model

import java.time.Instant

import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import phobosive.reservation.api.ReservationStatus
import phobosive.reservation.impl.EventReservation.ReservationItem

sealed trait ReservationResponse extends EnumEntry
object ReservationResponse extends Enum[ReservationResponse] with PlayJsonEnum[ReservationResponse] {
  sealed trait ReservationReportResponse                                 extends ReservationResponse
  final case class Ok(ticketsReservations: Map[String, ReservationItem]) extends ReservationReportResponse

  sealed trait ReservationAcceptResponse                                                                         extends ReservationResponse
  final case class Success(reservationId: String, quantity: Int, reservedAt: Instant, status: ReservationStatus) extends ReservationAcceptResponse
  final case class SuccessCancel(reservationId: String, status: ReservationStatus, cancelledAt: Instant)         extends ReservationAcceptResponse

  sealed trait ReservationRejectedResponse   extends ReservationResponse
  final case object GenericError             extends ReservationRejectedResponse
  final case object ReservationAlreadyExists extends ReservationRejectedResponse
  final case object ReservationNotFound      extends ReservationRejectedResponse
  final case object NoTicketsAvailable       extends ReservationRejectedResponse
  final case object IllegalQuantity          extends ReservationRejectedResponse

  val values = findValues
}
