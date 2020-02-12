package phobosive.reservation.impl

import java.time.Instant
import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.typed.PersistenceId
import org.scalatest.{Matchers, WordSpecLike}
import phobosive.reservation.api.ReservationStatus
import phobosive.reservation.impl.EventReservation.ReservationItem
import phobosive.reservation.impl.model.ReservationResponse._
import phobosive.reservation.impl.model.{ReservationCommand, ReservationResponse}

class EventReservationAggregateSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with WordSpecLike with Matchers {

  private def randomId(): String = UUID.randomUUID().toString

  "EventReservation" should {

    "reserve ticket" in {
      // given
      val probe              = createTestProbe[ReservationResponse]()
      val ref                = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), prepareInitialState()))
      val expectedCustomerId = randomId()

      // when
      ref ! ReservationCommand.NewReservation(expectedCustomerId, 5, probe.ref)

      // then
      val response = probe.expectMessageType[Success]
      response.quantity should equal(5)
      response.status should equal(ReservationStatus.Full)
    }

    "reserve ticket partially due to client limit" in {
      // given
      val probe              = createTestProbe[ReservationResponse]()
      val expectedQuantity   = 3
      val initialState       = prepareInitialState(ticketsAvailable = 10, clientTicketLimit = expectedQuantity)
      val ref                = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))
      val expectedCustomerId = randomId()

      // when
      ref ! ReservationCommand.NewReservation(expectedCustomerId, 5, probe.ref)

      val response = probe.expectMessageType[Success]
      response.quantity should equal(expectedQuantity)
      response.status should equal(ReservationStatus.PartialClientLimit)
    }

    "reserve ticket partially due to event full" in {
      // given
      val probe              = createTestProbe[ReservationResponse]()
      val ticketsAvailable   = 10
      val ticketsReserved    = 8
      val expectedQuantity   = ticketsAvailable - ticketsReserved
      val initialState       = prepareInitialState(ticketsAvailable = ticketsAvailable, ticketsReserved = ticketsReserved)
      val ref                = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))
      val expectedCustomerId = randomId()

      // when
      ref ! ReservationCommand.NewReservation(expectedCustomerId, 5, probe.ref)

      val response = probe.expectMessageType[Success]
      response.quantity should equal(expectedQuantity)
      response.status should equal(ReservationStatus.PartialEventFull)
    }

    "doesn't allow to reserve ticket if client already have" in {
      // given
      val probe                  = createTestProbe[ReservationResponse]()
      val expectedCustomerId     = randomId()
      val expectedReservationMap = Map(expectedCustomerId -> prepareReservationItem())
      val initialState           = prepareInitialState(reservationMap = expectedReservationMap)
      val ref                    = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.NewReservation(expectedCustomerId, 5, probe.ref)

      probe.expectMessage(ReservationAlreadyExists)
    }

    "doesn't allow to reserve ticket with illegal quantity" in {
      // given
      val probe              = createTestProbe[ReservationResponse]()
      val ref                = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), prepareInitialState()))
      val expectedCustomerId = randomId()

      // when
      ref ! ReservationCommand.NewReservation(expectedCustomerId, -3, probe.ref)

      probe.expectMessage(IllegalQuantity)
    }

    "doesn't allow to reserve ticket if not available" in {
      // given
      val probe              = createTestProbe[ReservationResponse]()
      val initialState       = prepareInitialState(ticketsAvailable = 10, ticketsReserved = 10)
      val ref                = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))
      val expectedCustomerId = randomId()

      // when
      ref ! ReservationCommand.NewReservation(expectedCustomerId, 1, probe.ref)

      probe.expectMessage(NoTicketsAvailable)
    }

    "extend reservation" in {
      // given
      val probe                   = createTestProbe[ReservationResponse]()
      val expectedCustomerId      = randomId()
      val expectedReservationId   = randomId()
      val expectedReservationItem = prepareReservationItem(reservationId = expectedReservationId)
      val expectedReservationMap  = Map(expectedCustomerId -> expectedReservationItem)
      val initialState            = prepareInitialState(reservationMap = expectedReservationMap)
      val ref                     = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.ExtendReservation(expectedCustomerId, expectedReservationId, probe.ref)

      // then
      val response = probe.expectMessageType[Success]
      response.quantity should equal(expectedReservationItem.quantity)
      response.status should equal(expectedReservationItem.status)
      response.reservedAt should be > expectedReservationItem.reservedAt
    }

    "fail to extend reservation if not matched it customer id" in {
      // given
      val probe                 = createTestProbe[ReservationResponse]()
      val expectedCustomerId    = randomId()
      val expectedReservationId = randomId()
      val initialState          = prepareInitialState()
      val ref                   = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.ExtendReservation(expectedCustomerId, expectedReservationId, probe.ref)

      probe.expectMessage(ReservationNotFound)
    }

    "fail to extend reservation if not found by reservation id" in {
      // given
      val probe                  = createTestProbe[ReservationResponse]()
      val expectedCustomerId     = randomId()
      val expectedReservationId  = randomId()
      val expectedReservationMap = Map(expectedCustomerId -> prepareReservationItem())
      val initialState           = prepareInitialState(reservationMap = expectedReservationMap)
      val ref                    = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.ExtendReservation(expectedCustomerId, expectedReservationId, probe.ref)

      probe.expectMessage(ReservationNotFound)
    }

    "cancel reservation" in {
      // given
      val probe                   = createTestProbe[ReservationResponse]()
      val expectedCustomerId      = randomId()
      val expectedReservationId   = randomId()
      val expectedReservationItem = prepareReservationItem(reservationId = expectedReservationId)
      val expectedReservationMap  = Map(expectedCustomerId -> expectedReservationItem)
      val initialState            = prepareInitialState(reservationMap = expectedReservationMap)
      val ref                     = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.CancelReservation(expectedCustomerId, expectedReservationId, probe.ref)

      // then
      val response = probe.expectMessageType[SuccessCancel]
      response.status should equal(ReservationStatus.Cancelled)
      response.cancelledAt should be > expectedReservationItem.reservedAt
    }

    "fail to cancel reservation if not matched it customer id" in {
      // given
      val probe                 = createTestProbe[ReservationResponse]()
      val expectedCustomerId    = randomId()
      val expectedReservationId = randomId()
      val initialState          = prepareInitialState()
      val ref                   = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.CancelReservation(expectedCustomerId, expectedReservationId, probe.ref)

      probe.expectMessage(ReservationNotFound)
    }

    "fail to cancel reservation if not found by reservation id" in {
      // given
      val probe                  = createTestProbe[ReservationResponse]()
      val expectedCustomerId     = randomId()
      val expectedReservationId  = randomId()
      val expectedReservationMap = Map(expectedCustomerId -> prepareReservationItem())
      val initialState           = prepareInitialState(reservationMap = expectedReservationMap)
      val ref                    = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.CancelReservation(expectedCustomerId, expectedReservationId, probe.ref)

      probe.expectMessage(ReservationNotFound)
    }

    "get customer reservations" in {
      // given
      val probe                    = createTestProbe[ReservationReportResponse]()
      val expectedCustomerId       = randomId()
      val expectedCustomerId2      = randomId()
      val expectedReservationId    = randomId()
      val expectedReservationItem  = prepareReservationItem(reservationId = expectedReservationId)
      val expectedReservationItem2 = prepareReservationItem()
      val expectedReservationMap =
        Map(
          (expectedCustomerId  -> expectedReservationItem),
          (expectedCustomerId2 -> expectedReservationItem2)
        )
      val initialState = prepareInitialState(reservationMap = expectedReservationMap)
      val ref          = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.GetCustomerReservations(expectedCustomerId, probe.ref)

      // then
      val response     = probe.expectMessageType[Ok]
      val reservations = response.ticketsReservations
      reservations should have size 1
      reservations should contain key expectedCustomerId
      reservations(expectedCustomerId) should equal(expectedReservationItem)
    }

    "return empty response if no customer reservations found" in {
      // given
      val probe                    = createTestProbe[ReservationReportResponse]()
      val expectedCustomerId       = randomId()
      val expectedCustomerId2      = randomId()
      val expectedCustomerId3      = randomId()
      val expectedReservationId    = randomId()
      val expectedReservationItem  = prepareReservationItem(reservationId = expectedReservationId)
      val expectedReservationItem2 = prepareReservationItem()
      val expectedReservationMap =
        Map(
          (expectedCustomerId  -> expectedReservationItem),
          (expectedCustomerId2 -> expectedReservationItem2)
        )
      val initialState = prepareInitialState(reservationMap = expectedReservationMap)
      val ref          = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.GetCustomerReservations(expectedCustomerId3, probe.ref)

      // then
      val response     = probe.expectMessageType[Ok]
      val reservations = response.ticketsReservations
      reservations should have size 0
    }

    "get all reservations" in {
      // given
      val probe                    = createTestProbe[ReservationReportResponse]()
      val expectedCustomerId       = randomId()
      val expectedCustomerId2      = randomId()
      val expectedReservationId    = randomId()
      val expectedReservationItem  = prepareReservationItem(reservationId = expectedReservationId)
      val expectedReservationItem2 = prepareReservationItem()
      val expectedReservationMap =
        Map(
          (expectedCustomerId  -> expectedReservationItem),
          (expectedCustomerId2 -> expectedReservationItem2)
        )
      val initialState = prepareInitialState(reservationMap = expectedReservationMap)
      val ref          = spawn(EventReservation.create(PersistenceId("EventReservation", randomId()), initialState))

      // when
      ref ! ReservationCommand.GetAllReservations(probe.ref)

      // then
      val response     = probe.expectMessageType[Ok]
      val reservations = response.ticketsReservations
      reservations should have size 2
      reservations should contain key expectedCustomerId
      reservations should contain key expectedCustomerId2
      reservations(expectedCustomerId) should equal(expectedReservationItem)
      reservations(expectedCustomerId2) should equal(expectedReservationItem2)
    }
  }

  private def prepareInitialState(
    ticketsAvailable: Int                        = 30,
    clientTicketLimit: Int                       = 5,
    ticketsReserved: Int                         = 0,
    reservationMap: Map[String, ReservationItem] = Map()
  ) = new EventReservation(ticketsAvailable, clientTicketLimit, ticketsReserved, reservationMap)

  private def prepareReservationItem(
    reservationId: String     = UUID.randomUUID().toString,
    quantity: Int             = 3,
    reservedAt: Instant       = Instant.now(),
    status: ReservationStatus = ReservationStatus.Full
  ) = ReservationItem(reservationId, quantity, reservedAt, status)
}
