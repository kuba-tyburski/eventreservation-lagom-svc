package phobosive.reservation.impl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import phobosive.reservation.api._
import phobosive.reservation.impl.model.ReservationCommand._
import phobosive.reservation.impl.model.ReservationEvent._
import phobosive.reservation.impl.model.ReservationResponse
//import phobosive.reservation.impl.repository.CommonPostgresProfile.api._
import phobosive.reservation.impl.repository.ReservationReportRepository
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Implementation of the ReservationService.
 */
class EventReservationServiceImpl(
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry,
  reservationReportRepository: ReservationReportRepository,
  db: Database
)(implicit ec: ExecutionContext, as: ActorSystem, mat: Materializer)
    extends ReservationService {

  /**
   * Looks up the entity for the given ID.
   */
  private def entityRef(id: String): EntityRef[Command] =
    clusterSharding.entityRefFor(EventReservation.typeKey, id)

  implicit val timeout = Timeout(5.seconds)

  /**
   * get all reservations for event
   *
   * @return reservations view for all events
   */
  override def getAllReservations: ServiceCall[NotUsed, AllEventReservationsView] = ServiceCall { request =>
    // todo validate data from request jwt, authorization etc

    processSource(
      Source.fromPublisher[CustomerReservationReport](
        db.stream[CustomerReservationReport](
          reservationReportRepository.findAllNotCancelled()
        )
      )
    )
  }

  /**
   * get all reservations made by customer
   *
   * @param customerId - customer unique id
   * @return customer reservations report for all event
   */
  override def getCustomerReservations(customerId: String): ServiceCall[NotUsed, AllEventReservationsView] = ServiceCall { _ =>
    db.run(reservationReportRepository.findByCustomerId(customerId)).flatMap { result =>
      processSource(Source(result))
    }
  }

  /**
   * try to reserve tickets on event
   *
   * @param eventId
   * @param customerId
   * @request ticket reservation request
   * @return
   */
  override def reserveTicket(eventId: String, customerId: String): ServiceCall[TicketReservationRequest, CustomerReservationReport] = ServiceCall { request =>
    import ReservationResponse._
    entityRef(eventId)
      .ask[ReservationResponse](replyTo => NewReservation(customerId, request.quantity, replyTo))
      .map {
        case Success(reservationId, ticketsReserved, reservedAt, status) =>
          CustomerReservationReport(reservationId, eventId, customerId, Some(request.quantity), ticketsReserved, status.entryName, reservedAt, None)

        case ReservationNotFound => throw NotFound("Reservation not found")
        case NoTicketsAvailable  => throw BadRequest("No tickets available for event")
        case IllegalQuantity     => throw BadRequest("Quantity out of accepted range")
        case _ =>
          throw TransportException.fromCodeAndMessage(
            TransportErrorCode.InternalServerError,
            new ExceptionMessage("InternalServerError", "Generic error")
          )
      }
  }

  /**
   * extend existing reservation time
   *
   * @param eventId
   * @param customerId
   * @param reservationId
   * @return
   */
  override def extendTicketReservation(eventId: String, customerId: String, reservationId: String): ServiceCall[NotUsed, CustomerReservationReport] =
    ServiceCall { _ =>
      import ReservationResponse._
      entityRef(eventId)
        .ask[ReservationResponse](replyTo => ExtendReservation(customerId, reservationId, replyTo))
        .map {
          case Success(reservationId, ticketsReserved, reservedAt, status) =>
            CustomerReservationReport(
              reservationId,
              eventId,
              customerId,
              None,
              ticketsReserved,
              status.entryName,
              reservedAt,
              None
            )

          case ReservationNotFound => throw NotFound("Reservation not found")
          case _ =>
            throw TransportException.fromCodeAndMessage(
              TransportErrorCode.InternalServerError,
              new ExceptionMessage("InternalServerError", "Generic error")
            )
        }
    }

  /**
   * cancel existing reservation
   *
   * @param eventId
   * @param customerId
   * @param reservationId
   * @return
   */
  override def cancelTicketReservation(eventId: String, customerId: String, reservationId: String): ServiceCall[NotUsed, CustomerCancelReport] =
    ServiceCall { _ =>
      import ReservationResponse._
      entityRef(eventId)
        .ask[ReservationResponse](replyTo => CancelReservation(customerId, reservationId, replyTo))
        .map {
          case SuccessCancel(reservationId, status, cancelledAt) =>
            CustomerCancelReport(
              reservationId,
              eventId,
              customerId,
              status,
              cancelledAt
            )

          case ReservationNotFound => throw NotFound("Reservation not found")
          case _ =>
            throw TransportException.fromCodeAndMessage(
              TransportErrorCode.InternalServerError,
              new ExceptionMessage("InternalServerError", "Generic error")
            )
        }
    }

  /**
   * This gets published to Kafka.
   */
  override def reservationTopic(): Topic[EventReservationsView] = TopicProducer.taggedStreamWithOffset(Event.Tag) { (tag, fromOffset) =>
    import ReservationResponse._
    persistentEntityRegistry
      .eventStream(tag, fromOffset)
      .filter(_.event.isInstanceOf[ReservationAdded])
      .mapAsync(4) {
        case EventStreamElement(id, _, offset) =>
          entityRef(id)
            .ask[ReservationResponse](replyTo => GetAllReservations(replyTo))
            .map {
              case Ok(ticketsReservations) => computeEventReservationView(id, ticketsReservations) -> offset
            }
      }
  }

  private def processSource(source: Source[CustomerReservationReport, NotUsed]): Future[AllEventReservationsView] =
    source
      .groupBy(1000, report => report.eventId)
      .map(computeEventReservationView)
      .reduce((l, r) => EventReservationsView(l.id, l.reservations ++ r.reservations))
      .mergeSubstreams
      .runWith(Sink.fold(AllEventReservationsView(Seq()))((aggr, erv) => AllEventReservationsView(erv +: aggr.events)))

  private def computeEventReservationView(report: CustomerReservationReport) =
    EventReservationsView(
      report.eventId,
      Map(
        report.customerId -> TicketReservation(report.id, report.ticketsReserved, report.reservedAt)
      )
    )

  private def computeEventReservationView(id: String, ticketsReservations: Map[String, EventReservation.ReservationItem]) =
    EventReservationsView(
      id,
      ticketsReservations.map {
        case (customerId, reservation) =>
          customerId -> TicketReservation(reservation.reservationId, reservation.quantity, reservation.reservedAt)
      }
    )
}
