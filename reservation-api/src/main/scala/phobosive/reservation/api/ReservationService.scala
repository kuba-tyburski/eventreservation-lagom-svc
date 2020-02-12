package phobosive.reservation.api

import java.time.Instant

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import play.api.libs.json.{Format, Json}

object ReservationService {
  val TOPIC_NAME = "reservations"
}

/**
 * The Reservation service interface.
 * <p>
 * This describes everything that Lagom needs to know about how to serve and
 * consume the ReservationService.
 */
trait ReservationService extends Service {

  /**
   * get all reservations for event
   * @return reservations view for all events
   */
  def getAllReservations: ServiceCall[NotUsed, AllEventReservationsView]

  /**
   * get all reservations made by customer
   * @param customerId - customer unique id
   * @return customer reservations report for all event
   */
  def getCustomerReservations(customerId: String): ServiceCall[NotUsed, AllEventReservationsView]

  /**
   * try to reserve tickets on event
   * @param eventId
   * @param customerId
   * @request ticket reservation request
   * @return
   */
  def reserveTicket(eventId: String, customerId: String): ServiceCall[TicketReservationRequest, CustomerReservationReport]

  /**
   * extend existing reservation time
   * @param eventId
   * @param customerId
   * @param reservationId
   * @return
   */
  def extendTicketReservation(eventId: String, customerId: String, reservationId: String): ServiceCall[NotUsed, CustomerReservationReport]

  /**
   * cancel existing reservation
   * @param eventId
   * @param customerId
   * @param reservationId
   * @return
   */
  def cancelTicketReservation(eventId: String, customerId: String, reservationId: String): ServiceCall[NotUsed, CustomerCancelReport]

  /**
   * This gets published to Kafka.
   */
  def reservationTopic(): Topic[EventReservationsView]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("reservation")
      .withCalls(
        restCall(Method.POST, "/api/event/:eventId/customer/:customerId", reserveTicket _),// todo customerId passed in jwt
        restCall(Method.POST, "/api/event/:eventId/customer/:customerId/reservation/:reservationId/extend", extendTicketReservation _), // todo customerId passed in jwt
        restCall(Method.DELETE, "/api/event/:eventId/customer/:customerId/reservation/:reservationId", cancelTicketReservation _),// todo customerId passed in jwt

        restCall(Method.GET, "/api/customer/:customerId/reservations", getCustomerReservations _),// todo customerId passed in jwt
        restCall(Method.GET, "/api/admin/reservations", getAllReservations _) // todo add some role based access [jwt]
      )
      .withTopics(
        topic(ReservationService.TOPIC_NAME, reservationTopic _)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[EventReservationsView](_.id)
          )
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

/**
 * Ticket reservation request
 *
 * @param quantity - Quantity of tickets reserved
 */
final case class TicketReservationRequest(quantity: Int)
object TicketReservationRequest {
  implicit val format: Format[TicketReservationRequest] = Json.format
}

/**
 * Ticket reservation
 *
 * @param id - reservation id
 * @param quantity - Quantity of tickets reserved
 * @param reservedAt - instant of reservation time
 */
final case class TicketReservation(id: String, quantity: Int, reservedAt: Instant)
object TicketReservation {
  implicit val format: Format[TicketReservation] = Json.format
}

/**
 * Event reservations
 *
 * @param id - event id
 * @param reservations - map of reservations (customerId -> seq[reservation])
 */
final case class EventReservationsView(id: String, reservations: Map[String, TicketReservation])
object EventReservationsView {
  implicit val format: Format[EventReservationsView] = Json.format
}

/**
 * All event reservations
 *
 * @param events - rall events
 */
final case class AllEventReservationsView(events: Seq[EventReservationsView])
object AllEventReservationsView {
  implicit val format: Format[AllEventReservationsView] = Json.format
}

/**
 * Report of making a reservation for customer
 *
 * @param id - reservation id
 * @param eventId
 * @param customerId
 * @param ticketsOrdered - no of tickets ordered
 * @param ticketsReserved - no of tickets reserved
 * @param status - reservation status
 * @param reservedAt - reservation time
 * @param cancelledAt - cancel time
 * // todo add some flag for notifying it went from reserved to buy
 */
final case class CustomerReservationReport(
  id: String,
  eventId: String,
  customerId: String,
  ticketsOrdered: Option[Int],
  ticketsReserved: Int,
  status: String,
  reservedAt: Instant,
  cancelledAt: Option[Instant]
)
object CustomerReservationReport {
  implicit val format: Format[CustomerReservationReport] = Json.format
}

/**
 * Report of making a reservation for customer
 *
 * @param id - reservation id
 * @param eventId
 * @param customerId
 * @param status - reservation status
 * @param cancelledAt - cancel time
 * // todo add some flag for notifying it went from reserved to buy
 */
final case class CustomerCancelReport(
  id: String,
  eventId: String,
  customerId: String,
  status: ReservationStatus,
  cancelledAt: Instant
)
object CustomerCancelReport {
  implicit val format: Format[CustomerCancelReport] = Json.format
}

sealed trait ReservationStatus extends EnumEntry
object ReservationStatus extends Enum[ReservationStatus] with PlayJsonEnum[ReservationStatus] {
  case object Full               extends ReservationStatus
  case object PartialClientLimit extends ReservationStatus
  case object PartialEventFull   extends ReservationStatus
  case object Cancelled          extends ReservationStatus

  val values = findValues
}

//object ReservationStatus extends Enumeration {
//  type ReservationStatus = Value
//  val Full, PartialClientLimit, PartialEventFull, Cancelled = Value
//}
