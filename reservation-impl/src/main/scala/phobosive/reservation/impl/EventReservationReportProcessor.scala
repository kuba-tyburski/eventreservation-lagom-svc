package phobosive.reservation.impl

import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, ReadSideProcessor}
import phobosive.reservation.api.{CustomerReservationReport, ReservationStatus}
import phobosive.reservation.impl.model.ReservationEvent._
import phobosive.reservation.impl.repository.ReservationReportRepository

class EventReservationReportProcessor(readSide: SlickReadSide, repository: ReservationReportRepository) extends ReadSideProcessor[Event] {
  override def buildHandler(): ReadSideProcessor.ReadSideHandler[Event] =
    readSide
      .builder[Event]("event-reservation-report")
      .setGlobalPrepare(repository.createTable())
      .setEventHandler[ReservationAdded] { envelope =>
        repository.createReport(
          CustomerReservationReport(
            envelope.event.reservationId,
            envelope.entityId,
            envelope.event.customerId,
            Some(envelope.event.ticketsOrdered),
            envelope.event.ticketsReserved,
            envelope.event.reservationStatus.entryName,
            envelope.event.reservedAt,
            None
          )
        )
      }
      .setEventHandler[ReservationExtended] { envelope =>
        repository.updateReservationTime(envelope.event.reservationId, envelope.event.reservedAt)
      }
      .setEventHandler[ReservationCancelled] { envelope =>
        repository.updateCancelTime(envelope.event.reservationId, envelope.event.cancelledAt, ReservationStatus.Cancelled)
      }
      .build()

  override def aggregateTags: Set[AggregateEventTag[Event]] = Event.Tag.allTags
}
