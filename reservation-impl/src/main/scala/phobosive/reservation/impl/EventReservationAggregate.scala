package phobosive.reservation.impl

import java.time.Instant

import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.{EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import com.lightbend.lagom.scaladsl.persistence._
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import phobosive.reservation.api.ReservationStatus
import phobosive.reservation.impl.EventReservation.ReservationItem
import phobosive.reservation.impl.model.ReservationCommand._
import phobosive.reservation.impl.model.ReservationEvent._
import phobosive.reservation.impl.model.ReservationResponse
import phobosive.reservation.impl.model.ReservationResponse._
import phobosive.reservation.impl.support.{CommandHandlerSupport, EventHandlerSupport, EventReservationProperties}
import play.api.libs.json.{Format, Json}

import scala.collection.immutable.Seq

object EventReservation {
  // todo obtain Event data from foreign service, initialize EventReservation with that data
  val initial: EventReservation = EventReservation(30, 5, 0, Map.empty)

  val typeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("EventReservation")

  private[impl] def create(persistenceId: PersistenceId): EventSourcedBehavior[Command, Event, EventReservation] =
    create(persistenceId, EventReservation.initial)

  private[impl] def create(persistenceId: PersistenceId, initial: EventReservation): EventSourcedBehavior[Command, Event, EventReservation] =
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, EventReservation](
        persistenceId  = persistenceId,
        emptyState     = initial,
        commandHandler = (eventReservation, cmd) => eventReservation.applyCommand(cmd),
        eventHandler   = (eventReservation, event) => eventReservation.applyEvent(event)
      )

  def apply(entityContext: EntityContext[Command]): Behavior[Command] = {
    val persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
    create(persistenceId)
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2)) // todo to config
  }

  final case class ReservationItem(reservationId: String, quantity: Int, reservedAt: Instant, status: ReservationStatus)
  implicit val formatReservationItem: Format[ReservationItem] = Json.format

  implicit val formatEventReservation: Format[EventReservation] = Json.format
}

final case class EventReservation(
  ticketsAvailable: Int,
  clientTicketLimit: Int,
  ticketsReserved: Int,
  ticketMap: Map[String, ReservationItem]
) extends EventReservationProperties
    with CommandHandlerSupport
    with EventHandlerSupport {

  def applyCommand(cmd: Command): ReplyEffect[Event, EventReservation] =
    cmd match {
      case GetAllReservations(replyTo)                  => onGetAllReservations(replyTo)
      case GetCustomerReservations(customerId, replyTo) => onGetCustomerReservations(customerId, replyTo)

      case NewReservation(customerId, quantity, replyTo)         => onNewReservation(customerId, quantity, replyTo)
      case ExtendReservation(customerId, reservationId, replyTo) => onExtendReservation(customerId, reservationId, replyTo)
      case CancelReservation(customerId, reservationId, replyTo) => onCancelReservation(customerId, reservationId, replyTo)
    }

  def applyEvent(evt: Event): EventReservation =
    evt match {
      case ReservationAdded(customerId, reservationId, _, ticketsReserved, reservedAt, reservationStatus) =>
        onTicketReservationNew(customerId, reservationId, ticketsReserved, reservedAt, reservationStatus)
      case ReservationExtended(customerId, reservationId, reservedAt) => onTicketReservationExtended(customerId, reservationId, reservedAt)
      case ReservationCancelled(customerId, reservationId, _)         => onTicketReservationCancelled(customerId, reservationId)
    }
}

/**
 * Akka serialization, used by both persistence and remoting, needs to have
 * serializers registered for every type serialized or deserialized. While it's
 * possible to use any serializer you want for Akka messages, out of the box
 * Lagom provides support for JSON, via this registry abstraction.
 *
 * The serializers are registered here, and then provided to Lagom in the
 * application loader.
 */
object ReservationSerializerRegistry extends JsonSerializerRegistry {
  import EventReservation._
//  import ReservationResponse._

  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[EventReservation],
    JsonSerializer[ReservationAdded],
    JsonSerializer[ReservationExtended],
    JsonSerializer[ReservationCancelled],
    // the replies use play-json as well
    JsonSerializer[ReservationItem],
    JsonSerializer[ReservationResponse]
  )
}
