package phobosive.reservation.impl.model

import java.time.Instant

import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger}
import phobosive.reservation.api.ReservationStatus
import play.api.libs.json.{Format, Json}

object ReservationEvent {
  sealed trait Event extends AggregateEvent[Event] {
    override def aggregateTag: AggregateEventTagger[Event] = Event.Tag
  }

  object Event {
    val Tag: AggregateEventShards[Event] = AggregateEventTag.sharded[Event](numShards = 10)
  }

  final case class ReservationAdded(
    customerId: String,
    reservationId: String,
    ticketsOrdered: Int,
    ticketsReserved: Int,
    reservedAt: Instant,
    reservationStatus: ReservationStatus
  ) extends Event
  final case class ReservationExtended(customerId: String, reservationId: String, reservedAt: Instant)   extends Event
  final case class ReservationCancelled(customerId: String, reservationId: String, cancelledAt: Instant) extends Event

  implicit val formatReservationAdded: Format[ReservationAdded]         = Json.format
  implicit val formatReservationExtended: Format[ReservationExtended]   = Json.format
  implicit val formatReservationCancelled: Format[ReservationCancelled] = Json.format
}
